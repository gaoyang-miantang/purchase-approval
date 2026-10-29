#!/usr/bin/env bash
# ==========================================================
# CloudStudio 全栈沙箱 — 一键启动脚本
# 后端：Spring Boot（端口 8000）
# 前端：Vite dev server（端口 3000，/api 反向代理到 8000）
# 用法：在项目根目录执行  bash start.sh
# ==========================================================
set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
RED='\033[0;31m'
NC='\033[0m' # No Color

APP_NAME="🧾 采购审批中心"

echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN}  ${APP_NAME} — CloudStudio 全栈启动 ${NC}"
echo -e "${CYAN}========================================${NC}"
echo ""

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$PROJECT_DIR/backend"
FRONTEND_DIR="$PROJECT_DIR/frontend"
JAR="$BACKEND_DIR/target/purchase-approval-1.0.0.jar"

# --------------- 环境检查 ---------------
if ! command -v java >/dev/null 2>&1; then
  echo -e "${RED}未检测到 java，请先安装 JDK 17：${NC}"
  echo "  sudo apt update && sudo apt install -y openjdk-17-jdk"
  exit 1
fi
if ! command -v node >/dev/null 2>&1; then
  echo -e "${RED}未检测到 node，请先安装 Node.js 18+${NC}"
  exit 1
fi

# --------------- 后端 ---------------
echo -e "${YELLOW}[1/4] 准备后端依赖...${NC}"
cd "$BACKEND_DIR"
if [ -f "$JAR" ]; then
  echo -e "${GREEN}  已找到构建产物，跳过编译${NC}"
else
  if ! command -v mvn >/dev/null 2>&1; then
    echo -e "${RED}未检测到 maven，且没有现成 jar。${NC}"
    echo "  请安装 maven（apt install -y maven），或在本机 mvn package 后上传 target/ 目录"
    exit 1
  fi
  mvn -q -DskipTests package
fi

echo -e "${YELLOW}[2/4] 启动后端服务 (端口 8000)...${NC}"
cd "$BACKEND_DIR"
java -jar "$JAR" --server.port=8000 &
BACKEND_PID=$!
echo -e "${GREEN}  后端 PID: $BACKEND_PID${NC}"

# 等待后端就绪（/api/auth/me 未登录返回 401 即代表服务已起来）
echo -e "${YELLOW}  等待后端启动...${NC}"
READY=0
for _ in $(seq 1 60); do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8000/api/auth/me 2>/dev/null || echo "000")
  if [ "$CODE" = "401" ] || [ "$CODE" = "200" ]; then
    echo -e "${GREEN}  后端已就绪 (HTTP $CODE)${NC}"
    READY=1
    break
  fi
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo -e "${RED}  后端进程已退出，请查看上方日志${NC}"
    exit 1
  fi
  sleep 1
done
if [ "$READY" = "0" ]; then
  echo -e "${RED}  等待超时，后端可能仍在启动，请稍后访问${NC}"
fi

# --------------- 种子数据 ---------------
echo -e "${YELLOW}[2.5/4] 初始化演示数据...${NC}"
echo -e "${GREEN}  已由 SeedData 在后端启动时自动写入（6 个账号 + 9 张单据）${NC}"

# --------------- 前端 ---------------
echo -e "${YELLOW}[3/4] 安装前端依赖...${NC}"
cd "$FRONTEND_DIR"
if [ ! -d "node_modules" ]; then
  npm install
else
  echo -e "${GREEN}  依赖已存在，跳过安装${NC}"
fi

echo -e "${YELLOW}[4/4] 启动前端开发服务器 (端口 3000)...${NC}"
cd "$FRONTEND_DIR"
npx vite --host 0.0.0.0 --port 3000 &
FRONTEND_PID=$!
echo -e "${GREEN}  前端 PID: $FRONTEND_PID${NC}"

# --------------- 完成 ---------------
echo ""
echo -e "${CYAN}========================================${NC}"
echo -e "${GREEN}  ✅ ${APP_NAME} 已启动！${NC}"
echo -e "${CYAN}========================================${NC}"
echo ""
echo -e "  📡 前端地址:  ${GREEN}http://localhost:3000${NC}"
echo -e "  📡 后端地址:  ${GREEN}http://localhost:8000${NC}"
echo -e "  🔑 演示账号:  ${GREEN}zhangwei / lijing / wangfang / chenming${NC}  密码 123456"
echo ""
echo -e "  ${YELLOW}💡 CloudStudio 会自动检测打开的端口，${NC}"
echo -e "  ${YELLOW}   生成可公开访问的预览链接。${NC}"
echo ""
echo -e "  ${YELLOW}按 Ctrl+C 停止所有服务${NC}"
echo ""

# 捕获退出信号，清理子进程
cleanup() {
  echo ""
  echo -e "${YELLOW}正在停止服务...${NC}"
  kill "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
  wait "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
  echo -e "${GREEN}服务已停止。${NC}"
  exit 0
}
trap cleanup SIGINT SIGTERM

# 保持前台运行
wait
