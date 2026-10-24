# EasyTier for Minecraft 技术开发计划

**项目版本：** v0.1 规划稿
**目标平台：** Minecraft Java Edition 1.21.1
**Mod Loader：** NeoForge
**开发语言：** Java 21
**组网引擎：** EasyTier Core（独立子进程）
**网络模式：** 无 TUN
**目标：** 在 Minecraft 内完成 EasyTier 组网、端口管理和多人联机，同时提供独立服务端支持。

---

## 1. 项目目标与范围

### 1.1 核心目标

开发一款 NeoForge Mod，使玩家无需单独操作 EasyTier 客户端，即可通过 Minecraft 游戏界面建立或加入 EasyTier 虚拟网络，实现跨互联网的多人联机。

Mod 不重写 Minecraft 网络协议，不修改 EasyTier 的 P2P 协议，也不依赖独立的房间管理服务器。

EasyTier Core 负责虚拟网络、P2P 穿透、连接加密和中继；Mod 负责进程管理、配置生成、端口映射、Minecraft 联机集成及用户界面。

### 1.2 基本原则

1. 始终使用 `--no-tun`，避免创建虚拟网卡。
2. 使用 EasyTier 原生 `--network-name`、`--network-secret` 作为组网依据。
3. 提供玩家直接输入网络名称和密码的方式，不自建邀请码认证体系。
4. Minecraft 主端口自动识别；其他 Mod 使用的端口可通过配置文件指定。
5. 默认最小权限，只允许配置中指定的端口。
6. 提供需要明确开启的可信网络全部端口模式。
7. 支持单人世界开放 LAN 和独立服务器。
8. 优先兼容 Windows x64，随后扩展 Linux 和 macOS。
9. 不修改用户已有的系统网络路由、防火墙规则或 EasyTier 实例。

### 1.3 明确不做的内容（v0.1）

- 完整 VPN/TUN 接入。
- 自建用户账户和好友关系系统。
- 中心化房间数据库。
- 自动识别所有第三方 Mod 的所有网络端口。
- 无法保证兼容的任意 UDP 广播发现协议。
- 自动下载和执行未经验证的 EasyTier 二进制文件。
- Minecraft Bedrock Edition 兼容。

## 2. 使用场景

### 场景 A：单人世界邀请好友

房主进入单人世界，通过 Mod 创建或加入 EasyTier 网络。

Mod 启动 EasyTier，随后打开 LAN 世界或检测现有 LAN 服务。好友使用相同的网络名称和密码加入网络。

好友侧 Mod 为 Minecraft 建立本机端口转发，并使用 Minecraft 原生多人连接功能进入世界。

### 场景 B：已存在的私人 EasyTier 网络

玩家已经有自己的 EasyTier 网络。

Mod 允许填写现有 `network_name`、`network_secret` 和共享节点地址，不强制创建新网络。

网络内既可以存在 Minecraft 玩家，也可以存在通过其他方式运行的 EasyTier 节点。

### 场景 C：独立 Minecraft 服务器

服务端安装 NeoForge Mod，配置好 EasyTier 网络和服务器端口后，通过启动服务器自动启动 EasyTier Core。

客户端安装相同 Mod，加入同一个 EasyTier 网络后即可连接服务器。

服务端运行期间，EasyTier 可以持续运行，不需要依赖游戏界面。

### 场景 D：额外端口与可信服务器网络

用户可设置额外的 TCP/UDP 端口及其映射规则。例如，某些语音、数据同步或其他 Mod 需要额外的网络服务。

对于完全可信的服务器网络，管理员可以开启全部端口访问模式，但需要单独确认安全风险。

## 3. 网络架构

### 3.1 主机端

主机部署以下组件：

- Minecraft Integrated Server 或 Dedicated Server。
- EasyTier Core 子进程。
- 网络进程管理器。
- 虚拟网络节点管理器。
- 入站端口访问策略管理器。
- 配置管理器。

主机通过 EasyTier 的虚拟 IP 提供 Minecraft TCP 服务，并根据配置决定哪些额外服务可以被网络成员访问。

### 3.2 客户端

客户端部署以下组件：

- Minecraft Client。
- EasyTier Core 子进程。
- 本地 TCP/UDP 转发管理器。
- 虚拟网络节点发现模块。
- 连接状态监测模块。
- Minecraft 服务器连接集成模块。

无 TUN 模式下，Minecraft 不能假定操作系统能够直接路由到虚拟 IP。

