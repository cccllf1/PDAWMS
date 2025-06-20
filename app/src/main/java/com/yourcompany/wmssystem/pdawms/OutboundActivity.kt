package com.yourcompany.wmssystem.pdawms

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

data class OutboundItem(
    val sku: String,
    val productName: String,
    var location: String,
    var quantity: Int,
    val color: String,
    val size: String,
    val batch: String = "",
    var imageUrl: String = "",
    var maxStock: Int = 0,
    val locationStocks: Map<String, Int> = emptyMap(),  // 当前SKU在各库位的库存分布
    val productId: String? = null,
    val allColors: List<ColorOption> = emptyList(),  // 商品的所有颜色选项
    val allSizes: Map<String, List<SizeOption>> = emptyMap(),  // 每个颜色下的所有尺码选项
    var selectedColorIndex: Int = 0,  // 当前选择的颜色索引
    var selectedSizeIndex: Int = 0,   // 当前选择的尺码索引
    val isSkuLocked: Boolean = false  // 是否锁定SKU（扫描特定SKU时为true）
)

data class ColorOption(
    val color: String,
    val imagePath: String
)

data class SizeOption(
    val skuCode: String,
    val skuSize: String,
    val locationStocks: Map<String, Int>
)

class OutboundListAdapter(
    private var items: MutableList<OutboundItem>,
    private val getLocationOptions: () -> List<String>,
    private val onDeleteClick: (Int) -> Unit,
    private val onItemUpdate: (Int, OutboundItem) -> Unit,
    private val onSmartSplit: (Int, Int) -> Unit
) : RecyclerView.Adapter<OutboundListAdapter.ViewHolder>() {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgProduct: ImageView = view.findViewById(R.id.imgProduct)
        val txtProductCode: TextView = view.findViewById(R.id.txtProductCode)
        val spinnerColor: Spinner = view.findViewById(R.id.spinnerColor)
        val spinnerSize: Spinner = view.findViewById(R.id.spinnerSize)
        val spinnerLocation: Spinner = view.findViewById(R.id.spinnerLocation)
        val editQuantity: EditText = view.findViewById(R.id.editQuantity)
        val txtCurrentStock: TextView = view.findViewById(R.id.txtCurrentStock)
        val txtMaxStock: TextView = view.findViewById(R.id.txtMaxStock)
        val btnDelete: Button = view.findViewById(R.id.btnDelete)
        val txtSkuMaxStock: TextView = view.findViewById(R.id.txtSkuMaxStock)
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val layoutInflater = android.view.LayoutInflater.from(parent.context)
        val view = layoutInflater.inflate(R.layout.item_outbound_product, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        
        // 设置商品信息
        holder.txtProductCode.text = "SKU: ${item.sku}"
        holder.txtCurrentStock.text = "${item.maxStock}"
        holder.txtMaxStock.text = ""
        
        // 显示库存和货位信息
        val totalAvailableStock = item.locationStocks.values.sum()
        val stockLocationCount = item.locationStocks.filter { it.value > 0 }.size
        holder.txtSkuMaxStock.text = "库存：${totalAvailableStock}件\n货位：${stockLocationCount}个"
        
        // 设置图片
        if (item.imageUrl.isNotEmpty()) {
            val processedImageUrl = processImageUrl(item.imageUrl, holder.itemView.context)
            Glide.with(holder.itemView.context)
                .load(processedImageUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_gallery)
                .into(holder.imgProduct)
        } else {
            holder.imgProduct.setImageResource(android.R.drawable.ic_menu_gallery)
        }
        
        // 设置颜色选择器（只显示有库存的颜色）
        setupColorSpinner(holder, item, position)
        
        // 设置尺码选择器（只显示当前颜色下有库存的尺码）
        setupSizeSpinner(holder, item, position)
        
        // 设置库位选择器（只显示当前SKU有库存的库位）
        setupLocationSpinner(holder, item, position)
        
        // 设置数量
        holder.editQuantity.setText(item.quantity.toString())
        
        // 数量输入监听器 - 支持自动拆分
        holder.editQuantity.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                try {
                    if (holder.adapterPosition == RecyclerView.NO_POSITION || 
                        holder.adapterPosition >= items.size || 
                        holder.adapterPosition < 0) {
                        return
                    }
                    
                    val requestedQuantity = s.toString().toIntOrNull() ?: 0
                    val currentItem = items[holder.adapterPosition]
                    
                    if (requestedQuantity <= 0) {
                        val updatedItem = currentItem.copy(quantity = 0)
                        items[holder.adapterPosition] = updatedItem
                        onItemUpdate(holder.adapterPosition, updatedItem)
                        return
                    }
                    
                    if (requestedQuantity <= currentItem.maxStock) {
                        // 不超库存，直接设置
                        val updatedItem = currentItem.copy(quantity = requestedQuantity)
                        items[holder.adapterPosition] = updatedItem
                        onItemUpdate(holder.adapterPosition, updatedItem)
                    } else {
                        // 超出库存，自动拆分一条
                        val shortage = requestedQuantity - currentItem.maxStock
                        
                        // 检查是否有其他库位有库存
                        val otherLocationStocks = currentItem.locationStocks.filter { (location, stock) ->
                            location != currentItem.location && stock > 0
                        }
                        
                        if (otherLocationStocks.isNotEmpty()) {
                            val totalAvailableStock = otherLocationStocks.values.sum() + currentItem.maxStock
                            
                            if (requestedQuantity <= totalAvailableStock) {
                                // 可以通过拆分满足需求，自动执行
                                Log.d("WMS_OUTBOUND", "🧠 自动拆分: 输入 $requestedQuantity，当前库位 ${currentItem.maxStock}，缺少 $shortage")
                                
                                // 先设置当前项为最大库存
                                val updatedItem = currentItem.copy(quantity = currentItem.maxStock)
                                items[holder.adapterPosition] = updatedItem
                                holder.editQuantity.setText(currentItem.maxStock.toString())
                                onItemUpdate(holder.adapterPosition, updatedItem)
                                
                                // 触发自动拆分
                                onSmartSplit(holder.adapterPosition, shortage)
                                
                                Toast.makeText(holder.itemView.context, 
                                    "✅ 自动拆分！当前 ${currentItem.maxStock} 件，拆分 $shortage 件到其他库位", 
                                    Toast.LENGTH_SHORT).show()
                            } else {
                                // 即使拆分也不够
                                Toast.makeText(holder.itemView.context, 
                                    "库存不足！需要 $requestedQuantity 件，全部库位总共只有 $totalAvailableStock 件", 
                                    Toast.LENGTH_LONG).show()
                                holder.editQuantity.setText(currentItem.maxStock.toString())
                            }
                        } else {
                            // 没有其他库位有库存
                            Toast.makeText(holder.itemView.context, 
                                "库存不足！当前库位最大 ${currentItem.maxStock} 件，其他库位无库存", 
                                Toast.LENGTH_SHORT).show()
                            holder.editQuantity.setText(currentItem.maxStock.toString())
                        }
                    }
                } catch (e: Exception) {
                    Log.e("OutboundAdapter", "数量输入异常: ${e.message}")
                }
            }
        })
        
        // 删除按钮
        holder.btnDelete.setOnClickListener {
            onDeleteClick(holder.adapterPosition)
        }
    }
    
    private fun setupColorSpinner(holder: ViewHolder, item: OutboundItem, position: Int) {
        // 设置颜色选择器（只显示有库存的颜色）
        if (item.allColors.isNotEmpty()) {
            val colorLabels = item.allColors.map { it.color }
            val adapter = ArrayAdapter(holder.itemView.context, android.R.layout.simple_spinner_item, colorLabels)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            holder.spinnerColor.adapter = adapter
            
            // 设置当前选择
            val currentColorIndex = item.allColors.indexOfFirst { it.color == item.color }
            if (currentColorIndex >= 0) {
                holder.spinnerColor.setSelection(currentColorIndex)
                items[position].selectedColorIndex = currentColorIndex
            }
            
            // 🔒 如果SKU被锁定，禁用颜色选择器
            holder.spinnerColor.isEnabled = !item.isSkuLocked
            
            // 监听颜色选择变化（只在未锁定时有效）
            if (!item.isSkuLocked) {
                holder.spinnerColor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                        if (holder.adapterPosition != RecyclerView.NO_POSITION && holder.adapterPosition < items.size) {
                            val selectedColor = item.allColors[pos]
                            items[holder.adapterPosition].selectedColorIndex = pos
                            
                            // 更新图片
                            if (selectedColor.imagePath.isNotEmpty()) {
                                val processedImageUrl = processImageUrl(selectedColor.imagePath, holder.itemView.context)
                                Glide.with(holder.itemView.context)
                                    .load(processedImageUrl)
                                    .placeholder(android.R.drawable.ic_menu_gallery)
                                    .error(android.R.drawable.ic_menu_gallery)
                                    .into(holder.imgProduct)
                                items[holder.adapterPosition].imageUrl = selectedColor.imagePath
                            }
                            
                            // 更新尺码选择器
                            setupSizeSpinner(holder, items[holder.adapterPosition], holder.adapterPosition)
                        }
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            } else {
                // 锁定状态下，清除监听器
                holder.spinnerColor.onItemSelectedListener = null
            }
        }
    }
    
    private fun setupSizeSpinner(holder: ViewHolder, item: OutboundItem, position: Int) {
        // 设置尺码选择器（只显示当前颜色下有库存的尺码）
        val selectedColor = if (item.selectedColorIndex < item.allColors.size) {
            item.allColors[item.selectedColorIndex].color
        } else {
            item.color
        }
        
        val sizesForColor = item.allSizes[selectedColor] ?: emptyList()
        
        if (sizesForColor.isNotEmpty()) {
            val sizeLabels = sizesForColor.map { "${it.skuSize} (${it.locationStocks.values.sum()}件)" }
            val adapter = ArrayAdapter(holder.itemView.context, android.R.layout.simple_spinner_item, sizeLabels)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            holder.spinnerSize.adapter = adapter
            
            // 设置当前选择
            val currentSizeIndex = sizesForColor.indexOfFirst { it.skuCode == item.sku }
            if (currentSizeIndex >= 0) {
                holder.spinnerSize.setSelection(currentSizeIndex)
                items[position].selectedSizeIndex = currentSizeIndex
            }
            
            // 🔒 如果SKU被锁定，禁用尺码选择器
            holder.spinnerSize.isEnabled = !item.isSkuLocked
            
            // 监听尺码选择变化（只在未锁定时有效）
            if (!item.isSkuLocked) {
                holder.spinnerSize.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                        if (holder.adapterPosition != RecyclerView.NO_POSITION && holder.adapterPosition < items.size) {
                            val selectedSize = sizesForColor[pos]
                            items[holder.adapterPosition].selectedSizeIndex = pos
                            
                            // 更新SKU相关信息
                            val updatedItem = items[holder.adapterPosition].copy(
                                sku = selectedSize.skuCode,
                                size = selectedSize.skuSize,
                                locationStocks = selectedSize.locationStocks,
                                location = selectedSize.locationStocks.maxByOrNull { it.value }?.key ?: "无货位",
                                maxStock = selectedSize.locationStocks.maxByOrNull { it.value }?.value ?: 0,
                                quantity = minOf(items[holder.adapterPosition].quantity, selectedSize.locationStocks.values.maxOrNull() ?: 0)
                            )
                            items[holder.adapterPosition] = updatedItem
                            onItemUpdate(holder.adapterPosition, updatedItem)
                            
                            // 更新显示信息
                            holder.txtProductCode.text = "SKU: ${updatedItem.sku}"
                            val totalStock = updatedItem.locationStocks.values.sum()
                            val stockLocationCount = updatedItem.locationStocks.filter { it.value > 0 }.size
                            holder.txtSkuMaxStock.text = "库存：${totalStock}件\n货位：${stockLocationCount}个"
                            
                            holder.txtCurrentStock.text = "${updatedItem.maxStock}"
                            holder.txtMaxStock.text = ""
                            holder.editQuantity.setText(updatedItem.quantity.toString())
                            
                            // 更新库位选择器
                            setupLocationSpinner(holder, updatedItem, holder.adapterPosition)
                        }
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            } else {
                // 锁定状态下，清除监听器
                holder.spinnerSize.onItemSelectedListener = null
            }
        }
    }
    
    private fun setupLocationSpinner(holder: ViewHolder, item: OutboundItem, position: Int) {
        // 设置库位选择器（只显示当前SKU有库存的库位）
        val availableLocations = item.locationStocks.filter { it.value > 0 }
        val locationLabels = availableLocations.map { (location, stock) ->
            "$location (${stock}件)"
        }
        
        if (locationLabels.isNotEmpty()) {
            val adapter = ArrayAdapter(holder.itemView.context, android.R.layout.simple_spinner_item, locationLabels)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            holder.spinnerLocation.adapter = adapter
            
            // 设置当前选择
            val currentLocationIndex = availableLocations.keys.indexOf(item.location)
            if (currentLocationIndex >= 0) {
                holder.spinnerLocation.setSelection(currentLocationIndex)
            }
            
            // 监听库位选择变化
            holder.spinnerLocation.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                    if (holder.adapterPosition != RecyclerView.NO_POSITION && holder.adapterPosition < items.size) {
                        val selectedLocation = availableLocations.keys.elementAt(pos)
                        val selectedStock = availableLocations[selectedLocation] ?: 0
                        
                        val updatedItem = items[holder.adapterPosition].copy(
                            location = selectedLocation,
                            maxStock = selectedStock,
                            quantity = minOf(items[holder.adapterPosition].quantity, selectedStock)
                        )
                        items[holder.adapterPosition] = updatedItem
                        onItemUpdate(holder.adapterPosition, updatedItem)
                        
                        // 更新显示
                        val totalStock = updatedItem.locationStocks.values.sum()
                        val stockLocationCount = updatedItem.locationStocks.filter { it.value > 0 }.size
                        holder.txtSkuMaxStock.text = "库存：${totalStock}件\n货位：${stockLocationCount}个"
                        
                        holder.txtCurrentStock.text = "$selectedStock"
                        holder.txtMaxStock.text = ""
                        holder.editQuantity.setText(updatedItem.quantity.toString())
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
    }

    private fun processImageUrl(rawImageUrl: String, context: Context): String {
        return if (rawImageUrl.isNotEmpty()) {
            if (rawImageUrl.startsWith("http://") || rawImageUrl.startsWith("https://")) {
                rawImageUrl
            } else {
                val baseUrl = ApiClient.getServerUrl(context)
                "${baseUrl.trimEnd('/')}/$rawImageUrl"
            }
        } else {
            ""
        }
    }
}

class OutboundActivity : AppCompatActivity() {
    
    private lateinit var editProductCode: EditText
    private lateinit var btnConfirmProduct: Button
    private lateinit var txtOutboundTitle: TextView
    private lateinit var btnConfirmOutbound: Button
    private lateinit var recyclerOutboundList: RecyclerView
    private lateinit var editQuantityInput: EditText  // 新增：数量输入框
    
    private lateinit var outboundAdapter: OutboundListAdapter
    private val outboundItems = mutableListOf<OutboundItem>()
    private val locationOptions = mutableListOf<String>()
    
    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val scanData = when (intent?.action) {
                "android.intent.action.SCANRESULT" -> intent.getStringExtra("value")
                "android.intent.ACTION_DECODE_DATA" -> intent.getStringExtra("barcode_string")
                "com.symbol.datawedge.api.RESULT_ACTION" -> intent.getStringExtra("com.symbol.datawedge.data_string")
                "com.honeywell.decode.intent.action.SCAN_RESULT" -> intent.getStringExtra("SCAN_RESULT")
                "nlscan.action.SCANNER_RESULT" -> intent.getStringExtra("SCAN_BARCODE1")
                "scan.rcv.message" -> intent.getStringExtra("barocode")
                else -> null
            }
            
            scanData?.let { insertToFocusedEditText(it) }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_outbound)
        
        Log.d("WMS_OUTBOUND", "📤 出库页面启动")
        
        initViews()
        setupRecyclerView()
        setupClickListeners()
        loadLocationOptions()
        
        // 添加统一导航栏
        val navBarContainer = findViewById<LinearLayout>(R.id.navBarContainer)
        UnifiedNavBar.addToActivity(this, navBarContainer, "outbound")
    }
    
    override fun onResume() {
        super.onResume()
        Log.d("WMS_OUTBOUND", "📤 出库页面恢复，注册扫码接收器")
        // 注册扫码广播接收器
        setupScanReceiver()
    }
    
    override fun onPause() {
        super.onPause()
        Log.d("WMS_OUTBOUND", "📤 出库页面暂停，注销扫码接收器")
        try {
            unregisterReceiver(scanReceiver)
        } catch (e: Exception) {
            Log.e("WMS_OUTBOUND", "注销扫码接收器失败: ${e.message}")
        }
    }
    
    private fun initViews() {
        editProductCode = findViewById(R.id.editProductCode)
        btnConfirmProduct = findViewById(R.id.btnConfirmProduct)
        txtOutboundTitle = findViewById(R.id.txtOutboundTitle)
        btnConfirmOutbound = findViewById(R.id.btnConfirmOutbound)
        recyclerOutboundList = findViewById(R.id.recyclerOutboundList)
        editQuantityInput = findViewById(R.id.editQuantityInput)
    }
    
    private fun setupRecyclerView() {
        outboundAdapter = OutboundListAdapter(
            outboundItems,
            { locationOptions },
            { position -> deleteOutboundItem(position) },
            { position, item -> updateOutboundItem(position, item) },
            { position, shortage -> smartSplit(position, shortage) }
        )
        
        recyclerOutboundList.layoutManager = LinearLayoutManager(this)
        recyclerOutboundList.adapter = outboundAdapter
    }
    
    private fun setupScanReceiver() {
        val intentFilter = IntentFilter().apply {
            addAction("android.intent.action.SCANRESULT")
            addAction("android.intent.ACTION_DECODE_DATA")
            addAction("com.symbol.datawedge.api.RESULT_ACTION")
            addAction("com.honeywell.decode.intent.action.SCAN_RESULT")
            addAction("nlscan.action.SCANNER_RESULT")
            addAction("scan.rcv.message")
        }
        registerReceiver(scanReceiver, intentFilter)
    }

    private fun insertToFocusedEditText(data: String) {
        runOnUiThread {
            val focusedView = currentFocus
            when (focusedView) {
                editProductCode -> {
                    editProductCode.setText(data)
                    Log.d("WMS_SCAN", "📦 扫码输入到商品编码框: $data")
                    // 扫码后自动执行查询，但不清空输入框
                    smartQueryProduct(data, keepInput = true)
                }
                editQuantityInput -> {
                    editQuantityInput.setText(data)
                    Log.d("WMS_SCAN", "📦 扫码输入到数量框: $data")
                }
                else -> {
                    // 如果焦点在其他地方，默认填入商品码输入框
                    editProductCode.setText(data)
                    Log.d("WMS_SCAN", "📦 扫码输入到默认商品编码框: $data")
                    // 扫码后自动执行查询，但不清空输入框
                    smartQueryProduct(data, keepInput = true)
                }
            }
        }
    }

    private fun smartQueryProduct(productCode: String, keepInput: Boolean = false) {
        Log.d("WMS_OUTBOUND", "🔍 开始智能查询: $productCode")
        
        lifecycleScope.launch {
            try {
                // 1️⃣ 首先尝试SKU外部条码查询
                if (productCode.contains("-")) {
                    try {
                        Log.d("WMS_OUTBOUND", "🔍 尝试SKU外部条码查询: $productCode")
                        val skuResponse = ApiClient.getApiService().getSkuByExternalCode(productCode)
                        if (skuResponse.isSuccessful) {
                            val skuApiResponse = skuResponse.body()
                            if (skuApiResponse?.success == true && skuApiResponse.data != null) {
                                Log.d("WMS_OUTBOUND", "✅ SKU外部条码查询成功: ${skuApiResponse.data.sku_code}")
                                handleSkuData(skuApiResponse.data)
                                if (!keepInput) {
                                    editProductCode.setText("")
                                }
                                return@launch
                            }
                        }
                        Log.d("WMS_OUTBOUND", "SKU外部条码查询失败，尝试商品查询")
                    } catch (e: Exception) {
                        Log.d("WMS_OUTBOUND", "SKU外部条码查询异常: ${e.message}")
                    }
                }
                
                // 2️⃣ 采用入库页面的逻辑：直接查询商品
                val baseProductCode = productCode.split("-").firstOrNull() ?: productCode
                Log.d("WMS_OUTBOUND", "📦 直接查询商品: $baseProductCode")
                
                try {
                    val productResponse = ApiClient.getApiService().getProductByCode(baseProductCode)
                    if (productResponse.isSuccessful) {
                        val productApiResponse = productResponse.body()
                        if (productApiResponse?.success == true && productApiResponse.data != null) {
                            val productData = productApiResponse.data
                            Log.d("WMS_OUTBOUND", "✅ 商品查询成功: ${productData.product_name}")
                            
                            // 如果扫码的是完整SKU，解析目标SKU
                            if (productCode.contains("-")) {
                                val targetSku = productCode
                                Log.d("WMS_OUTBOUND", "🔍 解析目标SKU: $targetSku")
                                handleProductDataWithTargetSku(productData, targetSku)
                            } else {
                                // 扫码的是商品编码，使用普通处理
                                handleProductData(productData)
                            }
                            
                            if (!keepInput) {
                                editProductCode.setText("")
                            }
                            return@launch
                        }
                    }
                    Log.d("WMS_OUTBOUND", "商品查询失败")
                } catch (e: Exception) {
                    Log.d("WMS_OUTBOUND", "商品查询异常: ${e.message}")
                }
                
                // 3️⃣ 尝试商品外部条码查询
                try {
                    val productResponse = ApiClient.getApiService().getProductByExternalCode(productCode)
                    if (productResponse.isSuccessful) {
                        val productApiResponse = productResponse.body()
                        if (productApiResponse?.success == true && productApiResponse.data != null) {
                            val productData = productApiResponse.data
                            Log.d("WMS_OUTBOUND", "✅ 商品外部条码查询成功: ${productData.product_name}")
                            
                            // 如果扫码的是完整SKU，解析目标SKU
                            if (productCode.contains("-")) {
                                val targetSku = productCode
                                Log.d("WMS_OUTBOUND", "🔍 解析目标SKU: $targetSku")
                                handleProductDataWithTargetSku(productData, targetSku)
                            } else {
                                // 扫码的是商品编码，使用普通处理
                                handleProductData(productData)
                            }
                            
                            if (!keepInput) {
                                editProductCode.setText("")
                            }
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    Log.d("WMS_OUTBOUND", "商品外部条码查询失败: ${e.message}")
                }
                
                // 4️⃣ 所有查询都失败
                Log.w("WMS_OUTBOUND", "⚠️ 所有查询方式都失败")
                Toast.makeText(this@OutboundActivity, "未找到商品或SKU: $productCode", Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                Log.e("WMS_OUTBOUND", "❌ 智能查询异常: ${e.message}")
                Toast.makeText(this@OutboundActivity, "网络错误，请重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleSkuData(skuData: SkuInfo) {
        try {
            // 获取预设数量
            val presetQuantity = editQuantityInput.text.toString().toIntOrNull() ?: 1
            Log.d("WMS_OUTBOUND", "📦 处理SKU数据: ${skuData.sku_code}, 预设数量: $presetQuantity")
            
            // 构建库位库存分布
            val locationStocks = mutableMapOf<String, Int>()
            var totalStock = 0
            skuData.locations?.forEach { locationData ->
                if (locationData.stock_quantity > 0) {
                    locationStocks[locationData.location_code] = locationData.stock_quantity
                    totalStock += locationData.stock_quantity
                }
            }
            
            if (totalStock == 0) {
                Toast.makeText(this@OutboundActivity, "SKU ${skuData.sku_code} 库存为0，无法出库", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 检查预设数量是否超出总库存
            if (presetQuantity > totalStock) {
                Toast.makeText(this@OutboundActivity, 
                    "SKU ${skuData.sku_code} 库存不足！需要 $presetQuantity 件，总库存只有 $totalStock 件", 
                    Toast.LENGTH_LONG).show()
                return
            }
            
            // 默认选择库存少的库位（优先清空小库位）
            val defaultLocation = locationStocks.minByOrNull { it.value }?.key ?: "无货位"
            val defaultLocationStock = locationStocks[defaultLocation] ?: totalStock
            
            // 🔍 检查是否已存在相同SKU+库位的出库项目
            val existingIndex = outboundItems.indexOfFirst { item ->
                item.sku == skuData.sku_code && item.location == defaultLocation
            }
            
            Log.d("WMS_OUTBOUND", "🔍 检查库位占用情况:")
            Log.d("WMS_OUTBOUND", "   目标SKU: ${skuData.sku_code}")
            Log.d("WMS_OUTBOUND", "   默认库位: $defaultLocation")
            Log.d("WMS_OUTBOUND", "   默认库位库存: $defaultLocationStock")
            Log.d("WMS_OUTBOUND", "   预设数量: $presetQuantity")
            Log.d("WMS_OUTBOUND", "   已存在索引: $existingIndex")
            
            // 打印所有现有的出库项目
            outboundItems.forEachIndexed { index, item ->
                Log.d("WMS_OUTBOUND", "   现有项目[$index]: ${item.sku} 在 ${item.location}, 数量: ${item.quantity}/${item.maxStock}")
            }
            
            if (existingIndex >= 0) {
                // 已存在，累加数量
                val existingItem = outboundItems[existingIndex]
                val newQuantity = existingItem.quantity + presetQuantity
                val maxAllowedQuantity = existingItem.maxStock
                
                if (newQuantity <= maxAllowedQuantity) {
                    // 不超库存，直接累加
                    val updatedItem = existingItem.copy(quantity = newQuantity)
                    outboundItems[existingIndex] = updatedItem
                    Log.d("WMS_OUTBOUND", "✅ 累加数量: ${skuData.sku_code} 在 $defaultLocation，原数量 ${existingItem.quantity} + $presetQuantity = $newQuantity")
                    Toast.makeText(this@OutboundActivity, "✅ 累加数量: ${skuData.sku_code} (+$presetQuantity)", Toast.LENGTH_SHORT).show()
                } else {
                    // 超出库存，尝试从其他库位补充
                    Log.d("WMS_OUTBOUND", "⚠️ 当前库位库存不足，尝试从其他库位补充")
                    val usedQuantity = maxAllowedQuantity - existingItem.quantity
                    val remainingNeed = presetQuantity - usedQuantity
                    
                    if (usedQuantity > 0) {
                        // 先填满当前库位
                        val updatedItem = existingItem.copy(quantity = maxAllowedQuantity)
                        outboundItems[existingIndex] = updatedItem
                        Log.d("WMS_OUTBOUND", "✅ 填满当前库位: ${skuData.sku_code} 在 $defaultLocation，数量: ${existingItem.quantity} → $maxAllowedQuantity")
                    }
                    
                    // 从其他库位补充剩余数量
                    if (remainingNeed > 0) {
                        Log.d("WMS_OUTBOUND", "🧠 需要从其他库位补充: $remainingNeed 件")
                        smartSplit(existingIndex, remainingNeed)
                    }
                }
            } else {
                // 不存在，创建新的出库项目
                
                // 解析SKU编码获取商品编码、颜色、尺码
                val skuParts = skuData.sku_code.split("-")
                val productCode = skuParts.getOrNull(0) ?: skuData.sku_code
                val color = skuData.sku_color ?: (skuParts.getOrNull(1) ?: "")
                val size = skuData.sku_size ?: (skuParts.getOrNull(2) ?: "")
                
                // 创建出库项目
                val outboundItem = OutboundItem(
                    sku = skuData.sku_code,
                    productName = productCode,  // 如果没有商品名称，使用商品编码
                    location = defaultLocation,
                    quantity = minOf(presetQuantity, defaultLocationStock),
                    color = color,
                    size = size,
                    batch = "",
                    imageUrl = processImageUrl(skuData.image_path ?: ""),
                    maxStock = defaultLocationStock,
                    locationStocks = locationStocks,
                    productId = "",  // SKU查询可能没有product_id
                    allColors = emptyList(),  // SKU查询时暂不处理动态选择器
                    allSizes = emptyMap(),
                    selectedColorIndex = 0,
                    selectedSizeIndex = 0
                )
                outboundItems.add(outboundItem)
                Log.d("WMS_OUTBOUND", "✅ 新增出库项: ${skuData.sku_code} 在 $defaultLocation，数量 $presetQuantity")
                
                // 如果预设数量超过默认库位库存，触发智能拆分
                if (presetQuantity > defaultLocationStock) {
                    val shortage = presetQuantity - defaultLocationStock
                    Log.d("WMS_OUTBOUND", "🧠 需要智能拆分: 预设 $presetQuantity，当前库位 $defaultLocationStock，缺少 $shortage")
                    
                    val position = outboundItems.size - 1
                    smartSplit(position, shortage)
                }
            }
            
            updateOutboundTitle()
            outboundAdapter.notifyDataSetChanged()
            btnConfirmOutbound.isEnabled = outboundItems.isNotEmpty()
            
            Toast.makeText(this@OutboundActivity, "✅ 已添加SKU: ${skuData.sku_code}", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e("WMS_OUTBOUND", "❌ 处理SKU数据异常: ${e.message}")
            Toast.makeText(this@OutboundActivity, "处理SKU数据失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleProductDataWithTargetSku(productData: Product, targetSku: String) {
        try {
            // 获取预设数量
            val presetQuantity = editQuantityInput.text.toString().toIntOrNull() ?: 1
            Log.d("WMS_OUTBOUND", "📦 处理商品数据，目标SKU: $targetSku, 预设数量: $presetQuantity")
            Log.d("WMS_OUTBOUND", "📦 商品信息: ${productData.product_code} - ${productData.product_name}")
            Log.d("WMS_OUTBOUND", "📦 商品总库存: ${productData.product_total_quantity}")
            Log.d("WMS_OUTBOUND", "📦 颜色数量: ${productData.colors?.size ?: 0}")
            Log.d("WMS_OUTBOUND", "📦 SKU数量: ${productData.skus?.size ?: 0}")
            
            // 首先检查 skus 数组中是否有目标SKU
            val skuExists = productData.skus?.any { it.sku_code == targetSku } == true
            Log.d("WMS_OUTBOUND", "🔍 在skus数组中查找目标SKU: $targetSku, 找到: $skuExists")
            
            if (skuExists) {
                Log.d("WMS_OUTBOUND", "✅ SKU存在于数据中，继续在colors结构中查找详细信息")
            } else {
                Log.w("WMS_OUTBOUND", "⚠️ 目标SKU不在skus数组中！")
                productData.skus?.forEach { sku ->
                    Log.d("WMS_OUTBOUND", "   可用SKU: ${sku.sku_code}")
                }
            }
            
            // 查找目标SKU
            Log.d("WMS_OUTBOUND", "🔍 在商品数据中查找目标SKU: $targetSku")
            Log.d("WMS_OUTBOUND", "🔍 商品数据包含 ${productData.colors?.size ?: 0} 种颜色")
            
            var foundTargetSku = false
            var allAvailableSkus = mutableListOf<String>()
            var availableSkusWithStock = mutableListOf<String>()
            
            productData.colors?.forEach { colorData ->
                Log.d("WMS_OUTBOUND", "🎨 检查颜色: ${colorData.color}, 有 ${colorData.sizes?.size ?: 0} 个尺码")
                colorData.sizes?.forEach { sizeData ->
                    val skuCode = sizeData.sku_code
                    val skuSize = sizeData.sku_size ?: ""
                    val skuColor = colorData.color
                    val skuStock = sizeData.sku_total_quantity ?: 0
                    allAvailableSkus.add(skuCode)
                    
                    if (skuStock > 0) {
                        availableSkusWithStock.add(skuCode)
                    }
                    
                    Log.d("WMS_OUTBOUND", "📏 检查SKU: $skuCode (颜色:$skuColor, 尺码:$skuSize), 库存: $skuStock")
                    
                    // 精确匹配
                    if (skuCode == targetSku) {
                        foundTargetSku = true
                        Log.d("WMS_OUTBOUND", "🎯 精确匹配找到目标SKU: $targetSku (颜色:$skuColor, 尺码:$skuSize), 库存: $skuStock")
                        
                        // 检查库存
                        if (skuStock > 0) {
                            processTargetSku(sizeData, colorData, productData, presetQuantity)
                        } else {
                            Log.w("WMS_OUTBOUND", "❌ 目标SKU库存为0: $targetSku")
                            Toast.makeText(this@OutboundActivity, "SKU $targetSku 库存为0，无法出库", Toast.LENGTH_LONG).show()
                        }
                        return
                    }
                }
            }
            
            // 如果没有精确匹配，尝试模糊匹配
            if (!foundTargetSku) {
                Log.d("WMS_OUTBOUND", "🔍 精确匹配失败，尝试模糊匹配")
                Log.d("WMS_OUTBOUND", "🔍 所有可用SKU: ${allAvailableSkus.joinToString(", ")}")
                
                // 尝试忽略大小写的匹配
                val targetSkuLower = targetSku.lowercase()
                productData.colors?.forEach { colorData ->
                    colorData.sizes?.forEach { sizeData ->
                        val skuCode = sizeData.sku_code
                        val skuStock = sizeData.sku_total_quantity ?: 0
                        if (skuCode.lowercase() == targetSkuLower) {
                            foundTargetSku = true
                            Log.d("WMS_OUTBOUND", "🎯 模糊匹配找到目标SKU: $targetSku → $skuCode, 库存: $skuStock")
                            
                            // 检查库存
                            if (skuStock > 0) {
                                processTargetSku(sizeData, colorData, productData, presetQuantity)
                            } else {
                                Log.w("WMS_OUTBOUND", "❌ 目标SKU库存为0: $skuCode")
                                Toast.makeText(this@OutboundActivity, "SKU $skuCode 库存为0，无法出库", Toast.LENGTH_LONG).show()
                            }
                            return
                        }
                    }
                }
            }
            
            if (!foundTargetSku) {
                Log.w("WMS_OUTBOUND", "❌ 未找到目标SKU: $targetSku")
                Log.w("WMS_OUTBOUND", "❌ 所有可用SKU: ${allAvailableSkus.joinToString(", ")}")
                Log.w("WMS_OUTBOUND", "❌ 有库存的SKU: ${availableSkusWithStock.joinToString(", ")}")
                
                if (availableSkusWithStock.isNotEmpty()) {
                    Toast.makeText(this@OutboundActivity, 
                        "未找到SKU: $targetSku\n有库存的SKU: ${availableSkusWithStock.take(5).joinToString(", ")}", 
                        Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@OutboundActivity, 
                        "未找到SKU: $targetSku\n该商品所有SKU都无库存", 
                        Toast.LENGTH_LONG).show()
                }
                return
            }
            
        } catch (e: Exception) {
            Log.e("WMS_OUTBOUND", "❌ 处理目标SKU数据异常: ${e.message}")
            Toast.makeText(this@OutboundActivity, "处理SKU数据失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun processTargetSku(sizeData: SkuInfo, colorData: ColorInfo, productData: Product, presetQuantity: Int) {
        val targetSku = sizeData.sku_code
        Log.d("WMS_OUTBOUND", "🎯 处理目标SKU: $targetSku")
        val totalStock = sizeData.sku_total_quantity ?: 0
        
        if (totalStock == 0) {
            Toast.makeText(this@OutboundActivity, "SKU $targetSku 库存为0，无法出库", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 检查预设数量是否超出总库存
        if (presetQuantity > totalStock) {
            Toast.makeText(this@OutboundActivity, 
                "SKU $targetSku 库存不足！需要 $presetQuantity 件，总库存只有 $totalStock 件", 
                Toast.LENGTH_LONG).show()
            return
        }
        
        // 获取各库位的库存分布
        val locationStocks = mutableMapOf<String, Int>()
        sizeData.locations?.forEach { locationData ->
            if (locationData.stock_quantity > 0) {
                locationStocks[locationData.location_code] = locationData.stock_quantity
            }
        }
        
        // 优先选择已有记录的库位，如果没有则选择库存少的库位
        val existingLocations = outboundItems.filter { it.sku == targetSku }
            .map { it.location }
            .filter { locationStocks.containsKey(it) }
        
        val defaultLocation = if (existingLocations.isNotEmpty()) {
            // 优先选择已有记录的库位中库存最少的
            existingLocations.minByOrNull { locationStocks[it] ?: Int.MAX_VALUE } ?: locationStocks.minByOrNull { it.value }?.key ?: "无货位"
        } else {
            // 没有已有记录，选择库存最少的库位
            locationStocks.minByOrNull { it.value }?.key ?: "无货位"
        }
        val defaultLocationStock = locationStocks[defaultLocation] ?: totalStock
        
        // 🔍 检查是否已存在相同SKU+库位的出库项目
        val existingIndex = outboundItems.indexOfFirst { item ->
            item.sku == targetSku && item.location == defaultLocation
        }
        Log.d("WMS_OUTBOUND", "🔍 检查库位占用情况:")
        Log.d("WMS_OUTBOUND", "   目标SKU: $targetSku")
        Log.d("WMS_OUTBOUND", "   默认库位: $defaultLocation")
        Log.d("WMS_OUTBOUND", "   默认库位库存: $defaultLocationStock")
        Log.d("WMS_OUTBOUND", "   已存在索引: $existingIndex")
        
        // 打印所有现有的出库项目
        outboundItems.forEachIndexed { index, item ->
            Log.d("WMS_OUTBOUND", "   现有项目[$index]: ${item.sku} 在 ${item.location}, 数量: ${item.quantity}/${item.maxStock}")
        }
        
        if (existingIndex >= 0) {
            // 已存在，累加数量
            val existingItem = outboundItems[existingIndex]
            val newQuantity = existingItem.quantity + presetQuantity
            val maxAllowedQuantity = existingItem.maxStock
            
            if (newQuantity <= maxAllowedQuantity) {
                // 不超库存，直接累加
                val updatedItem = existingItem.copy(quantity = newQuantity)
                outboundItems[existingIndex] = updatedItem
                Log.d("WMS_OUTBOUND", "✅ 累加数量: $targetSku 在 $defaultLocation，原数量 ${existingItem.quantity} + $presetQuantity = $newQuantity")
                Toast.makeText(this@OutboundActivity, "✅ 累加数量: $targetSku (+$presetQuantity)", Toast.LENGTH_SHORT).show()
            } else {
                // 超出库存，尝试从其他库位补充
                Log.d("WMS_OUTBOUND", "⚠️ 当前库位库存不足，尝试从其他库位补充")
                val usedQuantity = maxAllowedQuantity - existingItem.quantity
                val remainingNeed = presetQuantity - usedQuantity
                
                if (usedQuantity > 0) {
                    // 先填满当前库位
                    val updatedItem = existingItem.copy(quantity = maxAllowedQuantity)
                    outboundItems[existingIndex] = updatedItem
                    Log.d("WMS_OUTBOUND", "✅ 填满当前库位: $targetSku 在 $defaultLocation，数量: ${existingItem.quantity} → $maxAllowedQuantity")
                }
                
                // 从其他库位补充剩余数量
                if (remainingNeed > 0) {
                    Log.d("WMS_OUTBOUND", "🧠 需要从其他库位补充: $remainingNeed 件")
                    smartSplit(existingIndex, remainingNeed)
                }
            }
        } else {
            // 检查是否有相同SKU的其他库位记录可以累加
            val sameSkuItems = outboundItems.filter { it.sku == targetSku }
            Log.d("WMS_OUTBOUND", "🔍 查找相同SKU的记录: 找到 ${sameSkuItems.size} 条")
            
            if (sameSkuItems.isNotEmpty()) {
                // 尝试累加到有剩余库存的库位
                var addedToExisting = false
                for (item in sameSkuItems) {
                    val remainingStock = item.maxStock - item.quantity
                    if (remainingStock > 0) {
                        val addQuantity = minOf(presetQuantity, remainingStock)
                        val itemIndex = outboundItems.indexOf(item)
                        val newQuantity = item.quantity + addQuantity
                        val updatedItem = item.copy(quantity = newQuantity)
                        outboundItems[itemIndex] = updatedItem
                        
                        Log.d("WMS_OUTBOUND", "✅ 累加到现有记录: $targetSku 在 ${item.location}，原数量 ${item.quantity} + $addQuantity = $newQuantity")
                        Toast.makeText(this@OutboundActivity, "✅ 累加数量: $targetSku 在 ${item.location} (+$addQuantity)", Toast.LENGTH_SHORT).show()
                        
                        // 如果还有剩余需求，继续处理
                        val remainingNeed = presetQuantity - addQuantity
                        if (remainingNeed > 0) {
                            Log.d("WMS_OUTBOUND", "🧠 还有剩余需求: $remainingNeed 件，从其他库位补充")
                            smartSplit(itemIndex, remainingNeed)
                        }
                        
                        addedToExisting = true
                        break
                    }
                }
                
                if (!addedToExisting) {
                    // 所有现有记录都已满，创建新记录
                    Log.d("WMS_OUTBOUND", "⚠️ 所有现有记录都已满，创建新记录")
                    createNewItem(targetSku, colorData, productData, presetQuantity, locationStocks, defaultLocation, defaultLocationStock)
                }
            } else {
                // 没有相同SKU的记录，创建新记录
                Log.d("WMS_OUTBOUND", "📦 没有相同SKU的记录，创建新记录")
                createNewItem(targetSku, colorData, productData, presetQuantity, locationStocks, defaultLocation, defaultLocationStock)
            }
        }
        
        Log.d("WMS_OUTBOUND", "✅ 成功添加目标SKU: $targetSku")
        
        updateOutboundTitle()
        outboundAdapter.notifyDataSetChanged()
        btnConfirmOutbound.isEnabled = outboundItems.isNotEmpty()
    }

    private fun handleProductData(productData: Product) {
        try {
            // 获取预设数量
            val presetQuantity = editQuantityInput.text.toString().toIntOrNull() ?: 1
            Log.d("WMS_OUTBOUND", "📦 使用预设数量: $presetQuantity")
            
            // 处理有SKU的商品
            if (productData.colors?.isNotEmpty() == true) {
                // 构建颜色选项和尺码选项
                val allColors = productData.colors.filter { colorData ->
                    // 只包含有库存的颜色
                    colorData.sizes?.any { sizeData ->
                        (sizeData.locations?.any { it.stock_quantity > 0 }) == true
                    } == true
                }.map { colorData ->
                    ColorOption(
                        color = colorData.color,
                        imagePath = colorData.image_path ?: ""
                    )
                }
                
                val allSizes = mutableMapOf<String, List<SizeOption>>()
                productData.colors.forEach { colorData ->
                    val sizesForColor = colorData.sizes?.filter { sizeData ->
                        // 只包含有库存的尺码
                        sizeData.locations?.any { it.stock_quantity > 0 } == true
                    }?.map { sizeData ->
                        val locationStocks = mutableMapOf<String, Int>()
                        sizeData.locations?.forEach { locationData ->
                            if (locationData.stock_quantity > 0) {
                                locationStocks[locationData.location_code] = locationData.stock_quantity
                            }
                        }
                        SizeOption(
                            skuCode = sizeData.sku_code,
                            skuSize = sizeData.sku_size ?: "",
                            locationStocks = locationStocks
                        )
                    } ?: emptyList()
                    
                    if (sizesForColor.isNotEmpty()) {
                        allSizes[colorData.color] = sizesForColor
                    }
                }
                
                // 🔧 修改逻辑：只创建一个商品卡，让用户通过选择器选择具体SKU
                // 找到第一个有库存的SKU作为默认选择
                var defaultSku: SkuInfo? = null
                var defaultColor = ""
                var defaultLocationStocks = mutableMapOf<String, Int>()
                
                productData.colors?.forEach { colorData ->
                    colorData.sizes?.forEach { skuInfo ->
                        val totalStock = skuInfo.sku_total_quantity ?: 0
                        if (totalStock > 0 && defaultSku == null) {
                            defaultSku = skuInfo
                            defaultColor = colorData.color
                            
                            // 获取默认SKU的库位分布
                            skuInfo.locations?.forEach { locationData ->
                                if (locationData.stock_quantity > 0) {
                                    defaultLocationStocks[locationData.location_code] = locationData.stock_quantity
                                }
                            }
                        }
                    }
                }
                
                if (defaultSku != null) {
                    val totalStock = defaultSku!!.sku_total_quantity ?: 0
                    
                    // 检查预设数量是否超出总库存
                    if (presetQuantity > totalStock) {
                        Toast.makeText(this@OutboundActivity, 
                            "SKU ${defaultSku!!.sku_code} 库存不足！需要 $presetQuantity 件，总库存只有 $totalStock 件", 
                            Toast.LENGTH_LONG).show()
                        return
                    }
                    
                    // 默认选择库存少的库位（优先清空小库位）
                    val defaultLocation = defaultLocationStocks.minByOrNull { it.value }?.key ?: "无货位"
                    val defaultLocationStock = defaultLocationStocks[defaultLocation] ?: totalStock
                    
                    // 🔍 检查是否已存在相同SKU+库位的出库项目
                    val existingIndex = outboundItems.indexOfFirst { item ->
                        item.sku == defaultSku!!.sku_code && item.location == defaultLocation
                    }
                    
                    if (existingIndex >= 0) {
                        // 已存在，累加数量
                        val existingItem = outboundItems[existingIndex]
                        val newQuantity = existingItem.quantity + presetQuantity
                        val maxAllowedQuantity = existingItem.maxStock
                        
                        if (newQuantity <= maxAllowedQuantity) {
                            // 不超库存，直接累加
                            val updatedItem = existingItem.copy(quantity = newQuantity)
                            outboundItems[existingIndex] = updatedItem
                            Log.d("WMS_OUTBOUND", "✅ 累加数量: ${defaultSku!!.sku_code} 在 $defaultLocation，原数量 ${existingItem.quantity} + $presetQuantity = $newQuantity")
                            Toast.makeText(this@OutboundActivity, "✅ 累加数量: ${defaultSku!!.sku_code} (+$presetQuantity)", Toast.LENGTH_SHORT).show()
                        } else {
                            // 超出库存，尝试从其他库位补充
                            Log.d("WMS_OUTBOUND", "⚠️ 当前库位库存不足，尝试从其他库位补充")
                            val usedQuantity = maxAllowedQuantity - existingItem.quantity
                            val remainingNeed = presetQuantity - usedQuantity
                            
                            if (usedQuantity > 0) {
                                // 先填满当前库位
                                val updatedItem = existingItem.copy(quantity = maxAllowedQuantity)
                                outboundItems[existingIndex] = updatedItem
                                Log.d("WMS_OUTBOUND", "✅ 填满当前库位: ${defaultSku!!.sku_code} 在 $defaultLocation，数量: ${existingItem.quantity} → $maxAllowedQuantity")
                            }
                            
                            // 从其他库位补充剩余数量
                            if (remainingNeed > 0) {
                                Log.d("WMS_OUTBOUND", "🧠 需要从其他库位补充: $remainingNeed 件")
                                smartSplit(existingIndex, remainingNeed)
                            }
                        }
                    } else {
                        // 不存在，创建新的出库项目
                        val actualQuantity = minOf(presetQuantity, defaultLocationStock)
                        
                        val outboundItem = OutboundItem(
                            sku = defaultSku!!.sku_code,
                            productName = productData.product_name,
                            location = defaultLocation,
                            quantity = actualQuantity,
                            color = defaultColor,
                            size = defaultSku!!.sku_size ?: "",
                            batch = "",
                            imageUrl = processImageUrl(productData.colors?.find { it.color == defaultColor }?.image_path ?: ""),
                            maxStock = defaultLocationStock,
                            locationStocks = defaultLocationStocks,
                            productId = productData.product_id,
                            allColors = allColors,  // 提供所有颜色选项供用户选择
                            allSizes = allSizes,    // 提供所有尺码选项供用户选择
                            selectedColorIndex = allColors.indexOfFirst { it.color == defaultColor }.takeIf { it >= 0 } ?: 0,
                            selectedSizeIndex = 0
                        )
                        outboundItems.add(outboundItem)
                        Log.d("WMS_OUTBOUND", "✅ 新增出库项: ${defaultSku!!.sku_code} 在 $defaultLocation，数量 $actualQuantity")
                        
                        Log.d("WMS_OUTBOUND", "✅ 创建单个商品卡: ${defaultSku!!.sku_code}, 用户可选择其他SKU")
                        
                    }
                } else {
                    Toast.makeText(this@OutboundActivity, "商品 ${productData.product_code} 没有有效库存", Toast.LENGTH_SHORT).show()
                    return
                }
            } else {
                // 处理无SKU的商品 - 需要查询库存分布
                val totalStock = productData.product_total_quantity ?: 0  // 修正：无SKU商品使用product_total_quantity
                if (totalStock > 0) {
                    // 检查预设数量是否超出总库存
                    if (presetQuantity > totalStock) {
                        Toast.makeText(this@OutboundActivity, 
                            "商品 ${productData.product_code} 库存不足！需要 $presetQuantity 件，总库存只有 $totalStock 件", 
                            Toast.LENGTH_LONG).show()
                        return
                    }
                    
                    // 这里应该查询该商品在各库位的分布，暂时使用总库存
                    val locationStocks = mapOf("无货位" to totalStock)
                    
                    val outboundItem = OutboundItem(
                        sku = productData.product_code,
                        productName = productData.product_name,
                        location = "无货位",
                        quantity = presetQuantity,  // 使用预设数量
                        color = "",
                        size = "",
                        batch = "",
                        imageUrl = processImageUrl(productData.image_path ?: ""),
                        maxStock = totalStock,
                        locationStocks = locationStocks,
                        productId = productData.product_id,
                        allColors = emptyList(),
                        allSizes = emptyMap(),
                        selectedColorIndex = 0,
                        selectedSizeIndex = 0
                    )
                    outboundItems.add(outboundItem)
                } else {
                    Toast.makeText(this@OutboundActivity, "商品 ${productData.product_code} 库存为0，无法出库", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            
            updateOutboundTitle()
            outboundAdapter.notifyDataSetChanged()
            btnConfirmOutbound.isEnabled = outboundItems.isNotEmpty()
            
        } catch (e: Exception) {
            Log.e("WMS_OUTBOUND", "❌ 处理商品数据异常: ${e.message}")
            Toast.makeText(this@OutboundActivity, "处理商品数据失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun deleteOutboundItem(position: Int) {
        if (position >= 0 && position < outboundItems.size) {
            outboundItems.removeAt(position)
            outboundAdapter.notifyItemRemoved(position)
            updateOutboundTitle()
            btnConfirmOutbound.isEnabled = outboundItems.isNotEmpty()
        }
    }
    
    private fun updateOutboundItem(position: Int, item: OutboundItem) {
        if (position >= 0 && position < outboundItems.size) {
            val oldItem = outboundItems[position]
            // 只在真正有变化时才输出日志
            if (oldItem.quantity != item.quantity || oldItem.location != item.location) {
                Log.d("WMS_OUTBOUND", "📝 更新出库项[$position]: ${item.sku} -> 数量:${oldItem.quantity}→${item.quantity}, 库位:${item.location}")
            }
            outboundItems[position] = item
        }
    }
    
    private fun updateOutboundTitle() {
        txtOutboundTitle.text = "出库商品(${outboundItems.size})"
    }
    
    private fun confirmOutbound() {
        if (outboundItems.isEmpty()) {
            Toast.makeText(this, "请添加出库商品", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 验证数量
        for (item in outboundItems) {
            if (item.quantity <= 0) {
                Toast.makeText(this, "出库数量必须大于0", Toast.LENGTH_SHORT).show()
                return
            }
            if (item.quantity > item.maxStock) {
                Toast.makeText(this, "出库数量不能超过库存: ${item.sku}", Toast.LENGTH_SHORT).show()
                return
            }
        }
        
        // 显示确认对话框
        val totalItems = outboundItems.size
        val totalQuantity = outboundItems.sumOf { it.quantity }
        
        AlertDialog.Builder(this)
            .setTitle("确认出库")
            .setMessage("确定要出库 $totalItems 种商品，总数量 $totalQuantity 吗？")
            .setPositiveButton("确认") { _, _ ->
                executeOutbound()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun executeOutbound() {
        Log.d("WMS_OUTBOUND", "🚀 开始执行出库")
        
        lifecycleScope.launch {
            try {
                val requests = outboundItems.map { item ->
                    // 检查登录状态并获取用户ID
                    var userId = ApiClient.getCurrentUserId()
                    if (userId.isNullOrEmpty()) {
                        userId = "wms_user"  // 使用默认用户ID
                        Log.d("WMS_OUTBOUND", "使用默认用户ID: $userId")
                    }
                    
                    Log.d("WMS_OUTBOUND", "🔧 构建出库请求: ${item.sku} -> location: ${item.location}, quantity: ${item.quantity}, color: ${item.color}, size: ${item.size}")
                    
                    // 🔧 更新为新的API结构，使用snake_case字段名
                    OutboundRequest(
                        sku_code = item.sku,  // 主要字段
                        location_code = if (item.location == "无货位") null else item.location,  // 无货位时传null
                        outbound_quantity = item.quantity,  // 修复字段名
                        operator_id = userId,  // 必需字段
                        batch_number = if (item.batch.isNotEmpty()) item.batch else null,
                        is_urgent = false,
                        notes = "PDA出库操作 - ${item.color} ${item.size}"
                    )
                }
                
                val successResults = mutableListOf<String>()
                val errorMessages = mutableListOf<String>()
                
                for (request in requests) {
                    try {
                        val response = ApiClient.getApiService().outbound(request)
                        if (response.isSuccessful) {
                            val apiResponse = response.body()
                            if (apiResponse?.success == true && apiResponse.inventory != null) {
                                val result = apiResponse.inventory
                                Log.d("WMS_OUTBOUND", "✅ 出库成功: ${request.sku_code}")
                                Log.d("WMS_OUTBOUND", "📊 库存更新: 库位${result.location_code}剩余${result.sku_location_quantity}件，SKU总库存${result.sku_total_quantity}件")
                                
                                // 构建成功的详细信息
                                val successMsg = "✅ ${result.sku_code}\n" +
                                                 "   库位: ${result.location_code} (剩余 ${result.sku_location_quantity}件)\n" +
                                                 "   SKU总库存: ${result.sku_total_quantity}件"
                                successResults.add(successMsg)
                                Log.d("WMS_OUTBOUND", "✅ 出库成功: $successMsg")
                            } else {
                                val errorMsg = "❌ ${request.sku_code}: ${apiResponse?.error_message ?: "出库失败"}"
                                errorMessages.add(errorMsg)
                                Log.w("WMS_OUTBOUND", errorMsg)
                            }
                        } else {
                            val errorBody = response.errorBody()?.string() ?: response.message()
                            val errorMsg = "❌ ${request.sku_code}: HTTP ${response.code()} - $errorBody"
                            errorMessages.add(errorMsg)
                            Log.w("WMS_OUTBOUND", errorMsg)
                        }
                    } catch (e: Exception) {
                        val errorMsg = "❌ ${request.sku_code}: ${e.message}"
                        errorMessages.add(errorMsg)
                        Log.e("WMS_OUTBOUND", errorMsg, e)
                    }
                }
                
                // 更新UI
                runOnUiThread {
                    val finalMessage = buildString {
                        if (successResults.isNotEmpty()) {
                            append("出库成功 (${successResults.size}条):\n")
                            append("--------------------\n")
                            append(successResults.joinToString("\n\n"))
                        }
                        if (errorMessages.isNotEmpty()) {
                            if (successResults.isNotEmpty()) append("\n\n")
                            append("出库失败 (${errorMessages.size}条):\n")
                            append("--------------------\n")
                            append(errorMessages.joinToString("\n"))
                        }
                    }

                    AlertDialog.Builder(this@OutboundActivity)
                        .setTitle("出库结果")
                        .setMessage(finalMessage)
                        .setPositiveButton("确定") { _, _ ->
                            if (successResults.isNotEmpty()) {
                                // 只有在有成功条目时才清空列表
                                outboundItems.clear()
                                outboundAdapter.notifyDataSetChanged()
                                updateOutboundTitle()
                                btnConfirmOutbound.isEnabled = false
                            }
                        }
                        .setCancelable(false)
                        .show()
                }
                
            } catch (e: Exception) {
                Log.e("WMS_OUTBOUND", "❌ 执行出库异常: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@OutboundActivity, "出库失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun smartSplit(position: Int, shortage: Int) {
        if (position < 0 || position >= outboundItems.size) return
        
        val baseItem = outboundItems[position]
        Log.d("WMS_OUTBOUND", "🧠 智能拆分开始: ${baseItem.sku}, 需要补充 $shortage 件")
        
        // 获取其他有库存的库位
        val availableLocations = baseItem.locationStocks.filter { (location, stock) ->
            location != baseItem.location && stock > 0
        }.toMutableMap()
        
        if (availableLocations.isEmpty()) {
            Toast.makeText(this, "没有其他库位有库存可供拆分", Toast.LENGTH_SHORT).show()
            return
        }
        
        var remainingNeed = shortage
        val newItems = mutableListOf<OutboundItem>()
        
        // 按库存量升序排列，优先使用库存少的库位（先清空小库位）
        val sortedLocations = availableLocations.toList().sortedBy { it.second }
        
        for ((location, stock) in sortedLocations) {
            if (remainingNeed <= 0) break
            
            // 🔍 检查是否已有相同SKU+库位的记录
            val existingIndex = outboundItems.indexOfFirst { item ->
                item.sku == baseItem.sku && item.location == location
            }
            
            if (existingIndex >= 0) {
                // 已有记录，尝试累加
                val existingItem = outboundItems[existingIndex]
                val remainingStock = existingItem.maxStock - existingItem.quantity
                val addQuantity = minOf(remainingNeed, remainingStock)
                
                if (addQuantity > 0) {
                    val newQuantity = existingItem.quantity + addQuantity
                    val updatedItem = existingItem.copy(quantity = newQuantity)
                    outboundItems[existingIndex] = updatedItem
                    
                    Log.d("WMS_OUTBOUND", "✅ 智能拆分累加到现有记录: ${baseItem.sku} 在 $location，原数量 ${existingItem.quantity} + $addQuantity = $newQuantity")
                    remainingNeed -= addQuantity
                }
            } else {
                // 没有记录，创建新记录
                val takeQuantity = minOf(remainingNeed, stock)
                if (takeQuantity > 0) {
                    val newItem = baseItem.copy(
                        location = location,
                        quantity = takeQuantity,
                        maxStock = stock
                    )
                    newItems.add(newItem)
                    remainingNeed -= takeQuantity
                    
                    Log.d("WMS_OUTBOUND", "📦 拆分新增: $location, 库存: $stock, 取用: $takeQuantity, 剩余需求: $remainingNeed")
                }
            }
        }
        
        if (remainingNeed > 0) {
            Toast.makeText(this, "警告：仍有 $remainingNeed 件无法满足", Toast.LENGTH_LONG).show()
        }
        
        // 将新项目添加到列表中（在原项目后面）
        var insertPosition = position + 1
        for (newItem in newItems) {
            outboundItems.add(insertPosition, newItem)
            insertPosition++
        }
        
        // 更新UI
        outboundAdapter.notifyDataSetChanged()
        updateOutboundTitle()
        
        val successCount = newItems.size
        val successQuantity = newItems.sumOf { it.quantity }
        val splitDetails = newItems.joinToString(", ") { "${it.location}:${it.quantity}件" }
        Toast.makeText(this, 
            "✅ 智能拆分完成！\n优先清空小库位: $splitDetails", 
            Toast.LENGTH_LONG).show()
        
        Log.d("WMS_OUTBOUND", "✅ 智能拆分完成(库存少优先): 新增 $successCount 项，总需求 ${shortage}, 实际满足 $successQuantity")
    }
    
    private fun createFromOtherLocations(targetSku: String, colorData: ColorInfo, productData: Product, presetQuantity: Int, locationStocks: Map<String, Int>) {
        Log.d("WMS_OUTBOUND", "🔍 从其他库位创建: $targetSku, 需要数量: $presetQuantity")
        
        // 获取其他有库存的库位（排除已占用的库位）
        val occupiedLocations = outboundItems.filter { it.sku == targetSku }.map { it.location }.toSet()
        val availableLocations = locationStocks.filter { (location, stock) ->
            location !in occupiedLocations && stock > 0
        }.toMutableMap()
        
        if (availableLocations.isEmpty()) {
            Toast.makeText(this@OutboundActivity, "没有其他库位有库存可供创建", Toast.LENGTH_LONG).show()
            return
        }
        
        var remainingNeed = presetQuantity
        val newItems = mutableListOf<OutboundItem>()
        
        // 按库存量升序排列，优先使用库存少的库位（先清空小库位）
        val sortedLocations = availableLocations.toList().sortedBy { it.second }
        
        for ((location, stock) in sortedLocations) {
            if (remainingNeed <= 0) break
            
            // 🔍 检查是否已有相同SKU+库位的记录
            val existingIndex = outboundItems.indexOfFirst { item ->
                item.sku == targetSku && item.location == location
            }
            
            if (existingIndex >= 0) {
                // 已有记录，尝试累加
                val existingItem = outboundItems[existingIndex]
                val remainingStock = existingItem.maxStock - existingItem.quantity
                val addQuantity = minOf(remainingNeed, remainingStock)
                
                if (addQuantity > 0) {
                    val newQuantity = existingItem.quantity + addQuantity
                    val updatedItem = existingItem.copy(quantity = newQuantity)
                    outboundItems[existingIndex] = updatedItem
                    
                    Log.d("WMS_OUTBOUND", "✅ 从其他库位累加到现有记录: $targetSku 在 $location，原数量 ${existingItem.quantity} + $addQuantity = $newQuantity")
                    remainingNeed -= addQuantity
                }
            } else {
                // 没有记录，创建新记录
                val takeQuantity = minOf(remainingNeed, stock)
                if (takeQuantity > 0) {
                    // 🎯 为特定SKU创建单一选项的颜色和尺码列表
                    val lockedColors = listOf(ColorOption(
                        color = colorData.color,
                        imagePath = colorData.image_path ?: ""
                    ))
                    
                    val lockedSizes = mapOf(colorData.color to listOf(SizeOption(
                        skuCode = targetSku,
                        skuSize = colorData.sizes?.firstOrNull { it.sku_code == targetSku }?.sku_size ?: "",
                        locationStocks = locationStocks
                    )))
                    
                    val newItem = OutboundItem(
                        sku = targetSku,
                        productName = productData.product_name,
                        location = location,
                        quantity = takeQuantity,
                        color = colorData.color,
                        size = colorData.sizes?.firstOrNull { it.sku_code == targetSku }?.sku_size ?: "",
                        batch = "",
                        imageUrl = processImageUrl(colorData.image_path ?: ""),
                        maxStock = stock,
                        locationStocks = locationStocks,
                        productId = productData.product_id,
                        allColors = lockedColors,
                        allSizes = lockedSizes,
                        selectedColorIndex = 0,
                        selectedSizeIndex = 0,
                        isSkuLocked = true
                    )
                    newItems.add(newItem)
                    remainingNeed -= takeQuantity
                    
                    Log.d("WMS_OUTBOUND", "📦 从其他库位创建: $location, 库存: $stock, 取用: $takeQuantity, 剩余需求: $remainingNeed")
                }
            }
        }
        
        if (remainingNeed > 0) {
            Toast.makeText(this@OutboundActivity, "警告：仍有 $remainingNeed 件无法满足", Toast.LENGTH_LONG).show()
        }
        
        // 将新项目添加到列表中
        outboundItems.addAll(newItems)
        
        // 更新UI
        outboundAdapter.notifyDataSetChanged()
        updateOutboundTitle()
        
        val successCount = newItems.size
        val successQuantity = newItems.sumOf { it.quantity }
        val createDetails = newItems.joinToString(", ") { "${it.location}:${it.quantity}件" }
        Toast.makeText(this@OutboundActivity, 
            "✅ 从其他库位创建完成！\n优先清空小库位: $createDetails", 
            Toast.LENGTH_LONG).show()
        
        Log.d("WMS_OUTBOUND", "✅ 从其他库位创建完成: 新增 $successCount 项，总需求 ${presetQuantity}, 实际满足 $successQuantity")
    }
    
    private fun processImageUrl(rawImageUrl: String): String {
        return if (rawImageUrl.isNotEmpty()) {
            if (rawImageUrl.startsWith("http://") || rawImageUrl.startsWith("https://")) {
                rawImageUrl
            } else {
                val baseUrl = ApiClient.getServerUrl(this)
                "${baseUrl.trimEnd('/')}/$rawImageUrl"
            }
        } else {
            ""
        }
    }

    private fun setupClickListeners() {
        btnConfirmProduct.setOnClickListener {
            val productCode = editProductCode.text.toString().trim()
            if (productCode.isEmpty()) {
                Toast.makeText(this, "请输入商品编码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            smartQueryProduct(productCode)
        }
        
        btnConfirmOutbound.setOnClickListener {
            confirmOutbound()
        }
    }
    
    private fun loadLocationOptions() {
        lifecycleScope.launch {
            try {
                Log.d("WMS_OUTBOUND", "🏪 开始加载货位选项")
                val response = ApiClient.getApiService().getInventoryByLocation()
                
                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse?.success == true && apiResponse.data != null) {
                        locationOptions.clear()
                        locationOptions.add("无货位")
                        
                        // 提取所有唯一的库位代码
                        val uniqueLocations = apiResponse.data
                            .mapNotNull { it.location_code }
                            .filter { it.isNotBlank() && it != "null" }
                            .distinct()
                            .sorted()
                        
                        locationOptions.addAll(uniqueLocations)
                        Log.d("WMS_OUTBOUND", "✅ 货位选项加载成功: ${uniqueLocations.size} 个")
                    } else {
                        Log.w("WMS_OUTBOUND", "⚠️ 货位选项加载失败: ${apiResponse?.error_message}")
                        loadDefaultLocations()
                    }
                } else {
                    Log.w("WMS_OUTBOUND", "⚠️ API调用失败: ${response.code()}")
                    loadDefaultLocations()
                }
            } catch (e: Exception) {
                Log.e("WMS_OUTBOUND", "❌ 货位选项加载异常: ${e.message}")
                loadDefaultLocations()
            }
        }
    }
    
    private fun loadDefaultLocations() {
        locationOptions.clear()
        locationOptions.add("无货位")
        locationOptions.add("A01-01-01")
        locationOptions.add("A01-01-02")
        locationOptions.add("B01-01-01")
        locationOptions.add("西8排1架6层4位")
        locationOptions.add("西8排2架6层4位")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(scanReceiver)
        } catch (e: Exception) {
            Log.e("WMS_OUTBOUND", "注销广播接收器失败: ${e.message}")
        }
        Log.d("WMS_OUTBOUND", "�� 出库页面销毁")
    }

    private fun createNewItem(targetSku: String, colorData: ColorInfo, productData: Product, presetQuantity: Int, locationStocks: Map<String, Int>, defaultLocation: String, defaultLocationStock: Int) {
        Log.d("WMS_OUTBOUND", "📦 创建新项目: $targetSku 在 $defaultLocation")
        
        // 🎯 为特定SKU创建单一选项的颜色和尺码列表（用于显示，但会被禁用）
        val lockedColors = listOf(ColorOption(
            color = colorData.color,
            imagePath = colorData.image_path ?: ""
        ))
        
        val lockedSizes = mapOf(colorData.color to listOf(SizeOption(
            skuCode = targetSku,
            skuSize = colorData.sizes?.firstOrNull { it.sku_code == targetSku }?.sku_size ?: "",
            locationStocks = locationStocks
        )))
        
        // 计算实际创建的数量
        val actualQuantity = minOf(presetQuantity, defaultLocationStock)
        
        val outboundItem = OutboundItem(
            sku = targetSku,
            productName = productData.product_name,
            location = defaultLocation,
            quantity = actualQuantity,
            color = colorData.color,
            size = colorData.sizes?.firstOrNull { it.sku_code == targetSku }?.sku_size ?: "",
            batch = "",
            imageUrl = processImageUrl(colorData.image_path ?: ""),
            maxStock = defaultLocationStock,
            locationStocks = locationStocks,
            productId = productData.product_id,
            allColors = lockedColors,  // 提供单一颜色选项用于显示
            allSizes = lockedSizes,    // 提供单一尺码选项用于显示
            selectedColorIndex = 0,   // 锁定为第一个（也是唯一的）选项
            selectedSizeIndex = 0,    // 锁定为第一个（也是唯一的）选项
            isSkuLocked = true        // 标记为锁定SKU，适配器会禁用选择器
        )
        outboundItems.add(outboundItem)
        Log.d("WMS_OUTBOUND", "✅ 新增出库项: $targetSku 在 $defaultLocation，数量 $actualQuantity")
        
        // 如果预设数量超过默认库位库存，触发智能拆分
        if (presetQuantity > defaultLocationStock) {
            val shortage = presetQuantity - defaultLocationStock
            Log.d("WMS_OUTBOUND", "🧠 需要智能拆分: 预设 $presetQuantity，当前库位 $defaultLocationStock，缺少 $shortage")
            
            val position = outboundItems.size - 1
            smartSplit(position, shortage)
        }
    }
} 