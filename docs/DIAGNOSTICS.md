# 网络诊断指南

## 三类故障的区分（计划 §12.10）
| 现象 | 类别 | 判定方法 |
|---|---|---|
| EasyTier 进程 ERROR/退出 | 组网失败 | `gradle-server*.log` 搜 `[etmc] easytier exited`，看退出码与最近日志 |
| 进程 CONNECTED 但本地映射口无监听 | 代理失败 | `ss -tln | grep <local_port>`；查 easytier 日志 `port_forward` 配置 |
| 本地口监听但连接超时 | 服务失败 | 目标 MC 未开 LAN / 端口不在白名单（拒绝=超时，无服务=立即拒绝） |

## 常见错误
- `Invalid port number: a b`：白名单多端口必须逐条独立（已在 v0.1.0 修复）
- `connect timeout after 2s`（首次）然后成功：连接器绑定物理网卡的首包丢失，重试正常
- `bind failed / Address in use`：EasyTier 任一 listener 冲突即整体退出——检查 11010-11013/9994/15888 及配置端口
- `failed to create config from cli`：proxy-networks 两侧前缀不等长（必须同前缀，最小 /24）
- 端口转发目标写 127.0.0.1：房主侧服务须监听 127.0.0.1 或 0.0.0.0（子网代理映射 127.0.0.1/32→房主虚拟IP/同前缀）

## 管理命令（服务端）
- `/etmc status` 进程+网络状态
- `/etmc peers` 成员列表
- `/etmc reload` 重读配置并重启网络
- `/etmc reconnect` 重连

## 手动检查工具
```
easytier-cli -p 127.0.0.1:<rpc_port> node    # 虚拟 IP
easytier-cli -p 127.0.0.1:<rpc_port> peer    # 成员与隧道类型
easytier-cli -p 127.0.0.1:<rpc_port> route   # 路由（proxy_cidrs）
```
