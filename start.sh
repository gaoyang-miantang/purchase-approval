#!/usr/bin/env bash
# ==========================================================
# CloudStudio 全栈沙箱 — 一键启动脚本
# 后端：Spring Boot（端口 8000）
# 前端：Vite dev server（端口 3000，/api 反向代理到 8000）
# 用法：bash start.sh   （POSIX 兼容，sh start.sh 也能跑）
# ==========================================================
set -e

green()  { printf '\033[0;32m%s\033[0m\n' "$1"; }
yellow() { printf '\033[1;33m%s\033[0m\n' "$1"; }
cyan()   { printf '\033[0;36m%s\033[0m\n' "$1"; }
red()    { printf '\033[0;31m%s\033[0m\n' "$1"; }

APP_NAME="采购审批中心"

cyan "========================================"
cyan "  ${APP_NAME} — CloudStudio 全栈启动"
cyan "========================================"
printf '\n'

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$PROJECT_DIR/backend"
FRONTEND_DIR="$PROJECT_DIR/frontend"
JAR="$BACKEND_DIR/target/purchase-approval-1.0.0.jar"

# --------------- 环境检查 ---------------
if ! command -v java >/dev/null 2>&1; then
  red "未检测到 java，请先安装 JDK 17 或以上："
  printf '  sudo apt update && sudo apt install -y openjdk-17-jdk\n'
  exit 1
fi
if ! command -v node >/dev/null 2>&1; then
  red "未检测到 node，请先安装 Node.js 18+"
  exit 1
fi

# --------------- 端口占用检查 ---------------
# 上一次启动的进程没退出时，Spring Boot 只会丢一句 "Port 8000 was already in use"，
# 在滚动日志里很难发现。这里提前检查并给出可执行的处置命令。
port_in_use() {
  local port="$1"
  if command -v ss >/dev/null 2>&1; then
    ss -ltn 2>/dev/null | grep -q ":$port "
  elif command -v lsof >/dev/null 2>&1; then
    lsof -i ":$port" -sTCP:LISTEN >/dev/null 2>&1
  else
    return 1
  fi
}

for port in 8000 3000; do
  if port_in_use "$port"; then
    red "端口 $port 已被占用，启动会失败。"
    printf '  多半是上一次的服务还没退出。查看占用：\n'
    printf '    ss -ltnp | grep :%s\n' "$port"
    printf '  结束它（把 PID 换成上一步查到的）：\n'
    printf '    kill <PID>\n'
    exit 1
  fi
done
green "端口检查通过（8000 / 3000 空闲）"

# --------------- 后端 ---------------
yellow "[1/4] 准备后端依赖..."
cd "$BACKEND_DIR"
if [ -f "$JAR" ]; then
  green "  已找到构建产物，跳过编译"
else
  if ! command -v mvn >/dev/null 2>&1; then
    red "未检测到 maven，且没有现成 jar。"
    printf '  请安装 maven（apt install -y maven），或在本机 mvn package 后上传 target/ 目录\n'
    exit 1
  fi
  mvn -q -DskipTests package
fi

yellow "[2/4] 启动后端服务 (端口 8000)..."
cd "$BACKEND_DIR"
# 把 JVM 临时目录固定在项目内：部分受限环境对系统 %TEMP% / /tmp 不可写，
# Spring Boot 建不了 tomcat-docbase 目录会直接启动失败。
TMP_DIR="$BACKEND_DIR/target/tmp"
mkdir -p "$TMP_DIR"
java -Djava.io.tmpdir="$TMP_DIR" -jar "$JAR" --server.port=8000 &
BACKEND_PID=$!
green "  后端 PID: $BACKEND_PID"

# 等待后端就绪（/api/auth/me 未登录返回 401 即代表服务已起来）
yellow "  等待后端启动..."
if ! command -v curl >/dev/null 2>&1; then
  yellow "  未检测到 curl，改为固定等待 12 秒"
  sleep 12
else
  READY=0
  i=0
  while [ "$i" -lt 60 ]; do
    i=$((i + 1))
    CODE=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8000/api/auth/me 2>/dev/null || echo "000")
    if [ "$CODE" = "401" ] || [ "$CODE" = "200" ]; then
      green "  后端已就绪 (HTTP $CODE)"
      READY=1
      break
    fi
    if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
      red "  后端进程已退出，请查看上方日志"
      exit 1
    fi
    sleep 1
  done
  if [ "$READY" = "0" ]; then
    red "  等待超时，后端可能仍在启动，请稍后访问"
  fi
fi

# --------------- 种子数据 ---------------
yellow "[2.5/4] 初始化演示数据..."
green "  已由 SeedData 在后端启动时自动写入（6 个账号 + 9 张单据）"

# --------------- 前端 ---------------
yellow "[3/4] 检查前端依赖..."
cd "$FRONTEND_DIR"

# node_modules 不能跨平台复用：Windows 上装的是 esbuild/win32-x64，
# 拷到 Linux 后既没有可执行权限、二进制也对不上，表现为 "vite: Permission denied"。
# 这里只要发现标记不可用，就整目录重装。
NEED_INSTALL=0
[ -d node_modules ] || NEED_INSTALL=1
[ -x node_modules/.bin/vite ] || NEED_INSTALL=1

if [ "$NEED_INSTALL" = "1" ]; then
  yellow "  依赖缺失或在其它系统安装过，正在重新安装（约 1-3 分钟）..."
  rm -rf node_modules
  if ! npm install; then
    red "  npm install 失败。若为网络问题，可先执行："
    printf '    npm config set registry https://registry.npmmirror.com\n'
    exit 1
  fi
else
  green "  依赖已就绪，跳过安装"
fi

yellow "[4/4] 启动前端开发服务器 (端口 3000)..."
cd "$FRONTEND_DIR"
npx vite --host 0.0.0.0 --port 3000 &
FRONTEND_PID=$!
green "  前端 PID: $FRONTEND_PID"

# --------------- 完成 ---------------
printf '\n'
cyan "========================================"
green "  ${APP_NAME} 已启动！"
cyan "========================================"
printf '\n'
printf '  📡 前端地址:  '
green "http://localhost:3000"
printf '  📡 后端地址:  '
green "http://localhost:8000"
printf '  🔑 演示账号:  '
green "zhangwei / lijing / wangfang / chenming"
printf '     密码:     123456\n'
printf '\n'
yellow "  💡 CloudStudio 会自动检测打开的端口，"
yellow "     生成可公开访问的预览链接。"
printf '\n'
yellow "  按 Ctrl+C 停止所有服务"
printf '\n'

# 捕获退出信号，清理子进程（INT/TERM 写法在 bash 与 dash 下都成立）
cleanup() {
  printf '\n'
  yellow "正在停止服务..."
  kill "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
  wait "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
  green "服务已停止。"
  exit 0
}
trap cleanup INT TERM

# 保持前台运行
wait
