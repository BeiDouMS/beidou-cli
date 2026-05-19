# beidou

BeiDou-Server 游戏服务端命令行管理工具，AI agent 通过 HTTP API 远程启停服务、查询数据、修改配置、发放道具等。

## 安装

### macOS / Linux

```bash
curl -fsSL https://raw.githubusercontent.com/BeiDouMS/beidou-cli/master/install.sh | bash
```

安装到 `~/.local/bin/beidou`，自动写入 `~/.bashrc` / `~/.zshrc`。

### Windows

```powershell
irm https://raw.githubusercontent.com/BeiDouMS/beidou-cli/master/install.ps1 | iex
```

安装并自动加入用户 PATH，重启终端即可使用。

### 手动下载

从 [Releases](https://github.com/BeiDouMS/beidou-cli/releases/latest) 页面下载对应平台的二进制。

## 快速开始

```bash
# 1. 配置服务端连接（一次就行）
beidou config --server http://localhost:8686 --username admin --password 123456

# 2. 查看可用 API
beidou apis

# 3. 测试连通性
beidou call GET /server/v1/online
```

## AI Agent 使用指南

如果你是个 AI agent 需要操作本工具，按以下顺序：

```bash
# 第一步：确认 CLI 可用（二进制在 PATH 中）
beidou version

# 第二步：让用户提供配置，或执行
beidou config --server <url> --username <user> --password <pass>

# 第三步：查看全部可用接口
beidou apis

# 第四步：调用 API（单次）
beidou call GET /server/v1/online

# 批量查询（一次 shell 调用全返回，减少交互）
echo '
GET /server/v1/online
GET /server/v1/version
POST /character/v1/online/list {"pageNum":1,"pageSize":10}
' | beidou batch
```

**注意事项：**
- Git Bash（Windows）下路径参数会被 MSYS 自动转换，beidou 会自动修复。如遇问题加 `MSYS_NO_PATHCONV=1`
- 敏感操作（关服、删除、修改数据）默认拦截，需加 `--force`
- 成功码是 `20000`（不是 HTTP 200），别误判失败
- 首次 `call` 自动登录获取 token，无需手动 `beidou login`
- 非交互环境下 `beidou config` 会报错提示用命令行参数

## 构建

如需从源码构建：

```bash
# JAR
mvn package -DskipTests

# 原生二进制（需要 GraalVM JDK 21 + native-image）
# Linux/macOS: 需要 GCC/Xcode
# Windows: 需要 Visual Studio 2022+ 含 C++ 桌面开发
./build-native.sh  # macOS/Linux
build-native.bat   # Windows
```