因此，客户端通过 EasyTier 的 `--port-forward` 建立本机监听地址，再通过该地址连接目标服务器。

基础连接路径：

Minecraft Client → `127.0.0.1:25565` → EasyTier 本地端口转发 → EasyTier P2P/Relay → 房主虚拟 IP:25565 → Minecraft Server。

### 3.3 第一版的网络模型

第一版限制为一个 EasyTier 网络对应一台主要 Minecraft 房主或服务器。

建议约定：

- 房主虚拟 IP：`10.126.126.1`
- 其他成员：EasyTier DHCP 分配
- Minecraft 对外端口：默认 `25565`
- Minecraft 客户端本地映射端口：默认 `25565`

IP 地址和端口都允许修改。

固定房主 IP 的目的不是依赖固定网络拓扑，而是降低第一版的节点发现复杂度，避免必须开发新的控制协议。

后续再支持一个虚拟网络中存在多个 Minecraft 服务器的情况。

## 4. 组网和认证

### 4.1 网络配置

每个网络需要具备：

- Network Name
- Network Secret
- Bootstrap Peers
- 本地节点名称
- IP 分配模式
- 房主目标 IP

Network Name 是 EasyTier 的网络标识，不是唯一且不可伪造的全球房间 ID。

Network Secret 是入网凭证，应支持高强度随机生成和手动输入。

### 4.2 创建网络

创建网络时：

1. 用户输入网络名称，或由 Mod 生成随机名称。
2. 用户输入网络密码，或由 Mod 生成高强度密码。
3. 用户选择共享节点。
4. Mod 保存本地网络配置。
5. Mod 启动 EasyTier Core。
6. 界面显示启动状态和实际分配的虚拟 IP。

默认建议生成不可预测的网络名称和至少 128 bit 随机熵的密码。

### 4.3 加入网络

加入现有网络时，用户输入相同的网络名称、密码和必要的发现节点地址。

Mod 自动启动 EasyTier、查询网络成员以及检测房主是否在线。

不需要独立的邀请码服务器。

### 4.4 网络信息分享

界面提供“复制网络信息”，可复制：

- 网络名称
- 网络密码
- 共享节点地址
- 房主目标 IP
- Minecraft 目标端口

密码默认隐藏，只有用户主动复制或显示时才允许暴露。

共享网络配置不表示授予 Minecraft 管理员权限，也不能替代 Minecraft 自身的账户认证及白名单。

需要明确提示：共享网络密码的成员可能访问虚拟网络中的其他已开放服务。

## 5. 端口管理设计

### 5.1 三种端口管理方式

**模式一：自动 Minecraft 端口**

默认开启。

Mod 检测单人世界 LAN 服务或独立服务器实际使用的 Minecraft TCP 端口，将其加入允许访问列表。

对于单人世界，优先使用可配置的固定端口 `25565`。如果端口被占用，则允许用户指定其他端口。

需要记录实际开放的端口，而不是仅依靠用户设置的端口推断服务器状态。

**模式二：自定义白名单**

用户通过配置文件设置其他 TCP/UDP 端口。

支持：

- 单个 TCP 端口。
- 单个 UDP 端口。
- TCP/UDP 端口范围。
- 本地端口到远端端口的映射。
- 按房主或客户端角色启用规则。
- 独立启用、禁用每条规则。

对入站服务，使用 EasyTier 的端口白名单或 ACL 进行限制。

对客户端主动访问的远端服务，使用 EasyTier `--port-forward` 创建本地代理。

**模式三：可信网络全部端口**

仅用于可信网络或专用服务器。

启用后，不再通过端口白名单限制受保护节点的所有入站 TCP/UDP 目标端口。

该模式不意味着客户端可以透明访问虚拟网络上的所有应用。客户端仍需使用端口转发、SOCKS5 或其他具备代理支持的应用连接方式。

不主动开启公网端口映射，也不创建全端口监听器。

必须进行明确的安全确认，并提供明显的当前模式提示。

### 5.2 第三方 Mod 端口兼容性

第三方 Mod 可能存在以下情况：

- 服务端监听固定端口。
- 服务端监听动态端口。
- 客户端需要访问额外的 TCP 服务。
- 客户端需要访问额外的 UDP 服务。
- 客户端自身也需要接受网络连接。
- 使用 UDP 广播、组播或特殊地址发现其他玩家。

前四种优先使用白名单与显式映射处理。

客户端也充当服务端的情况，需要在客户端建立对应的入站规则，并确定其虚拟 IP。

