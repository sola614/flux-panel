package local

import (
	"bufio"
	"bytes"
	"context"
	"errors"
	"net"
	"time"

	"github.com/go-gost/core/chain"
	"github.com/go-gost/core/handler"
	"github.com/go-gost/core/hop"
	md "github.com/go-gost/core/metadata"
	"github.com/go-gost/core/observer/stats"
	"github.com/go-gost/core/recorder"
	ctxvalue "github.com/go-gost/x/ctx"
	xnet "github.com/go-gost/x/internal/net"
	"github.com/go-gost/x/internal/util/forwarder"
	"github.com/go-gost/x/internal/util/sniffing"
	tls_util "github.com/go-gost/x/internal/util/tls"
	rate_limiter "github.com/go-gost/x/limiter/rate"
	xstats "github.com/go-gost/x/observer/stats"
	stats_wrapper "github.com/go-gost/x/observer/stats/wrapper"
	xrecorder "github.com/go-gost/x/recorder"
	"github.com/go-gost/x/registry"
)

func init() {
	registry.HandlerRegistry().Register("tcp", NewHandler)
	registry.HandlerRegistry().Register("udp", NewHandler)
	registry.HandlerRegistry().Register("forward", NewHandler)
}

type forwardHandler struct {
	hop      hop.Hop
	md       metadata
	options  handler.Options
	recorder recorder.RecorderObject
	certPool tls_util.CertPool
}

func NewHandler(opts ...handler.Option) handler.Handler {
	options := handler.Options{}
	for _, opt := range opts {
		opt(&options)
	}

	return &forwardHandler{
		options: options,
	}
}

func (h *forwardHandler) Init(md md.Metadata) (err error) {
	if err = h.parseMetadata(md); err != nil {
		return
	}

	for _, ro := range h.options.Recorders {
		if ro.Record == xrecorder.RecorderServiceHandler {
			h.recorder = ro
			break
		}
	}

	if h.md.certificate != nil && h.md.privateKey != nil {
		h.certPool = tls_util.NewMemoryCertPool()
	}

	return
}

// Forward implements handler.Forwarder.
func (h *forwardHandler) Forward(hop hop.Hop) {
	h.hop = hop
}

