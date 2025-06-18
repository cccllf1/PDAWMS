package com.yourcompany.wmssystem.pdawms

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : Activity() {
    
    // WMS模块按钮
    private lateinit var btnInboundModule: Button
    private lateinit var btnOutboundModule: Button
    private lateinit var btnInventoryModule: Button
    private lateinit var btnLocationModule: Button
    private lateinit var btnScanModule: Button
    private lateinit var btnReportModule: Button
    private lateinit var btnLogout: Button
    
    private lateinit var txtTitle: TextView
    private lateinit var txtSubtitle: TextView
    
    private var authToken = ""
    private var userName = ""
    private var userId = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化API客户端
        ApiClient.init(this)
        
        // 检查登录状态
        if (!ApiClient.isLoggedIn()) {
            startLoginActivity()
            return
        }
        
        setContentView(R.layout.activity_main)
        
        Log.d("WMS_MAIN", "📦 PDA-WMS仓库管理系统启动")
        
        loadLoginInfo()
        initViews()
        setupClickListeners()
    }
    
    private fun startLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun loadLoginInfo() {
        val sharedPrefs = getSharedPreferences("wms_prefs", Context.MODE_PRIVATE)
        userName = sharedPrefs.getString("user_name", "") ?: ""
        userId = ApiClient.getCurrentUserId() ?: ""
    }
    
    private fun initViews() {
        txtTitle = findViewById(R.id.txtTitle)
        txtSubtitle = findViewById(R.id.txtSubtitle)
        btnInboundModule = findViewById(R.id.btnInboundModule)
        btnOutboundModule = findViewById(R.id.btnOutboundModule)
        btnInventoryModule = findViewById(R.id.btnInventoryModule)
        btnLocationModule = findViewById(R.id.btnLocationModule)
        btnScanModule = findViewById(R.id.btnScanModule)
        btnReportModule = findViewById(R.id.btnReportModule)
        btnLogout = findViewById(R.id.btnLogout)
        
        // 显示用户信息
        txtTitle.text = "📦 PDA-WMS仓库管理 - $userName"
        txtSubtitle.text = "✅ 已登录"
    }
    
    private fun setupClickListeners() {
        // 第一排：入库 + 出库
        btnInboundModule.setOnClickListener { 
            startActivity(Intent(this, InboundActivity::class.java))
        }
        
        btnOutboundModule.setOnClickListener { 
            startActivity(Intent(this, OutboundActivity::class.java))
        }
        
        // 第二排：库存管理 + 库位管理
        btnInventoryModule.setOnClickListener { 
            startActivity(Intent(this, InventoryActivity::class.java))
        }
        
        btnLocationModule.setOnClickListener { 
            Toast.makeText(this, "📍 库位管理功能开发中...", Toast.LENGTH_SHORT).show() 
        }
        
        // 第三排：扫码功能 + 报表统计
        btnScanModule.setOnClickListener { 
            startActivity(Intent(this, ScanActivity::class.java))
        }
        
        btnReportModule.setOnClickListener { 
            Toast.makeText(this, "📊 报表统计功能开发中...", Toast.LENGTH_SHORT).show() 
        }
        
        // 退出登录按钮
        btnLogout.setOnClickListener { 
            logout()
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 1, 0, "退出登录")
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            1 -> {
                logout()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
    
    private fun logout() {
        ApiClient.clearAuth(this)
        Toast.makeText(this, "已退出登录", Toast.LENGTH_SHORT).show()
        startLoginActivity()
    }
    
    override fun onResume() {
        super.onResume()
        Log.d("WMS_MAIN", "🔄 WMS主界面恢复显示")
        
        if (!ApiClient.isLoggedIn()) {
            startLoginActivity()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d("WMS_MAIN", "❌ WMS主界面销毁")
    }
} 