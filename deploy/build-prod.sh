#!/bin/bash
echo "================================"
echo "   Building for Production"
echo "================================"

echo "[1/2] Building Frontend..."
cd frontend
npm install
npm run build
cp -r dist/ ../backend/openshift-monitor-utility/src/main/resources/static/
cd ..

echo "[2/2] Building Backend JAR..."
cd backend/openshift-monitor-utility
mvn clean package -DskipTests
cd ../..

echo "================================"
echo " BUILD COMPLETE!"
echo " JAR → backend/openshift-monitor-utility/target/*.jar"
echo " Run: java -jar backend/openshift-monitor-utility/target/*.jar"
echo "================================"
