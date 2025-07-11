# 📱 PDA WMS 系统安装指南

## 🚀 快速安装

### 方法一：使用自动安装脚本（推荐）

#### Windows用户：
```bash
# 双击运行
install-pda.bat
```

#### Mac/Linux用户：
```bash
# 在终端运行
./install-pda.sh
```

### 方法二：手动安装

```bash
# 1. 编译APK
./gradlew assembleDebug

# 2. 安装到连接的设备
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 📋 安装前准备

### 1. PDA设备设置
- ✅ 开启**开发者选项**
- ✅ 开启**USB调试**
- ✅ 信任连接的电脑
- ✅ 确保USB连接稳定

### 2. 电脑环境
- ✅ 安装Android SDK/Android Studio
- ✅ ADB工具可用
- ✅ Java 8+环境

## 🔧 功能特点

### ✅ 已修复的问题
- **扫码崩溃** - BroadcastReceiver生命周期管理
- **焦点问题** - 扫码不会输入到错误窗口
- **无货位出库** - 支持无货位商品正常出库
- **编译错误** - 修复所有编译依赖问题

### 🎯 核心功能
- **📦 入库管理** - 商品入库、货位分配
- **📤 出库管理** - 商品出库、库存扣减
- **🏪 库存查询** - 实时库存查看
- **📍 货位管理** - 货位信息维护
- **🔍 条码扫描** - 支持多种PDA扫码模式

## 📞 技术支持

### 常见问题

#### Q: 扫码时应用崩溃？
A: 已修复！使用最新版本，确保BroadcastReceiver正确注册。

#### Q: 无货位商品无法出库？
A: 已修复！服务器端已支持"无货位"特殊处理。

#### Q: 扫码输入到错误的输入框？
A: 已修复！新增ScanFocusManager智能焦点管理。

#### Q: 设备检测不到？
A: 检查USB调试是否开启，尝试重新连接USB。

### 联系方式
- **技术支持**: WMS开发团队
- **GitHub**: https://github.com/cccllf1/PDAWMS
- **问题反馈**: 提交GitHub Issue

## 📊 版本历史

### v1.0 (最新)
- ✅ 修复扫码崩溃问题
- ✅ 修复无货位出库问题
- ✅ 新增扫码焦点管理
- ✅ 完善错误处理机制
- ✅ 提供自动安装脚本

## 🔄 更新说明

安装脚本会自动：
1. 检测连接的PDA设备
2. 编译最新版本APK
3. 安装到所有连接的设备
4. 提供详细的安装日志

## 📱 设备兼容性

支持的PDA设备：
- Honeywell系列
- Symbol/Zebra系列  
- 新大陆系列
- 其他支持Android的PDA设备

## 🚨 注意事项

1. **首次安装**需要手动信任应用
2. **权限设置**确保应用有存储和网络权限
3. **服务器配置**首次使用需要设置服务器地址
4. **网络连接**确保PDA能访问服务器

---

**最后更新**: $(date "+%Y-%m-%d")
**维护团队**: WMS开发团队 