func (h *forwardHandler) Handle(ctx context.Context, conn net.Conn, opts ...handler.HandleOption) (err error) {
	defer conn.Close()

	start := time.Now()

	ro := &xrecorder.HandlerRecorderObject{
		Service:    h.options.Service,
		RemoteAddr: conn.RemoteAddr().String(),
		LocalAddr:  conn.LocalAddr().String(),
		Network:    "tcp",
		Time:       start,
		SID:        string(ctxvalue.SidFromContext(ctx)),
	}

	ro.ClientIP = conn.RemoteAddr().String()
	if clientAddr := ctxvalue.ClientAddrFromContext(ctx); clientAddr != "" {
		ro.ClientIP = string(clientAddr)
	} else {
		ctx = ctxvalue.ContextWithClientAddr(ctx, ctxvalue.ClientAddr(conn.RemoteAddr().String()))
	}

	if h, _, _ := net.SplitHostPort(ro.ClientIP); h != "" {
		ro.ClientIP = h
	}

	network := "tcp"
	if conn.RemoteAddr().Network() == "udp" {
		network = "udp"
	}
	ro.Network = network

	pStats := xstats.Stats{}
	conn = stats_wrapper.WrapConn(conn, &pStats)

	defer func() {
		if err != nil {
			ro.Err = err.Error()
		}
		ro.InputBytes = pStats.Get(stats.KindInputBytes)
		ro.OutputBytes = pStats.Get(stats.KindOutputBytes)
		ro.Duration = time.Since(start)

	}()

	if !h.checkRateLimit(conn.RemoteAddr()) {
		return rate_limiter.ErrRateLimit
	}

	var proto string
	if network == "tcp" && h.md.sniffing {
		if h.md.sniffingTimeout > 0 {
			conn.SetReadDeadline(time.Now().Add(h.md.sniffingTimeout))
		}

		br := bufio.NewReader(conn)
		proto, _ = sniffing.Sniff(ctx, br)
		ro.Proto = proto

		if h.md.sniffingTimeout > 0 {
			conn.SetReadDeadline(time.Time{})
		}

		dial := func(ctx context.Context, network, address string) (net.Conn, error) {
			var buf bytes.Buffer
			cc, err := h.options.Router.Dial(ctxvalue.ContextWithBuffer(ctx, &buf), "tcp", address)
			ro.Route = buf.String()
			return cc, err
		}
		sniffer := &forwarder.Sniffer{
			Websocket:           h.md.sniffingWebsocket,
			WebsocketSampleRate: h.md.sniffingWebsocketSampleRate,
			Recorder:            h.recorder.Recorder,
			RecorderOptions:     h.recorder.Options,
			Certificate:         h.md.certificate,
			PrivateKey:          h.md.privateKey,
			NegotiatedProtocol:  h.md.alpn,
			CertPool:            h.certPool,
			MitmBypass:          h.md.mitmBypass,
			ReadTimeout:         h.md.readTimeout,
		}

		conn = xnet.NewReadWriteConn(br, conn, conn)
		switch proto {
		case sniffing.ProtoHTTP:
			return sniffer.HandleHTTP(ctx, conn,
				forwarder.WithDial(dial),
				forwarder.WithHop(h.hop),
				forwarder.WithBypass(h.options.Bypass),
				forwarder.WithHTTPKeepalive(h.md.httpKeepalive),
				forwarder.WithRecorderObject(ro),
			)
		case sniffing.ProtoTLS:
			return sniffer.HandleTLS(ctx, conn,
				forwarder.WithDial(dial),
				forwarder.WithHop(h.hop),
				forwarder.WithBypass(h.options.Bypass),
				forwarder.WithRecorderObject(ro),
			)
		}
	}

	// 多目标节点（负载均衡 / 主备）场景：若选中节点拨号失败，则在同一次连接内
	// 标记该节点并重新选择下一个候选节点重试。否则"主节点已挂"时，本次连接
	// 必然失败，用户会误以为负载均衡/故障切换没有生效。
	maxAttempts := nodeAttempts(h.hop)

	var cc net.Conn
	for attempt := 0; attempt < maxAttempts; attempt++ {
		target := &chain.Node{}
		if h.hop != nil {
			target = h.hop.Select(ctx,
				hop.ProtocolSelectOption(proto),
			)
		}
		if target == nil {
			err = errors.New("node not available")
			return err
		}

		addr := target.Addr
		if opts := target.Options(); opts != nil {
			switch opts.Network {
			case "unix":
				network = opts.Network
			default:
				if _, _, e := net.SplitHostPort(addr); e != nil {
					addr += ":0"
				}
			}
		}

		ro.Network = network
		ro.Host = addr

		var buf bytes.Buffer
		dctx := ctxvalue.ContextWithBuffer(ctx, &buf)
		// 非最后一个候选节点使用较短的拨号上限：黑洞节点（丢包 / 无响应）会让
		// TCP 连接一直挂到 Router 的默认超时（15s），导致首次连接直接超时失败。
		// 最后一个候选节点仍用 Router 的正常超时，保证"慢但可用"的节点能连上。
		if maxAttempts > 1 && attempt < maxAttempts-1 {
			var cancel context.CancelFunc
			dctx, cancel = context.WithTimeout(dctx, failoverDialTimeout)
			cc, err = h.options.Router.Dial(dctx, network, addr)
			cancel()
		} else {
			cc, err = h.options.Router.Dial(dctx, network, addr)
		}
		ro.Route = buf.String()
		if err == nil {
			if marker := target.Marker(); marker != nil {
				marker.Reset()
			}
			break
		}
		// TODO: the router itself may be failed due to the failed node in the router,
		// the dead marker may be a wrong operation.
		if marker := target.Marker(); marker != nil {
			marker.Mark()
		}
	}
	if err != nil {
		return err
	}
	defer cc.Close()

	xnet.Transport(conn, cc)

	return nil
}

// failoverDialTimeout 是"多候选节点"场景下非最后一个候选节点的拨号上限。
// 黑洞节点（防火墙 DROP 而非 REJECT）不会返回 RST，TCP 连接会一直挂到 Router
// 的默认超时（15s），使首次连接在客户端看来就是"不通"。限制为 3s 后可快速
// 回落到下一个候选节点；最后一个候选节点不受此限制。
const failoverDialTimeout = 3 * time.Second

// nodeAttempts 返回单次连接内最多尝试的候选节点数。
// 只有一个候选节点时只尝试一次；多节点（负载均衡 / 主备）时逐个尝试，
// 配合 FailFilter 的失败标记实现"本次连接内"的故障切换。
func nodeAttempts(h hop.Hop) int {
	if h == nil {
		return 1
	}
	if nl, ok := h.(hop.NodeList); ok {
		if n := len(nl.Nodes()); n > 1 {
			return n
		}
	}
	return 1
}

func (h *forwardHandler) checkRateLimit(addr net.Addr) bool {
	if h.options.RateLimiter == nil {
		return true
	}
	host, _, _ := net.SplitHostPort(addr.String())
	if limiter := h.options.RateLimiter.Limiter(host); limiter != nil {
		return limiter.Allow(1)
	}

	return true
}