UDP 广播、组播和依赖真实局域网网卡的功能不能保证通过端口映射实现，应作为单独的兼容性需求。

### 5.3 配置修改策略

不同的参数采用不同的生效方式：

- UI 设置：可随时修改。
- 普通配置：保存时校验。
- 本地代理规则：优先尝试动态更新。
- IP、网络名称、网络密码：通常需要重建 EasyTier 网络连接。
- 无法动态修改的访问控制规则：提示需要重启网络。

第一版允许通过安全重启 EasyTier 应用变更，避免过早依赖复杂管理 RPC。

后续再评估通过 EasyTier 的 PatchConfig RPC 动态管理转发规则。

## 6. 配置系统

### 6.1 配置文件规划

建议分为：

1. `config/easytier-mc-common.toml`：通用运行选项。
2. `config/easytier-mc-networks.toml`：本地网络配置及端口映射。
3. `config/easytier-mc-private.toml`：网络密码等敏感信息。

独立服务器使用自己的运行目录。

密码文件不得经 NeoForge 的服务器配置同步机制发送给客户端。

所有配置文件都应支持版本号，便于未来升级时迁移。

### 6.2 推荐配置结构

以下是 Mod 自己使用的 TOML 格式设计，不是直接交给 EasyTier Core 解析的原生配置：

```toml
config_version = 1

[core]
mode = "managed"
binary_path = ""
auto_start = false
auto_restart = true
no_tun = true

[network]
role = "host"
name = "mc-friends-example"
peers = ["tcp://YOUR-PEER:11010"]
address_mode = "static"
virtual_ip = "10.126.126.1"
host_ip = "10.126.126.1"

[minecraft]
auto_detect_port = true
preferred_port = 25565
local_join_port = 25565

[exposure]
mode = "allowlist"
auto_allow_minecraft = true
tcp_ports = ["25565", "30000-30005"]
udp_ports = ["24454"]

[client_proxy]
bind_address = "127.0.0.1"

[[client_proxy.rules]]
name = "Minecraft"
protocol = "tcp"
target = "host"
local_port = 25565
remote_port = 25565
enabled = true

[[client_proxy.rules]]
name = "Extra UDP Service"
protocol = "udp"
target = "host"
local_port = 24454
remote_port = 24454
enabled = false

[security]
trusted_all_confirmed = false
redact_secrets_in_logs = true
```

密码单独放在私人配置文件中：

```toml
[network]
secret = "REPLACE_WITH_PRIVATE_SECRET"
```

`mode = "trusted_all"` 时，必须同时有 `trusted_all_confirmed = true`，否则配置校验失败并拒绝启动网络。

### 6.3 配置校验

至少验证：

- 网络名称不为空。
- 密码不为空。
- IP 地址格式合法。
- TCP/UDP 端口在合法范围内。
- 端口范围起点不大于终点。
- 同一协议下本地监听地址与端口没有冲突。
- 额外端口不会覆盖 EasyTier Core 正在使用的端口。
- 用户不能通过普通白名单配置意外进入全部端口模式。
- 目标 IP 必须是允许使用的目标节点地址。
- 错误配置不能使 Minecraft 本体崩溃。

对于监听端口已被占用的情况，提供可读的错误信息。

## 7. EasyTier 子进程管理

### 7.1 模块职责

创建 `EasyTierProcessManager`，专门负责 EasyTier Core 生命周期。

核心功能包括：

- 定位 EasyTier Core 可执行文件。
- 验证 Core 版本及文件完整性。
- 判断操作系统和 CPU 架构。
- 构建命令行参数。
- 将密码通过私有配置或受控环境变量传递给进程。
- 启动进程。
- 异步消费标准输出和错误输出。
- 监测进程退出。
- 自动重启异常退出的进程。
- 正常关闭进程。
- 防止同一个实例重复启动。
- 避免误杀系统内其他 EasyTier 实例。

禁止将网络密码直接写入公开日志。

### 7.2 推荐进程状态

使用明确的状态机：

- STOPPED：未运行。
- STARTING：正在启动。
- CONNECTING：正在发现网络节点。
- CONNECTED：已建立网络连接。
- READY：目标服务可以通过映射端口访问。
- RECONNECTING：连接丢失，正在重连。
- STOPPING：正在关闭。
- ERROR：出现无法自动恢复的错误。

网络连接状态与 Minecraft 服务就绪状态必须分开判断。

EasyTier 已经运行，不代表 Minecraft 服务器可连接。

### 7.3 崩溃恢复

