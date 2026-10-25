# EasyTier for Minecraft (etmc)

在 Minecraft 内完成 [EasyTier](https://github.com/EasyTier/EasyTier) 组网与多人联机的 NeoForge Mod。
无需虚拟网卡（--no-tun）、无需管理员权限、不修改系统网络配置。

## 特性（v0.1.0）
- 游戏内创建/加入 EasyTier 网络（Network Name + Secret，密码可随机生成 ≥128bit 熵）
- 房主通过 EasyTier 子网代理暴露本地 LAN/服务端；客户端经 --port-forward 本地映射直接用原版多人入口加入
- 三种端口模式：自动 Minecraft 端口 / 自定义 TCP+UDP 白名单（支持范围）/ 可信网络全端口（需显式确认）
- 独立服务器支持：随服启动组网、`/etmc status|peers|reload|reconnect` 管理命令（权限等级 2）
- 进程看护：状态机监测、异常退避重启、kill -9 场景孤儿守护（Linux bash 看守进程）

## 安装
1. NeoForge 1.21.1（21.1.264+）客户端或服务端
2. 放入 `mods/etmc-0.1.0.jar`
3. 确保同目录可找到 EasyTier Core（配置 `etmc-common.toml` 的 `core.binary_path`，或放在 PATH）
4. 客户端按 **K** 打开网络面板

## 配置（config/）
| 文件 | 内容 |
|---|---|
| `etmc-common.toml` | Core 路径、自动启动/重启 |
| `etmc-networks.toml` | 角色、网络名、端口、白名单、转发规则 |
| `etmc-private.toml` | 网络密码（绝不随 NeoForge 配置同步） |

## 网络原理
```
客户端 MC → 127.0.0.1:25565 → EasyTier --port-forward → P2P/中继
  → 房主虚拟 IP 10.126.126.1:25565 → 子网代理(127.0.0.1/32 映射) → 房主 MC
```

## 安全须知
- 共享网络密码的成员可访问虚拟网络中其他已开放服务
- trusted_all 模式暴露房主全部本地服务，仅在可信网络启用（需 `trusted_all_confirmed = true`）
- 密码永不写入日志与崩溃报告（自动脱敏）

## 开发
```
JAVA_TOOL_OPTIONS="-Xmx6g" gradle build        # 打包
JAVA_TOOL_OPTIONS="-Xmx1g" gradle test         # 29 项测试（含真实 easytier 双实例集成测试）
JAVA_TOOL_OPTIONS="-Xmx1g" gradle runServer -x downloadAssets   # headless 服务端
```
