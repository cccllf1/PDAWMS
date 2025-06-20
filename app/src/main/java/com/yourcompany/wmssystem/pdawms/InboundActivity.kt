package com.yourcompany.wmssystem.pdawms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import android.widget.AdapterView
import android.text.TextWatcher
import android.text.Editable
import com.bumptech.glide.Glide
import com.google.gson.Gson

// 入库商品数据类
data class InboundItem(
    val sku: String,
    val product_name: String,
    val location: String,
    var quantity: Int, // Allow quantity to be mutable
    val color: String,
    val size: String,
    var image_url: String, // Allow image_url to be mutable
    val batch: String = "",
    val productData: Product? = null // To hold the full product object
)

// 新的API响应模型
data class ProductResponse(
    val success: Boolean,
    val data: ProductData?,
    val error_code: String?,
    val error_message: String?
)

data class ProductData(
    val products: List<Product>?,
    val pagination: Pagination?
)

// 新的入库请求模型

// 新的入库响应模型

class InboundListAdapter(
    private var items: MutableList<InboundItem>,
    private val getLocationOptions: () -> List<String>,
    private val onDeleteClick: (Int) -> Unit,
    private val onItemUpdate: (Int, InboundItem) -> Unit
) : RecyclerView.Adapter<InboundListAdapter.ViewHolder>() {
    
    // 存储每个商品的真实SKU选项
    private val productSkuOptions = mutableMapOf<String, ProductSkuOptions>()
    
    data class ProductSkuOptions(
        val colors: List<String>,
        val sizes: List<String>,
        val colorSizeMap: Map<String, List<String>>, // 颜色对应的尺码列表
        val colorSizeSkuMap: Map<String, Map<String, String>> = emptyMap() // 颜色 -> 尺码 -> SKU编码
    )
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgProduct: ImageView
        val txtProductCode: TextView
        val spinnerColor: Spinner
        val spinnerSize: Spinner
        val spinnerLocation: Spinner
        val editQuantity: EditText
        val btnDelete: Button
        val editSkuTotalStock: EditText
        val editLocationStock: EditText
        
        init {
            try {
                imgProduct = view.findViewById(R.id.imgProduct)
                txtProductCode = view.findViewById(R.id.txtProductCode)
                spinnerColor = view.findViewById(R.id.spinnerColor)
                spinnerSize = view.findViewById(R.id.spinnerSize)
                spinnerLocation = view.findViewById(R.id.spinnerLocation)
                editQuantity = view.findViewById(R.id.editQuantity)
                btnDelete = view.findViewById(R.id.btnDelete)
                editSkuTotalStock = view.findViewById(R.id.editImageNote) // Re-purposing this view
                editLocationStock = view.findViewById(R.id.editImageNote2) // Re-purposing this view
                Log.d("ViewHolder", "所有视图初始化成功")
            } catch (e: Exception) {
                Log.e("ViewHolder", "视图初始化失败: ${e.message}", e)
                throw e
            }
        }
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        try {
            Log.d("InboundAdapter", "开始创建ViewHolder")
            val layoutInflater = android.view.LayoutInflater.from(parent.context)
            Log.d("InboundAdapter", "获取LayoutInflater成功")
            
            val view = layoutInflater.inflate(R.layout.item_inbound_product, parent, false)
            Log.d("InboundAdapter", "布局inflate成功")
            
            val viewHolder = ViewHolder(view)
            Log.d("InboundAdapter", "ViewHolder创建成功")
            
            return viewHolder
        } catch (e: Exception) {
            Log.e("InboundAdapter", "创建ViewHolder失败: ${e.message}", e)
            throw RuntimeException("ViewHolder创建失败，原因: ${e.message}", e)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        try {
            val item = items[position]
            Log.d("InboundAdapter", "开始绑定数据，位置: $position")
            
            // 设置商品信息
            holder.txtProductCode.text = "${item.sku} - ${item.product_name}"
            
            // 加载商品图片
            if (item.image_url.isNotEmpty()) {
                try {
                    Glide.with(holder.itemView.context)
                        .load(item.image_url)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_gallery)
                        .into(holder.imgProduct)
                    Log.d("InboundAdapter", "加载图片: ${item.image_url}")
                } catch (e: Exception) {
                    Log.e("InboundAdapter", "图片加载失败: ${e.message}")
                    holder.imgProduct.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            } else {
                holder.imgProduct.setImageResource(android.R.drawable.ic_menu_gallery)
            }
            
            // 获取该商品的SKU选项 - 🔧 从完整SKU中提取商品编码
            val productCode = if (item.sku.contains("-")) {
                item.sku.split("-")[0]  // 从 "129092-黄色-M" 提取 "129092"
            } else {
                item.sku  // 如果没有"-"，直接使用原值
            }
            val skuOptions = productSkuOptions[productCode]
            Log.d("InboundAdapter", "查找SKU选项: item.sku=${item.sku} -> productCode=$productCode -> 找到选项=${skuOptions != null}")
            
            if (skuOptions != null) {
                // 使用真实的颜色选项
                val colorAdapter = ArrayAdapter(holder.itemView.context, android.R.layout.simple_spinner_item, skuOptions.colors)
                colorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                holder.spinnerColor.adapter = colorAdapter
                
                // 设置当前选中的颜色，如果没有指定则使用第一个
                val colorIndex = if (item.color.isNotEmpty()) {
                    skuOptions.colors.indexOf(item.color)
                } else {
                    0  // 使用第一个颜色
                }
                
                if (colorIndex >= 0 && colorIndex < skuOptions.colors.size) {
                    holder.spinnerColor.setSelection(colorIndex)
                    // 更新item的颜色为当前选择的颜色
                    val selectedColor = skuOptions.colors[colorIndex]
                    items[holder.adapterPosition] = items[holder.adapterPosition].copy(color = selectedColor)
                }
                
                // 颜色选择监听器 - 更新对应的尺码选项
                holder.spinnerColor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        try {
                            if (holder.adapterPosition == RecyclerView.NO_POSITION || holder.adapterPosition >= items.size) {
                                Log.w("InboundAdapter", "🚨 颜色选择 - 适配器位置无效: ${holder.adapterPosition}")
                                return
                            }
                            val selectedColor = skuOptions.colors[position]
                            val currentItem = items[holder.adapterPosition]

                            // 1. 立刻用新颜色更新item
                            var updatedItem = currentItem.copy(color = selectedColor)

                            // 2. 更新尺码选择器的选项
                            val sizesForColor = skuOptions.colorSizeMap[selectedColor] ?: skuOptions.sizes
                            val sizeAdapter = ArrayAdapter(holder.itemView.context, android.R.layout.simple_spinner_item, sizesForColor)
                            sizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                            holder.spinnerSize.adapter = sizeAdapter

                            // 3. 决定尺码的选中项
                            var sizeIndex = sizesForColor.indexOf(updatedItem.size)
                            if (sizeIndex == -1 && sizesForColor.isNotEmpty()) {
                                sizeIndex = 0 // 如果旧尺码不存在，自动选择第一个
                            }

                            // 4. 如果尺码有效，更新SKU和UI
                            if (sizeIndex != -1) {
                                holder.spinnerSize.setSelection(sizeIndex)
                                val selectedSize = sizesForColor[sizeIndex]
                                val skuCode = skuOptions.colorSizeSkuMap[selectedColor]?.get(selectedSize) ?: updatedItem.sku
                                updatedItem = updatedItem.copy(size = selectedSize, sku = skuCode)
                                holder.txtProductCode.text = "${skuCode} - ${updatedItem.product_name}"
                            }
                            
                            // 5. 强制更新图片 (这是修复的关键)
                            updateProductImage(holder, updatedItem)

                            // 6. 保存所有更改
                            items[holder.adapterPosition] = updatedItem
                            onItemUpdate(holder.adapterPosition, updatedItem)
                            Log.d("InboundAdapter", "颜色变更为: $selectedColor, 图片已刷新")

                        } catch (e: Exception) {
                            Log.e("InboundAdapter", "🚨 颜色选择器发生异常: ${e.message}", e)
                        }
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
                
                // 设置尺码选择器
                val currentColor = items[holder.adapterPosition].color
                val sizesForCurrentColor = skuOptions.colorSizeMap[currentColor] ?: skuOptions.sizes
                val sizeAdapter = ArrayAdapter(holder.itemView.context, android.R.layout.simple_spinner_item, sizesForCurrentColor)
                sizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                holder.spinnerSize.adapter = sizeAdapter
                
                // 设置当前选中的尺码，如果没有指定则使用第一个
                val sizeIndex = if (item.size.isNotEmpty()) {
                    sizesForCurrentColor.indexOf(item.size)
                } else {
                    0  // 使用第一个尺码
                }
                
                if (sizeIndex >= 0 && sizeIndex < sizesForCurrentColor.size) {
                    holder.spinnerSize.setSelection(sizeIndex)
                    val selectedSize = sizesForCurrentColor[sizeIndex]
                    
                    // 获取对应的SKU编码并更新显示
                    val skuCode = skuOptions.colorSizeSkuMap[currentColor]?.get(selectedSize) ?: item.sku
                    val updatedItem = items[holder.adapterPosition].copy(
                        size = selectedSize,
                        sku = skuCode
                    )
                    items[holder.adapterPosition] = updatedItem
                    holder.txtProductCode.text = "${skuCode} - ${updatedItem.product_name}"
                    
                    Log.d("InboundAdapter", "初始设置: 颜色 $currentColor, 尺码 $selectedSize, SKU: $skuCode")
                }
                
                // 尺码选择监听器
                holder.spinnerSize.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        // 🚨 超级安全检查：防止所有可能的崩溃
                        try {
                            // 检查position有效性
                            if (position < 0 || position >= sizesForCurrentColor.size) {
                                Log.w("InboundAdapter", "🚨 尺码选择位置无效: $position, 尺码数量: ${sizesForCurrentColor.size}")
                                return
                            }
                            
                            // 🔧 安全检查：确保holder.adapterPosition有效
                            if (holder.adapterPosition == RecyclerView.NO_POSITION || 
                                holder.adapterPosition >= items.size || 
                                holder.adapterPosition < 0) {
                                Log.w("InboundAdapter", "🚨 尺码选择 - 无效的adapter position: ${holder.adapterPosition}, 列表大小: ${items.size}")
                                return
                            }
                            
                            val selectedSize = sizesForCurrentColor[position]
                            val currentColor = items[holder.adapterPosition].color
                            
                            // 获取对应的SKU编码
                            val skuCode = skuOptions.colorSizeSkuMap[currentColor]?.get(selectedSize) 
                                ?: items[holder.adapterPosition].sku
                            
                            // 再次检查位置是否仍然有效
                            if (holder.adapterPosition >= items.size || holder.adapterPosition < 0) {
                                Log.w("InboundAdapter", "🚨 尺码选择操作中位置变为无效: ${holder.adapterPosition}, 列表大小: ${items.size}")
                                return
                            }
                            
                            // 更新item数据和显示的商品编码
                            val updatedItem = items[holder.adapterPosition].copy(
                                size = selectedSize,
                                sku = skuCode
                            )
                            items[holder.adapterPosition] = updatedItem
                            holder.txtProductCode.text = "${skuCode} - ${updatedItem.product_name}"
                            
                            // 更新商品图片
                            updateProductImage(holder, updatedItem)
                            
                            Log.d("InboundAdapter", "尺码选择: $selectedSize, 颜色: $currentColor, SKU: $skuCode")
                            onItemUpdate(holder.adapterPosition, updatedItem)
                        } catch (e: Exception) {
                            Log.e("InboundAdapter", "🚨 尺码选择器发生异常: ${e.message}", e)
                        }
                    }
                    
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
                
            } else {
                // 🔧 如果没有SKU信息，使用商品本身的颜色和尺码，不使用"默认颜色"
                val itemColors = if (item.color.isNotEmpty()) listOf(item.color) else listOf("未知颜色")
                val itemSizes = if (item.size.isNotEmpty()) listOf(item.size) else listOf("未知尺码")
                
                val colorAdapter = ArrayAdapter(holder.itemView.context, android.R.layout.simple_spinner_item, itemColors)
                colorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                holder.spinnerColor.adapter = colorAdapter
                holder.spinnerColor.setSelection(0)
                
                val sizeAdapter = ArrayAdapter(holder.itemView.context, android.R.layout.simple_spinner_item, itemSizes)
                sizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                holder.spinnerSize.adapter = sizeAdapter
                holder.spinnerSize.setSelection(0)
                
                Log.d("InboundAdapter", "使用商品本身颜色尺码: 颜色=${item.color}, 尺码=${item.size}")
            }
            
            // 设置货位选择器
            // 获取货位选项，并添加一个空选项作为默认
            val currentLocationOptions = getLocationOptions().toMutableList()
            
            // 🔧 确保"无货位"在选项列表中
            if (!currentLocationOptions.contains("无货位")) {
                currentLocationOptions.add(0, "无货位")  // 添加到第一位
                Log.d("InboundAdapter", "添加'无货位'到选项列表")
            }
            
            // 如果当前商品的货位不在选项列表中，添加它
            if (item.location.isNotEmpty() && item.location != "无货位" && !currentLocationOptions.contains(item.location)) {
                currentLocationOptions.add(item.location)
                Log.d("InboundAdapter", "添加新货位到选项列表: ${item.location}")
            }
            
            val locationOptionsWithEmpty = listOf("请选择货位") + currentLocationOptions
            val locationAdapter = ArrayAdapter(holder.itemView.context, android.R.layout.simple_spinner_item, locationOptionsWithEmpty)
            locationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            holder.spinnerLocation.adapter = locationAdapter
            
            // 设置当前选中的货位 - 🔧 修复"无货位"显示问题
            val locationIndex = if (item.location.isNotEmpty()) {
                if (item.location == "无货位") {
                    // 如果是"无货位"，也要在选项中查找并选择
                    val index = currentLocationOptions.indexOf("无货位")
                    if (index >= 0) index + 1 else 0  // +1 因为前面添加了"请选择货位"
                } else {
                    // 其他具体货位
                    val index = currentLocationOptions.indexOf(item.location)
                    if (index >= 0) index + 1 else 0  // +1 因为前面添加了"请选择货位"
                }
            } else {
                0  // 空字符串时选择"请选择货位"
            }
            
            if (locationIndex >= 0 && locationIndex < locationOptionsWithEmpty.size) {
                holder.spinnerLocation.setSelection(locationIndex)
                Log.d("InboundAdapter", "设置货位选择: 位置=$locationIndex, 货位=${locationOptionsWithEmpty[locationIndex]}")
            }
            
            // 货位选择监听器
            holder.spinnerLocation.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    // 🚨 超级安全检查：防止所有可能的崩溃
                    try {
                        // 检查position有效性
                        if (position < 0 || position >= locationOptionsWithEmpty.size) {
                            Log.w("InboundAdapter", "🚨 货位选择位置无效: $position, 货位数量: ${locationOptionsWithEmpty.size}")
                            return
                        }
                        
                        // 🔧 安全检查：确保holder.adapterPosition有效
                        if (holder.adapterPosition == RecyclerView.NO_POSITION || 
                            holder.adapterPosition >= items.size || 
                            holder.adapterPosition < 0) {
                            Log.w("InboundAdapter", "🚨 货位选择 - 无效的adapter position: ${holder.adapterPosition}, 列表大小: ${items.size}")
                            return
                        }
                        
                        val selectedLocation = if (position > 0) {
                            locationOptionsWithEmpty[position]
                        } else {
                            "无货位"  // 🔧 如果选择了"请选择货位"，设为"无货位"而不是空字符串
                        }
                        
                        // 再次检查位置是否仍然有效
                        if (holder.adapterPosition >= items.size || holder.adapterPosition < 0) {
                            Log.w("InboundAdapter", "🚨 货位选择操作中位置变为无效: ${holder.adapterPosition}, 列表大小: ${items.size}")
                            return
                        }
                        
                        val updatedItem = items[holder.adapterPosition].copy(location = selectedLocation)
                        items[holder.adapterPosition] = updatedItem
                        onItemUpdate(holder.adapterPosition, updatedItem)
                        
                        Log.d("InboundAdapter", "货位选择: $selectedLocation")
                        // Update location stock display when location changes
                        updateStockDisplay(holder, updatedItem.productData, updatedItem.sku, selectedLocation)
                    } catch (e: Exception) {
                        Log.e("InboundAdapter", "🚨 货位选择器发生异常: ${e.message}", e)
                    }
                }
                
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            
            // 设置数量
            holder.editQuantity.setText(item.quantity.toString())
            
            // 数量变化监听
            holder.editQuantity.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    // 🚨 超级安全检查：防止所有可能的崩溃
                    try {
                        // 🔧 安全检查：确保holder.adapterPosition有效
                        if (holder.adapterPosition == RecyclerView.NO_POSITION || 
                            holder.adapterPosition >= items.size || 
                            holder.adapterPosition < 0) {
                            Log.w("InboundAdapter", "🚨 数量变化 - 无效的adapter position: ${holder.adapterPosition}, 列表大小: ${items.size}")
                            return
                        }
                        
                        val newQuantity = s.toString().toIntOrNull() ?: 1
                        items[holder.adapterPosition] = items[holder.adapterPosition].copy(quantity = newQuantity)
                        onItemUpdate(holder.adapterPosition, items[holder.adapterPosition])
                    } catch (e: Exception) {
                        Log.e("InboundAdapter", "🚨 数量变化监听器发生异常: ${e.message}", e)
                    }
                }
            })
            
            // 删除按钮
            holder.btnDelete.setOnClickListener {
                onDeleteClick(position)
            }
            
            // Set hints for the stock fields
            holder.editSkuTotalStock.hint = "SKU总库存"
            holder.editLocationStock.hint = "库位库存"
            holder.editSkuTotalStock.isEnabled = false // Make them read-only
            holder.editLocationStock.isEnabled = false
            
            // Update stock information display
            updateStockDisplay(holder, item.productData, item.sku, item.location)
            
            Log.d("InboundAdapter", "数据绑定完成")
        } catch (e: Exception) {
            Log.e("InboundAdapter", "绑定数据失败: ${e.message}", e)
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: MutableList<InboundItem>) {
        items = newItems
        notifyDataSetChanged()
    }
    
    // 更新商品图片
    private fun updateProductImage(holder: ViewHolder, item: InboundItem) {
        // 确保商品数据存在
        val productData = item.productData ?: run {
            holder.imgProduct.setImageResource(android.R.drawable.ic_menu_gallery)
            return
        }
        
        // 重新计算图片URL (只查找颜色图片)
        val newImageUrl = getBestImageUrl(productData, item.sku, item.color, holder.itemView.context)
        
        // 更新item中的URL，以便持久化
        if (holder.adapterPosition >= 0 && holder.adapterPosition < items.size) {
            if (items[holder.adapterPosition].image_url != newImageUrl) {
                items[holder.adapterPosition] = items[holder.adapterPosition].copy(image_url = newImageUrl)
            }
        }
        
        // 使用Glide加载图片
        if (newImageUrl.isNotEmpty()) {
            try {
                Glide.with(holder.itemView.context)
                    .load(newImageUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery) // 加载中占位图
                    .error(android.R.drawable.ic_menu_gallery)       // 失败时占位图
                    .into(holder.imgProduct)
                Log.d("InboundAdapter", "Glide加载图片: $newImageUrl")
            } catch (e: Exception) {
                Log.e("InboundAdapter", "图片更新失败: ${e.message}")
                holder.imgProduct.setImageResource(android.R.drawable.ic_menu_gallery)
            }
        } else {
            // 如果URL为空，直接设置占位图
            holder.imgProduct.setImageResource(android.R.drawable.ic_menu_gallery)
            Log.d("InboundAdapter", "图片URL为空，设置占位图")
        }
    }
    
    // 设置商品的SKU选项 - 更新为新的API结构
    fun setProductSkuOptions(productCode: String, colors: List<ColorInfo>?, skus: List<SkuInfo>?) {
        Log.d("InboundAdapter", "设置商品 $productCode 的SKU选项: colors=${colors?.size}, skus=${skus?.size}")
        
        if (colors.isNullOrEmpty()) {
            Log.w("InboundAdapter", "颜色数据为空，无法设置SKU选项")
            return
        }
        
        // 提取所有颜色
        val allColors = colors.map { it.color }.distinct()
        
        // 创建颜色到尺码-SKU的映射
        val colorSizeMap = mutableMapOf<String, List<String>>()
        val colorSizeSkuMap = mutableMapOf<String, MutableMap<String, String>>() // 颜色 -> 尺码 -> SKU编码
        
        // 从colors数据中提取每个颜色的尺码和SKU信息
        for (colorInfo in colors) {
            val colorName = colorInfo.color
            val sizesForColor = mutableListOf<String>()
            val sizeSkuMapForColor = mutableMapOf<String, String>()
            
            colorInfo.sizes?.forEach { skuInfo ->
                val size = skuInfo.sku_size
                val skuCode = skuInfo.sku_code
                if (size != null && skuCode.isNotEmpty()) {
                    sizesForColor.add(size)
                    sizeSkuMapForColor[size] = skuCode
                    Log.d("InboundAdapter", "颜色 $colorName, 尺码 $size -> SKU: $skuCode")
                }
            }
            
            if (sizesForColor.isNotEmpty()) {
                colorSizeMap[colorName] = sizesForColor.distinct()
                colorSizeSkuMap[colorName] = sizeSkuMapForColor
            } else {
                // 如果该颜色没有尺码数据，使用通用尺码
                colorSizeMap[colorName] = listOf("均码")
                colorSizeSkuMap[colorName] = mutableMapOf("均码" to productCode)
            }
        }
        
        // 提取所有尺码
        val allSizes = colorSizeMap.values.flatten().distinct()
        val finalSizes = if (allSizes.isEmpty()) listOf("均码") else allSizes
        
        productSkuOptions[productCode] = ProductSkuOptions(
            colors = allColors,
            sizes = finalSizes,
            colorSizeMap = colorSizeMap,
            colorSizeSkuMap = colorSizeSkuMap
        )
        
        Log.d("InboundAdapter", "成功设置商品 $productCode 的SKU选项:")
        Log.d("InboundAdapter", "  颜色${allColors.size}个: $allColors")
        Log.d("InboundAdapter", "  尺码${finalSizes.size}个: $finalSizes")
        Log.d("InboundAdapter", "  颜色-尺码映射: $colorSizeMap")
        Log.d("InboundAdapter", "  颜色-尺码-SKU映射: $colorSizeSkuMap")
    }
    
    // 获取最佳图片URL - 优先级：颜色图片 > 商品图片
    private fun getBestImageUrl(product: Product, skuCode: String, color: String, context: Context): String {
        Log.d("InboundActivity", "🖼️ 查找图片 (仅限颜色): 颜色=$color")
        
        // 1. 只查找并使用指定颜色的图片
        product.colors?.find { it.color == color }?.image_path?.let { path ->
            if (path.isNotEmpty()) {
                val fullUrl = processImageUrl(path, context)
                Log.d("InboundActivity", "✅ 找到颜色级图片: $fullUrl")
                return fullUrl
            }
        }
        
        // 2. 如果指定颜色没有图片路径，或路径为空，则返回空字符串
        Log.w("InboundActivity", "❌ 未找到颜色 '$color' 的有效图片路径，返回空")
        return "" // 不再回退到商品主图
    }
    
    // 🔧 处理图片URL，拼接服务器地址
    private fun processImageUrl(imagePath: String, context: Context): String {
        return if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
            imagePath
        } else {
            val baseUrl = ApiClient.getServerUrl(context)
            (baseUrl.trimEnd('/') + "/" + imagePath.trimStart('/'))
        }
    }

    // Helper function moved to the adapter's scope
    private fun updateStockDisplay(holder: ViewHolder, product: Product?, skuCode: String, locationCode: String) {
        var skuTotal = 0
        var locTotal = 0
        product?.colors?.asSequence()
            ?.flatMap { it.sizes ?: emptyList() }
            ?.find { it.sku_code == skuCode }
            ?.also { skuInfo ->
                skuTotal = skuInfo.sku_total_quantity ?: 0
                locTotal = skuInfo.locations?.find { it.location_code == locationCode }?.stock_quantity ?: 0
            }
        holder.editSkuTotalStock.setText(skuTotal.toString())
        holder.editLocationStock.setText(locTotal.toString())
    }
}