EasyTier 异常退出时：

1. 记录退出码与最后一段脱敏日志。
2. 标记连接已中断。
3. 按退避策略尝试重新启动。
4. 重新检查虚拟 IP 和节点状态。
5. 恢复端口代理。
6. 通知玩家恢复结果。

建议设置连续重启次数上限，避免异常情况下无限启动子进程。

### 7.4 外部 Core 兼容

提供两种使用方式：

- Managed：Mod 管理随项目发布的已验证 EasyTier Core。
- Custom：用户指定 EasyTier Core 文件路径。

Custom 模式同样要验证版本和所需能力。

如果用户已运行 EasyTier，第一版不自动接管该系统进程。

## 8. Minecraft 集成

### 8.1 单人世界开放 LAN

需要实现：

- 检测当前是否进入单人世界。
- 检测 Integrated Server 是否已经开放 LAN。
- 获取 LAN 真实监听端口。
- 提供“通过 EasyTier 联机”入口。
- 在用户授权后开放 LAN。
- 在房主离开世界时停止相应的联机服务。
- 清理当前世界的临时端口映射及状态。

应优先通过可用的 NeoForge API 或事件实现；没有稳定扩展点时，才考虑使用 Mixin。

不要为了获取 LAN 端口持续扫描操作系统全部监听端口。

### 8.2 客户端加入服务器

客户端需要完成：

1. 确保 EasyTier 已运行。
2. 确认房主节点在线。
3. 建立本地端口转发。
4. 检查本地监听端口。
5. 测试目标 Minecraft TCP 服务可达性。
6. 使用原生 Minecraft 连接流程加入本地映射地址。

若固定的 `25565` 已被占用，可选择其他本地端口，并使用实际映射端口连接。

额外 Mod 使用固定地址和端口时，可能无法自动适配新端口，必须提示用户。

### 8.3 独立服务器支持

服务端不得加载仅客户端可用的 GUI 类。

应提供：

- 服务器启动时自动组网。
- 服务器准备完成后激活端口策略。
- 服务器关闭时清理托管的 EasyTier 子进程。
- 控制台查询网络状态。
- 控制台重启网络。
- 配置文件独立管理。
- 无需图形桌面环境的运行方式。

推荐管理命令：

- `/etmc status`
- `/etmc peers`
- `/etmc reload`
- `/etmc reconnect`

这些命令必须限制给有权限的管理员。

## 9. GUI 设计

### 9.1 主页面

从 Minecraft 主菜单和多人游戏页面进入。

展示：

- 当前网络名称。
- EasyTier 运行状态。
- 当前虚拟 IP。
- 在线节点数。
- 当前网络连接类型。
- 开放中的 Minecraft 服务。
- 连接或断开按钮。

### 9.2 创建网络页面

包含网络名称、密码、共享节点地址和创建按钮。

生成网络时允许自动生成名称与密码。

### 9.3 加入网络页面

包含网络名称、密码、共享节点列表和加入按钮。

输入成功后，展示目标节点及其在线状态。

### 9.4 端口管理页面

展示自动识别的 Minecraft 端口、自定义端口列表、本机映射规则，以及实际转发状态。

每条端口规则都显示协议、来源端口、目标端口、启用状态和错误原因。

### 9.5 高级设置页面

包括 EasyTier Core 路径、日志等级、自动启动、异常重启、代理监听地址、网络诊断，以及可信网络全部端口模式。

全部端口模式需要明确确认并在启用期间持续显示警告。

## 10. 推荐代码架构

```text
src/main/java/.../etmc/
  EasyTierMinecraft.java

  config/
    ModConfig.java
    NetworkProfile.java
    PortRule.java
    ConfigValidator.java

  core/
    EasyTierProcessManager.java
    EasyTierCommandBuilder.java
    EasyTierBinaryManager.java
    ProcessState.java
    CoreVersionValidator.java

  network/
    NetworkManager.java
    PeerManager.java
    PeerInfo.java
    PortForwardManager.java
    PortPolicyManager.java
    NetworkDiagnostics.java

  minecraft/
    LanWorldManager.java
    MinecraftPortDetector.java
    ServerConnectionManager.java

  client/
    ClientInitializer.java
    screen/
      NetworkHomeScreen.java
      CreateNetworkScreen.java
      JoinNetworkScreen.java
      PortSettingsScreen.java

  server/
    DedicatedServerManager.java
    ServerCommands.java

  util/
    PlatformDetector.java
    SecretRedactor.java
    ProcessLogReader.java
```

