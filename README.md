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

---

## 特性

- 支持按 **隧道账号级别** 管理流量转发数量，可用于用户/隧道配额控制
- 支持 **TCP** 和 **UDP** 协议的转发
- 支持两种转发模式：**端口转发** 与 **隧道转发**
- 可针对 **指定用户的指定隧道进行限速** 设置
- 支持配置 **单向或双向流量计费方式**，灵活适配不同计费模型
- 提供灵活的转发策略配置，适用于多种网络场景
- 支持转发、隧道、节点的**批量删除**
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
