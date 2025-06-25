package com.yourcompany.wmssystem.pdawms

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log

object NetworkUtils {
    
    /**
     * 检查网络连接状态
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    Log.d("NetworkUtils", "📶 WiFi连接可用")
                    true
                }
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    Log.d("NetworkUtils", "📱 移动数据连接可用")
                    true
                }
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                    Log.d("NetworkUtils", "🔌 以太网连接可用")
                    true
                }
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.isConnected == true
        }
    }
    
    /**
     * 获取网络类型描述
     */
    fun getNetworkType(context: Context): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val activeNetwork = connectivityManager.getNetworkCapabilities(network)
            
            when {
                activeNetwork?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
                activeNetwork?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "移动数据"
                activeNetwork?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "以太网"
                else -> "无网络"
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            when (networkInfo?.type) {
                ConnectivityManager.TYPE_WIFI -> "WiFi"
                ConnectivityManager.TYPE_MOBILE -> "移动数据"
                ConnectivityManager.TYPE_ETHERNET -> "以太网"
                else -> "无网络"
            }
        }
    }
    
    /**
     * 分析网络错误并提供用户友好的错误信息
     */
    fun analyzeNetworkError(error: Throwable): String {
        val errorMessage = error.message ?: ""
        
        return when {
            errorMessage.contains("Unable to resolve host") -> 
                "🌐 无法解析服务器地址，请检查：\n• 服务器地址是否正确\n• 网络连接是否正常"
            
            errorMessage.contains("ConnectException") || errorMessage.contains("Connection refused") -> 
                "🔌 无法连接到服务器，请检查：\n• 服务器是否正在运行\n• 端口号是否正确\n• 防火墙设置"
            
            errorMessage.contains("SocketTimeoutException") || errorMessage.contains("timeout") -> 
                "⏰ 连接超时，请检查：\n• 网络连接速度\n• 服务器响应状态\n• 稍后重试"
            
            errorMessage.contains("SSLHandshakeException") -> 
                "🔒 SSL连接失败，请检查：\n• 服务器证书\n• 使用http://而非https://"
            
            errorMessage.contains("UnknownHostException") -> 
                "❓ 未知主机，请检查：\n• 服务器地址拼写\n• DNS设置"
            
            errorMessage.contains("NetworkOnMainThreadException") -> 
                "⚠️ 网络操作在主线程，这是程序错误"
            
            else -> "❌ 网络错误: ${errorMessage.take(100)}"
        }
    }
    
    /**
     * 提供网络问题的解决建议
     */
    fun getNetworkTroubleshootingTips(context: Context): String {
        val networkType = getNetworkType(context)
        val isConnected = isNetworkAvailable(context)
        
        return buildString {
            appendLine("🔧 网络诊断信息：")
            appendLine("• 网络类型: $networkType")
            appendLine("• 连接状态: ${if (isConnected) "✅ 已连接" else "❌ 未连接"}")
            appendLine()
            appendLine("💡 故障排除建议：")
            appendLine("1. 检查WiFi/移动数据是否开启")
            appendLine("2. 确认服务器地址格式正确")
            appendLine("3. 尝试切换网络连接")
            appendLine("4. 检查防火墙或代理设置")
            appendLine("5. 联系系统管理员确认服务器状态")
        }
    }
    
    /**
     * 验证服务器地址格式
     */
    fun validateServerUrl(url: String): ValidationResult {
        if (url.isBlank()) {
            return ValidationResult(false, "服务器地址不能为空")
        }
        
        val trimmedUrl = url.trim()
        
        // 检查是否包含协议
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            return ValidationResult(false, "地址应以 http:// 或 https:// 开头")
        }
        
        // 检查是否包含端口
        val hasPort = trimmedUrl.matches(Regex("https?://[^/]+:\\d+.*"))
        if (!hasPort) {
            return ValidationResult(false, "建议指定端口号，如: http://192.168.1.100:8610")
        }
        
        // 检查IP地址格式
        val ipRegex = Regex("https?://(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}):(\\d+).*")
        val ipMatch = ipRegex.find(trimmedUrl)
        
        if (ipMatch != null) {
            val ip = ipMatch.groupValues[1]
            val port = ipMatch.groupValues[2].toIntOrNull()
            
            // 验证IP地址
            val ipParts = ip.split(".")
            for (part in ipParts) {
                val num = part.toIntOrNull()
                if (num == null || num < 0 || num > 255) {
                    return ValidationResult(false, "IP地址格式不正确: $ip")
                }
            }
            
            // 验证端口
            if (port == null || port < 1 || port > 65535) {
                return ValidationResult(false, "端口号应在1-65535之间")
            }
        }
        
        return ValidationResult(true, "地址格式正确")
    }
    
    data class ValidationResult(
        val isValid: Boolean,
        val message: String
    )
} 