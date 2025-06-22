@echo off
chcp 65001 >nul
title PDA WMS 系统安装脚本

echo.
echo 🔧 PDA WMS 系统安装脚本 v1.0
echo ================================
echo.

:: 检查是否在正确的目录
if not exist "gradlew.bat" (
    echo ❌ 错误：请在项目根目录运行此脚本
    pause
    exit /b 1
)

:: 检查连接的设备
echo 📱 检查连接的PDA设备...
adb devices | findstr "device$" > temp_devices.txt
for /f %%i in ('type temp_devices.txt ^| find /c /v ""') do set DEVICE_COUNT=%%i
del temp_devices.txt

if %DEVICE_COUNT%==0 (
    echo ❌ 没有检测到连接的PDA设备
    echo.
    echo 请确保：
    echo   1. PDA设备已通过USB连接到电脑
    echo   2. 已开启USB调试模式
    echo   3. 已信任此电脑
    echo.
    pause
    exit /b 1
)

echo ✅ 检测到 %DEVICE_COUNT% 个PDA设备
echo.

:: 显示设备列表
echo 📋 连接的设备列表：
for /f "tokens=1,2" %%a in ('adb devices ^| findstr "device$"') do (
    echo   • 设备ID: %%a
)
echo.

:: 检查APK是否存在
set APK_PATH=app\build\outputs\apk\debug\app-debug.apk
if not exist "%APK_PATH%" (
    echo 📦 APK文件不存在，开始编译...
    echo ⏳ 正在编译，请稍候...
    call gradlew.bat assembleDebug
    
    if errorlevel 1 (
        echo ❌ 编译失败，请检查错误信息
        pause
        exit /b 1
    )
    echo ✅ 编译完成
) else (
    echo ✅ 发现现有APK文件
)

:: 获取APK文件大小
for %%F in ("%APK_PATH%") do set APK_SIZE=%%~zF
set /a APK_SIZE_MB=%APK_SIZE%/1024/1024
echo 📦 APK文件大小: %APK_SIZE_MB% MB
echo.

:: 安装到所有连接的设备
echo 🚀 开始安装到所有PDA设备...
for /f "tokens=1,2" %%a in ('adb devices ^| findstr "device$"') do (
    echo 📱 正在安装到设备: %%a
    adb -s %%a install -r "%APK_PATH%"
    
    if errorlevel 1 (
        echo ❌ 设备 %%a 安装失败
    ) else (
        echo ✅ 设备 %%a 安装成功
    )
    echo.
)

echo 🎉 安装脚本执行完成！
echo.
echo 📋 使用说明：
echo   1. 在PDA设备上找到 'WMS系统' 应用
echo   2. 首次使用需要设置服务器地址
echo   3. 登录后即可使用扫码功能
echo.
echo 🔧 常见问题：
echo   • 扫码崩溃 - 已修复，重启应用即可
echo   • 无货位出库 - 已修复，可正常出库  
echo   • 焦点问题 - 已修复，扫码不会乱跳
echo.
echo 📞 技术支持: WMS开发团队
echo.
pause 