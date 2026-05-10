#!/bin/bash
echo "================================"
echo "   Starting OpenShift Monitor"
echo "================================"

# Start Backend
echo "[1/2] Starting Backend on :8080..."
osascript -e 'tell app "Terminal" to do script "cd '$PWD'/backend/openshift-monitor-utility && mvn spring-boot:run"'

sleep 10

# Start Frontend
echo "[2/2] Starting Frontend on :5173..."
osascript -e 'tell app "Terminal" to do script "cd '$PWD'/frontend && npm run dev"'

echo ""
echo "================================"
echo " Backend  → http://localhost:8080"
echo " Frontend → http://localhost:5173"
echo "================================"
