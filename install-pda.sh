#!/bin/bash

# PDA WMS 系统安装脚本
# 作者: WMS开发团队
# 版本: v1.0
# 日期: $(date "+%Y-%m-%d")

echo "🔧 PDA WMS 系统安装脚本 v1.0"
echo "================================"

# 检查是否在正确的目录
if [ ! -f "gradlew" ]; then
    echo "❌ 错误：请在项目根目录运行此脚本"
    exit 1
fi

# 检查连接的设备
echo "📱 检查连接的PDA设备..."
DEVICES=$(adb devices | grep "device$" | wc -l | tr -d ' ')

if [ "$DEVICES" -eq 0 ]; then
    echo "❌ 没有检测到连接的PDA设备"
    echo "请确保："
    echo "  1. PDA设备已通过USB连接到电脑"
    echo "  2. 已开启USB调试模式"
    echo "  3. 已信任此电脑"
    exit 1
fi

echo "✅ 检测到 $DEVICES 个PDA设备"
echo ""

# 显示设备列表
echo "📋 连接的设备列表："
adb devices | grep "device$" | while read device status; do
    echo "  • 设备ID: $device"
done
echo ""

# 检查APK是否存在
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_PATH" ]; then
    echo "📦 APK文件不存在，开始编译..."
    echo "⏳ 正在编译，请稍候..."
    ./gradlew assembleDebug
    
    if [ $? -ne 0 ]; then
        echo "❌ 编译失败，请检查错误信息"
        exit 1
    fi
    echo "✅ 编译完成"
else
    echo "✅ 发现现有APK文件"
fi

# 获取APK信息
APK_SIZE=$(ls -lh "$APK_PATH" | awk '{print $5}')
echo "📦 APK文件大小: $APK_SIZE"
echo ""

# 安装到所有连接的设备
echo "🚀 开始安装到所有PDA设备..."
adb devices | grep "device$" | while read device status; do
    echo "📱 正在安装到设备: $device"
    adb -s "$device" install -r "$APK_PATH"
    
    if [ $? -eq 0 ]; then
        echo "✅ 设备 $device 安装成功"
    else
        echo "❌ 设备 $device 安装失败"
    fi
    echo ""
done

echo "🎉 安装脚本执行完成！"
echo ""
echo "📋 使用说明："
echo "  1. 在PDA设备上找到 'WMS系统' 应用"
echo "  2. 首次使用需要设置服务器地址"
echo "  3. 登录后即可使用扫码功能"
echo ""
echo "🔧 常见问题："
echo "  • 扫码崩溃 - 已修复，重启应用即可"
echo "  • 无货位出库 - 已修复，可正常出库"
echo "  • 焦点问题 - 已修复，扫码不会乱跳"
echo ""
echo "📞 技术支持: WMS开发团队" 