各模块通过接口交互，GUI 不直接管理操作系统进程。

建议重点测试 `EasyTierCommandBuilder`、`ConfigValidator`、`PortPolicyManager` 和进程状态机。

## 11. 开发阶段和里程碑

### P0：底层可行性验证

**优先级：最高**

任务：

- 锁定 EasyTier Core 版本。
- 验证 Windows 下无需管理员权限运行 `--no-tun`。
- 两台机器使用相同网络名称和密码成功组网。
- 验证固定房主 IP 与客户端 DHCP 共存。
- 验证客户端 TCP 端口转发。
- 验证 UDP 端口转发及双向通信。
- 验证 TCP/UDP 白名单的实际限制行为。
- 确认房主本地监听服务可通过 EasyTier 虚拟 IP 到达。
- 验证不同 NAT 环境下的中继回退。
- 验证全部端口模式的实际访问边界。

**验收条件：** 不依赖系统虚拟网卡，使用普通用户权限完成跨互联网 TCP/UDP 转发，并验证允许和拒绝访问的行为。

若该阶段失败，应先解决 EasyTier 的实际网络能力问题，不开始 GUI 工作。

### P1：NeoForge 项目与进程管理

任务：

- 建立 NeoForge 1.21.1 MDK 工程。
- 配置 Java 21。
- 完成主 Mod 入口。
- 实现外部 Core 启动与关闭。
- 实现日志捕获。
- 实现进程状态机。
- 实现配置加载和校验。
- 添加测试环境。

**验收条件：** 启动 Minecraft 后能够启动、监测和关闭 EasyTier，并且进程异常不会导致游戏崩溃。

### P2：基础组网功能

任务：

- 创建网络配置。
- 加入现有网络。
- 读取当前虚拟 IP。
- 查询 Peer。
- 检测连接状态。
- 检测连接丢失。
- 基础自动重连。
- 实现 Network Name/Secret GUI。

**验收条件：** 两个 Minecraft 客户端能够通过界面加入同一个 EasyTier 网络。

### P3：Minecraft 联机闭环

任务：

- 检测 LAN 开放状态。
- 获取 Minecraft 服务端端口。
- 建立默认端口映射。
- 完成客户端本地连接。
- 实现退出世界后的资源清理。
- 处理端口占用与连接异常。

**验收条件：** 两台不同网络下的 Minecraft 客户端可以完成进入世界、移动、聊天、方块操作和正常退出。

### P4：自定义端口与高级模式

任务：

- 实现 TCP/UDP 自定义白名单。
- 实现多端口转发。
- 实现端口范围解析。
- 实现配置热重载或受控重启。
- 实现入站访问控制。
- 实现可信全部端口模式。
- 实现端口冲突检测和错误提示。

**验收条件：** 额外的 TCP 和 UDP 服务能够按配置进行通信；被禁止的端口无法通过虚拟网络访问。

### P5：独立服务器与稳定性

任务：

- 实现专用服务器生命周期管理。
- 实现服务器管理命令。
- 验证多个成员加入。
- 验证 Core 崩溃恢复。
- 验证网络掉线恢复。
- 测试长时间运行。
- 排查资源泄漏和子进程残留。

**验收条件：** 独立服务器可以无人值守启动、加入 EasyTier 网络并长期提供 Minecraft 联机服务。

### P6：发布准备

任务：

- Windows x64 正式打包。
- Linux x64 兼容测试。
- 其他架构分阶段适配。
- EasyTier Core 完整性验证。
- README 和安装文档。
- 开源许可证检查。
- 错误诊断文档。
- GitHub Release 构建。
- Modrinth / CurseForge 分发准备。

**验收条件：** 新用户能够按照文档安装和联机，不必手动运行 EasyTier 命令。

## 12. 测试计划

| 测试类别 | 重点场景 |
|---|---|
| 网络 | 相同 NAT、不同 NAT、运营商 NAT、仅中继 |
| 联机 | 单人 LAN、独立服务器、多玩家连接 |
| 协议 | TCP、UDP、双向收发、超时 |
| 端口 | 自动端口、自定义端口、范围、冲突 |
| 安全 | 错误密码、未授权端口、全部端口限制 |
| 进程 | 正常退出、崩溃、重启、重复启动 |
| 平台 | Windows、Linux、macOS |
| 兼容 | 纯净 NeoForge、含网络功能的 Mod 整合包 |
| 稳定性 | 持续连接、频繁切换世界、网络中断 |

