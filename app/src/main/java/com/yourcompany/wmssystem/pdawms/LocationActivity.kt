package com.yourcompany.wmssystem.pdawms

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import java.net.URL

class LocationActivity : AppCompatActivity() {
    
    private lateinit var edtSearch: EditText
    private lateinit var btnSearch: Button
    private lateinit var btnAddLocation: Button
    private lateinit var txtLocationCount: TextView
    private lateinit var recyclerViewLocations: RecyclerView
    
    private lateinit var locationAdapter: LocationAdapter
    private val locations = mutableListOf<LocationWithStats>()
    private val allLocations = mutableListOf<LocationWithStats>()
    private var currentLocationCode: String = "" // 当前操作的库位编码
    
    // ===== 库位详情对话框状态 =====
    private var currentInventoryDialog: AlertDialog? = null
    private var currentInventoryRecyclerView: RecyclerView? = null
    private var currentInventoryAdapter: LocationInventoryGridAdapter? = null
    private var currentTotalSkuTextView: TextView? = null
    private var currentTotalQuantityTextView: TextView? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location)
        
        Log.d("WMS_LOCATION", "📍 库位管理界面启动")
        
        initViews()
        setupUnifiedNavBar()
        setupRecyclerView()
        setupClickListeners()
        loadLocations()
    }
    
    private fun initViews() {
        edtSearch = findViewById(R.id.edtSearch)
        btnSearch = findViewById(R.id.btnSearch)
        btnAddLocation = findViewById(R.id.btnAddLocation)
        txtLocationCount = findViewById(R.id.txtLocationCount)
        recyclerViewLocations = findViewById(R.id.recyclerViewLocations)
    }
    
    private fun setupUnifiedNavBar() {
        val navContainer = findViewById<LinearLayout>(R.id.navContainer)
        UnifiedNavBar.addToActivity(this, navContainer, "location")
    }
    
    private fun setupRecyclerView() {
        locationAdapter = LocationAdapter(locations) { location, action ->
            when (action) {
                "view" -> viewLocationDetails(location)
                "edit" -> editLocation(location)
                "delete" -> deleteLocation(location)
            }
        }
        recyclerViewLocations.layoutManager = LinearLayoutManager(this)
        recyclerViewLocations.adapter = locationAdapter
    }
    
    private fun setupClickListeners() {
        btnSearch.setOnClickListener { performSearch() }
        btnAddLocation.setOnClickListener { showAddLocationDialog() }
        
        // 搜索框回车搜索
        edtSearch.setOnEditorActionListener { _, _, _ ->
            performSearch()
            true
        }
    }
    
    private fun loadLocations() {
        lifecycleScope.launch {
            try {
                Log.d("WMS_LOCATION", "🔄 开始加载库位列表")
                
                val response = ApiClient.getApiService().getLocations(page_size = 1000)
                if (response.isSuccessful && response.body()?.success == true) {
                    val locationList = response.body()?.data ?: emptyList()
                    
                    // 转换为带统计信息的库位列表
                    val locationsWithStats = locationList.map { location: Location ->
                        LocationWithStats(
                            location = location,
                            skuCount = 0,
                            productCount = 0,
                            totalQuantity = 0
                        )
                    }.toMutableList()
                    
                    // 加载每个库位的库存统计信息
                    loadLocationStats(locationsWithStats)
                    
                } else {
                    val errorMsg = response.body()?.error_message ?: "获取库位列表失败"
                    Log.e("WMS_LOCATION", "❌ 获取库位列表失败: $errorMsg")
                    Toast.makeText(this@LocationActivity, errorMsg, Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Log.e("WMS_LOCATION", "❌ 网络错误: ${e.message}", e)
                Toast.makeText(this@LocationActivity, "网络错误: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun loadLocationStats(locationsWithStats: MutableList<LocationWithStats>) {
        lifecycleScope.launch {
            try {
                for (locationWithStats in locationsWithStats) {
                    val location = locationWithStats.location
                    try {
                        val response = ApiClient.getApiService().getLocationInventory(location.location_code)
                        
                        if (response.isSuccessful && response.body()?.success == true) {
                            val inventory = response.body()?.data
                            if (inventory != null) {
                                locationWithStats.skuCount = inventory.summary?.total_items ?: 0
                                locationWithStats.productCount = inventory.summary?.total_items ?: 0
                                locationWithStats.totalQuantity = inventory.summary?.total_quantity ?: 0
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("WMS_LOCATION", "获取库位 ${location.location_code} 统计信息失败: ${e.message}")
                        // 继续处理下一个库位
                    }
                }
                
                // 更新列表
                allLocations.clear()
                allLocations.addAll(locationsWithStats)
                locations.clear()
                locations.addAll(locationsWithStats)
                
                runOnUiThread {
                    locationAdapter.notifyDataSetChanged()
                    updateLocationCount()
                }
                
                Log.d("WMS_LOCATION", "✅ 库位列表加载完成，共 ${locations.size} 个库位")
                
            } catch (e: Exception) {
                Log.e("WMS_LOCATION", "❌ 加载库位统计信息错误: ${e.message}", e)
            }
        }
    }
    
    private fun performSearch() {
        val query = edtSearch.text.toString().trim()
        
        if (query.isEmpty()) {
            // 显示所有库位
            locations.clear()
            locations.addAll(allLocations)
        } else {
            // 过滤库位
            val filtered = allLocations.filter { locationWithStats ->
                val location = locationWithStats.location
                location.location_code.contains(query, ignoreCase = true) ||
                location.location_name?.contains(query, ignoreCase = true) == true ||
                location.description?.contains(query, ignoreCase = true) == true
            }
            
            locations.clear()
            locations.addAll(filtered)
        }
        
        locationAdapter.notifyDataSetChanged()
        updateLocationCount()
        
        Log.d("WMS_LOCATION", "🔍 搜索完成，匹配 ${locations.size} 个库位")
    }
    
    private fun updateLocationCount() {
        txtLocationCount.text = "共 ${locations.size} 个库位"
    }
    
    private fun showAddLocationDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_location_form, null)
        val dialog = AlertDialog.Builder(this)
            .setTitle("新增库位")
            .setView(dialogView)
            .setPositiveButton("添加", null)
            .setNegativeButton("取消", null)
            .create()
        
        dialog.setOnShowListener {
            val btnPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnPositive.setOnClickListener {
                createLocation(dialogView, dialog)
            }
        }
        
        dialog.show()
    }
    
    private fun createLocation(dialogView: View, dialog: AlertDialog) {
        val edtLocationCode = dialogView.findViewById<EditText>(R.id.edtLocationCode)
        val edtLocationName = dialogView.findViewById<EditText>(R.id.edtLocationName)
        val edtCategory1Label = dialogView.findViewById<EditText>(R.id.edtCategory1Label)
        val edtCategory1 = dialogView.findViewById<EditText>(R.id.edtCategory1)
        val edtCategory2Label = dialogView.findViewById<EditText>(R.id.edtCategory2Label)
        val edtCategory2 = dialogView.findViewById<EditText>(R.id.edtCategory2)
        val edtDescription = dialogView.findViewById<EditText>(R.id.edtDescription)
        
        val locationCode = edtLocationCode.text.toString().trim()
        if (locationCode.isEmpty()) {
            Toast.makeText(this, "请输入库位编码", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                val request = CreateLocationRequest(
                    location_code = locationCode,
                    location_name = edtLocationName.text.toString().trim().ifEmpty { null },
                    category1Label = edtCategory1Label.text.toString().trim().ifEmpty { null },
                    category1 = edtCategory1.text.toString().trim().ifEmpty { null },
                    category2Label = edtCategory2Label.text.toString().trim().ifEmpty { null },
                    category2 = edtCategory2.text.toString().trim().ifEmpty { null },
                    description = edtDescription.text.toString().trim().ifEmpty { null },
                    priority = 0
                )
                
                val response = ApiClient.getApiService().createLocation(request)
                
                runOnUiThread {
                    if (response.isSuccessful && response.body()?.success == true) {
                        Toast.makeText(this@LocationActivity, "库位创建成功", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        loadLocations() // 重新加载列表
                    } else {
                        val errorMsg = response.body()?.error_message ?: "创建库位失败"
                        Toast.makeText(this@LocationActivity, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                }
                
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@LocationActivity, "网络错误: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun viewLocationDetails(locationWithStats: LocationWithStats) {
        showLocationInventoryDialog(locationWithStats)
    }
    
    private fun showLocationInventoryDialog(locationWithStats: LocationWithStats) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_location_inventory_grid, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
            
        // 获取控件引用
        val txtDialogTitle = dialogView.findViewById<TextView>(R.id.txtDialogTitle)
        val txtTotalSku = dialogView.findViewById<TextView>(R.id.txtTotalSku)
        val txtTotalQuantity = dialogView.findViewById<TextView>(R.id.txtTotalQuantity)
        val recyclerViewGrid = dialogView.findViewById<RecyclerView>(R.id.recyclerViewInventoryGrid)
        val btnClose = dialogView.findViewById<Button>(R.id.btnClose)
        val btnCloseDialog = dialogView.findViewById<Button>(R.id.btnCloseDialog)

        
        // 设置标题（只显示库位编码）
        val location = locationWithStats.location
        txtDialogTitle.text = location.location_code
        
        // 设置当前库位编码，供SKU操作使用
        currentLocationCode = location.location_code
        
        // 设置统计信息
        txtTotalSku.text = "SKU: ${locationWithStats.skuCount}"
        txtTotalQuantity.text = "总量: ${locationWithStats.totalQuantity}件"
        
        // 设置网格布局管理器，一排三列
        recyclerViewGrid.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 3)
        
        // 保存对话框引用，用于后续更新
        currentInventoryDialog = dialog
        currentInventoryRecyclerView = recyclerViewGrid
        currentTotalSkuTextView = txtTotalSku
        currentTotalQuantityTextView = txtTotalQuantity
        
        // 加载库存数据
        loadLocationInventoryForDialog(location.location_code, recyclerViewGrid, txtTotalSku, txtTotalQuantity)
        
        // 设置点击事件
        btnClose.setOnClickListener { 
            clearCurrentDialogReferences()
            dialog.dismiss() 
        }
        btnCloseDialog.setOnClickListener { 
            clearCurrentDialogReferences()
            dialog.dismiss() 
        }
        
        dialog.show()
        
        // 设置对话框大小 - 更大尺寸
        val window = dialog.window
        window?.setLayout((resources.displayMetrics.widthPixels * 0.98).toInt(), 
                         (resources.displayMetrics.heightPixels * 0.9).toInt())
    }
    
    private fun loadLocationInventoryForDialog(
        locationCode: String, 
        recyclerView: RecyclerView,
        txtTotalSku: TextView,
        txtTotalQuantity: TextView
    ) {
        lifecycleScope.launch {
            try {
                Log.d("WMS_LOCATION", "🔄 加载库位库存对话框数据: $locationCode")
                
                val response = ApiClient.getApiService().getLocationInventory(locationCode)
                if (response.isSuccessful && response.body()?.success == true) {
                    val inventory = response.body()?.data
                    
                    if (inventory != null) {
                        runOnUiThread {
                            // 更新统计信息
                            val skuCount = inventory.summary?.total_items ?: 0
                            val totalQuantity = inventory.summary?.total_quantity ?: 0
                            txtTotalSku.text = "SKU: $skuCount"
                            txtTotalQuantity.text = "总量: ${totalQuantity}件"
                            
                            // 获取库存数据（API已经返回了指定库位的数据）
                            val items = inventory.items ?: emptyList()
                            
                            Log.d("WMS_LOCATION", "📦 库存数据: 共${items.size}个SKU")
                            items.forEachIndexed { index, item ->
                                Log.d("WMS_LOCATION", "📦 SKU[$index]: ${item.sku_code}, 数量: ${item.stock_quantity}, 图片: ${item.image_path}")
                            }
                            
                            // 设置图片网格适配器
                            Log.d("WMS_LOCATION", "🔧 创建适配器，共${items.size}个条目")
                            val gridAdapter = LocationInventoryGridAdapter(items.toMutableList()) { item ->
                                showSkuOperationMenu(this@LocationActivity, item)
                            }
                            recyclerView.adapter = gridAdapter
                            currentInventoryAdapter = gridAdapter // 保存适配器引用
                            Log.d("WMS_LOCATION", "🔧 适配器已设置到RecyclerView")
                            
                            if (items.isEmpty()) {
                                Log.w("WMS_LOCATION", "⚠️ 库位 $locationCode 没有库存数据")
                            }
                        }
                        
                        Log.d("WMS_LOCATION", "✅ 库位库存对话框数据加载完成")
                    }
                    
                } else {
                    val errorMsg = response.body()?.error_message ?: "获取库位库存失败"
                    Log.e("WMS_LOCATION", "❌ 获取库位库存失败: $errorMsg")
                    runOnUiThread {
                        Toast.makeText(this@LocationActivity, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                }
                
            } catch (e: Exception) {
                Log.e("WMS_LOCATION", "❌ 网络错误: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this@LocationActivity, "网络错误: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun editLocation(locationWithStats: LocationWithStats) {
        val location = locationWithStats.location
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_location_form, null)
        
        // 填充现有数据
        dialogView.findViewById<EditText>(R.id.edtLocationCode).setText(location.location_code)
        dialogView.findViewById<EditText>(R.id.edtLocationName).setText(location.location_name ?: "")
        dialogView.findViewById<EditText>(R.id.edtCategory1Label).setText(location.category_name_1 ?: "")
        dialogView.findViewById<EditText>(R.id.edtCategory1).setText(location.category_code_1 ?: "")
        dialogView.findViewById<EditText>(R.id.edtCategory2Label).setText(location.category_name_2 ?: "")
        dialogView.findViewById<EditText>(R.id.edtCategory2).setText(location.category_code_2 ?: "")
        dialogView.findViewById<EditText>(R.id.edtDescription).setText(location.description ?: "")
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("编辑库位")
            .setView(dialogView)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .setNeutralButton("删除", null)
            .create()
        
        dialog.setOnShowListener {
            val btnPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnPositive.setOnClickListener {
                updateLocation(dialogView, dialog, location)
            }

            // 处理删除按钮点击
            val btnDelete = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            btnDelete.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("删除库位")
                    .setMessage("确定要删除库位 ${location.location_code} 吗？\n注意：只能删除没有库存的库位。")
                    .setPositiveButton("删除") { _, _ ->
                        dialog.dismiss()
                        performDeleteLocation(location)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
        
        dialog.show()
    }
    
    private fun updateLocation(dialogView: View, dialog: AlertDialog, location: Location) {
        val edtLocationCode = dialogView.findViewById<EditText>(R.id.edtLocationCode)
        val edtLocationName = dialogView.findViewById<EditText>(R.id.edtLocationName)
        val edtCategory1Label = dialogView.findViewById<EditText>(R.id.edtCategory1Label)
        val edtCategory1 = dialogView.findViewById<EditText>(R.id.edtCategory1)
        val edtCategory2Label = dialogView.findViewById<EditText>(R.id.edtCategory2Label)
        val edtCategory2 = dialogView.findViewById<EditText>(R.id.edtCategory2)
        val edtDescription = dialogView.findViewById<EditText>(R.id.edtDescription)
        
        val locationCode = edtLocationCode.text.toString().trim()
        if (locationCode.isEmpty()) {
            Toast.makeText(this, "请输入库位编码", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                val request = UpdateLocationRequest(
                    location_code = locationCode,
                    location_name = edtLocationName.text.toString().trim().ifEmpty { null },
                    category1Label = edtCategory1Label.text.toString().trim().ifEmpty { null },
                    category1 = edtCategory1.text.toString().trim().ifEmpty { null },
                    category2Label = edtCategory2Label.text.toString().trim().ifEmpty { null },
                    category2 = edtCategory2.text.toString().trim().ifEmpty { null },
                    description = edtDescription.text.toString().trim().ifEmpty { null },
                    priority = location.priority
                )
                
                val response = ApiClient.getApiService().updateLocation(location.location_id!!, request)
                
                runOnUiThread {
                    if (response.isSuccessful && response.body()?.success == true) {
                        Toast.makeText(this@LocationActivity, "库位更新成功", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        loadLocations() // 重新加载列表
                    } else {
                        val errorMsg = response.body()?.error_message ?: "更新库位失败"
                        Toast.makeText(this@LocationActivity, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                }
                
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@LocationActivity, "网络错误: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun deleteLocation(locationWithStats: LocationWithStats) {
        val location = locationWithStats.location
        
        AlertDialog.Builder(this)
            .setTitle("删除库位")
            .setMessage("确定要删除库位 ${location.location_code} 吗？\n\n注意：只能删除没有库存的库位。")
            .setPositiveButton("删除") { _, _ ->
                performDeleteLocation(location)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun performDeleteLocation(location: Location) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getApiService().deleteLocation(location.location_id!!)
                
                runOnUiThread {
                    if (response.isSuccessful && response.body()?.success == true) {
                        Toast.makeText(this@LocationActivity, "库位删除成功", Toast.LENGTH_SHORT).show()
                        loadLocations() // 重新加载列表
                    } else {
                        val errorMsg = response.body()?.error_message ?: "删除库位失败"
                        Toast.makeText(this@LocationActivity, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                }
                
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@LocationActivity, "网络错误: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 显示SKU操作菜单
    private fun showSkuOperationMenu(context: android.content.Context, item: LocationInventoryItem) {
        val skuCode = item.sku_code ?: "未知SKU"
        val quantity = item.stock_quantity ?: 0
        val unit = item.unit ?: "件"
        val productName = item.product_name ?: "未知商品"
        
        // 创建自定义对话框
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_sku_operation, null)
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()
        
        // 设置标题和信息
        dialogView.findViewById<TextView>(R.id.txtSkuTitle).text = "SKU: $skuCode"
        
        val locationInfo = if (currentLocationCode.isNotEmpty()) currentLocationCode else (item.location_code ?: "未知")
        val infoText = "货位: $locationInfo\n库存: $quantity $unit"
        dialogView.findViewById<TextView>(R.id.txtSkuInfo).text = infoText
        
        // 设置按钮点击事件
        dialogView.findViewById<Button>(R.id.btnInbound).setOnClickListener {
            dialog.dismiss()
            performInboundOperation(context, item)
        }
        
        dialogView.findViewById<Button>(R.id.btnOutbound).setOnClickListener {
            dialog.dismiss()
            performOutboundOperation(context, item)
        }
        
        dialogView.findViewById<Button>(R.id.btnInventory).setOnClickListener {
            dialog.dismiss()
            performInventoryOperation(context, item)
        }
        
        dialogView.findViewById<Button>(R.id.btnTransfer).setOnClickListener {
            dialog.dismiss()
            performTransferOperation(context, item)
        }
        
        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    // 入库操作
    private fun performInboundOperation(context: android.content.Context, item: LocationInventoryItem) {
        Log.d("WMS_LOCATION", "🔄 执行入库操作: ${item.sku_code}")
        
        val input = EditText(context).apply {
            hint = "请输入入库数量"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("1")
        }
        
        AlertDialog.Builder(context)
            .setTitle("📦 入库操作")
            .setMessage("库位: ${currentLocationCode}\nSKU: ${item.sku_code}\n当前库存: ${item.stock_quantity} ${item.unit ?: "件"}")
            .setView(input)
            .setPositiveButton("确认入库") { _, _ ->
                val quantity = input.text.toString().toIntOrNull() ?: 0
                if (quantity > 0) {
                    executeSkuInboundOperation(context, currentLocationCode, item, quantity)
                } else {
                    Toast.makeText(context, "请输入有效的入库数量", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    // 出库操作
    private fun performOutboundOperation(context: android.content.Context, item: LocationInventoryItem) {
        Log.d("WMS_LOCATION", "🔄 执行出库操作: ${item.sku_code}")
        
        val input = EditText(context).apply {
            hint = "请输入出库数量"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("1")
        }
        
        AlertDialog.Builder(context)
            .setTitle("📤 出库操作")
            .setMessage("库位: ${currentLocationCode}\nSKU: ${item.sku_code}\n当前库存: ${item.stock_quantity} ${item.unit ?: "件"}")
            .setView(input)
            .setPositiveButton("确认出库") { _, _ ->
                val quantity = input.text.toString().toIntOrNull() ?: 0
                val currentStock = item.stock_quantity ?: 0
                
                if (quantity > 0) {
                    if (quantity <= currentStock) {
                        executeSkuOutboundOperation(context, currentLocationCode, item, quantity)
                    } else {
                        Toast.makeText(context, "出库数量不能超过当前库存($currentStock)", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "请输入有效的出库数量", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    // 盘点操作
    private fun performInventoryOperation(context: android.content.Context, item: LocationInventoryItem) {
        Log.d("WMS_LOCATION", "🔄 执行盘点操作: ${item.sku_code}")
        
        val input = EditText(context).apply {
            hint = "请输入实际盘点数量"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("${item.stock_quantity ?: 0}")
        }
        
        AlertDialog.Builder(context)
            .setTitle("📋 盘点操作")
            .setMessage("库位: ${currentLocationCode}\nSKU: ${item.sku_code}\n系统库存: ${item.stock_quantity} ${item.unit ?: "件"}")
            .setView(input)
            .setPositiveButton("确认盘点") { _, _ ->
                val actualQuantity = input.text.toString().toIntOrNull()
                if (actualQuantity != null && actualQuantity >= 0) {
                    executeSkuInventoryOperation(context, currentLocationCode, item, actualQuantity)
                } else {
                    Toast.makeText(context, "请输入有效的盘点数量", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    // 转移操作
    private fun performTransferOperation(context: android.content.Context, item: LocationInventoryItem) {
        Log.d("WMS_LOCATION", "🔄 执行转移操作: ${item.sku_code}")

        // 先输入目标库位
        val inputLocation = EditText(context).apply {
            hint = "请输入目标库位编码 (留空=无货位)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }

        AlertDialog.Builder(context)
            .setTitle("🔄 转移操作")
            .setMessage("当前库位: ${currentLocationCode}\nSKU: ${item.sku_code}\n当前库存: ${item.stock_quantity} ${item.unit ?: "件"}")
            .setView(inputLocation)
            .setPositiveButton("下一步") { _, _ ->
                var targetLocation = inputLocation.text.toString().trim()
                if (targetLocation.isEmpty()) {
                    targetLocation = "无货位"
                }
                showTransferQuantityDialog(context, item, targetLocation)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // 显示转移数量对话框
    private fun showTransferQuantityDialog(context: android.content.Context, item: LocationInventoryItem, targetLocation: String) {
        val currentStock = item.stock_quantity ?: 0
        val inputQty = EditText(context).apply {
            hint = "请输入转移数量"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(currentStock.toString())
            selectAll()
        }

        AlertDialog.Builder(context)
            .setTitle("🔄 确认转移")
            .setMessage("从库位: ${currentLocationCode}\n到库位: $targetLocation\nSKU: ${item.sku_code}\n当前库存: $currentStock ${item.unit ?: "件"}")
            .setView(inputQty)
            .setPositiveButton("确认转移") { _, _ ->
                val quantity = inputQty.text.toString().toIntOrNull() ?: 0
                if (quantity > 0) {
                    if (quantity <= currentStock) {
                        executeSkuTransferOperation(context, currentLocationCode, targetLocation, item, quantity)
                    } else {
                        Toast.makeText(context, "转移数量不能超过当前库存($currentStock)", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "请输入有效的转移数量", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    // 执行SKU入库操作
    private fun executeSkuInboundOperation(context: android.content.Context, locationCode: String, item: LocationInventoryItem, quantity: Int) {
        Log.d("WMS_LOCATION", "✅ 开始执行SKU入库操作: 库位=$locationCode, SKU=${item.sku_code}, 数量=$quantity")
        
        lifecycleScope.launch {
            try {
                // 构建入库请求
                val request = InboundRequest(
                    sku_code = item.sku_code ?: "",
                    location_code = locationCode,
                    inbound_quantity = quantity,
                    operator_id = ApiClient.getCurrentUserId() ?: "wms_user",
                    batch_number = null,
                    is_urgent = false,
                    notes = "货位管理页面入库操作"
                )
                
                Log.d("WMS_LOCATION", "🔄 发送入库请求...")
                val response = ApiClient.getApiService().inbound(request)
                
                runOnUiThread {
                    if (response.isSuccessful) {
                        val apiResponse = response.body()
                        if (apiResponse?.success == true && apiResponse.inventory != null) {
                            val result = apiResponse.inventory
                            
                            // 成功弹窗 - 参考入库页面的详细显示
                            val successMessage = buildString {
                                append("✅ 入库操作成功！\n")
                                append("--------------------\n")
                                append("SKU编码: ${result.sku_code}\n")
                                append("库位: ${result.location_code}\n")
                                append("入库数量: $quantity 件\n")
                                append("库位库存: ${result.sku_location_quantity} 件\n")
                                append("SKU总库存: ${result.sku_total_quantity} 件")
                            }
                            
                            AlertDialog.Builder(this@LocationActivity)
                                .setTitle("📦 入库结果")
                                .setMessage(successMessage)
                                .setPositiveButton("确定") { _, _ ->
                                    // 使用API返回的数据更新UI，而不是重新加载所有数据
                                    updateLocationInventoryAfterOperation(
                                        locationCode = result.location_code,
                                        skuCode = result.sku_code,
                                        newLocationQuantity = result.sku_location_quantity,
                                        newTotalQuantity = result.sku_total_quantity
                                    )
                                }
                                .setCancelable(false)
                                .show()
                                
                            Log.d("WMS_LOCATION", "✅ 入库成功: $successMessage")
                            
                        } else {
                            // 失败弹窗
                            val errorMessage = buildString {
                                append("❌ 入库操作失败！\n")
                                append("--------------------\n")
                                append("SKU编码: ${item.sku_code}\n")
                                append("库位: $locationCode\n")
                                append("入库数量: $quantity 件\n")
                                append("失败原因: ${apiResponse?.error_message ?: "未知错误"}")
                            }
                            
                            AlertDialog.Builder(this@LocationActivity)
                                .setTitle("❌ 入库失败")
                                .setMessage(errorMessage)
                                .setPositiveButton("确定", null)
                                .show()
                                
                            Log.e("WMS_LOCATION", "❌ 入库失败: ${apiResponse?.error_message}")
                        }
                    } else {
                        val errorBody = response.errorBody()?.string() ?: response.message()
                        val errorMessage = buildString {
                            append("❌ 入库操作失败！\n")
                            append("--------------------\n")
                            append("SKU编码: ${item.sku_code}\n")
                            append("库位: $locationCode\n")
                            append("入库数量: $quantity 件\n")
                            append("错误代码: HTTP ${response.code()}\n")
                            append("错误信息: $errorBody")
                        }
                        
                        AlertDialog.Builder(this@LocationActivity)
                            .setTitle("❌ 网络错误")
                            .setMessage(errorMessage)
                            .setPositiveButton("确定", null)
                            .show()
                            
                        Log.e("WMS_LOCATION", "❌ HTTP错误: ${response.code()} - $errorBody")
                    }
                }
                
            } catch (e: Exception) {
                Log.e("WMS_LOCATION", "❌ 入库操作异常: ${e.message}", e)
                runOnUiThread {
                    val errorMessage = buildString {
                        append("❌ 入库操作异常！\n")
                        append("--------------------\n")
                        append("SKU编码: ${item.sku_code}\n")
                        append("库位: $locationCode\n")
                        append("入库数量: $quantity 件\n")
                        append("异常信息: ${e.message}")
                    }
                    
                    AlertDialog.Builder(this@LocationActivity)
                        .setTitle("❌ 系统异常")
                        .setMessage(errorMessage)
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }
    }
    
    // 执行SKU出库操作
    private fun executeSkuOutboundOperation(context: android.content.Context, locationCode: String, item: LocationInventoryItem, quantity: Int) {
        Log.d("WMS_LOCATION", "✅ 开始执行SKU出库操作: 库位=$locationCode, SKU=${item.sku_code}, 数量=$quantity")
        
        lifecycleScope.launch {
            try {
                // 构建出库请求
                val request = OutboundRequest(
                    sku_code = item.sku_code ?: "",
                    location_code = locationCode,
                    outbound_quantity = quantity,
                    operator_id = ApiClient.getCurrentUserId() ?: "wms_user",
                    batch_number = null,
                    is_urgent = false,
                    notes = "货位管理页面出库操作"
                )
                
                Log.d("WMS_LOCATION", "🔄 发送出库请求...")
                val response = ApiClient.getApiService().outbound(request)
                
                runOnUiThread {
                    if (response.isSuccessful) {
                        val apiResponse = response.body()
                        if (apiResponse?.success == true && apiResponse.inventory != null) {
                            val result = apiResponse.inventory
                            
                            // 成功弹窗 - 参考入库页面的详细显示
                            val successMessage = buildString {
                                append("✅ 出库操作成功！\n")
                                append("--------------------\n")
                                append("SKU编码: ${result.sku_code}\n")
                                append("库位: ${result.location_code}\n")
                                append("出库数量: $quantity 件\n")
                                append("剩余库存: ${result.sku_location_quantity} 件\n")
                                append("SKU总库存: ${result.sku_total_quantity} 件")
                            }
                            
                            AlertDialog.Builder(this@LocationActivity)
                                .setTitle("📤 出库结果")
                                .setMessage(successMessage)
                                .setPositiveButton("确定") { _, _ ->
                                    // 使用API返回的数据更新UI，而不是重新加载所有数据
                                    updateLocationInventoryAfterOperation(
                                        locationCode = result.location_code,
                                        skuCode = result.sku_code,
                                        newLocationQuantity = result.sku_location_quantity,
                                        newTotalQuantity = result.sku_total_quantity
                                    )
                                }
                                .setCancelable(false)
                                .show()
                                
                            Log.d("WMS_LOCATION", "✅ 出库成功: $successMessage")
                            
                        } else {
                            // 失败弹窗
                            val errorMessage = buildString {
                                append("❌ 出库操作失败！\n")
                                append("--------------------\n")
                                append("SKU编码: ${item.sku_code}\n")
                                append("库位: $locationCode\n")
                                append("出库数量: $quantity 件\n")
                                append("失败原因: ${apiResponse?.error_message ?: "未知错误"}")
                            }
                            
                            AlertDialog.Builder(this@LocationActivity)
                                .setTitle("❌ 出库失败")
                                .setMessage(errorMessage)
                                .setPositiveButton("确定", null)
                                .show()
                                
                            Log.e("WMS_LOCATION", "❌ 出库失败: ${apiResponse?.error_message}")
                        }
                    } else {
                        val errorBody = response.errorBody()?.string() ?: response.message()
                        val errorMessage = buildString {
                            append("❌ 出库操作失败！\n")
                            append("--------------------\n")
                            append("SKU编码: ${item.sku_code}\n")
                            append("库位: $locationCode\n")
                            append("出库数量: $quantity 件\n")
                            append("错误代码: HTTP ${response.code()}\n")
                            append("错误信息: $errorBody")
                        }
                        
                        AlertDialog.Builder(this@LocationActivity)
                            .setTitle("❌ 网络错误")
                            .setMessage(errorMessage)
                            .setPositiveButton("确定", null)
                            .show()
                            
                        Log.e("WMS_LOCATION", "❌ HTTP错误: ${response.code()} - $errorBody")
                    }
                }
                
            } catch (e: Exception) {
                Log.e("WMS_LOCATION", "❌ 出库操作异常: ${e.message}", e)
                runOnUiThread {
                    val errorMessage = buildString {
                        append("❌ 出库操作异常！\n")
                        append("--------------------\n")
                        append("SKU编码: ${item.sku_code}\n")
                        append("库位: $locationCode\n")
                        append("出库数量: $quantity 件\n")
                        append("异常信息: ${e.message}")
                    }
                    
                    AlertDialog.Builder(this@LocationActivity)
                        .setTitle("❌ 系统异常")
                        .setMessage(errorMessage)
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }
    }
    
    // 执行SKU盘点操作
    private fun executeSkuInventoryOperation(context: android.content.Context, locationCode: String, item: LocationInventoryItem, actualQuantity: Int) {
        val systemQuantity = item.stock_quantity ?: 0
        val difference = actualQuantity - systemQuantity
        
        Log.d("WMS_LOCATION", "✅ 开始执行SKU盘点操作: 库位=$locationCode, SKU=${item.sku_code}, 系统库存=$systemQuantity, 实际库存=$actualQuantity, 差异=$difference")
        
        lifecycleScope.launch {
            try {
                // 构建库存调整请求（盘点操作）
                val request = InventoryAdjustRequest(
                    sku_code = item.sku_code ?: "",
                    location_code = locationCode,
                    target_quantity = actualQuantity, // 目标数量
                    operator_id = ApiClient.getCurrentUserId() ?: "wms_user",
                    batch_number = null,
                    is_urgent = false,
                    notes = "货位管理页面盘点操作 - 系统库存:$systemQuantity, 实际库存:$actualQuantity"
                )
                
                Log.d("WMS_LOCATION", "🔄 发送盘点调整请求...")
                val response = ApiClient.getApiService().adjustInventory(request)
                
                runOnUiThread {
                    if (response.isSuccessful) {
                        val apiResponse = response.body()
                        if (apiResponse?.success == true) {
                            
                            // 成功弹窗 - 根据差异显示不同内容
                            val successMessage = buildString {
                                if (difference == 0) {
                                    append("✅ 盘点完成！\n")
                                    append("--------------------\n")
                                    append("库存数量准确无误\n")
                                } else {
                                    append("✅ 盘点调整完成！\n")
                                    append("--------------------\n")
                                    append("发现差异并已调整\n")
                                }
                                append("SKU编码: ${item.sku_code}\n")
                                append("库位: $locationCode\n")
                                append("系统库存: $systemQuantity 件\n")
                                append("实际库存: $actualQuantity 件\n")
                                if (difference != 0) {
                                    append("调整数量: ${if (difference > 0) "+" else ""}$difference 件")
                                }
                            }
                            
                            AlertDialog.Builder(this@LocationActivity)
                                .setTitle("📋 盘点结果")
                                .setMessage(successMessage)
                                .setPositiveButton("确定") { _, _ ->
                                    // 盘点操作成功后，刷新库位详情对话框
                                    refreshLocationInventoryDialog()
                                }
                                .setCancelable(false)
                                .show()
                                
                            Log.d("WMS_LOCATION", "✅ 盘点成功: $successMessage")
                            
                        } else {
                            // 失败弹窗
                            val errorMessage = buildString {
                                append("❌ 盘点操作失败！\n")
                                append("--------------------\n")
                                append("SKU编码: ${item.sku_code}\n")
                                append("库位: $locationCode\n")
                                append("系统库存: $systemQuantity 件\n")
                                append("实际库存: $actualQuantity 件\n")
                                append("调整数量: ${if (difference > 0) "+" else ""}$difference 件\n")
                                append("失败原因: ${apiResponse?.error_message ?: "未知错误"}")
                            }
                            
                            AlertDialog.Builder(this@LocationActivity)
                                .setTitle("❌ 盘点失败")
                                .setMessage(errorMessage)
                                .setPositiveButton("确定", null)
                                .show()
                                
                            Log.e("WMS_LOCATION", "❌ 盘点失败: ${apiResponse?.error_message}")
                        }
                    } else {
                        val errorBody = response.errorBody()?.string() ?: response.message()
                        val errorMessage = buildString {
                            append("❌ 盘点操作失败！\n")
                            append("--------------------\n")
                            append("SKU编码: ${item.sku_code}\n")
                            append("库位: $locationCode\n")
                            append("系统库存: $systemQuantity 件\n")
                            append("实际库存: $actualQuantity 件\n")
                            append("调整数量: ${if (difference > 0) "+" else ""}$difference 件\n")
                            append("错误代码: HTTP ${response.code()}\n")
                            append("错误信息: $errorBody")
                        }
                        
                        AlertDialog.Builder(this@LocationActivity)
                            .setTitle("❌ 网络错误")
                            .setMessage(errorMessage)
                            .setPositiveButton("确定", null)
                            .show()
                            
                        Log.e("WMS_LOCATION", "❌ HTTP错误: ${response.code()} - $errorBody")
                    }
                }
                
            } catch (e: Exception) {
                Log.e("WMS_LOCATION", "❌ 盘点操作异常: ${e.message}", e)
                runOnUiThread {
                    val errorMessage = buildString {
                        append("❌ 盘点操作异常！\n")
                        append("--------------------\n")
                        append("SKU编码: ${item.sku_code}\n")
                        append("库位: $locationCode\n")
                        append("系统库存: $systemQuantity 件\n")
                        append("实际库存: $actualQuantity 件\n")
                        append("调整数量: ${if (difference > 0) "+" else ""}$difference 件\n")
                        append("异常信息: ${e.message}")
                    }
                    
                    AlertDialog.Builder(this@LocationActivity)
                        .setTitle("❌ 系统异常")
                        .setMessage(errorMessage)
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }
    }
    
    // 执行SKU转移操作
    private fun executeSkuTransferOperation(context: android.content.Context, fromLocation: String, toLocation: String, item: LocationInventoryItem, quantity: Int) {
        Log.d("WMS_LOCATION", "✅ 开始执行SKU转移操作: 从库位=$fromLocation, 到库位=$toLocation, SKU=${item.sku_code}, 数量=$quantity")
        
        lifecycleScope.launch {
            try {
                // 构建转移请求
                val request = InventoryTransferRequest(
                    sku_code = item.sku_code ?: "",
                    from_location_code = fromLocation,
                    to_location_code = toLocation,
                    transfer_quantity = quantity,
                    operator_id = ApiClient.getCurrentUserId() ?: "wms_user",
                    batch_number = null,
                    is_urgent = false,
                    notes = "货位管理页面转移操作"
                )
                
                Log.d("WMS_LOCATION", "🔄 发送转移请求...")
                val response = ApiClient.getApiService().transferInventory(request)
                
                runOnUiThread {
                    if (response.isSuccessful) {
                        val apiResponse = response.body()
                        if (apiResponse?.success == true) {
                            
                            // 成功弹窗
                            val successMessage = buildString {
                                append("✅ 转移操作成功！\n")
                                append("--------------------\n")
                                append("SKU编码: ${item.sku_code}\n")
                                append("从库位: $fromLocation\n")
                                append("到库位: $toLocation\n")
                                append("转移数量: $quantity 件\n")
                                append("操作完成时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}")
                            }
                            
                            AlertDialog.Builder(this@LocationActivity)
                                .setTitle("🔄 转移结果")
                                .setMessage(successMessage)
                                .setPositiveButton("确定") { _, _ ->
                                    // 转移操作成功后，刷新库位详情对话框
                                    refreshLocationInventoryDialog()
                                }
                                .setCancelable(false)
                                .show()
                                
                            Log.d("WMS_LOCATION", "✅ 转移成功: $successMessage")
                            
                        } else {
                            // 失败弹窗
                            val errorMessage = buildString {
                                append("❌ 转移操作失败！\n")
                                append("--------------------\n")
                                append("SKU编码: ${item.sku_code}\n")
                                append("从库位: $fromLocation\n")
                                append("到库位: $toLocation\n")
                                append("转移数量: $quantity 件\n")
                                append("失败原因: ${apiResponse?.error_message ?: "未知错误"}")
                            }
                            
                            AlertDialog.Builder(this@LocationActivity)
                                .setTitle("❌ 转移失败")
                                .setMessage(errorMessage)
                                .setPositiveButton("确定", null)
                                .show()
                                
                            Log.e("WMS_LOCATION", "❌ 转移失败: ${apiResponse?.error_message}")
                        }
                    } else {
                        val errorBody = response.errorBody()?.string() ?: response.message()
                        val errorMessage = buildString {
                            append("❌ 转移操作失败！\n")
                            append("--------------------\n")
                            append("SKU编码: ${item.sku_code}\n")
                            append("从库位: $fromLocation\n")
                            append("到库位: $toLocation\n")
                            append("转移数量: $quantity 件\n")
                            append("错误代码: HTTP ${response.code()}\n")
                            append("错误信息: $errorBody")
                        }
                        
                        AlertDialog.Builder(this@LocationActivity)
                            .setTitle("❌ 网络错误")
                            .setMessage(errorMessage)
                            .setPositiveButton("确定", null)
                            .show()
                            
                        Log.e("WMS_LOCATION", "❌ HTTP错误: ${response.code()} - $errorBody")
                    }
                }
                
            } catch (e: Exception) {
                Log.e("WMS_LOCATION", "❌ 转移操作异常: ${e.message}", e)
                runOnUiThread {
                    val errorMessage = buildString {
                        append("❌ 转移操作异常！\n")
                        append("--------------------\n")
                        append("SKU编码: ${item.sku_code}\n")
                        append("从库位: $fromLocation\n")
                        append("到库位: $toLocation\n")
                        append("转移数量: $quantity 件\n")
                        append("异常信息: ${e.message}")
                    }
                    
                    AlertDialog.Builder(this@LocationActivity)
                        .setTitle("❌ 系统异常")
                        .setMessage(errorMessage)
                        .setPositiveButton("确定", null)
                        .show()
                }
            }
        }
    }
    
    // 显示库位入库对话框
    private fun showLocationInboundDialog(location: Location) {
        val input = EditText(this).apply {
            hint = "请输入SKU编码"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        
        AlertDialog.Builder(this)
            .setTitle("📦 库位入库")
            .setMessage("库位: ${location.location_code}")
            .setView(input)
            .setPositiveButton("下一步") { _, _ ->
                val skuCode = input.text.toString().trim()
                if (skuCode.isNotEmpty()) {
                    showInboundQuantityDialog(location, skuCode)
                } else {
                    Toast.makeText(this, "请输入SKU编码", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    // 显示入库数量对话框
    private fun showInboundQuantityDialog(location: Location, skuCode: String) {
        val input = EditText(this).apply {
            hint = "请输入入库数量"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("1")
        }
        
        AlertDialog.Builder(this)
            .setTitle("📦 确认入库")
            .setMessage("库位: ${location.location_code}\nSKU: $skuCode")
            .setView(input)
            .setPositiveButton("确认入库") { _, _ ->
                val quantity = input.text.toString().toIntOrNull() ?: 0
                if (quantity > 0) {
                    executeLocationInbound(location, skuCode, quantity)
                } else {
                    Toast.makeText(this, "请输入有效的入库数量", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    // 显示库位出库对话框
    private fun showLocationOutboundDialog(location: Location) {
        val input = EditText(this).apply {
            hint = "请输入SKU编码"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        
        AlertDialog.Builder(this)
            .setTitle("📤 库位出库")
            .setMessage("库位: ${location.location_code}")
            .setView(input)
            .setPositiveButton("下一步") { _, _ ->
                val skuCode = input.text.toString().trim()
                if (skuCode.isNotEmpty()) {
                    showOutboundQuantityDialog(location, skuCode)
                } else {
                    Toast.makeText(this, "请输入SKU编码", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    // 显示出库数量对话框
    private fun showOutboundQuantityDialog(location: Location, skuCode: String) {
        val input = EditText(this).apply {
            hint = "请输入出库数量"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("1")
        }
        
        AlertDialog.Builder(this)
            .setTitle("📤 确认出库")
            .setMessage("库位: ${location.location_code}\nSKU: $skuCode")
            .setView(input)
            .setPositiveButton("确认出库") { _, _ ->
                val quantity = input.text.toString().toIntOrNull() ?: 0
                if (quantity > 0) {
                    executeLocationOutbound(location, skuCode, quantity)
                } else {
                    Toast.makeText(this, "请输入有效的出库数量", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    // 显示库位盘点对话框
    private fun showLocationInventoryCountDialog(location: Location) {
        AlertDialog.Builder(this)
            .setTitle("📋 库位盘点")
            .setMessage("库位: ${location.location_code}\n\n开始对该库位进行全面盘点？")
            .setPositiveButton("开始盘点") { _, _ ->
                executeLocationInventoryCheck(location)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    // 执行库位入库
    private fun executeLocationInbound(location: Location, skuCode: String, quantity: Int) {
        Log.d("WMS_LOCATION", "✅ 库位入库: 库位=${location.location_code}, SKU=$skuCode, 数量=$quantity")
        Toast.makeText(this, "入库成功！\n库位: ${location.location_code}\nSKU: $skuCode\n数量: $quantity", Toast.LENGTH_LONG).show()
        
        // TODO: 调用实际的入库API
        // ApiClient.getApiService().performLocationInbound(location.location_code, skuCode, quantity)
    }
    
    // 执行库位出库
    private fun executeLocationOutbound(location: Location, skuCode: String, quantity: Int) {
        Log.d("WMS_LOCATION", "✅ 库位出库: 库位=${location.location_code}, SKU=$skuCode, 数量=$quantity")
        Toast.makeText(this, "出库成功！\n库位: ${location.location_code}\nSKU: $skuCode\n数量: $quantity", Toast.LENGTH_LONG).show()
        
        // TODO: 调用实际的出库API
        // ApiClient.getApiService().performLocationOutbound(location.location_code, skuCode, quantity)
    }
    
    // 执行库位盘点
    private fun executeLocationInventoryCheck(location: Location) {
        Log.d("WMS_LOCATION", "✅ 库位盘点: 库位=${location.location_code}")
        Toast.makeText(this, "盘点完成！\n库位: ${location.location_code}\n状态: 盘点中...", Toast.LENGTH_LONG).show()
        
        // TODO: 调用实际的盘点API
        // ApiClient.getApiService().startLocationInventoryCheck(location.location_code)
    }

    // ===== 工具方法：库存/对话框刷新 =====
    // 更新操作后刷新列表和对话框
    private fun updateLocationInventoryAfterOperation(
        locationCode: String,
        skuCode: String,
        newLocationQuantity: Int,
        newTotalQuantity: Int
    ) {
        // 如果详情对话框打开且为同一库位，更新SKU数量
        if (currentLocationCode == locationCode && currentInventoryAdapter != null) {
            updateSkuQuantityInDialog(skuCode, newLocationQuantity)
        }
        // 更新主列表统计
        updateLocationStatsInMainList(locationCode)
    }

    // 在库位详情对话框中更新某 SKU 数量
    private fun updateSkuQuantityInDialog(skuCode: String, newQuantity: Int) {
        currentInventoryAdapter?.let {
            it.updateSkuQuantity(skuCode, newQuantity)
            currentTotalQuantityTextView?.text = "总量: ${it.getTotalQuantity()}件"
        }
    }

    // 重新获取库位汇总并刷新主列表
    private fun updateLocationStatsInMainList(locationCode: String) {
        lifecycleScope.launch {
            try {
                val resp = ApiClient.getApiService().getLocationInventory(locationCode)
                if (resp.isSuccessful && resp.body()?.success == true) {
                    val inv = resp.body()?.data
                    val idx = locations.indexOfFirst { it.location.location_code == locationCode }
                    if (idx != -1 && inv != null) {
                        locations[idx].skuCount = inv.summary?.total_items ?: 0
                        locations[idx].totalQuantity = inv.summary?.total_quantity ?: 0
                        runOnUiThread { locationAdapter.notifyItemChanged(idx) }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // 刷新已经打开的库位详情对话框
    private fun refreshLocationInventoryDialog() {
        if (currentLocationCode.isNotEmpty() && currentInventoryRecyclerView != null) {
            loadLocationInventoryForDialog(
                currentLocationCode,
                currentInventoryRecyclerView!!,
                currentTotalSkuTextView!!,
                currentTotalQuantityTextView!!
            )
        }
    }

    // 关闭详情对话框时清理引用
    private fun clearCurrentDialogReferences() {
        currentInventoryDialog = null
        currentInventoryRecyclerView = null
        currentInventoryAdapter = null
        currentTotalSkuTextView = null
        currentTotalQuantityTextView = null
        currentLocationCode = ""
    }
}

// 带统计信息的库位数据类
data class LocationWithStats(
    val location: Location,
    var skuCount: Int,
    var productCount: Int,
    var totalQuantity: Int
)

// 库位列表适配器
class LocationAdapter(
    private val locations: MutableList<LocationWithStats>,
    private val onLocationAction: (LocationWithStats, String) -> Unit
) : RecyclerView.Adapter<LocationAdapter.LocationViewHolder>() {
    
    class LocationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtLocationCode: TextView = itemView.findViewById(R.id.txtLocationCode)
        val txtLocationCategory: TextView = itemView.findViewById(R.id.txtLocationCategory)
        val txtLocationName: TextView = itemView.findViewById(R.id.txtLocationName)
        val txtDescription: TextView = itemView.findViewById(R.id.txtDescription)
        val txtSkuCount: TextView = itemView.findViewById(R.id.txtSkuCount)
        val txtProductCount: TextView = itemView.findViewById(R.id.txtProductCount)
        val txtTotalQuantity: TextView = itemView.findViewById(R.id.txtTotalQuantity)
        val btnViewDetails: Button = itemView.findViewById(R.id.btnViewDetails)
        val btnEditLocation: Button = itemView.findViewById(R.id.btnEditLocation)
        val layoutInventoryStats: LinearLayout = itemView.findViewById(R.id.layoutInventoryStats)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_location, parent, false)
        return LocationViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
        val locationWithStats = locations[position]
        val location = locationWithStats.location
        
        holder.txtLocationCode.text = location.location_code
        holder.txtLocationName.text = location.location_name ?: "未命名"
        holder.txtDescription.text = location.description ?: ""
        holder.txtSkuCount.text = locationWithStats.skuCount.toString()
        holder.txtProductCount.text = locationWithStats.productCount.toString()
        holder.txtTotalQuantity.text = locationWithStats.totalQuantity.toString()
        
        // 设置分类信息
        val category1 = location.category_name_1 ?: "仓库"
        val category1Val = location.category_code_1 ?: "-"
        val category2 = location.category_name_2 ?: "货架"
        val category2Val = location.category_code_2 ?: "-"
        holder.txtLocationCategory.text = "$category1: $category1Val / $category2: $category2Val"
        
        // 点击展开/收起库存统计
        holder.itemView.setOnClickListener {
            val isVisible = holder.layoutInventoryStats.visibility == View.VISIBLE
            holder.layoutInventoryStats.visibility = if (isVisible) View.GONE else View.VISIBLE
        }
        
        // 按钮点击事件
        holder.btnViewDetails.setOnClickListener {
            onLocationAction(locationWithStats, "view")
        }
        
        holder.btnEditLocation.setOnClickListener {
            onLocationAction(locationWithStats, "edit")
        }
        
        // 长按删除
        holder.itemView.setOnLongClickListener {
            onLocationAction(locationWithStats, "delete")
            true
        }
    }
    
    override fun getItemCount(): Int = locations.size
}

// 库位库存图片网格适配器
class LocationInventoryGridAdapter(
    private val items: MutableList<LocationInventoryItem>,
    private val onItemClick: (LocationInventoryItem) -> Unit
) : RecyclerView.Adapter<LocationInventoryGridAdapter.GridViewHolder>() {
    
    class GridViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgProduct: ImageView = itemView.findViewById(R.id.imgProduct)
        val txtSkuCode: TextView = itemView.findViewById(R.id.txtSkuCode)
        val txtQuantity: TextView = itemView.findViewById(R.id.txtQuantity)
        val txtSkuInfo: TextView = itemView.findViewById(R.id.txtSkuInfo)
        val txtColorSize: TextView = itemView.findViewById(R.id.txtColorSize)
        val txtQuantityInfo: TextView = itemView.findViewById(R.id.txtQuantityInfo)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridViewHolder {
        Log.d("WMS_LOCATION", "🏗️ 创建ViewHolder")
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inventory_image_grid, parent, false)
        Log.d("WMS_LOCATION", "🏗️ ViewHolder创建完成")
        return GridViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: GridViewHolder, position: Int) {
        val item = items[position]
        
        Log.d("WMS_LOCATION", "📦 绑定库存项 $position: SKU=${item.sku_code}, 数量=${item.stock_quantity}")
        
        // 解析SKU编码
        val skuParts = item.sku_code.split("-")
        val productCode = if (skuParts.isNotEmpty()) skuParts[0] else item.sku_code
        
        // 设置SKU编码（显示商品编码）
        holder.txtSkuCode.text = productCode
        
        // 设置数量
        holder.txtQuantity.text = item.stock_quantity.toString()
        
        // 设置颜色和尺码信息（从SKU编码中提取）
        val colorSize = buildString {
            if (skuParts.size > 1) {
                append(skuParts[1]) // 颜色
            }
            if (skuParts.size > 2) {
                if (isNotEmpty()) append("-")
                append(skuParts[2]) // 尺寸
            }
        }
        holder.txtColorSize.text = colorSize.ifEmpty { "未知规格" }
        
        // 加载商品图片作为背景
        loadProductImage(item, holder.imgProduct)
        
        // 点击事件 - 通过回调调用Activity的方法
        holder.itemView.setOnClickListener {
            try {
                Log.d("WMS_LOCATION", "📱 点击SKU卡片: ${item.sku_code}")
                onItemClick(item)
                
            } catch (e: Exception) {
                Log.e("WMS_LOCATION", "❌ 显示SKU操作菜单失败: ${e.message}", e)
                Toast.makeText(holder.itemView.context, "操作失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun loadProductImage(item: LocationInventoryItem, imageView: ImageView) {
        try {
            Log.d("WMS_LOCATION", "🖼️ 加载图片 - SKU: ${item.sku_code}, 数量: ${item.stock_quantity}")
            Log.d("WMS_LOCATION", "📷 图片路径: ${item.image_path}")
            
            // 设置ImageView为背景图片模式
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            
            // 根据SKU编码生成不同的默认图片
            val skuBasedImage = getSkuBasedImage(item.sku_code)
            
            // 先显示占位图片
            Log.d("WMS_LOCATION", "🖼️ 设置占位图: $skuBasedImage")
            imageView.setImageResource(skuBasedImage)
            
            // 检查是否有图片路径
            if (!item.image_path.isNullOrBlank()) {
                // 使用ApiClient构建完整的图片URL
                val fullImageUrl = ApiClient.processImageUrl(item.image_path, imageView.context)
                Log.d("WMS_LOCATION", "🌐 完整图片URL: $fullImageUrl")
                
                // 加载网络图片
                loadNetworkImage(fullImageUrl, imageView, skuBasedImage)
            } else {
                Log.d("WMS_LOCATION", "📷 SKU ${item.sku_code} 无图片路径，使用占位图")
            }
            
        } catch (e: Exception) {
            Log.w("WMS_LOCATION", "加载图片失败: ${e.message}")
            imageView.setImageResource(android.R.drawable.ic_menu_gallery)
        }
    }
    
    private fun loadNetworkImage(imageUrl: String, imageView: ImageView, fallbackImage: Int) {
        // 使用协程在后台线程加载图片
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("WMS_LOCATION", "🌐 开始加载网络图片: $imageUrl")
                
                val url = java.net.URL(imageUrl)
                val connection = url.openConnection()
                connection.doInput = true
                connection.connect()
                
                val inputStream = connection.getInputStream()
                
                // 先获取图片尺寸，避免直接加载大图导致OOM
                val options = android.graphics.BitmapFactory.Options()
                options.inJustDecodeBounds = true
                android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
                inputStream.close()
                
                // 重新打开连接获取图片数据
                val connection2 = url.openConnection()
                connection2.doInput = true
                connection2.connect()
                val inputStream2 = connection2.getInputStream()
                
                // 计算合适的缩放比例（目标尺寸：200x200像素）
                val targetSize = 200
                val sampleSize = calculateInSampleSize(options, targetSize, targetSize)
                
                // 使用缩放比例加载图片
                val decodeOptions = android.graphics.BitmapFactory.Options()
                decodeOptions.inSampleSize = sampleSize
                decodeOptions.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565 // 使用更少内存的格式
                
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream2, null, decodeOptions)
                inputStream2.close()
                
                if (bitmap != null) {
                    // 在主线程更新UI
                    withContext(Dispatchers.Main) {
                        Log.d("WMS_LOCATION", "✅ 网络图片加载成功，尺寸: ${bitmap.width}x${bitmap.height}")
                        imageView.setImageBitmap(bitmap)
                    }
                } else {
                    Log.w("WMS_LOCATION", "⚠️ 网络图片解码失败，使用占位图")
                    withContext(Dispatchers.Main) {
                        imageView.setImageResource(fallbackImage)
                    }
                }
                
            } catch (e: OutOfMemoryError) {
                Log.e("WMS_LOCATION", "❌ 图片加载内存溢出: ${e.message}")
                withContext(Dispatchers.Main) {
                    imageView.setImageResource(fallbackImage)
                }
            } catch (e: Exception) {
                Log.w("WMS_LOCATION", "❌ 网络图片加载失败: ${e.message}")
                // 加载失败时在主线程显示占位图
                withContext(Dispatchers.Main) {
                    imageView.setImageResource(fallbackImage)
                }
            }
        }
    }
    
    private fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
    

    
    override fun getItemCount(): Int = items.size
    
    // 更新特定SKU的数量
    fun updateSkuQuantity(skuCode: String, newQuantity: Int) {
        val itemIndex = items.indexOfFirst { it.sku_code == skuCode }
        if (itemIndex != -1) {
            // 创建一个新的item对象来更新数量
            val updatedItem = items[itemIndex].copy(stock_quantity = newQuantity)
            items[itemIndex] = updatedItem
            notifyItemChanged(itemIndex)
            Log.d("WMS_LOCATION", "✅ 已更新SKU $skuCode 的数量为 $newQuantity")
        } else {
            Log.w("WMS_LOCATION", "⚠️ 未找到SKU $skuCode 进行数量更新")
        }
    }
    
    // 获取所有SKU的总数量
    fun getTotalQuantity(): Int {
        return items.sumOf { it.stock_quantity }
    }
    
    // 根据SKU编码生成唯一的占位图片
    private fun getSkuBasedImage(skuCode: String): Int {
        Log.d("WMS_LOCATION", "🎨 为SKU ${skuCode} 选择占位图")
        
        // 根据SKU中的颜色信息选择对应的图标
        val drawable = when {
            skuCode.contains("黑色", true) -> {
                Log.d("WMS_LOCATION", "🖤 检测到黑色，使用黑色图标")
                android.R.drawable.ic_menu_sort_by_size
            }
            skuCode.contains("白色", true) -> {
                Log.d("WMS_LOCATION", "🤍 检测到白色，使用白色图标")
                android.R.drawable.ic_menu_gallery
            }
            skuCode.contains("红色", true) -> {
                Log.d("WMS_LOCATION", "❤️ 检测到红色，使用红色图标")
                android.R.drawable.ic_menu_delete
            }
            skuCode.contains("蓝色", true) -> {
                Log.d("WMS_LOCATION", "💙 检测到蓝色，使用蓝色图标")
                android.R.drawable.ic_menu_info_details
            }
            skuCode.contains("绿色", true) -> {
                Log.d("WMS_LOCATION", "💚 检测到绿色，使用绿色图标")
                android.R.drawable.ic_menu_add
            }
            skuCode.contains("黄色", true) -> {
                Log.d("WMS_LOCATION", "💛 检测到黄色，使用黄色图标")
                android.R.drawable.ic_menu_edit
            }
            skuCode.contains("卡其色", true) -> {
                Log.d("WMS_LOCATION", "🤎 检测到卡其色，使用卡其色图标")
                android.R.drawable.ic_menu_upload
            }
            skuCode.contains("本色", true) -> {
                Log.d("WMS_LOCATION", "🤍 检测到本色，使用本色图标")
                android.R.drawable.ic_menu_view
            }
            skuCode.startsWith("TEST", true) -> {
                Log.d("WMS_LOCATION", "🧪 检测到测试SKU，使用测试图标")
                android.R.drawable.ic_menu_search
            }
            else -> {
                Log.d("WMS_LOCATION", "🎯 使用默认图标选择逻辑")
                // 对于其他SKU，使用哈希值选择图标
                val availableImages = listOf(
                    android.R.drawable.ic_menu_gallery,
                    android.R.drawable.ic_menu_preferences,
                    android.R.drawable.ic_menu_share,
                    android.R.drawable.ic_menu_send,
                    android.R.drawable.ic_menu_save,
                    android.R.drawable.ic_menu_recent_history,
                    android.R.drawable.ic_menu_rotate,
                    android.R.drawable.ic_menu_manage
                )
                val index = Math.abs(skuCode.hashCode()) % availableImages.size
                availableImages[index]
            }
        }
        
        Log.d("WMS_LOCATION", "🖼️ SKU ${skuCode} 最终选择图标: $drawable")
        return drawable
    }
    
}