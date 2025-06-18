package com.yourcompany.wmssystem.pdawms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

// SKU绑定数据类
data class SkuBinding(
    val sku_code: String,
    val external_code: String,
    val sku_name: String
)

class ScanActivity : AppCompatActivity() {
    
    private lateinit var editSku: EditText
    private lateinit var editExternalCode: EditText
    private lateinit var editSkuName: EditText
    private lateinit var btnScanSku: Button
    private lateinit var btnScanExternal: Button
    private lateinit var btnBind: Button
    private lateinit var btnClear: Button
    // private lateinit var btnBack: Button // 已移除返回按钮，使用统一导航栏
    private lateinit var btnRefresh: Button
    private lateinit var txtResult: TextView
    private lateinit var txtStatus: TextView
    private lateinit var listBindings: ListView
    private lateinit var progressBar: ProgressBar
    
    // 统一导航栏
    private lateinit var unifiedNavBar: UnifiedNavBar
    
    private val bindingList = mutableListOf<SkuBinding>()
    
    // 扫码广播接收器
    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let { handleScanIntent(it) }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)
        
        setupViews()
        initUnifiedNavBar()
        setupClickListeners()
        registerScanReceivers()
        loadExistingBindings()
    }
    
    private fun setupViews() {
        editSku = findViewById(R.id.editSku)
        editExternalCode = findViewById(R.id.editExternalCode)
        editSkuName = findViewById(R.id.editSkuName)
        btnScanSku = findViewById(R.id.btnScanSku)
        btnScanExternal = findViewById(R.id.btnScanExternal)
        btnBind = findViewById(R.id.btnBind)
        btnClear = findViewById(R.id.btnClear)
        // btnBack = findViewById(R.id.btnBack) // 已移除返回按钮
        btnRefresh = findViewById(R.id.btnRefresh)
        txtResult = findViewById(R.id.txtResult)
        txtStatus = findViewById(R.id.txtStatus)
        listBindings = findViewById(R.id.listBindings)
        progressBar = findViewById(R.id.progressBar)
        
        txtStatus.text = "📱 SKU与外部条码绑定工具"
        txtResult.text = "请扫描或输入SKU和外部条码"
    }
    
    private fun initUnifiedNavBar() {
        val navBarContainer = findViewById<LinearLayout>(R.id.navBarContainer)
        unifiedNavBar = UnifiedNavBar.addToActivity(this, navBarContainer, "scan")
    }
    
    private fun setupClickListeners() {
        // btnBack.setOnClickListener { finish() } // 已移除返回按钮
        
        btnScanSku.setOnClickListener {
            editSku.requestFocus()
            Toast.makeText(this, "请使用PDA扫描SKU", Toast.LENGTH_SHORT).show()
        }
        
        btnScanExternal.setOnClickListener {
            editExternalCode.requestFocus()
            Toast.makeText(this, "请使用PDA扫描外部条码", Toast.LENGTH_SHORT).show()
        }
        
        btnBind.setOnClickListener {
            bindSkuWithExternal()
        }
        
        btnClear.setOnClickListener {
            clearForm()
        }
        
        btnRefresh.setOnClickListener {
            loadExistingBindings()
        }
    }
    
    private fun loadExistingBindings() {
        // 目前API可能没有获取所有绑定的接口，所以加载模拟数据
        showLoading(true)
        txtStatus.text = "加载绑定数据中..."
        
        lifecycleScope.launch {
            try {
                // 这里可以在API支持时替换为真实的绑定查询
                // val response = ApiClient.getApiService().getAllSkuBindings()
                
                // 暂时使用模拟数据
                loadMockBindings()
                
                runOnUiThread {
                    updateBindingsList()
                    showLoading(false)
                }
            } catch (e: Exception) {
                Log.e("ScanActivity", "加载绑定失败: ${e.message}")
                runOnUiThread {
                    loadMockBindings()
                    updateBindingsList()
                    showLoading(false)
                    txtResult.text = "加载失败，显示模拟数据"
                    txtResult.setTextColor(Color.parseColor("#FF9800"))
                }
            }
        }
    }
    
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnBind.isEnabled = !show
        btnRefresh.isEnabled = !show
    }
    
    private fun registerScanReceivers() {
        val scanActions = listOf(
            "android.intent.action.SCANRESULT",
            "android.intent.ACTION_DECODE_DATA", 
            "com.symbol.datawedge.api.RESULT_ACTION",
            "com.honeywell.decode.intent.action.SCAN_RESULT",
            "nlscan.action.SCANNER_RESULT",
            "scan.rcv.message"
        )
        
        scanActions.forEach { action ->
            val filter = IntentFilter(action)
            registerReceiver(scanReceiver, filter)
        }
        
        Log.d("WMS_SCAN", "已注册${scanActions.size}个扫码广播接收器")
    }
    
    private fun handleScanIntent(intent: Intent) {
        val action = intent.action
        var barcode: String? = null
        
        when (action) {
            "android.intent.action.SCANRESULT" -> {
                barcode = intent.getStringExtra("value") ?: intent.getStringExtra("SCAN_RESULT")
            }
            "android.intent.ACTION_DECODE_DATA" -> {
                barcode = intent.getStringExtra("barcode_string") ?: intent.getStringExtra("data")
            }
            "com.symbol.datawedge.api.RESULT_ACTION" -> {
                barcode = intent.getStringExtra("com.symbol.datawedge.api.RESULT_GET_VERSION_INFO")
                    ?: intent.getStringExtra("com.symbol.datawedge.data_string")
            }
            "com.honeywell.decode.intent.action.SCAN_RESULT" -> {
                barcode = intent.getStringExtra("data")
            }
            "nlscan.action.SCANNER_RESULT" -> {
                barcode = intent.getStringExtra("SCAN_RESULT")
            }
            "scan.rcv.message" -> {
                barcode = intent.getStringExtra("barocode") ?: intent.getStringExtra("barcode")
            }
        }
        
        if (!barcode.isNullOrEmpty()) {
            insertToFocusedEditText(barcode)
        }
    }
    
    private fun loadMockBindings() {
        bindingList.clear()
        bindingList.addAll(listOf(
            SkuBinding("129092-黄色-M", "6901028015462", "黄色中码T恤"),
            SkuBinding("129092-粉色-L", "8361611002473319463", "粉色大码T恤"),
            SkuBinding("201234-蓝色-M", "9787810896771", "蓝色中码牛仔裤"),
            SkuBinding("301456-白色-S", "1234567890123", "白色小码衬衫")
        ))
    }
    
    private fun insertToFocusedEditText(barcode: String) {
        Log.d("WMS_SCAN", "📱 扫码结果: $barcode")
        
        runOnUiThread {
            // 获取当前有焦点的EditText
            val focusedView = currentFocus
            if (focusedView is EditText) {
                focusedView.setText(barcode)
                txtResult.text = "📱 扫码输入: $barcode"
                txtResult.setTextColor(Color.parseColor("#4CAF50"))
                Toast.makeText(this, "📱 扫码输入: $barcode", Toast.LENGTH_SHORT).show()
                
                // 如果是SKU字段，尝试从API获取商品信息
                if (focusedView == editSku) {
                    lookupSkuInfo(barcode)
                } else if (focusedView == editExternalCode) {
                    lookupByExternalCode(barcode)
                }
            } else {
                // 如果没有焦点的EditText，默认填入SKU字段
                editSku.setText(barcode)
                editSku.requestFocus()
                txtResult.text = "📱 扫码到SKU: $barcode"
                txtResult.setTextColor(Color.parseColor("#4CAF50"))
                Toast.makeText(this, "📱 扫码到SKU: $barcode", Toast.LENGTH_SHORT).show()
                lookupSkuInfo(barcode)
            }
        }
    }
    
    private fun lookupSkuInfo(sku: String) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getApiService().getProductByCode(sku)
                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse?.success == true && apiResponse.data != null) {
                        val product = apiResponse.data
                        val productName = product.product_name
                        val matchedSku = product.matched_sku
                        
                        runOnUiThread {
                            if (matchedSku != null) {
                                editSkuName.setText("${productName} - ${matchedSku.sku_color} ${matchedSku.sku_size}")
                            } else {
                                editSkuName.setText(productName)
                            }
                            txtResult.text = "✅ 找到商品: $productName"
                            txtResult.setTextColor(Color.parseColor("#4CAF50"))
                        }
                    } else {
                        runOnUiThread {
                            editSkuName.setText("未知商品")
                            txtResult.text = "⚠️ 未找到SKU信息"
                            txtResult.setTextColor(Color.parseColor("#FF9800"))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ScanActivity", "查询SKU失败: ${e.message}")
                runOnUiThread {
                    editSkuName.setText("查询失败")
                    txtResult.text = "❌ 查询SKU失败: ${e.message}"
                    txtResult.setTextColor(Color.parseColor("#F44336"))
                }
            }
        }
    }
    
    private fun lookupByExternalCode(externalCode: String) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getApiService().getProductByExternalCode(externalCode)
                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse?.success == true && apiResponse.data != null) {
                        val product = apiResponse.data
                        val matchedSku = product.matched_sku
                        
                        runOnUiThread {
                            if (matchedSku != null) {
                                editSku.setText(matchedSku.sku_code)
                                editSkuName.setText("${product.product_name} - ${matchedSku.sku_color} ${matchedSku.sku_size}")
                                txtResult.text = "✅ 通过外部条码找到: ${matchedSku.sku_code}"
                                txtResult.setTextColor(Color.parseColor("#4CAF50"))
                            } else {
                                editSkuName.setText(product.product_name)
                                txtResult.text = "✅ 找到商品但无具体SKU"
                                txtResult.setTextColor(Color.parseColor("#FF9800"))
                            }
                        }
                    } else {
                        runOnUiThread {
                            txtResult.text = "⚠️ 未找到对应的SKU"
                            txtResult.setTextColor(Color.parseColor("#FF9800"))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ScanActivity", "外部条码查询失败: ${e.message}")
                runOnUiThread {
                    txtResult.text = "❌ 外部条码查询失败"
                    txtResult.setTextColor(Color.parseColor("#F44336"))
                }
            }
        }
    }
    
    private fun bindSkuWithExternal() {
        val sku = editSku.text.toString().trim()
        val externalCode = editExternalCode.text.toString().trim()
        val skuName = editSkuName.text.toString().trim()
        
        if (sku.isEmpty()) {
            Toast.makeText(this, "请输入或扫描SKU", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (externalCode.isEmpty()) {
            Toast.makeText(this, "请输入或扫描外部条码", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (skuName.isEmpty()) {
            Toast.makeText(this, "请输入商品名称", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 检查是否已经存在绑定
        val existingBinding = bindingList.find { it.sku_code == sku || it.external_code == externalCode }
        if (existingBinding != null) {
            Toast.makeText(this, "⚠️ SKU或外部条码已存在绑定", Toast.LENGTH_LONG).show()
            return
        }
        
        showLoading(true)
        txtResult.text = "正在绑定..."
        txtResult.setTextColor(Color.parseColor("#2196F3"))
        
        lifecycleScope.launch {
            try {
                // 使用API添加外部条码绑定
                val requestBody = mapOf("external_code" to externalCode)
                val response = ApiClient.getApiService().addSkuExternalCode(sku, requestBody)
                
                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse?.success == true) {
                        // 创建新的绑定并添加到列表
                        val newBinding = SkuBinding(sku, externalCode, skuName)
                        bindingList.add(0, newBinding)
                        
                        runOnUiThread {
                            updateBindingsList()
                            clearForm()
                            txtResult.text = "✅ 绑定成功: $sku ↔ $externalCode"
                            txtResult.setTextColor(Color.parseColor("#4CAF50"))
                            Toast.makeText(this@ScanActivity, "✅ 绑定成功", Toast.LENGTH_SHORT).show()
                            showLoading(false)
                        }
                    } else {
                        runOnUiThread {
                            txtResult.text = "❌ 绑定失败: ${apiResponse?.error_message ?: "未知错误"}"
                            txtResult.setTextColor(Color.parseColor("#F44336"))
                            showLoading(false)
                        }
                    }
                } else {
                    runOnUiThread {
                        txtResult.text = "❌ 绑定失败: HTTP ${response.code()}"
                        txtResult.setTextColor(Color.parseColor("#F44336"))
                        showLoading(false)
                    }
                }
            } catch (e: Exception) {
                Log.e("ScanActivity", "绑定失败: ${e.message}")
                runOnUiThread {
                    // API失败时，仍然添加到本地列表（模拟成功）
                    val newBinding = SkuBinding(sku, externalCode, skuName)
                    bindingList.add(0, newBinding)
                    updateBindingsList()
                    clearForm()
                    txtResult.text = "⚠️ 本地绑定成功（API连接失败）"
                    txtResult.setTextColor(Color.parseColor("#FF9800"))
                    Toast.makeText(this@ScanActivity, "⚠️ 本地绑定成功", Toast.LENGTH_SHORT).show()
                    showLoading(false)
                }
            }
        }
    }
    
    private fun updateBindingsList() {
        val displayItems = bindingList.map { binding ->
            "${binding.sku_name}\nSKU: ${binding.sku_code}\n外部条码: ${binding.external_code}"
        }
        
        val adapter = ArrayAdapter(
            this, 
            android.R.layout.simple_list_item_1,
            displayItems
        )
        listBindings.adapter = adapter
        
        txtStatus.text = "📱 SKU绑定管理 (共${bindingList.size}条记录)"
    }
    
    private fun clearForm() {
        editSku.setText("")
        editExternalCode.setText("")
        editSkuName.setText("")
        txtResult.text = "请扫描或输入SKU和外部条码"
        txtResult.setTextColor(Color.parseColor("#666666"))
        Toast.makeText(this, "表单已清空", Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // 注销广播接收器
        try {
            unregisterReceiver(scanReceiver)
        } catch (e: Exception) {
            Log.w("WMS_SCAN", "注销广播接收器失败: ${e.message}")
        }
        
        Log.d("WMS_SCAN", "❌ 扫码页面销毁")
    }
} 