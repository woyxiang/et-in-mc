# P0 底层可行性验证报告

**结论：通过**（本机验证；跨 NAT 场景待多机环境）
**日期：** 2025-10-24
**EasyTier Core：** v2.6.4（easytier-linux-x86_64，锁定）
**环境：** Debian 13 x86_64 容器，普通用户（非 root 完成全部验证，除一次性 sudo TUN 对照实验）

## 1. 核心组网配方（Mod 命令构建器的依据）

### 房主（暴露本地 Minecraft 服务）
```
easytier-core \
  --network-name <name> --network-secret <secret> \
  --no-tun \
  --ipv4 10.126.126.1 \
  --use-smoltcp true \
  --proxy-networks "127.0.0.1/32->10.126.126.1/32" \
  --tcp-whitelist 25565 --udp-whitelist <extra> \
  --listeners tcp://0.0.0.0:<listen_port> \
  --rpc-portal 127.0.0.1:<rpc_port>
```

### 客户端（本地端口转发连房主）
```
easytier-core \
  --network-name <name> --network-secret <secret> \
  --no-tun \
  --ipv4 10.126.126.4        # 或 --dhcp，与房主静态 IP 共存已验证 \
  --listeners tcp://127.0.0.1:<listen_port> \
  --peers tcp://<host>:<listen_port> \
  --rpc-portal 127.0.0.1:<rpc_port> \
  --port-forward tcp://0.0.0.0:25565/10.126.126.1:25565 \
                 udp://0.0.0.0:24454/10.126.126.1:24454
```

### 已验证数据通路
- **TCP：** 客户端 127.0.0.1:25565 → --port-forward → 隧道 → 房主 proxy-networks 映射 → 房主本地 127.0.0.1:25565 服务。回包 `MC-ECHO:ping` ✓
- **UDP：** 客户端 127.0.0.1:24454 → --port-forward → 隧道 → 映射 → 房主本地 UDP 服务。回包 `UDP-ECHO:hello` ✓
- 对照：房主用 TUN 模式（sudo）时客户端 no-tun --port-forward 同样通 → 客户端侧无 TUN 方案与房主模式解耦。

## 2. 白名单矩阵（--tcp-whitelist / --udp-whitelist，房主侧）

| 场景 | 端口在白名单 | 结果 |
|---|---|---|
| TCP 25565 | 是 | ✓ 通（MC-ECHO:ping） |
| TCP 25565 | 否（白名单=19999） | ✗ 超时（拒绝） |
| UDP 24454 | 是 | ✓ 通（UDP-ECHO:hello） |
| UDP 24454 | 否（白名单=19999） | ✗ 超时（拒绝） |

**结论：白名单同时约束子网代理（proxy-networks 映射）的入站访问，可以作为 v0.1 的入站 ACL 机制；空缺省=全放行。**

## 3. 关键发现（影响 Mod 设计）

1. **EasyTier 连接器 socket 绑定物理网卡**（ss 实证：SYN-SENT 源 172.25.87.100%eth0）。对 peer URL 为 127.0.0.1 时握手永不完成（"connect timeout after 2s"）。→ 同机联调必须用非 loopback 地址；生产环境影响小。
2. **proxy-networks 映射要求两侧前缀等长**："127.0.0.1/32->10.126.126.1/32" 合法；"/8->/24" 启动即失败。→ Mod 生成映射时强制同前缀长度。
3. **任一 listener/转发端口绑定失败（Address in use）→ 整个 EasyTier 实例退出。** → Mod 必须在启动前做端口占用预检，并捕获快速退出。
4. 非 root 下创建 TUN 失败 → 非 --no-tun 实例整体退出。--no-tun 是普通用户运行的硬前提（与计划一致）。
5. 同机多实例时，客户端 --port-forward 的本地监听端口不能与远端端口同号（loopback 短路造成假阳性/撞车）。→ 端口冲突校验需覆盖"本机转发端口 vs 本机其他监听"。
6. 默认 DHCP 与房主静态 IP（--ipv4 10.126.126.1）共存正常，成员路由表可见 10.126.126.1/32。
7. 系统已存在的用户 EasyTier 实例（xrcn 网络，11010 系端口）全程未受影响。

## 4. 测试脚本与进程（/home/ecs-user/workspace/et-in-mc/p0/）

- echo_server.py（TCP echo 127.0.0.1:25565）、udp_echo.py（UDP 127.0.0.1:24454）、udp_test.py（发 127.0.0.1:34454）
- 房主实例日志 etH.log、客户端实例日志 etG.log；rpc-portal：房主 25888、客户端 25893
- 本机 listener 端口约定：房主 21010、客户端 21015（避开系统实例 11010-11013/9994/15888）

## 5. 未在本机验证项（待多机环境）

- 跨 NAT 直连/打洞、仅中继回退（EasyTier 上游职责；Mod 侧只需正确传 --peers 与 --listeners）
- 真实 Minecraft 1.21.1 协议过隧道（P3 用真实客户端验证）
- Windows 无管理员权限运行（EasyTier 官方支持 --no-tun；P6 打包阶段在 Windows 实机复验）

## 6. P0 → P1 交接

- EasyTierCommandBuilder 按第 1 节配方生成命令行；
- ConfigValidator 必须实现发现 2/3/5 三条校验；
- 进程管理器需处理"启动后 N 秒内退出=ERROR"（发现 3）；
- 白名单默认关闭（空=全放行），Mod 层默认显式下发 Minecraft 端口。
