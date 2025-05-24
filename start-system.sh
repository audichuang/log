#!/bin/bash

echo "=== 批次作業日誌管理系統啟動腳本 ==="

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 檢查是否在正確的目錄
if [ ! -f "pom.xml" ]; then
    echo -e "${RED}錯誤: 請在項目根目錄執行此腳本${NC}"
    exit 1
fi

# 檢查 Java
echo -e "${BLUE}檢查 Java 環境...${NC}"
if ! command -v java &> /dev/null; then
    echo -e "${RED}錯誤: 未找到 Java，請安裝 Java 17 或更高版本${NC}"
    exit 1
fi

# 檢查 Maven
echo -e "${BLUE}檢查 Maven 環境...${NC}"
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}錯誤: 未找到 Maven，請安裝 Maven${NC}"
    exit 1
fi

# 檢查 Node.js
echo -e "${BLUE}檢查 Node.js 環境...${NC}"
if ! command -v node &> /dev/null; then
    echo -e "${RED}錯誤: 未找到 Node.js，請安裝 Node.js${NC}"
    exit 1
fi

# 清理之前的進程
echo -e "${YELLOW}清理之前的進程...${NC}"
pkill -f "spring-boot:run" 2>/dev/null || true
pkill -f "ng serve" 2>/dev/null || true
sleep 2

# 編譯後端
echo -e "${BLUE}編譯後端代碼...${NC}"
mvn clean compile
if [ $? -ne 0 ]; then
    echo -e "${RED}後端編譯失敗${NC}"
    exit 1
fi

# 創建日誌目錄
echo -e "${BLUE}創建必要的目錄...${NC}"
mkdir -p /tmp/batch/input
mkdir -p /tmp/batch/backup

# 啟動後端
echo -e "${GREEN}啟動後端服務 (端口 8089)...${NC}"
mvn spring-boot:run > backend.log 2>&1 &
BACKEND_PID=$!

# 等待後端啟動
echo -e "${YELLOW}等待後端服務啟動...${NC}"
for i in {1..30}; do
    if curl -s http://localhost:8089/actuator/health > /dev/null 2>&1; then
        echo -e "${GREEN}後端服務啟動成功！${NC}"
        break
    fi
    if [ $i -eq 30 ]; then
        echo -e "${RED}後端服務啟動超時${NC}"
        kill $BACKEND_PID 2>/dev/null
        exit 1
    fi
    sleep 2
    echo -n "."
done

# 檢查前端目錄
if [ ! -d "frontend" ]; then
    echo -e "${RED}錯誤: 未找到 frontend 目錄${NC}"
    kill $BACKEND_PID 2>/dev/null
    exit 1
fi

# 進入前端目錄並安裝依賴
echo -e "${BLUE}檢查前端依賴...${NC}"
cd frontend

if [ ! -d "node_modules" ]; then
    echo -e "${YELLOW}安裝前端依賴...${NC}"
    npm install
    if [ $? -ne 0 ]; then
        echo -e "${RED}前端依賴安裝失敗${NC}"
        kill $BACKEND_PID 2>/dev/null
        exit 1
    fi
fi

# 啟動前端
echo -e "${GREEN}啟動前端服務 (端口 4200)...${NC}"
npm start > ../frontend.log 2>&1 &
FRONTEND_PID=$!

# 回到根目錄
cd ..

# 等待前端啟動
echo -e "${YELLOW}等待前端服務啟動...${NC}"
for i in {1..30}; do
    if curl -s http://localhost:4200 > /dev/null 2>&1; then
        echo -e "${GREEN}前端服務啟動成功！${NC}"
        break
    fi
    if [ $i -eq 30 ]; then
        echo -e "${RED}前端服務啟動超時${NC}"
        kill $BACKEND_PID $FRONTEND_PID 2>/dev/null
        exit 1
    fi
    sleep 2
    echo -n "."
done

echo ""
echo -e "${GREEN}=== 系統啟動完成! ===${NC}"
echo -e "${BLUE}前端地址: ${NC}http://localhost:4200"
echo -e "${BLUE}後端地址: ${NC}http://localhost:8089"
echo -e "${BLUE}API 文檔: ${NC}http://localhost:8089/actuator"
echo ""
echo -e "${YELLOW}系統信息:${NC}"
echo "- 後端進程 PID: $BACKEND_PID"
echo "- 前端進程 PID: $FRONTEND_PID"
echo "- 後端日誌: ./backend.log"
echo "- 前端日誌: ./frontend.log"
echo ""
echo -e "${YELLOW}測試 API:${NC}"
echo "curl http://localhost:8089/api/batch/jobs"
echo "curl http://localhost:8089/api/batch/logs/stream/status"
echo ""
echo -e "${YELLOW}停止系統:${NC}"
echo "kill $BACKEND_PID $FRONTEND_PID"
echo ""
echo -e "${GREEN}按 Ctrl+C 停止所有服務${NC}"

# 等待用戶中斷
trap "echo -e \"\n${YELLOW}正在停止服務...${NC}\"; kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; exit 0" INT

# 持續監控服務狀態
while true; do
    sleep 5
    if ! kill -0 $BACKEND_PID 2>/dev/null; then
        echo -e "${RED}後端服務已停止${NC}"
        kill $FRONTEND_PID 2>/dev/null
        break
    fi
    if ! kill -0 $FRONTEND_PID 2>/dev/null; then
        echo -e "${RED}前端服務已停止${NC}"
        kill $BACKEND_PID 2>/dev/null
        break
    fi
done 