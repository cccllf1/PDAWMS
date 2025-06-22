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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class ScanActivity : AppCompatActivity() {
    
    private lateinit var editSearch: EditText
    private lateinit var btnSearch: Button
    private lateinit var btnClear: Button
    private lateinit var txtResult: TextView
    private lateinit var txtStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutResults: LinearLayout
    private lateinit var recyclerProductCards: RecyclerView
    private lateinit var hierarchyAdapter: ProductHierarchyAdapter
    
    // 统一导航栏
    private lateinit var unifiedNavBar: UnifiedNavBar
    
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
    }
    
    private fun setupViews() {
        editSearch = findViewById(R.id.editSearch)
        btnSearch = findViewById(R.id.btnSearch)
        btnClear = findViewById(R.id.btnClear)
        txtResult = findViewById(R.id.txtResult)
        txtStatus = findViewById(R.id.txtStatus)
        progressBar = findViewById(R.id.progressBar)
        layoutResults = findViewById(R.id.layoutResults)
        recyclerProductCards = findViewById(R.id.recyclerProductCards)
        
        // 设置RecyclerView
        hierarchyAdapter = ProductHierarchyAdapter(this)
        recyclerProductCards.layoutManager = LinearLayoutManager(this)
        recyclerProductCards.adapter = hierarchyAdapter
        
        txtStatus.text = "🔍 商品/SKU搜索与外部条码管理"
        txtResult.text = "请扫描或输入商品代码、SKU或外部条码进行搜索"
    }
    
    private fun initUnifiedNavBar() {
        val navBarContainer = findViewById<LinearLayout>(R.id.navBarContainer)
        unifiedNavBar = UnifiedNavBar.addToActivity(this, navBarContainer, "scan")
    }
    
    private fun setupClickListeners() {
        btnSearch.setOnClickListener {
            performSearch()
        }
        
        btnClear.setOnClickListener {
            clearForm()
        }
        
        // 监听输入框的搜索动作
        editSearch.setOnEditorActionListener { _, _, _ ->
            performSearch()
            true
        }
    }
    
    private fun performSearch() {
        val query = editSearch.text.toString().trim()
        if (query.isEmpty()) {
            Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show()
            return
        }
        
        showLoading(true)
        clearResults()
        
        // 尝试多种查询方式
        lifecycleScope.launch {
            try {
                // 尝试所有可能的查询方式
                performMultipleSearch(query)
            } catch (e: Exception) {
                Log.e("ScanActivity", "搜索失败: ${e.message}")
                runOnUiThread {
                    showLoading(false)
                    txtResult.text = "❌ 搜索失败: ${e.message}"
                    txtResult.setTextColor(Color.parseColor("#F44336"))
                }
            }
        }
    }
    
    private suspend fun performMultipleSearch(query: String) {
        Log.d("ScanActivity", "开始搜索: $query")
        
        // 检测查询类型
        val isSkuCode = query.contains("-") && query.split("-").size >= 3  // SKU码通常格式: 产品代码-颜色-尺寸
        val searchType = when {
            isSkuCode -> "SKU代码"
            query.all { it.isDigit() } -> "外部条码"
            else -> "产品代码"
        }
        
        Log.d("ScanActivity", "检测到查询类型: $searchType")
        
        // 1. 直接尝试用 /api/products/code/{code} 端点（支持产品代码和SKU代码）
        try {
            Log.d("ScanActivity", "尝试产品/SKU代码查询: $query")
            val response = ApiClient.getApiService().getProductByCode(query)
            Log.d("ScanActivity", "产品/SKU代码查询HTTP状态: ${response.code()}, 是否成功: ${response.isSuccessful}")
            if (response.isSuccessful) {
                val apiResponse = response.body()
                Log.d("ScanActivity", "产品/SKU代码查询响应: success=${apiResponse?.success}, data存在=${apiResponse?.data != null}")
                Log.d("ScanActivity", "产品/SKU代码查询完整响应: $apiResponse")
                if (apiResponse?.success == true && apiResponse.data != null) {
                    displayProductResult(apiResponse.data, searchType)
                    return
                } else {
                    Log.d("ScanActivity", "产品/SKU代码查询API返回失败或无数据: ${apiResponse?.error_message}")
                }
            } else {
                Log.d("ScanActivity", "产品/SKU代码查询HTTP失败: ${response.code()} - ${response.message()}")
                val errorBody = response.errorBody()?.string()
                Log.d("ScanActivity", "产品/SKU代码查询错误响应体: $errorBody")
            }
        } catch (e: Exception) {
            Log.e("ScanActivity", "产品/SKU代码查询异常: ${e.message}", e)
        }
        
        // 2. 尝试外部条码查询（特别是对于纯数字查询）
        try {
            Log.d("ScanActivity", "尝试外部条码查询: $query")
            val response = ApiClient.getApiService().getProductByExternalCode(query)
            Log.d("ScanActivity", "外部条码查询HTTP状态: ${response.code()}, 是否成功: ${response.isSuccessful}")
            if (response.isSuccessful) {
                val apiResponse = response.body()
                Log.d("ScanActivity", "外部条码查询响应: success=${apiResponse?.success}, data存在=${apiResponse?.data != null}")
                Log.d("ScanActivity", "外部条码查询完整响应: $apiResponse")
                if (apiResponse?.success == true && apiResponse.data != null) {
                    displayProductResult(apiResponse.data, "外部条码")
                    return
                } else {
                    Log.d("ScanActivity", "外部条码查询API返回失败或无数据: ${apiResponse?.error_message}")
                }
            } else {
                Log.d("ScanActivity", "外部条码查询HTTP失败: ${response.code()} - ${response.message()}")
                val errorBody = response.errorBody()?.string()
                Log.d("ScanActivity", "外部条码查询错误响应体: $errorBody")
            }
        } catch (e: Exception) {
            Log.e("ScanActivity", "外部条码查询异常: ${e.message}", e)
        }
        
        // 3. 尝试通用搜索API
        try {
            Log.d("ScanActivity", "尝试通用搜索: $query")
            val response = ApiClient.getApiService().searchProducts(query)
            Log.d("ScanActivity", "通用搜索HTTP状态: ${response.code()}, 是否成功: ${response.isSuccessful}")
            if (response.isSuccessful) {
                val apiResponse = response.body()
                Log.d("ScanActivity", "通用搜索响应: success=${apiResponse?.success}, data存在=${apiResponse?.data != null}")
                Log.d("ScanActivity", "通用搜索完整响应: $apiResponse")
                if (apiResponse?.success == true && apiResponse.data != null && !apiResponse.data.products.isNullOrEmpty()) {
                    val firstProduct = apiResponse.data.products[0]
                    displayProductResult(firstProduct, "通用搜索")
                    return
                } else {
                    Log.d("ScanActivity", "通用搜索无结果或失败: ${apiResponse?.error_message}")
                }
            } else {
                Log.d("ScanActivity", "通用搜索HTTP失败: ${response.code()} - ${response.message()}")
                val errorBody = response.errorBody()?.string()
                Log.d("ScanActivity", "通用搜索错误响应体: $errorBody")
            }
        } catch (e: Exception) {
            Log.e("ScanActivity", "通用搜索异常: ${e.message}", e)
        }
        
        // 4. 如果都失败了，显示未找到
        Log.d("ScanActivity", "所有查询方式都失败")
        runOnUiThread {
            showLoading(false)
            txtResult.text = "⚠️ 未找到匹配的商品或SKU\n搜索内容: $query\n检测类型: $searchType\n\n已尝试:\n• 产品/SKU代码查询\n• 外部条码查询\n• 通用搜索\n\n请检查输入是否正确"
            txtResult.setTextColor(Color.parseColor("#FF9800"))
        }
    }
    
    private fun displayProductResult(product: Product, searchType: String) {
        runOnUiThread {
            showLoading(false)
            
            // 检查是否匹配到特定SKU或通过外部条码找到
            val matchedSku = product.matched_sku
            if (matchedSku != null && (searchType == "SKU代码" || searchType == "外部条码")) {
                // 直接弹出外部条码管理对话框
                txtResult.text = "✅ 通过${searchType}找到SKU: ${matchedSku.sku_code}\n正在打开外部条码管理..."
                txtResult.setTextColor(Color.parseColor("#4CAF50"))
                
                // 获取颜色信息
                val color = product.colors?.find { colorInfo ->
                    colorInfo.sizes?.any { it.sku_code == matchedSku.sku_code } == true
                }?.color ?: ""
                
                // 延迟一下显示对话框，让用户看到结果
                editSearch.postDelayed({
                    showExternalCodeManagementDialog(matchedSku, product.product_code, color)
                }, 500)
                
                return@runOnUiThread
            }
            
            // 显示简单的搜索结果文本
            if (matchedSku != null) {
                txtResult.text = "✅ 通过${searchType}找到SKU: ${matchedSku.sku_code}"
                txtResult.setTextColor(Color.parseColor("#4CAF50"))
            } else {
                txtResult.text = "✅ 通过${searchType}找到商品: ${product.product_name}"
                txtResult.setTextColor(Color.parseColor("#2196F3"))
            }
            
            // 使用层级适配器显示商品
            hierarchyAdapter.clear()
            hierarchyAdapter.setProduct(product, searchType)
            
            // 显示卡片列表，隐藏传统结果
            recyclerProductCards.visibility = View.VISIBLE
            layoutResults.removeAllViews()
        }
    }
    
    private fun showExternalCodeManagementDialog(sku: SkuInfo, productCode: String, color: String) {
        val dialog = ExternalCodesDialogFragment.newInstance(sku, productCode, color)
        dialog.show(supportFragmentManager, "ExternalCodesDialog")
    }
    
    private fun addLocationInfo(locations: List<LocationStock>) {
        val locationText = TextView(this).apply {
            text = "\n📍 库存位置:"
            textSize = 14f
            setTextColor(Color.parseColor("#333333"))
            setPadding(8, 8, 8, 4)
        }
        layoutResults.addView(locationText)
        
                 locations.forEach { location ->
             val locationView = TextView(this).apply {
                 text = "  ${location.location_code}: ${location.stock_quantity}件"
                 textSize = 12f
                 setTextColor(Color.parseColor("#666666"))
                 setPadding(16, 2, 8, 2)
             }
             layoutResults.addView(locationView)
         }
    }
    
    private fun addColorAndSkuInfo(colors: List<ColorInfo>) {
        val colorText = TextView(this).apply {
            text = "\n🎨 颜色详情:"
            textSize = 14f
            setTextColor(Color.parseColor("#333333"))
            setPadding(8, 8, 8, 4)
        }
        layoutResults.addView(colorText)
        
        colors.forEach { color ->
            val colorView = TextView(this).apply {
                text = "  ${color.color}: ${color.color_total_quantity ?: 0}件 (${color.sku_count ?: 0}个SKU)"
                textSize = 12f
                setTextColor(Color.parseColor("#666666"))
                setPadding(16, 2, 8, 2)
            }
            layoutResults.addView(colorView)
            
            // 显示该颜色下的SKU
            color.sizes?.forEach { sku ->
                val skuView = TextView(this).apply {
                    text = "    ${sku.sku_code}: ${sku.sku_size} - ${sku.sku_total_quantity ?: 0}件"
                    textSize = 11f
                    setTextColor(Color.parseColor("#888888"))
                    setPadding(24, 1, 8, 1)
                }
                layoutResults.addView(skuView)
                
                // 显示外部条码
                if (!sku.external_codes.isNullOrEmpty()) {
                    val codeView = TextView(this).apply {
                        text = "      条码: ${sku.external_codes.joinToString(", ")}"
                        textSize = 10f
                        setTextColor(Color.parseColor("#999999"))
                        setPadding(32, 1, 8, 1)
                    }
                    layoutResults.addView(codeView)
                }
            }
        }
    }
    
    private fun clearForm() {
        editSearch.setText("")
        txtResult.text = "请扫描或输入商品代码、SKU或外部条码进行搜索"
        txtResult.setTextColor(Color.parseColor("#666666"))
        clearResults()
    }
    
    private fun clearResults() {
        layoutResults.removeAllViews()
        hierarchyAdapter.clear()
        recyclerProductCards.visibility = View.GONE
    }
    
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnSearch.isEnabled = !show
        if (show) {
            txtResult.text = "🔍 搜索中..."
            txtResult.setTextColor(Color.parseColor("#2196F3"))
        }
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
            runOnUiThread {
                editSearch.setText(barcode)
                Toast.makeText(this, "📱 扫码输入: $barcode", Toast.LENGTH_SHORT).show()
                performSearch()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(scanReceiver)
        } catch (e: Exception) {
            Log.w("ScanActivity", "注销广播接收器失败: ${e.message}")
        }
    }
} 