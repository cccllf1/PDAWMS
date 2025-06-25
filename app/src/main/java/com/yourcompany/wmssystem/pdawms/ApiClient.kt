package com.yourcompany.wmssystem.pdawms

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import com.google.gson.GsonBuilder
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

object ApiClient {
    // 不再提供默认服务器地址，强制用户设置
    
    private var retrofit: Retrofit? = null
    private var token: String? = null
    private var userId: String? = null
    
    // 初始化
    fun init(context: Context) {
        // 从SharedPreferences读取保存的token和userId
        val sharedPrefs = context.getSharedPreferences("wms_prefs", Context.MODE_PRIVATE)
        token = sharedPrefs.getString("token", null)
        userId = sharedPrefs.getString("user_id", null)
        
        // 只有当用户设置了服务器地址时才初始化Retrofit
        val baseUrl = sharedPrefs.getString("server_url", null)
        if (baseUrl != null) {
            setupRetrofit(baseUrl)
        }
    }
    
    // 网络重试拦截器
    private class RetryInterceptor(private val maxRetry: Int = 3) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            var response: Response? = null
            var exception: IOException? = null
            
            for (i in 0..maxRetry) {
                try {
                    response = chain.proceed(request)
                    if (response.isSuccessful) {
                        return response
                    }
                    response.close()
                } catch (e: IOException) {
                    android.util.Log.w("ApiClient", "网络请求重试 ${i + 1}/${maxRetry + 1}: ${e.message}")
                    exception = e
                    if (i == maxRetry) {
                        throw e
                    }
                    // 等待一段时间后重试
                    Thread.sleep((i + 1) * 1000L)
                }
            }
            
            return response ?: throw (exception ?: IOException("未知网络错误"))
        }
    }
    
    // 设置Retrofit
    private fun setupRetrofit(baseUrl: String) {
        android.util.Log.d("ApiClient", "★★★ 当前API基础地址: $baseUrl")
        
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
            
            // 添加认证头
            token?.let {
                requestBuilder.addHeader("Authorization", "Bearer $it")
            }
            
            // 添加Content-Type
            requestBuilder.addHeader("Content-Type", "application/json")
            
            chain.proceed(requestBuilder.build())
        }
        
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(RetryInterceptor(maxRetry = 2)) // 添加重试拦截器
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS) // 减少连接超时
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true) // 启用连接失败重试
            .build()
        
        val gson = GsonBuilder()
            .setLenient()
            .create()

        retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
    
    // 获取API服务
    fun getApiService(): ApiService {
        return retrofit?.create(ApiService::class.java)
            ?: throw IllegalStateException("请先设置服务器地址再进行API调用")
    }
    
    // 设置认证信息
    fun setAuth(context: Context, token: String, userId: String?) {
        this.token = token
        this.userId = userId
        
        // 保存到SharedPreferences
        val sharedPrefs = context.getSharedPreferences("wms_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putString("token", token)
            .putString("user_id", userId ?: "")
            .putLong("token_save_time", System.currentTimeMillis()) // 记录保存时间
            .apply()
        
        android.util.Log.d("ApiClient", "✅ 认证信息已保存")
    }
    
    // 清除认证信息
    fun clearAuth(context: Context) {
        this.token = null
        this.userId = null
        
        val sharedPrefs = context.getSharedPreferences("wms_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .remove("token")
            .remove("user_id")
            .remove("token_save_time")
            .apply()
        
        android.util.Log.d("ApiClient", "🔄 认证信息已清除")
    }
    
    // 获取当前用户ID
    fun getCurrentUserId(): String? = userId
    
    // 检查是否已登录
    fun isLoggedIn(): Boolean = !token.isNullOrEmpty()
    
    // 检查Token是否可能过期（7天）
    fun isTokenExpired(context: Context): Boolean {
        val sharedPrefs = context.getSharedPreferences("wms_prefs", Context.MODE_PRIVATE)
        val saveTime = sharedPrefs.getLong("token_save_time", 0)
        val currentTime = System.currentTimeMillis()
        val sevenDays = 7 * 24 * 60 * 60 * 1000L
        
        return (currentTime - saveTime) > sevenDays
    }
    
    // 设置服务器地址 - 增强版本
    fun setServerUrl(context: Context, url: String) {
        val sharedPrefs = context.getSharedPreferences("wms_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putString("server_url", url)
            .putLong("server_url_save_time", System.currentTimeMillis()) // 记录设置时间
            .apply()
        
        setupRetrofit(url)
        android.util.Log.d("ApiClient", "✅ 服务器地址已设置: $url")
    }
    
    // 获取当前服务器地址
    fun getServerUrl(context: Context): String {
        val sharedPrefs = context.getSharedPreferences("wms_prefs", Context.MODE_PRIVATE)
        return sharedPrefs.getString("server_url", "") ?: ""
    }
    
    // 检查服务器连接状态
    fun checkServerConnection(context: Context): Boolean {
        val serverUrl = getServerUrl(context)
        if (serverUrl.isEmpty()) {
            android.util.Log.w("ApiClient", "⚠️ 服务器地址为空")
            return false
        }
        
        return try {
            // 这里可以添加一个简单的ping请求
            true
        } catch (e: Exception) {
            android.util.Log.e("ApiClient", "❌ 服务器连接检查失败: ${e.message}")
            false
        }
    }

    fun processImageUrl(path: String?, context: Context): String {
        if (path.isNullOrEmpty()) return ""
        
        // 如果已经是完整URL，直接返回
        if (path.startsWith("http")) return path
        
        // 获取服务器地址
        val serverUrl = getServerUrl(context)
        if (serverUrl.isEmpty()) {
            android.util.Log.w("ApiClient", "服务器地址为空，无法处理图片路径: $path")
            return ""
        }
        
        // 构建完整URL
        val fullUrl = serverUrl.trimEnd('/') + "/" + path.trimStart('/')
        android.util.Log.d("ApiClient", "图片URL处理: $path -> $fullUrl")
        return fullUrl
    }
} 