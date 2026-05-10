@echo off
echo ================================
echo    Building for Production
echo ================================

echo [1/2] Building Frontend...
cd frontend
call npm install
call npm run build
xcopy /E /I /Y dist ..\backend\openshift-monitor-utility\src\main\resources\static
cd ..

echo [2/2] Building Backend JAR...
cd backend\openshift-monitor-utility
call mvn clean package -DskipTests
cd ..\..

echo ================================
echo  BUILD COMPLETE!
echo  JAR is at:
echo  backend\openshift-monitor-utility\target\*.jar
echo.
echo  To run locally:
echo  java -jar backend\openshift-monitor-utility\target\deployment-monitor-1.0.0.jar
echo ================================
pause