重要验收项：

1. 无 TUN，不创建系统虚拟网卡。
2. 正常运行不需要管理员权限。
3. 网络密码不会出现在日志、崩溃报告中。
4. 断开 Minecraft 网络后不会残留失控的代理进程。
5. 白名单之外的服务无法通过覆盖网络访问。
6. 全部端口模式只能通过明确授权启用。
7. 不影响正常的原版多人服务器连接。
8. 单人世界关闭后，不继续暴露已停止的 Minecraft 服务。
9. 独立服务器不依赖任何客户端 GUI。
10. 网络错误可以区分组网失败、代理失败和 Minecraft 服务失败。

## 13. 开发周期估算

| 阶段 | 预计工作时间 |
|---|---|
| P0 底层验证 | 3–5 天 |
| P1 工程与进程管理 | 4–6 天 |
| P2 组网功能 | 4–6 天 |
| P3 Minecraft 联机 | 5–8 天 |
| P4 端口管理 | 4–7 天 |
| P5 服务端和稳定性 | 5–8 天 |
| P6 发布准备 | 3–5 天 |

合计约 **28–45 个有效开发工作日**，即约 6–9 周的单人全职开发量。

## 14. 主要技术风险及应对

**风险 1：无 TUN 模式访问 Minecraft 本地服务存在限制。**
应对：P0 首先验证真实 TCP 连接，不以 EasyTier Peer 在线代替服务可达性测试。

**风险 2：Minecraft LAN 使用不可预测的动态端口。**
应对：优先在 Mod 的 LAN 开放流程中采用固定端口，无法固定时必须向客户端同步或展示实际端口。

**风险 3：第三方 Mod 的通信行为不兼容端口映射。**
应对：明确区分普通 TCP/UDP 服务与广播、组播、客户端监听等特殊场景，不宣称支持所有 Mod。

**风险 4：网络密码泄露。**
应对：安全生成、脱敏、限制文件权限，不将秘密写入普通命令行参数或公开日志。

**风险 5：全部端口模式暴露非游戏服务。**
应对：默认关闭、显式授权、提供 ACL 支持，并优先在受控专用服务器上使用。

**风险 6：系统已有 EasyTier 实例造成冲突。**
应对：托管实例使用独立运行配置，检测管理端口、监听端口和实例名冲突，绝不直接停止用户已有实例。

**风险 7：不同 EasyTier Core 版本行为不同。**
应对：锁定核心版本，针对每个升级版本执行自动化回归测试。

**风险 8：跨平台二进制分发困难。**
应对：按 OS/Arch 分发经过校验的官方构建或符合许可证的自编译构建，允许用户选择已有 Core。

## 15. 版本路线图

### v0.1.0 — Technical MVP

必须完成：

- NeoForge 1.21.1。
- Windows x64。
- 外部 EasyTier Core。
- 无 TUN。
- Network Name/Secret 组网。
- 一个 Minecraft 房主。
- 单一 TCP 游戏端口映射。
- 基础连接状态和错误提示。

### v0.2.0 — Custom Ports

新增：

- 额外 TCP/UDP 端口。
- 端口范围。
- 配置文件管理。
- 入站白名单。
- 可信全部端口模式。
- 常见网络故障诊断。

### v0.3.0 — Dedicated Server

新增：

- 独立服务器支持。
- 控制台管理命令。
- Core 崩溃恢复。
- 多玩家稳定性测试。
- 长时间运行及日志管理。

### v0.4.0 — Cross Platform

新增：

- Linux。
- macOS。
- 更多 CPU 架构。
- 自动选择二进制。
- 安装与更新体验优化。

### v1.0.0 — Stable

目标：

- 完整用户界面。
- 稳定的端口策略管理。
- 完整跨平台适配。
- 可靠的进程生命周期。
- 已验证的多种第三方 Mod 网络配置。
- 完整文档、测试和发布流程。

## 16. 最终完成定义

项目的核心成功标准是：

一名玩家只需要安装 Mod，填写 EasyTier 网络名称和密码，便可以加入另一个网络中的 Minecraft 单人世界或独立服务器。

普通用户无需理解虚拟网卡、系统路由或 NAT 穿透，也无需单独操作 EasyTier 命令行。

需要额外网络端口的 Mod 可以通过配置文件指定，可信服务器可以选择全部端口访问模式。

整个过程保持无 TUN、不要求常规管理员权限、不自动改动系统网络配置，同时尽可能复用 EasyTier 官方原生能力。