class InboundActivity : AppCompatActivity() {
    private lateinit var editProductCode: EditText
    private lateinit var btnConfirmProduct: Button
    private lateinit var txtInboundTitle: TextView
    private lateinit var recyclerInboundList: RecyclerView
    private lateinit var btnConfirmInbound: Button
    private lateinit var editLocationInput: androidx.appcompat.widget.AppCompatAutoCompleteTextView
    
    private lateinit var inboundListAdapter: InboundListAdapter
    private val inboundItems = mutableListOf<InboundItem>()

    // 统一导航栏
    private lateinit var unifiedNavBar: UnifiedNavBar

    // API相关变量
    private var locationOptions = mutableListOf<String>()
    
    // 🚀 扫描队列处理机制 - 绝对不丢失任何扫描
    private val scanQueue = mutableListOf<String>()
    private var isProcessingQueue = false
    private var lastScanTime = 0L
    private var lastScanCode = ""

    // 扫码广播接收器
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
        Log.e("InboundActivity", "🔥🔥🔥 onCreate() 开始执行！🔥🔥🔥")
        setContentView(R.layout.activity_inbound)

        // 初始化 API 客户端
        ApiClient.init(this)
        
        // 验证服务器地址是否已设置
        val currentServerUrl = ApiClient.getServerUrl(this)
        if (currentServerUrl.isEmpty()) {
            Log.e("InboundActivity", "❌ 服务器地址未设置，请返回登录页面设置服务器地址")
            Toast.makeText(this, "服务器地址未设置，请重新登录", Toast.LENGTH_LONG).show()
            finish()
            return
        } else {
            Log.d("InboundActivity", "✅ 使用服务器地址: $currentServerUrl")
        }

