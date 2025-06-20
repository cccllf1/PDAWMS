package com.yourcompany.wmssystem.pdawms

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

data class InventoryDisplayItem(
    val product_code: String,
    val product_name: String,
    val total_quantity: Int,
    val location_count: Int,
    val color_count: Int,
    val image_path: String?,
    val colors: List<ColorInfo>
)

class InventoryAdapter(
    private var items: MutableList<InventoryDisplayItem>,
    private val onItemClick: (InventoryDisplayItem) -> Unit
) : RecyclerView.Adapter<InventoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgProduct: ImageView = view.findViewById(R.id.imgProduct)
        val txtProductCode: TextView = view.findViewById(R.id.txtProductCode)
        val txtProductName: TextView = view.findViewById(R.id.txtProductName)
        val txtTotalQuantity: TextView = view.findViewById(R.id.txtTotalQuantity)
        val txtLocationCount: TextView = view.findViewById(R.id.txtLocationCount)
        val txtColorCount: TextView = view.findViewById(R.id.txtColorCount)
        val recyclerColors: RecyclerView = view.findViewById(R.id.recyclerColors)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inventory, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        
        holder.txtProductCode.text = item.product_code
        holder.txtProductName.text = item.product_name
        holder.txtTotalQuantity.text = "总库存: ${item.total_quantity}"
        holder.txtLocationCount.text = "${item.location_count}个库位"
        holder.txtColorCount.text = "${item.color_count}种颜色"
        
        // 设置商品图片 (使用默认图片)
        holder.imgProduct.setImageResource(R.drawable.ic_launcher_foreground)
        
        // 设置点击事件
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<InventoryDisplayItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}

class InventoryActivity : AppCompatActivity() {
    
    private lateinit var recyclerInventory: RecyclerView
    private lateinit var searchEdit: EditText
    private lateinit var btnSearch: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var txtStatus: TextView
    
    private lateinit var inventoryAdapter: InventoryAdapter
    private val inventoryItems = mutableListOf<InventoryDisplayItem>()
    
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
        setContentView(R.layout.activity_inventory)
        
