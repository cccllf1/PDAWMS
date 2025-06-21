package com.yourcompany.wmssystem.pdawms

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class LocationInventoryActivity : AppCompatActivity() {
    
    private lateinit var btnBack: Button
    private lateinit var btnRefresh: Button
    private lateinit var txtTitle: TextView
    private lateinit var txtLocationCode: TextView
    private lateinit var txtLocationName: TextView
    private lateinit var txtSkuCount: TextView
    private lateinit var txtProductCount: TextView
    private lateinit var txtTotalQuantity: TextView
    private lateinit var recyclerViewInventory: RecyclerView
    
    private lateinit var inventoryAdapter: LocationInventoryAdapter
    private val inventoryItems = mutableListOf<LocationInventoryItem>()
    
    private var locationCode = ""
    private var locationName = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_inventory)
        
        // 获取传入的库位信息
        locationCode = intent.getStringExtra("location_code") ?: ""
        locationName = intent.getStringExtra("location_name") ?: ""
        
        Log.d("WMS_LOCATION_INV", "📦 库位库存详情界面启动: $locationCode")
        
        initViews()
        setupRecyclerView()
        setupClickListeners()
        loadLocationInventory()
    }
    
    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        btnRefresh = findViewById(R.id.btnRefresh)
        txtTitle = findViewById(R.id.txtTitle)
        txtLocationCode = findViewById(R.id.txtLocationCode)
        txtLocationName = findViewById(R.id.txtLocationName)
        txtSkuCount = findViewById(R.id.txtSkuCount)
        txtProductCount = findViewById(R.id.txtProductCount)
        txtTotalQuantity = findViewById(R.id.txtTotalQuantity)
        recyclerViewInventory = findViewById(R.id.recyclerViewInventory)
        
        // 设置标题信息
        txtLocationCode.text = locationCode
        txtLocationName.text = locationName.ifEmpty { "未命名库位" }
    }
    
    private fun setupRecyclerView() {
        inventoryAdapter = LocationInventoryAdapter(inventoryItems)
        recyclerViewInventory.layoutManager = LinearLayoutManager(this)
        recyclerViewInventory.adapter = inventoryAdapter
    }
    
    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }
        txtTitle.setOnClickListener { finish() }  // 点击标题也可以返回
        btnRefresh.setOnClickListener { loadLocationInventory() }
    }
    
    private fun loadLocationInventory() {
        lifecycleScope.launch {
            try {
                Log.d("WMS_LOCATION_INV", "🔄 开始加载库位库存: $locationCode")
                
                val response = ApiClient.getApiService().getLocationInventory(locationCode)
                if (response.isSuccessful && response.body()?.success == true) {
                    val inventory = response.body()?.data
                    
                    if (inventory != null) {
                        // 更新统计信息
                        val skuCount = inventory.summary?.total_items ?: 0
                        val totalQuantity = inventory.summary?.total_quantity ?: 0
                        txtSkuCount.text = skuCount.toString()
                        txtProductCount.text = skuCount.toString()
                        txtTotalQuantity.text = totalQuantity.toString()
                        
                        // 更新库存列表
                        inventoryItems.clear()
                        inventory.items?.let { items: List<LocationInventoryItem> ->
                            inventoryItems.addAll(items)
                        }
                        
                        inventoryAdapter.notifyDataSetChanged()
                        
                        Log.d("WMS_LOCATION_INV", "✅ 库位库存加载完成: SKU${skuCount}个, 总量${totalQuantity}件")
                    }
                    
                } else {
                    val errorMsg = response.body()?.error_message ?: "获取库位库存失败"
                    Log.e("WMS_LOCATION_INV", "❌ 获取库位库存失败: $errorMsg")
                    Toast.makeText(this@LocationInventoryActivity, errorMsg, Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Log.e("WMS_LOCATION_INV", "❌ 网络错误: ${e.message}", e)
                Toast.makeText(this@LocationInventoryActivity, "网络错误: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// 库位库存列表适配器
class LocationInventoryAdapter(
    private val items: MutableList<LocationInventoryItem>
) : RecyclerView.Adapter<LocationInventoryAdapter.InventoryViewHolder>() {
    
    class InventoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtProductCode: TextView = itemView.findViewById(R.id.txtProductCode)
        val txtProductName: TextView = itemView.findViewById(R.id.txtProductName)
        val txtSkuCode: TextView = itemView.findViewById(R.id.txtSkuCode)
        val txtSkuInfo: TextView = itemView.findViewById(R.id.txtSkuInfo)
        val txtStockQuantity: TextView = itemView.findViewById(R.id.txtStockQuantity)
        val txtUnit: TextView = itemView.findViewById(R.id.txtUnit)
        val txtBatchNumber: TextView = itemView.findViewById(R.id.txtBatchNumber)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InventoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_location_inventory, parent, false)
        return InventoryViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: InventoryViewHolder, position: Int) {
        val item = items[position]
        
        holder.txtProductCode.text = item.product_code
        holder.txtProductName.text = item.product_name ?: "未知商品"
        holder.txtSkuCode.text = item.sku_code
        
        // 拼接尺码颜色信息
        val skuInfo = buildString {
            if (!item.sku_color.isNullOrBlank()) {
                append(item.sku_color)
            }
            if (!item.sku_size.isNullOrBlank()) {
                if (isNotEmpty()) append(" - ")
                append(item.sku_size)
            }
        }
        holder.txtSkuInfo.text = skuInfo.ifEmpty { "-" }
        
        holder.txtStockQuantity.text = item.stock_quantity.toString()
        holder.txtUnit.text = item.unit ?: "件"
        holder.txtBatchNumber.text = item.batch_number ?: "-"
    }
    
    override fun getItemCount(): Int = items.size
} 