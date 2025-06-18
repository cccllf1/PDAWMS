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
        
        // 如果已登录，直接跳转到主页
        if (ApiClient.isLoggedIn()) {
            startMainActivity()
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
                val errorMsg = when {
                    e.message?.contains("Unable to resolve host") == true -> "无法连接到服务器，请检查网络和服务器地址"
                    e.message?.contains("timeout") == true -> "连接超时，请检查网络"
                    else -> "登录失败: ${e.message}"
                }
                Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_LONG).show()
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