        initViews()
        initUnifiedNavBar()
        setupRecyclerView()
        registerScanReceivers()
        loadInventoryData()
    }
    
    private fun initViews() {
        recyclerInventory = findViewById(R.id.recyclerInventory)
        searchEdit = findViewById(R.id.et_search)
        btnSearch = findViewById(R.id.btn_search)
        progressBar = findViewById(R.id.progressBar)
        txtStatus = findViewById(R.id.txtStatus)
        
        btnSearch.setOnClickListener {
            searchInventory()
        }
    }
    
    private fun initUnifiedNavBar() {
        val navBarContainer = findViewById<LinearLayout>(R.id.navBarContainer)
        unifiedNavBar = UnifiedNavBar.addToActivity(this, navBarContainer, "inventory")
    }
    
    private fun setupRecyclerView() {
        inventoryAdapter = InventoryAdapter(inventoryItems) { item ->
            showProductDetails(item)
        }
        recyclerInventory.layoutManager = LinearLayoutManager(this)
        recyclerInventory.adapter = inventoryAdapter
    }
    
    private fun loadInventoryData() {
        showLoading(true)
        txtStatus.text = "加载库存数据中..."
        
        lifecycleScope.launch {
            try {
                // 使用商品查询接口，因为库存查询接口已被删除
                val response = ApiClient.getApiService().getProducts(page = 1, page_size = 1000)
                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse?.success == true && apiResponse.data != null) {
                        val products = apiResponse.data.products ?: emptyList()
                        val displayItems = products.map { product ->
                            InventoryDisplayItem(
                                product_code = product.product_code,
                                product_name = product.product_name,
                                total_quantity = product.product_total_quantity ?: 0,
                                location_count = product.location_count ?: 0,
                                color_count = product.color_count ?: 0,
                                image_path = product.image_path,
                                colors = product.colors ?: emptyList()
                            )
                        }
                        
                        runOnUiThread {
                            inventoryAdapter.updateItems(displayItems)
                            txtStatus.text = "共 ${displayItems.size} 种商品"
                            showLoading(false)
                        }
                    } else {
                        runOnUiThread {
                            txtStatus.text = "加载失败: ${apiResponse?.error_message ?: "未知错误"}"
                            showLoading(false)
                        }
                    }
                } else {
                    runOnUiThread {
                        txtStatus.text = "网络错误: HTTP ${response.code()}"
                        showLoading(false)
                    }
                }
            } catch (e: Exception) {
                Log.e("InventoryActivity", "加载库存失败: ${e.message}")
                runOnUiThread {
                    txtStatus.text = "加载失败: ${e.message}"
                    showLoading(false)
                    // 显示模拟数据作为备用
                    loadMockData()
                }
            }
        }
    }
    
    private fun searchInventory() {
        val searchText = searchEdit.text.toString().trim()
        if (searchText.isEmpty()) {
            loadInventoryData()
            return
        }
        
        showLoading(true)
        txtStatus.text = "搜索中..."
        
        lifecycleScope.launch {
            try {
                // 尝试按商品编码搜索，使用商品查询接口
                val response = ApiClient.getApiService().getProductByCode(searchText)
                
                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse?.success == true && apiResponse.data != null) {
                        val product = apiResponse.data
                        val displayItems = listOf(
                            InventoryDisplayItem(
                                product_code = product.product_code,
                                product_name = product.product_name,
                                total_quantity = product.product_total_quantity ?: 0,
                                location_count = product.location_count ?: 0,
                                color_count = product.color_count ?: 0,
                                image_path = product.image_path,
                                colors = product.colors ?: emptyList()
                            )
                        )
                        
                        runOnUiThread {
                            inventoryAdapter.updateItems(displayItems)
                            txtStatus.text = "搜索到 ${displayItems.size} 种商品"
                            showLoading(false)
                        }
                    } else {
                        runOnUiThread {
                            txtStatus.text = "未找到相关商品"
                            inventoryAdapter.updateItems(emptyList())
                            showLoading(false)
                        }
                    }
                } else {
                    runOnUiThread {
                        txtStatus.text = "未找到相关商品"
                        inventoryAdapter.updateItems(emptyList())
                        showLoading(false)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    txtStatus.text = "搜索失败: ${e.message}"
                    showLoading(false)
                }
            }
        }
    }
    
    private fun loadMockData() {
        val mockItems = listOf(
            InventoryDisplayItem("129092", "女士T恤", 150, 3, 2, null, emptyList()),
            InventoryDisplayItem("201234", "男士牛仔裤", 89, 2, 3, null, emptyList()),
            InventoryDisplayItem("301456", "运动鞋", 45, 1, 4, null, emptyList())
        )
        inventoryAdapter.updateItems(mockItems)
        txtStatus.text = "离线模式: 共 ${mockItems.size} 种商品"
    }
    
    private fun showProductDetails(item: InventoryDisplayItem) {
        val details = StringBuilder().apply {
            append("商品编码: ${item.product_code}\n")
            append("商品名称: ${item.product_name}\n")
            append("总库存: ${item.total_quantity}\n")
            append("库位数量: ${item.location_count}\n")
            append("颜色数量: ${item.color_count}\n")
            
            if (item.colors.isNotEmpty()) {
                append("\n颜色明细:\n")
                item.colors.forEach { color ->
                    append("- ${color.color}: ${color.color_total_quantity ?: 0}件\n")
                }
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("商品详情")
            .setMessage(details.toString())
            .setPositiveButton("确定", null)
            .show()
    }
    
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnSearch.isEnabled = !show
        // btnRefresh.isEnabled = !show // 已移除刷新按钮
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
                searchEdit.setText(barcode)
                searchInventory()
                Toast.makeText(this, "扫码输入: $barcode", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(scanReceiver)
        } catch (e: Exception) {
            Log.w("InventoryActivity", "注销广播接收器失败: ${e.message}")
        }
    }
} 