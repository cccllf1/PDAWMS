package com.yourcompany.wmssystem.pdawms

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    
    private lateinit var editUsername: EditText
    private lateinit var editPassword: EditText
    private lateinit var editServerUrl: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnSetServer: Button
    private lateinit var btnClearServer: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        
        // 初始化API客户端
        ApiClient.init(this)
        
        initViews()
        setupClickListeners()
        loadServerUrl()
        
        // 检查登录状态和Token有效性
        checkLoginStatus()
    }
    
    private fun checkLoginStatus() {
        if (ApiClient.isLoggedIn()) {
            // 检查Token是否可能过期
            if (ApiClient.isTokenExpired(this)) {
                Toast.makeText(this, "⚠️ 登录已过期，请重新登录", Toast.LENGTH_LONG).show()
                ApiClient.clearAuth(this)
            } else {
                // 检查服务器连接
                val serverUrl = ApiClient.getServerUrl(this)
                if (serverUrl.isNotEmpty()) {
                    Toast.makeText(this, "✅ 自动登录中...", Toast.LENGTH_SHORT).show()
                    startMainActivity()
                } else {
                    Toast.makeText(this, "⚠️ 服务器地址丢失，请重新设置", Toast.LENGTH_LONG).show()
                    ApiClient.clearAuth(this)
                }
            }
        }
    }
    
    private fun initViews() {
        editUsername = findViewById(R.id.editUsername)
        editPassword = findViewById(R.id.editPassword)
        editServerUrl = findViewById(R.id.editServerUrl)
        btnLogin = findViewById(R.id.btnLogin)
        btnSetServer = findViewById(R.id.btnSetServer)
        btnClearServer = findViewById(R.id.btnClearServer)
    }
    
    private fun setupClickListeners() {
        btnLogin.setOnClickListener { performLogin() }
        btnSetServer.setOnClickListener { updateServerUrl() }
        btnClearServer.setOnClickListener { clearServerUrl() }
    }
    
    private fun loadServerUrl() {
        val currentUrl = ApiClient.getServerUrl(this)
        if (currentUrl.isEmpty()) {
            // 预填默认服务器地址
            editServerUrl.setText("192.168.11.252:8610")
        } else {
            editServerUrl.setText(currentUrl)
        }
    }
    
    private fun updateServerUrl() {
        val url = editServerUrl.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show()
            return
        }
        
        var serverUrl = url
        if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
            serverUrl = "http://$serverUrl"
        }
        if (!serverUrl.endsWith("/")) {
            serverUrl += "/"
        }
        
        // 验证服务器地址格式
        val validation = NetworkUtils.validateServerUrl(serverUrl)
        if (!validation.isValid) {
            Toast.makeText(this, "❌ ${validation.message}", Toast.LENGTH_LONG).show()
            return
        }
        
        // 检查网络连接
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Toast.makeText(this, "⚠️ 当前无网络连接，请检查网络设置", Toast.LENGTH_LONG).show()
            return
        }
        
        ApiClient.setServerUrl(this, serverUrl)
        Toast.makeText(this, "✅ 服务器地址已设置: $serverUrl", Toast.LENGTH_SHORT).show()
    }
    
    private fun clearServerUrl() {
        val sharedPrefs = getSharedPreferences("wms_prefs", MODE_PRIVATE)
        sharedPrefs.edit()
            .remove("server_url")
            .apply()
        
        editServerUrl.setText("")
        Toast.makeText(this, "服务器地址已清除，请重新设置", Toast.LENGTH_SHORT).show()
    }
    
    private fun performLogin() {
        val username = editUsername.text.toString().trim()
        val password = editPassword.text.toString().trim()
        val serverUrl = ApiClient.getServerUrl(this)
        
        if (serverUrl.isEmpty()) {
            Toast.makeText(this, "请先设置服务器地址", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请输入用户名和密码", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 检查网络连接
        if (!NetworkUtils.isNetworkAvailable(this)) {
            val tips = NetworkUtils.getNetworkTroubleshootingTips(this)
            Toast.makeText(this, "⚠️ 无网络连接", Toast.LENGTH_SHORT).show()
            
            // 显示详细的网络诊断信息
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("🔧 网络诊断")
                .setMessage(tips)
                .setPositiveButton("确定", null)
                .show()
            return
        }
        
        btnLogin.isEnabled = false
        btnLogin.text = "登录中..."
        
        lifecycleScope.launch {
            try {
                val request = LoginRequest(username, password)
                val response = ApiClient.getApiService().login(request)
                
                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse?.success == true && apiResponse.data != null) {
                        val loginData = apiResponse.data
                        
                        // 保存登录信息
                        ApiClient.setAuth(this@LoginActivity, loginData.token, loginData.user_id)
                        
                        // 保存用户信息到SharedPreferences
                        val sharedPrefs = getSharedPreferences("wms_prefs", MODE_PRIVATE)
                        sharedPrefs.edit()
                            .putString("user_name", loginData.user_name)
                            .putString("role", loginData.role)
                            .putBoolean("is_admin", loginData.is_admin)
                            .apply()
                        
                        Toast.makeText(this@LoginActivity, "登录成功", Toast.LENGTH_SHORT).show()
                        startMainActivity()
                    } else {
                        val errorMsg = apiResponse?.error_message ?: "登录失败"
                        Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val errorMsg = when (response.code()) {
                        401 -> "用户名或密码错误"
                        404 -> "服务器地址错误或服务不可用"
                        500 -> "服务器内部错误"
                        else -> "网络错误: ${response.code()}"
                    }
                    Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                
                // 使用网络工具类分析错误
                val errorMsg = NetworkUtils.analyzeNetworkError(e)
                Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_LONG).show()
                
                // 如果是网络问题，显示详细诊断信息
                if (e.message?.contains("Unable to resolve host") == true || 
                    e.message?.contains("ConnectException") == true ||
                    e.message?.contains("timeout") == true) {
                    
                    val tips = NetworkUtils.getNetworkTroubleshootingTips(this@LoginActivity)
                    androidx.appcompat.app.AlertDialog.Builder(this@LoginActivity)
                        .setTitle("🔧 网络问题诊断")
                        .setMessage(tips)
                        .setPositiveButton("确定", null)
                        .setNegativeButton("重新设置服务器") { _, _ ->
                            editServerUrl.requestFocus()
                        }
                        .show()
                }
            } finally {
                btnLogin.isEnabled = true
                btnLogin.text = "登录"
            }
        }
    }
    
    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
} 