package relay

import (
	"context"
	"errors"
	"fmt"
	"net"
	"time"

	"github.com/go-gost/core/chain"
	"github.com/go-gost/core/hop"
	"github.com/go-gost/core/limiter"
	"github.com/go-gost/core/logger"
	"github.com/go-gost/core/observer/stats"
	"github.com/go-gost/relay"
	ctxvalue "github.com/go-gost/x/ctx"
	xnet "github.com/go-gost/x/internal/net"
	"github.com/go-gost/x/limiter/traffic/wrapper"
	stats_wrapper "github.com/go-gost/x/observer/stats/wrapper"
)

func (h *relayHandler) handleForward(ctx context.Context, conn net.Conn, network string, log logger.Logger) error {
	resp := relay.Response{
		Version: relay.Version1,
		Status:  relay.StatusOK,
	}

	// 多目标节点（负载均衡 / 主备）场景：若选中节点拨号失败，则在同一次连接内
	// 标记该节点并重新选择下一个候选节点重试，避免"主节点已挂 → 本次连接必然失败"。
	maxAttempts := nodeAttempts(h.hop)

	var (
		target *chain.Node
		cc     net.Conn
		err    error
	)
	for attempt := 0; attempt < maxAttempts; attempt++ {
		target = h.hop.Select(ctx)
		if target == nil {
			resp.Status = relay.StatusServiceUnavailable
			resp.WriteTo(conn)
			err = errors.New("target not available")
			log.Error(err)
			return err
		}

		// 非最后一个候选节点使用较短的拨号上限：黑洞节点（丢包 / 无响应）会让
		// TCP 连接一直挂到 Router 的默认超时（15s），导致首次连接直接超时失败。
		// 最后一个候选节点仍用 Router 的正常超时，保证"慢但可用"的节点能连上。
		if maxAttempts > 1 && attempt < maxAttempts-1 {
			var cancel context.CancelFunc
			dctx, cancel := context.WithTimeout(ctx, failoverDialTimeout)
			cc, err = h.options.Router.Dial(dctx, network, target.Addr)
			cancel()
		} else {
			cc, err = h.options.Router.Dial(ctx, network, target.Addr)
		}
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
		resp.Status = relay.StatusHostUnreachable
		resp.WriteTo(conn)
		log.Error(err)

		return err
	}
	defer cc.Close()

	log = log.WithFields(map[string]any{
		"dst": fmt.Sprintf("%s/%s", target.Addr, network),
		"cmd": "forward",
	})

	log.Debugf("%s >> %s", conn.RemoteAddr(), target.Addr)

	{
		clientID := ctxvalue.ClientIDFromContext(ctx)
		rw := wrapper.WrapReadWriter(
			h.limiter,
			conn,
			string(clientID),
			limiter.ServiceOption(h.options.Service),
			limiter.ScopeOption(limiter.ScopeClient),
			limiter.NetworkOption(network),
			limiter.AddrOption(target.Addr),
			limiter.ClientOption(string(clientID)),
			limiter.SrcOption(conn.RemoteAddr().String()),
		)
		if h.options.Observer != nil {
			pstats := h.stats.Stats(string(clientID))
			pstats.Add(stats.KindTotalConns, 1)
			pstats.Add(stats.KindCurrentConns, 1)
			defer pstats.Add(stats.KindCurrentConns, -1)
			rw = stats_wrapper.WrapReadWriter(rw, pstats)
		}
		conn = xnet.NewReadWriteConn(rw, rw, conn)
	}

	if h.md.noDelay {
		if _, err := resp.WriteTo(conn); err != nil {
			log.Error(err)
			return err
		}
	}

	switch network {
	case "udp", "udp4", "udp6":
		rc := &udpConn{
			Conn: conn,
		}
		if !h.md.noDelay {
			// cache the header
			if _, err := resp.WriteTo(&rc.wbuf); err != nil {
				return err
			}
		}
		conn = rc
	default:
		rc := &tcpConn{
			Conn: conn,
		}
		if !h.md.noDelay {
			// cache the header
			if _, err := resp.WriteTo(&rc.wbuf); err != nil {
				return err
			}
		}
		conn = rc
	}

	t := time.Now()
	log.Debugf("%s <-> %s", conn.RemoteAddr(), target.Addr)
	xnet.Transport(conn, cc)
	log.WithFields(map[string]any{
		"duration": time.Since(t),
	}).Debugf("%s >-< %s", conn.RemoteAddr(), target.Addr)

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