        initViews()
        initUnifiedNavBar()
        setupRecyclerView()
        setupScanReceiver()
        setupClickListeners()
        loadLocationOptions()
        
        // 🧹 启动时清理重复记录
        Log.d("InboundActivity", "🚀 开始启动时清理...")
        mergeduplicateItems()
        
        // 🚨 临时强制清理所有重复记录
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d("InboundActivity", "🧹 延迟1秒后强制清理重复记录...")
            mergeduplicateItems()
        }, 1000)
        
        // 🚨 再次强制清理
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d("InboundActivity", "🧹 延迟3秒后再次强制清理...")
            mergeduplicateItems()
        }, 3000)
        
        Log.e("InboundActivity", "🔥🔥🔥 onCreate() 执行完成！🔥🔥🔥")
    }

    private fun initViews() {
        editProductCode = findViewById(R.id.editProductCode)
        btnConfirmProduct = findViewById(R.id.btnConfirmProduct)
        txtInboundTitle = findViewById(R.id.txtInboundTitle)
        recyclerInboundList = findViewById(R.id.recyclerInboundList)
        btnConfirmInbound = findViewById(R.id.btnConfirmInbound)
        editLocationInput = findViewById(R.id.editLocationInput)
        
        // 设置货位选择器的配置
        editLocationInput.threshold = 0  // 设置为0，这样点击就会显示所有选项
        editLocationInput.hint = "无货位"
        editLocationInput.setText("")  // 清空初始文本
        
        // 设置点击监听，点击时显示下拉列表
        editLocationInput.setOnClickListener {
            editLocationInput.showDropDown()
        }
        
        // 设置焦点监听，获得焦点时显示下拉列表
        editLocationInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                editLocationInput.showDropDown()
            }
        }
    }
    
    private fun initUnifiedNavBar() {
        val navBarContainer = findViewById<LinearLayout>(R.id.navBarContainer)
        unifiedNavBar = UnifiedNavBar.addToActivity(this, navBarContainer, "inbound")
    }

    private fun setupRecyclerView() {
        inboundListAdapter = InboundListAdapter(
            inboundItems,
            { locationOptions },  // 传递一个获取货位选项的函数
            onDeleteClick = { position -> removeItemAt(position) },
            onItemUpdate = { position, updatedItem -> 
                inboundItems[position] = updatedItem
                updateItemCount()
                
                // 🔄 检查修改后是否与其他商品重复，如果重复则合并
                Log.d("InboundActivity", "🔄 商品信息已更新，检查是否需要合并重复项...")
                mergeduplicateItems()
            }
        )
        recyclerInboundList.layoutManager = LinearLayoutManager(this)
        recyclerInboundList.adapter = inboundListAdapter
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

    private fun setupClickListeners() {
        // 商品确认按钮
        btnConfirmProduct.setOnClickListener {
            Log.e("InboundActivity", "★★★ 确认按钮被点击了！★★★")
            addProductToList()
        }

        // 确认入库按钮
        btnConfirmInbound.setOnClickListener {
            confirmInbound()
        }

        // 商品码输入监听
        editProductCode.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && editProductCode.text.toString().isNotEmpty()) {
                // 可以在这里添加自动搜索逻辑
            }
        }
    }

    private fun insertToFocusedEditText(data: String) {
        runOnUiThread {
            val focusedView = currentFocus
            when (focusedView) {
                editProductCode -> {
                    editProductCode.setText(data)
                    // 扫码后自动添加到列表
                    addProductToList()
                }
                else -> {
                    // 如果焦点在其他地方，默认填入商品码输入框
                    editProductCode.setText(data)
                    addProductToList()
                }
            }
        }
    }

    private fun loadLocationOptions() {
        Log.d("InboundActivity", "开始加载库位选项...")
        
        lifecycleScope.launch {
            try {
                // 从API获取真实的库位数据
                val response = ApiClient.getApiService().getLocations()
                if (response.isSuccessful && response.body()?.success == true) {
                    val locations = response.body()?.data ?: emptyList()
                    
                    runOnUiThread {
                        locationOptions.clear()
                        locationOptions.add("无货位")
                        
                        // 添加从API获取的真实库位
                        locations.forEach { location ->
                            locationOptions.add(location.location_code)
                        }
                        
                        Log.d("InboundActivity", "从API加载了 ${locations.size} 个真实库位")
                        Log.d("InboundActivity", "真实库位列表: $locationOptions")
                        
                        val adapter = ArrayAdapter(this@InboundActivity, 
                            android.R.layout.simple_dropdown_item_1line, locationOptions)
                        editLocationInput.setAdapter(adapter)
                        Toast.makeText(this@InboundActivity, "已加载 ${locations.size} 个真实库位", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("InboundActivity", "API获取库位失败: ${response.body()?.error_message}")
                    // 如果API失败，使用备用方案
                    loadFallbackLocationOptions()
                }
            } catch (e: Exception) {
                Log.e("InboundActivity", "获取库位异常: ${e.message}", e)
                // 如果API异常，使用备用方案
                loadFallbackLocationOptions()
            }
        }
    }
    
    private fun loadFallbackLocationOptions() {
        Log.d("InboundActivity", "使用备用库位数据...")
        runOnUiThread {
            locationOptions.clear()
            locationOptions.add("无货位")
            
            // 备用库位数据（从之前的API响应中提取的真实库位）
            val fallbackLocations = listOf(
                "154562", "7788", "C02-01-01", "压顶地JGHG",
                "西8排1架6层4位", "西8排1架6层5位", "西8排2架6层1位", 
                "西8排2架6层3位", "西8排2架6层4位", "西8排3架6层1位", 
                "西8排3架6层2位", "西8排地上窗"
            )
            locationOptions.addAll(fallbackLocations)
            
            Log.d("InboundActivity", "备用库位列表: $locationOptions")
            
            val adapter = ArrayAdapter(this@InboundActivity, 
                android.R.layout.simple_dropdown_item_1line, locationOptions)
            editLocationInput.setAdapter(adapter)
            Toast.makeText(this@InboundActivity, "已加载 ${fallbackLocations.size} 个备用库位", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun addProductToList() {
        val productCode = editProductCode.text.toString().trim()
        if (productCode.isEmpty()) {
            Toast.makeText(this, "请输入或扫描商品编码", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            processScannedCode(productCode)
        }
    }

    // This is the new, definitive method for processing codes
    private suspend fun processScannedCode(scannedCode: String) {
        // 1. Extract base product code for a guaranteed API call
        val baseProductCode = scannedCode.split("-").firstOrNull() ?: scannedCode
        
        val product: Product? = try {
            val response = ApiClient.getApiService().getProductByCode(baseProductCode)
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.data
            } else {
                Toast.makeText(this, "API查询失败: ${response.body()?.error_message}", Toast.LENGTH_SHORT).show()
                null
            }
        } catch (e: Exception) {
            Log.e("InboundActivity", "API异常 for $baseProductCode", e)
            Toast.makeText(this, "网络或API错误: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }

        if (product == null) {
            Toast.makeText(this, "无法获取商品[$baseProductCode]的详细信息", Toast.LENGTH_LONG).show()
            return
        }
        
        // At this point, `product` contains all color/size/SKU info.
        // We now determine the EXACT SKU the user wants.
        val localParsedInfo = parseProductCodeLocally(scannedCode)
        val targetColor = localParsedInfo?.color ?: product.colors?.firstOrNull()?.color ?: "N/A"
        val targetSize = localParsedInfo?.size ?: product.colors?.firstOrNull()?.sizes?.firstOrNull()?.sku_size ?: "N/A"

        val targetSkuInfo = product.colors?.asSequence()
            ?.flatMap { it.sizes ?: emptyList() }
            ?.find { it.sku_color == targetColor && it.sku_size == targetSize }

        val finalSkuCode = targetSkuInfo?.sku_code ?: scannedCode // Fallback to original code if specific SKU not found

        val location = editLocationInput.text.toString().trim().ifEmpty { "无货位" }

        // 4. Strict uniqueness check (Full SKU + Location)
        val existingItemIndex = inboundItems.indexOfFirst { it.sku == finalSkuCode && it.location == location }

        if (existingItemIndex != -1) {
            // Item exists, just increment quantity
            val existingItem = inboundItems[existingItemIndex]
            existingItem.quantity++
            inboundListAdapter.notifyItemChanged(existingItemIndex) // This will re-bind and update stock info too
            Toast.makeText(this, "数量已累加: ${existingItem.sku} - ${existingItem.quantity}", Toast.LENGTH_SHORT).show()
        } else {
            // Item does not exist, add a new one
            val newItem = InboundItem(
                sku = finalSkuCode,
                product_name = product.product_name,
                location = location,
                quantity = 1,
                color = targetColor,
                size = targetSize,
                image_url = getBestImageUrl(product, finalSkuCode, targetColor, this),
                productData = product // CRUCIAL: Attach the full product data object here
            )
            
            // Set SKU options in adapter BEFORE adding
            inboundListAdapter.setProductSkuOptions(baseProductCode, product.colors, product.skus)
            
            inboundItems.add(0, newItem)
            inboundListAdapter.notifyItemInserted(0)
            recyclerInboundList.scrollToPosition(0)
            Toast.makeText(this, "已添加新商品: $finalSkuCode", Toast.LENGTH_SHORT).show()
        }

        editProductCode.text.clear()
        updateItemCount()
    }

    private fun removeItemAt(position: Int) {
        if (position < inboundItems.size) {
            inboundItems.removeAt(position)
            inboundListAdapter.notifyItemRemoved(position)
            inboundListAdapter.notifyItemRangeChanged(position, inboundItems.size)
            updateItemCount()
        }
    }
    
    // 📦 本地条码解析数据类
    data class LocalProductInfo(
        val productCode: String,
        val color: String,
        val size: String
    )
    
    // 🔍 本地解析商品条码（格式：商品编码-颜色-尺码）
    private fun parseProductCodeLocally(code: String): LocalProductInfo? {
        try {
            Log.d("InboundActivity", "🔍 开始本地解析条码: $code")
            
            // 支持的格式：129092-黄色-XXL, 129092-黄色-M, ABC123-红色-L 等
            val parts = code.split("-")
            
            if (parts.size >= 3) {
                val productCode = parts[0]
                val color = parts[1]
                val size = parts[2]
                
                // 验证格式是否合理
                if (productCode.isNotEmpty() && color.isNotEmpty() && size.isNotEmpty()) {
                    Log.d("InboundActivity", "✅ 本地解析成功: 商品=$productCode, 颜色=$color, 尺码=$size")
                    return LocalProductInfo(productCode, color, size)
                }
            }
            
            Log.d("InboundActivity", "❌ 条码格式不符合本地解析规则: $code")
            return null
        } catch (e: Exception) {
            Log.e("InboundActivity", "❌ 本地解析异常: ${e.message}", e)
            return null
        }
    }
    
    private fun mergeduplicateItems() {
        Log.d("InboundActivity", "🧹 开始合并重复商品...")
        Log.d("InboundActivity", "🧹 合并前列表大小: ${inboundItems.size}")
        
        // 打印合并前的详细信息
        inboundItems.forEachIndexed { index, item ->
            Log.d("InboundActivity", "🧹 合并前[$index]: sku=${item.sku}, location=${item.location}, color=${item.color}, size=${item.size}, quantity=${item.quantity}")
        }
        
        val mergedMap = mutableMapOf<String, InboundItem>()
        
        for (item in inboundItems) {
            val key = "${item.sku}_${item.location}_${item.color}_${item.size}"
            Log.d("InboundActivity", "🧹 处理商品: $key")
            
            if (mergedMap.containsKey(key)) {
                // 如果已存在相同的商品，累加数量
                val existing = mergedMap[key]!!
                val newQuantity = existing.quantity + item.quantity
                mergedMap[key] = existing.copy(quantity = newQuantity)
                Log.d("InboundActivity", "🧹 合并商品: ${item.sku} 数量: ${existing.quantity} + ${item.quantity} = $newQuantity")
            } else {
                // 如果是新商品，直接添加
                mergedMap[key] = item
                Log.d("InboundActivity", "🧹 新增商品: $key")
            }
        }
        
        val originalSize = inboundItems.size
        val mergedList = mergedMap.values.toMutableList()
        
        Log.d("InboundActivity", "🧹 合并后列表大小: ${mergedList.size}")
        
        if (mergedList.size != originalSize) {
            inboundItems.clear()
            inboundItems.addAll(mergedList)
            
            // 🔧 安全地更新适配器，避免崩溃
            runOnUiThread {
                try {
                    inboundListAdapter.notifyDataSetChanged()
                    updateItemCount()
                    Log.d("InboundActivity", "🧹 适配器更新完成")
                } catch (e: Exception) {
                    Log.e("InboundActivity", "🧹 适配器更新失败: ${e.message}", e)
                }
            }
            
            Log.d("InboundActivity", "🧹 合并完成: $originalSize 条记录合并为 ${mergedList.size} 条")
            Toast.makeText(this, "已合并重复商品：$originalSize 条 → ${mergedList.size} 条", Toast.LENGTH_LONG).show()
        } else {
            Log.d("InboundActivity", "🧹 无需合并: 没有重复记录")
        }
        
        // 打印合并后的详细信息
        inboundItems.forEachIndexed { index, item ->
            Log.d("InboundActivity", "🧹 合并后[$index]: sku=${item.sku}, location=${item.location}, color=${item.color}, size=${item.size}, quantity=${item.quantity}")
        }
    }

    private fun confirmInbound() {
        if (inboundItems.isEmpty()) {
            Toast.makeText(this, "入库清单为空", Toast.LENGTH_SHORT).show()
            return
        }

        val totalItems = inboundItems.sumOf { it.quantity }
        
        AlertDialog.Builder(this)
            .setTitle("确认入库")
            .setMessage("确定要提交 ${inboundItems.size} 种商品，共 $totalItems 件的入库操作吗？")
            .setPositiveButton("确认入库") { _, _ ->
                performInbound()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun performInbound() {
        if (!ApiClient.isLoggedIn()) {
            Toast.makeText(this, "用户未登录，请重新登录", Toast.LENGTH_SHORT).show()
            // ... (rest of the login check)
            return
        }
        
        var userId = ApiClient.getCurrentUserId().takeIf { !it.isNullOrEmpty() } ?: "wms_user"

        btnConfirmInbound.isEnabled = false
        btnConfirmInbound.text = "入库中..."

        lifecycleScope.launch {
            val successResults = mutableListOf<String>()
            val errorMessages = mutableListOf<String>()

            for (item in inboundItems) {
                try {
                    // 智能备注：在备注中加入执行操作前的库存状态，用于调试
                    var preInboundSkuTotalQty = 0
                    var preInboundLocationQty = 0
                    item.productData?.let { product ->
                        val targetSkuInfo = product.colors?.asSequence()
                            ?.flatMap { it.sizes ?: emptyList() }
                            ?.find { it.sku_code == item.sku }
                        
                        if (targetSkuInfo != null) {
                            preInboundSkuTotalQty = targetSkuInfo.sku_total_quantity ?: 0
                            preInboundLocationQty = targetSkuInfo.locations
                                ?.find { it.location_code == item.location }
                                ?.stock_quantity ?: 0
                        }
                    }
                    val debugNotes = "PDA入库 | S-Qty:${preInboundSkuTotalQty}, L-Qty:${preInboundLocationQty}"

                    // 确保有有效的库位编码
                    val effectiveLocationCode = when {
                        item.location == "无货位" -> {
                            // 如果货位是"无货位"，使用第一个可用的真实库位
                            if (locationOptions.size > 1) {
                                locationOptions[1] // 跳过"无货位"，使用第一个真实库位
                            } else {
                                "154562" // 默认使用一个真实库位
                            }
                        }
                        item.location.isNotEmpty() && item.location != "无货位" -> item.location
                        else -> {
                            // 如果货位为空，也使用默认库位
                            if (locationOptions.size > 1) {
                                locationOptions[1]
                            } else {
                                "154562"
                            }
                        }
                    }

                    val request = InboundRequest(
                        sku_code = item.sku,
                        location_code = effectiveLocationCode,
                        inbound_quantity = item.quantity,
                        operator_id = userId,
                        batch_number = if (item.batch.isNotEmpty()) item.batch else null,
                        is_urgent = false,
                        notes = debugNotes // 使用带有库存状态的备注
                    )

                    Log.d("InboundActivity", "发送入库请求: ${Gson().toJson(request)}")
                    
                    val response = ApiClient.getApiService().inbound(request)
                    if (response.isSuccessful) {
                        val apiResponse = response.body()
                        if (apiResponse?.success == true && apiResponse.inventory != null) {
                            val result = apiResponse.inventory
                            // 构建成功的详细信息
                            val successMsg = "✅ ${result.sku_code}\n" +
                                             "   库位: ${result.location_code} (共 ${result.sku_location_quantity}件)\n" +
                                             "   SKU总库存: ${result.sku_total_quantity}件"
                            successResults.add(successMsg)
                            Log.d("InboundActivity", "✅ 入库成功: $successMsg")
                        } else {
                            val errorMsg = "❌ ${item.sku}: ${apiResponse?.error_message ?: "入库失败"}"
                            errorMessages.add(errorMsg)
                            Log.e("InboundActivity", errorMsg)
                        }
                    } else {
                        val errorBody = response.errorBody()?.string() ?: response.message()
                        val errorMsg = "❌ ${item.sku}: HTTP ${response.code()} - $errorBody"
                        errorMessages.add(errorMsg)
                        Log.e("InboundActivity", errorMsg)
                    }
                } catch (e: Exception) {
                    val errorMsg = "❌ ${item.sku}: ${e.message}"
                    errorMessages.add(errorMsg)
                    Log.e("InboundActivity", errorMsg, e)
                }
            }

            runOnUiThread {
                btnConfirmInbound.isEnabled = true
                btnConfirmInbound.text = "确认入库"

                val finalMessage = buildString {
                    if (successResults.isNotEmpty()) {
                        append("入库成功 (${successResults.size}条):\n")
                        append("--------------------\n")
                        append(successResults.joinToString("\n\n"))
                    }
                    if (errorMessages.isNotEmpty()) {
                        if (successResults.isNotEmpty()) append("\n\n")
                        append("入库失败 (${errorMessages.size}条):\n")
                        append("--------------------\n")
                        append(errorMessages.joinToString("\n"))
                    }
                }

                AlertDialog.Builder(this@InboundActivity)
                    .setTitle("入库结果")
                    .setMessage(finalMessage)
                    .setPositiveButton("确定") { _, _ ->
                        if (successResults.isNotEmpty()) {
                            // 只有在有成功条目时才清空列表
                            inboundItems.clear()
                            inboundListAdapter.notifyDataSetChanged()
                            updateItemCount()
                        }
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun updateItemCount() {
        val itemCount = inboundItems.size
        val totalQuantity = inboundItems.sumOf { it.quantity }
        
        txtInboundTitle.text = "入库商品($itemCount)"
        btnConfirmInbound.text = "确认入库"
        btnConfirmInbound.isEnabled = itemCount > 0
        
        if (itemCount > 0) {
            btnConfirmInbound.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_bright))
        } else {
            btnConfirmInbound.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(scanReceiver)
        } catch (e: Exception) {
            // 忽略异常
        }
    }

    // 🖼️ 获取最佳图片URL - 优先级：颜色图片 > 商品图片
    private fun getBestImageUrl(product: Product, skuCode: String, color: String, context: Context): String {
        Log.d("InboundActivity", "🖼️ 查找图片 (仅限颜色): 颜色=$color")
        
        // 1. 只查找并使用指定颜色的图片
        product.colors?.find { it.color == color }?.image_path?.let { path ->
            if (path.isNotEmpty()) {
                val fullUrl = processImageUrl(path, context)
                Log.d("InboundActivity", "✅ 找到颜色级图片: $fullUrl")
                return fullUrl
            }
        }
        
        // 2. 如果指定颜色没有图片路径，或路径为空，则返回空字符串
        Log.w("InboundActivity", "❌ 未找到颜色 '$color' 的有效图片路径，返回空")
        return "" // 不再回退到商品主图
    }
    
    // 🔧 处理图片URL，拼接服务器地址
    private fun processImageUrl(imagePath: String, context: Context): String {
        return if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
            imagePath
        } else {
            val baseUrl = ApiClient.getServerUrl(context)
            (baseUrl.trimEnd('/') + "/" + imagePath.trimStart('/'))
        }
    }
} 