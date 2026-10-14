# flux-panel转发面板 哆啦A梦转发面板

## 项目更新说明

本项目基于 [go-gost/gost](https://github.com/go-gost/gost) 和 [go-gost/x](https://github.com/go-gost/x) 两个开源库，实现了转发面板。

---

## 本项目改动

### 1. 节点安装脚本 `install.sh` 兼容 Alpine 等主流发行版

- 由 `#!/bin/bash` 改写为 **POSIX `sh`**，可在 Alpine 的 busybox `ash` 下直接运行，无需安装 bash。
- 新增 **init 系统自动探测**：自动识别 `systemd` / `OpenRC`，并生成对应服务文件（systemd unit 或 OpenRC `/etc/init.d/gost`），Alpine/Gentoo 等 OpenRC 系统不再需要 systemd。
- 新增 **包管理器适配**：`apt / dnf / yum / apk / pacman / zypper / emerge / xbps-install`，用于按需安装 `curl`、`dsniff` 等依赖。
- 新增 **下载器回退**：`curl` 优先，缺失时回退 `wget`，两者都没有时尝试自动安装 `curl`。
- 新增 **root 提权**（`sudo`/`doas`）与 **`/dev/tty` 交互回退**，兼容 `curl | sh` 场景。
- Alpine 官方仓库无 `dsniff`，会**优雅跳过 tcpkill** 并给出提示，不影响主流程。
- 修复历史缺陷：`main()` 中调用未定义的 `block_protocol`、菜单编号与逻辑不一致。
- 服务定义注入 `GOMEMLIMIT`，配合下文的内存优化，降低小内存机器被 OOM kill 的概率。

### 2. 转发 / 隧道 / 节点 支持批量删除

- 后端新增批量接口（请求体 `{ ids: [...] }`）：
  - 转发：`POST /api/v1/forward/batch-delete`、`POST /api/v1/forward/batch-force-delete`
  - 隧道：`POST /api/v1/tunnel/batch-delete`
  - 节点：`POST /api/v1/node/batch-delete`
- 服务层逐条复用既有单条删除逻辑（含权限校验、GOST 服务删除、依赖占用检查），**聚合成功/失败明细**，支持部分成功。统一返回结构：
  ```json
  {
    "total": 3,
    "success": 2,
    "failed": 1,
    "failures": [{ "id": 9, "reason": "该节点还有 1 个隧道作为入口节点在使用" }]
  }
  ```
- 前端转发、隧道、节点列表新增 **多选（勾选）+ 全选 + 批量删除** 操作条与二次确认弹窗；转发批量删除额外提供 **强制删除** 开关（跳过节点端校验）。

### 3. 节点监控简化上报 + 小内存（64M）稳定性

- **简化上报逻辑**：节点上报由"每 2 秒采集 CPU/内存/网络/开机时长"改为**仅上报在线心跳**（每 30 秒一次，payload 仅含 `online` 与时间戳）。
- 节点**在线/离线状态**本由 WebSocket 连接的建立与断开来判定，因此简化后不影响在线状态显示。
- 移除对 `gopsutil`（cpu/host/mem/net）的采集调用，**去掉了每次阻塞 1 秒的 `cpu.Percent`**，显著降低内存分配与 CPU 占用，配合 `GOMEMLIMIT` 有效降低 64M 机器被 OOM kill 的风险。
- 服务端与前端同步适配：`WebSocketServer` 改为按在线心跳确认；节点页不再展示已下线的 CPU/内存/流量指标。
- ⚠️ 节点端为 Go 二进制，需**重新编译并发布** `go-gost` 后该改动才会生效。

### 4. 用户管理「权限」分支支持批量处理

- 权限弹窗「分配新权限」的隧道选择改为**多选**，可一次为同一用户分配多个隧道（共享同一套流量/数量/到期配置）。
- 「已有权限」表格新增 **勾选列 + 全选 + 批量删除**，可一次移除该用户的多个隧道权限。
- 后端新增接口：`POST /api/v1/tunnel/user/batch-assign`、`POST /api/v1/tunnel/user/batch-remove`；均沿用 `@RequireRole`（仅管理员），批量删除时**逐条校验权限归属该用户**，防止越权。

### 5. 命名空间统一为 `sola614`、镜像仓库切换至 GHCR + 构建链路修复

**命名空间统一**（原 `bqlpfy/*` → `sola614/*`）

- Docker 镜像：`.github/workflows/docker-build.yml` 的推送目标，以及 `docker-compose-v4.yml` / `docker-compose-v6.yml` 的 `image:` 引用
- 安装脚本：`install.sh` 的 `REPO`、`panel_install.sh` 的 Release 下载地址
- 后端：`NodeServiceImpl` 生成的节点安装命令中的脚本下载地址
- 文档与前端：`README.md` 的安装命令、前端页脚 GitHub 链接（`profile.tsx` / `index.tsx` / `admin.tsx`）
- iOS：`PRODUCT_BUNDLE_IDENTIFIER` 由 `bqlpfy.flux*` 改为 `sola614.flux*`

**镜像仓库切换为 GitHub Container Registry（GHCR）**

- 镜像地址由 Docker Hub 的 `sola614/*` 改为 **`ghcr.io/sola614/*`**，与代码仓库同源，且**无需再配置任何 Secrets**——登录使用 Actions 内置的 `GITHUB_TOKEN`。
- `build-vite` / `build-java` 两个任务补充 `permissions: packages: write`，登录步骤改为 `registry: ghcr.io`。
- `docker buildx build` 增加 `--provenance=false`：不生成 attestation 附属清单，避免旧版 Docker / containerd 拉取多架构镜像时报 `no match for platform in manifest`。

> ⚠️ **首次推送成功后，必须手动把两个 Package 设为 Public**：GHCR 的容器包**默认是 Private**，不会自动继承仓库的公开可见性。请打开
> `https://github.com/users/sola614/packages/container/vite-frontend/settings`
> 与 `https://github.com/users/sola614/packages/container/springboot-backend/settings`，
> 在 *Danger Zone* 中将可见性改为 **Public**。否则 `panel_install.sh` 执行 `docker compose pull` 时会报 `unauthorized`。

**修复 Docker 镜像构建失败（两处硬阻塞）**

1. **前端依赖 peer 冲突（ERESOLVE）**：`package.json` 将 `@heroui/theme` 锁死为 `2.4.19`，而 `@heroui/accordion` 等组件的 `^` 范围会解析到最新版并声明 `@heroui/theme >= 2.4.24`，导致 `npm install` 直接报错退出，镜像构建必然失败。现已将 `@heroui/theme` 调整为 `^2.4.24`，与所有 `@heroui/*` 组件的 peer 约束对齐。
2. **后端镜像 apt 源替换失败**：`eclipse-temurin:21-jre` 基于 `ubuntu:24.04`（noble），该版本已改用 deb822 格式的 `/etc/apt/sources.list.d/ubuntu.sources`，旧路径 `/etc/apt/sources.list` 不存在，原 `sed` 会因读不到文件而报错终止构建。现改为**对两种格式都做兼容处理**。

**构建可复现性加固**

- `vite-frontend/.gitignore` 原先显式忽略了 `package-lock.json`，导致依赖版本无法锁定、每次构建都可能因上游发版而"漂移"。现已取消忽略并**提交 `package-lock.json`**，`Dockerfile` 同步由 `npm install` 改为 **`npm ci`**，构建结果完全可复现。
- 新增 `vite-frontend/.dockerignore` 与 `springboot-backend/.dockerignore`，避免宿主机的 `node_modules` / `target` 被 `COPY . .` 复制进镜像而破坏构建。

> ℹ️ CI 版本闸门说明：workflow 顶部 `env.VERSION` 决定是否构建镜像。**tag 已存在时不重建镜像**，仅当 `go-gost/` 有改动时才重建 GOST 二进制，否则只覆盖 Release 中的脚本与配置。因此若需重新构建并推送镜像，请**同步递增 `VERSION`**（并相应更新 `docker-compose-*.yml` 与 `install.sh` 中的版本号），或先删除同名 tag。

### 6. 转发管理交互与级联删除优化

**① 编辑提交后保持分组展开**

- 原实现提交成功后调用 `loadData()`（带整页 loading），列表被卸载，分类视图下所有隧道分组都会折叠回默认状态。
- 现将分组 `Accordion` 改为**受控**（`selectedKeys` + `onSelectionChange`），展开状态持久化到 `localStorage`（键名 `forward-expanded-tunnels`），并把提交后的刷新改为 `loadData(false)`。
- 保存时会**自动展开该转发所属的隧道分组**，即"在 A 服务器编辑某中转配置保存后，A 服务器保持展开"。

**② 删除节点 / 隧道支持级联删除关联转发**

- 原逻辑是"有下级依赖即拒绝删除"：删隧道时若存在转发或用户权限会报错；删节点时若被隧道引用为入口/出口节点会报错。
- 现为删除接口新增 `cascade` 参数（默认 `false`，保持原有安全语义），`cascade: true` 时按依赖链逐级清理：**节点 → 隧道 → 转发 → 用户权限**。
  - `POST /api/v1/tunnel/delete`、`POST /api/v1/tunnel/batch-delete`
  - `POST /api/v1/node/delete`、`POST /api/v1/node/batch-delete`
- 依赖占用类错误改为返回 **`code: 409`**（`R.CODE_IN_USE`），前端据此弹出"是否同时删除关联转发"的二次确认，确认后以 `cascade: true` 重试；批量删除的失败项也支持一键级联重试。
- 级联删除转发时优先走常规删除（会在节点端同步清理 gost 服务）；**若节点离线导致常规删除失败，则自动回退为强制删除**，避免"节点挂了就永远删不掉"。

**③ 负载均衡策略**

前端可选 4 种策略，最终以 `forwarder.selector.strategy` 下发给节点端 gost：

| 前端选项 | 值 | gost 实现 | 实际行为 |
|---|---|---|---|
| 主备模式 - 自上而下 | `fifo` | `FIFOStrategy` | 固定选第一个节点；该节点失败后被剔除，自动切到下一个 |
| 轮询模式 - 依次轮换 | `round` | `RoundRobinStrategy` | 按原子计数器依次轮换 |
| 随机模式 - 随机选择 | `rand` | `RandomStrategy` | 加权随机（节点 `weight` 元数据，默认 1） |
| 哈希模式 - IP 哈希 | `hash` | `HashStrategy` | 按客户端 IP 做 `crc32 % n` 取模，同一 IP 固定落到同一节点；无 hash 上下文时退化为随机 |

- ⚠️ **仅在单个转发配置了多个目标地址时才生效**。只有一个目标地址时前端会把策略强制为 `fifo`，且不显示策略选择框。
- 负载均衡的对象是**同一转发内的多个目标地址**，不是多个服务器 / 节点。
- 失败剔除由 `maxFails: 1` + `failTimeout: 600s` 控制：某节点拨号失败一次后，10 分钟内不再被选中（避免反复探测）。到期后会重新尝试；即使届时它仍不可用，也会被下面的"连接内重试"兜住，用户无感知。
- 未知策略值会被 gost 回退为轮询（`default → RoundRobinStrategy`）。
- 修复：`getStrategyDisplay` 原先缺少 `hash` 分支，导致哈希策略在卡片上显示为"未知"。

**故障切换（Failover）—— 本次修复**

> 原实现只有 gost 原生的「失败后标记」：选中节点拨号失败 → 标记 → **本次连接直接失败**，下一次连接才会跳过它。因此当主节点（如 B1）已挂时，**第一次连接必然失败**，表现为"配置了主备/负载，但看起来没生效"。
> 实测（B1 不通、B2 通，fifo）：
>
> ```
> attempt 1: FAIL   ← 打到已挂的 B1，本次连接失败
> attempt 2: OK     ← 才切到 B2
> attempt 3..6: OK
> ```

- 修复方式：在 `x/handler/forward/local/handler.go`（端口转发）与 `x/handler/relay/forward.go`（隧道转发的出口节点）的拨号处，增加**候选节点重试循环** —— 选中节点拨号失败时立即标记并**重新选择下一个候选节点**，直到成功或用完所有候选节点；候选节点数取自 `hop.NodeList`，单节点时行为与原来完全一致。
- 修复后实测（同一场景，两条路径都验证）：

  | 路径 | 结果 |
  |---|---|
  | `forward/local`（端口转发） | 成功 6 / 失败 0，首次连接即成功 |
  | `relay`（隧道转发出口节点） | 成功 6 / 失败 0，首次连接即成功 |

- 即：**主节点挂掉后，第一次连接就会在同一次连接内切到备用节点**，不再有"第一次必失败"的窗口。

**黑洞节点（DROP 而非 REJECT）**

> 若失效节点是"黑洞"——例如 IP 被墙、机房掉线，防火墙直接丢包而**不返回 RST** —— TCP 连接不会立即失败，而是一直挂到 Router 的默认拨号超时（**15s**）。即使有了上面的连接内重试，首次连接在客户端看来仍然是"不通"（多数客户端等不到 15s）。
> 实测：`1.1.1.1:12345`、`192.0.2.1:12345` 的拨号均挂满超时（20s+）才失败。

- 修复方式：对**非最后一个候选节点**设置 **3s 拨号上限**（`failoverDialTimeout` 常量，位于上述两个 handler），超时即回落下一个候选节点；**最后一个候选节点仍使用 Router 的正常超时**，保证"慢但可用"的节点仍能连上。
- 修复后实测（B1 = `192.0.2.1:12345` 黑洞、B2 正常，两条路径都验证）：

  | 尝试 | 耗时 | 结果 |
  |---|---|---|
  | 第 1 次 | 3.0s | 成功（3s 后回落 B2） |
  | 第 2 次起 | 0.0s | 成功（B1 已被标记，直连 B2） |

- 节点侧需要更新 gost 二进制才会生效（`install.sh` 会从 Release 拉取最新 `gost-<arch>`）。仅 `go-gost/` 有改动时，CI 会自动重建二进制并更新**同名 Release**，无需递增版本号。

### 7. 转发配置复制 + 流量统计重置

**① 转发管理新增「复制配置」**

- 转发卡片新增**复制**按钮：点击后打开「复制转发配置」弹窗，**隧道选择置空**，其余字段（转发名称、远程地址、出口网卡、负载策略）原样保留，用户只需选择目标隧道即可提交新增。
- 端口处理：入口端口在**节点级别唯一**，直接沿用原端口大概率会被占用，因此复制流程中一旦选择了新隧道，若原端口**不在新隧道入口节点的端口范围内**或**已被其它转发占用**，会自动置空改为「自动分配」，并给出提示。这样复制到同节点/异节点都能一次提交成功；若端口确实可用则会保留。
- 该自动置空**只在复制流程生效**，正常新增/编辑不受影响。

**② 流量统计重置机制评估**

| 对象 | 是否有 `flow_reset_time` | 是否自动重置 |
|---|---|---|
| `user`（账号） | ✅ 有 | ✅ 每月指定日重置（0 = 不重置） |
| `user_tunnel`（用户隧道权限） | ✅ 有 | ✅ 每月指定日重置（0 = 不重置） |
| **`tunnel`（隧道）** | ✅ 有（本次新增，默认每月 1 号） | ✅ 每月指定日重置**该隧道下所有转发** |
| **`forward`（转发）** | ❌ 没有该字段 | ⚠️ 自身不重置，由所属隧道驱动重置 |

- 重置由 `common/task/ResetFlowAsync#reset_flow` 完成，`@Scheduled(cron = "5 0 0 * * ?")` **每天 0 点 05 秒**执行，处理 `user`、`user_tunnel` 两张表，以及**按隧道配置重置其下所有转发**。
- 重置规则不是「每月 1 号」，而是**每条记录可配置的「每月第几号」**（`flow_reset_time` 存 0–31，0 表示不重置）；并处理了月末边界（如设为 31 号而当月只有 30 天时，在当月最后一天执行）。
- 重置使用 `setSql("in_flow = 0, out_flow = 0")` **原子更新**，只动流量字段，不会与到期任务的 `status` 更新互相覆盖。

**③ 转发配置新增「重置流量统计」**

- 转发卡片新增**重置流量**按钮，弹确认框（显示当前上下行统计），确认后清零该转发的 `in_flow` / `out_flow`。
- 后端新增 `POST /api/v1/forward/reset-flow`（`ForwardService#resetForwardFlow`）：校验转发归属（普通用户仅能操作自己的转发，且需持有该隧道权限），再用 `UpdateWrapper.setSql` 原子清零，**只动流量字段**，不会中断或重建转发服务。

### 8. 隧道新增「流量重置日期」，按月重置该隧道下所有转发

- 隧道新增/编辑表单新增**流量重置日期**配置项，选项与「用户权限分配」一致（`不重置` / `每月1号 ~ 每月31号`），**默认每月 1 号**。
- 到达该日期时，由 `ResetFlowAsync`（每天 0 点 05 秒）**同步重置该隧道下所有转发（`forward.tunnel_id` 匹配）的上下行流量**，用一条 `UPDATE ... SET in_flow = 0, out_flow = 0` 原子批量完成，不逐条读改写。
- 顺序与「用户 / 用户隧道」的重置逻辑完全一致：`0` 表示不重置；月末边界也一致（如配置 31 号而当月只有 30 天，则在当月最后一天执行）。
- 该字段只影响**定时重置计划**，与 gost 服务无关，因此修改它**不会**触发下方隧道的转发同步重建。

**数据库结构变更与存量库迁移**

- `tunnel` 表新增 `flow_reset_time int(10) NOT NULL DEFAULT 1`（`gost.sql` 已同步更新，新装库直接生效）。
- 本项目**没有引入 Flyway / Liquibase**，`gost.sql` 只在数据库首次初始化时执行，老库不会自动获得新列。因此新增了 `common/config/DbSchemaInitializer`（`ApplicationRunner`）在应用启动、任何请求进来之前做一次幂等自愈：
  查 `information_schema.COLUMNS`，缺列才执行 `ALTER TABLE`；失败仅记日志、不阻断启动。
  - 存量库升级后首次启动日志会出现：`数据库结构自愈：已为表 tunnel 补充字段 flow_reset_time`。
  - 若账号无 DDL 权限，日志会打印可手动执行的 ALTER 语句。
- **注意**：迁移会给存量隧道统一填充默认值 `1`（每月 1 号），即存量隧道从升级后会开始按月重置；如不需要，把对应隧道的重置日期改成「不重置」即可。

---

## 特性

- 支持按 **隧道账号级别** 管理流量转发数量，可用于用户/隧道配额控制
- 支持 **TCP** 和 **UDP** 协议的转发
- 支持两种转发模式：**端口转发** 与 **隧道转发**
- 可针对 **指定用户的指定隧道进行限速** 设置
- 支持配置 **单向或双向流量计费方式**，灵活适配不同计费模型
- 提供灵活的转发策略配置，适用于多种网络场景
- 支持转发、隧道、节点的**批量删除**
- 转发配置支持**一键复制**（仅需重选隧道，其余配置保留）
- 转发支持**手动重置流量统计**
- 隧道支持配置**流量重置日期**，按月自动重置该隧道下所有转发的流量统计
- 节点监控支持**仅上报在线状态**，适配低内存（64M）系统
- 用户隧道权限支持**批量分配与批量删除**
- 节点安装脚本兼容 **Alpine / OpenRC** 等主流 Linux 发行版

## 部署流程

---

### Docker Compose部署

#### 快速部署

面板端(稳定版)：

```bash
curl -L https://raw.githubusercontent.com/sola614/flux-panel/refs/heads/main/panel_install.sh -o panel_install.sh && chmod +x panel_install.sh && ./panel_install.sh
```

节点端(稳定版)：

```bash
curl -L https://raw.githubusercontent.com/sola614/flux-panel/refs/heads/main/install.sh -o install.sh && chmod +x install.sh && ./install.sh

```

面板端(开发版)：

```bash
curl -L https://raw.githubusercontent.com/sola614/flux-panel/refs/heads/beta/panel_install.sh -o panel_install.sh && chmod +x panel_install.sh && ./panel_install.sh
```

节点端(开发版)：

```bash
curl -L https://raw.githubusercontent.com/sola614/flux-panel/refs/heads/beta/install.sh -o install.sh && chmod +x install.sh && ./install.sh

```

#### 默认管理员账号

- **账号**: admin_user
- **密码**: admin_user

> ⚠️ 首次登录后请立即修改默认密码！

## 免责声明

本项目仅供个人学习与研究使用，基于开源项目进行二次开发。

使用本项目所带来的任何风险均由使用者自行承担，包括但不限于：

- 配置不当或使用错误导致的服务异常或不可用；
- 使用本项目引发的网络攻击、封禁、滥用等行为；
- 服务器因使用本项目被入侵、渗透、滥用导致的数据泄露、资源消耗或损失；
- 因违反当地法律法规所产生的任何法律责任。

本项目为开源的流量转发工具，仅限合法、合规用途。  
使用者必须确保其使用行为符合所在国家或地区的法律法规。

**作者不对因使用本项目导致的任何法律责任、经济损失或其他后果承担责任。**  
**禁止将本项目用于任何违法或未经授权的行为，包括但不限于网络攻击、数据窃取、非法访问等。**

如不同意上述条款，请立即停止使用本项目。

作者对因使用本项目所造成的任何直接或间接损失概不负责，亦不提供任何形式的担保、承诺或技术支持。

请务必在合法、合规、安全的前提下使用本项目。
