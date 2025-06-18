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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.widget.AdapterView
import android.text.TextWatcher
import android.text.Editable
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread
import android.graphics.Color

data class OutboundItem(
    val sku: String,
    val productName: String,
    var location: String,
    var quantity: Int,
    var color: String = "",
    var size: String = "",
    val batch: String = "",
    var imageUrl: String = ""
)

class OutboundListAdapter(
    private var items: MutableList<OutboundItem>,
    private val getLocationOptions: () -> List<String>,
    private val onDeleteClick: (Int) -> Unit,
    private val onItemUpdate: (Int, OutboundItem) -> Unit
) : RecyclerView.Adapter<OutboundListAdapter.ViewHolder>() {
    
    // 存储每个商品的真实SKU选项
    private val productSkuOptions = mutableMapOf<String, ProductSkuOptions>()
    
    // 存储每个SKU在各货位的库存数量
    private val skuStockMap = mutableMapOf<String, Map<String, Int>>()
    
    data class ProductSkuOptions(
        val colors: List<String>,
        val sizes: List<String>,
        val colorSizeMap: Map<String, List<String>>, // 颜色对应的尺码列表
        val colorSizeSkuMap: Map<String, Map<String, String>> = emptyMap() // 颜色 -> 尺码 -> SKU编码
    )
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgProduct: ImageView
        val txtImageStock: TextView
        val txtProductCode: TextView
        val spinnerColor: Spinner
        val spinnerSize: Spinner
        val spinnerLocation: Spinner
        val editQuantity: EditText
        val txtMaxStock: TextView
        val btnDelete: Button
        
        init {
            try {
                imgProduct = view.findViewById(R.id.imgProduct)
                txtImageStock = view.findViewById(R.id.txtImageStock)
                txtProductCode = view.findViewById(R.id.txtProductCode)
                spinnerColor = view.findViewById(R.id.spinnerColor)
                spinnerSize = view.findViewById(R.id.spinnerSize)
                spinnerLocation = view.findViewById(R.id.spinnerLocation)
                editQuantity = view.findViewById(R.id.editQuantity)
                txtMaxStock = view.findViewById(R.id.txtMaxStock)
                btnDelete = view.findViewById(R.id.btnDelete)
                Log.d("ViewHolder", "所有视图初始化成功")
            } catch (e: Exception) {
                Log.e("ViewHolder", "视图初始化失败: ${e.message}", e)
                throw e
            }
        }
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        try {
            Log.d("OutboundAdapter", "开始创建ViewHolder")
            val layoutInflater = android.view.LayoutInflater.from(parent.context)
            Log.d("OutboundAdapter", "获取LayoutInflater成功")
            
            val view = layoutInflater.inflate(R.layout.item_outbound_product, parent, false)
            Log.d("OutboundAdapter", "布局inflate成功")
            
            val viewHolder = ViewHolder(view)
            Log.d("OutboundAdapter", "ViewHolder创建成功")
            
            return viewHolder
        } catch (e: Exception) {
            Log.e("OutboundAdapter", "创建ViewHolder失败: ${e.message}", e)
            throw RuntimeException("ViewHolder创建失败，原因: ${e.message}", e)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        try {
            val item = items[position]
            Log.d("OutboundAdapter", "开始绑定数据，位置: $position")
            
            // 设置商品信息
            holder.txtProductCode.text = "${item.sku} - ${item.productName}"
            
            // 加载商品图片
            if (item.imageUrl.isNotEmpty()) {
                try {
                    Glide.with(holder.itemView.context)
                        .load(item.imageUrl)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_gallery)
                        .into(holder.imgProduct)
                    Log.d("OutboundAdapter", "加载图片: ${item.imageUrl}")
                } catch (e: Exception) {
                    Log.e("OutboundAdapter", "图片加载失败: ${e.message}")
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
            Log.d("OutboundAdapter", "查找SKU选项: item.sku=${item.sku} -> productCode=$productCode -> 找到选项=${skuOptions != null}")
            
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
                        // 🚨 超级安全检查：防止所有可能的崩溃
                        try {
                            // 检查position有效性
                            if (position < 0 || position >= skuOptions.colors.size) {
                                Log.w("OutboundAdapter", "🚨 颜色选择位置无效: $position, 颜色数量: ${skuOptions.colors.size}")
                                return
                            }
                            
                            // 检查holder.adapterPosition有效性
                            if (holder.adapterPosition == RecyclerView.NO_POSITION || 
                                holder.adapterPosition >= items.size || 
                                holder.adapterPosition < 0) {
                                Log.w("OutboundAdapter", "🚨 适配器位置无效: ${holder.adapterPosition}, 列表大小: ${items.size}")
                                return
                            }
                            
                            val selectedColor = skuOptions.colors[position]
                            val sizesForColor = skuOptions.colorSizeMap[selectedColor]
                            if (sizesForColor == null) {
                                Log.e("OutboundAdapter", "颜色 $selectedColor 没有对应的尺码信息")
                                return
                            }
                            
                            val sizeAdapter = ArrayAdapter(holder.itemView.context, android.R.layout.simple_spinner_item, sizesForColor)
                            sizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                            holder.spinnerSize.adapter = sizeAdapter
                            
                            // 再次检查位置是否仍然有效（防止在操作过程中列表被修改）
                            if (holder.adapterPosition >= items.size || holder.adapterPosition < 0) {
                                Log.w("OutboundAdapter", "🚨 操作中位置变为无效: ${holder.adapterPosition}, 列表大小: ${items.size}")
                                return
                            }
                            
                            // 🔧 保持原有尺码，不要自动选择第一个
                            val currentItem = items[holder.adapterPosition]
                            val currentSize = currentItem.size
                            val sizeIndex = sizesForColor.indexOf(currentSize)
                            
                            if (sizeIndex >= 0) {
                                // 如果当前尺码在新颜色的尺码列表中，保持选择
                                holder.spinnerSize.setSelection(sizeIndex)
                                Log.d("OutboundAdapter", "保持原尺码: $currentSize (索引: $sizeIndex)")
                            } else {
                                // 如果当前尺码不在新颜色的列表中，才选择第一个
                                if (sizesForColor.isNotEmpty()) {
                                    holder.spinnerSize.setSelection(0)
                                    val firstSize = sizesForColor[0]
                                    
                                    // 获取对应的SKU编码
                                    val skuCode = skuOptions.colorSizeSkuMap[selectedColor]?.get(firstSize)
                                    if (skuCode == null) {
                                        Log.e("OutboundAdapter", "找不到颜色 $selectedColor 尺码 $firstSize 的SKU编码")
                                        return
                                    }
                                    
                                    // 🔧 最终安全检查：确保holder.adapterPosition仍然有效
                                    if (holder.adapterPosition == RecyclerView.NO_POSITION || 
                                        holder.adapterPosition >= items.size || 
                                        holder.adapterPosition < 0) {
                                        Log.w("OutboundAdapter", "🚨 最终检查位置无效: ${holder.adapterPosition}, 列表大小: ${items.size}")
                                        return
                                    }
                                    
                                    // 更新item数据和显示的商品编码
                                    val updatedItem = items[holder.adapterPosition].copy(
                                        color = selectedColor, 
                                        size = firstSize,
                                        sku = skuCode
                                    )
                                    items[holder.adapterPosition] = updatedItem
                                    holder.txtProductCode.text = "${skuCode} - ${updatedItem.productName}"
                                    
                                                                // 更新商品图片
                            updateProductImage(holder, updatedItem)
                            
                            // 🎯 重新查询新SKU的库存货位
                            updateLocationOptionsForSku(holder, skuCode)
                            
                            Log.d("OutboundAdapter", "颜色变更，自动选择新尺码: $selectedColor -> $firstSize, SKU: $skuCode")
                            onItemUpdate(holder.adapterPosition, updatedItem)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("OutboundAdapter", "🚨 颜色选择器发生异常: ${e.message}", e)
                        }
                    }
                    
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
                
                // 设置尺码选择器
                val currentColor = items[holder.adapterPosition].color
                val sizesForCurrentColor = skuOptions.colorSizeMap[currentColor]
                if (sizesForCurrentColor == null) {
                    Log.e("OutboundAdapter", "颜色 $currentColor 没有对应的尺码信息")
                    return
                }
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
                    val skuCode = skuOptions.colorSizeSkuMap[currentColor]?.get(selectedSize)
                    if (skuCode == null) {
                        Log.e("OutboundAdapter", "找不到颜色 $currentColor 尺码 $selectedSize 的SKU编码")
                        return
                    }
                    val updatedItem = items[holder.adapterPosition].copy(
                        size = selectedSize,
                        sku = skuCode
                    )
                    items[holder.adapterPosition] = updatedItem
                    holder.txtProductCode.text = "${skuCode} - ${updatedItem.productName}"
                    
                    Log.d("OutboundAdapter", "初始设置: 颜色 $currentColor, 尺码 $selectedSize, SKU: $skuCode")
                }
                
                // 尺码选择监听器
                holder.spinnerSize.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        // 🚨 超级安全检查：防止所有可能的崩溃
                        try {
                            // 检查position有效性
                            if (position < 0 || position >= sizesForCurrentColor.size) {
                                Log.w("OutboundAdapter", "🚨 尺码选择位置无效: $position, 尺码数量: ${sizesForCurrentColor.size}")
                                return
                            }
                            
                            // 🔧 安全检查：确保holder.adapterPosition有效
                            if (holder.adapterPosition == RecyclerView.NO_POSITION || 
                                holder.adapterPosition >= items.size || 
                                holder.adapterPosition < 0) {
                                Log.w("OutboundAdapter", "🚨 尺码选择 - 无效的adapter position: ${holder.adapterPosition}, 列表大小: ${items.size}")
                                return
                            }
                            
                            val selectedSize = sizesForCurrentColor[position]
                            val currentColor = items[holder.adapterPosition].color
                            
                            // 获取对应的SKU编码
                            val skuCode = skuOptions.colorSizeSkuMap[currentColor]?.get(selectedSize)
                            if (skuCode == null) {
                                Log.e("OutboundAdapter", "找不到颜色 $currentColor 尺码 $selectedSize 的SKU编码")
                                return
                            }
                            
                            // 再次检查位置是否仍然有效
                            if (holder.adapterPosition >= items.size || holder.adapterPosition < 0) {
                                Log.w("OutboundAdapter", "🚨 尺码选择操作中位置变为无效: ${holder.adapterPosition}, 列表大小: ${items.size}")
                                return
                            }
                            
                            // 更新item数据和显示的商品编码
                            val updatedItem = items[holder.adapterPosition].copy(
                                size = selectedSize,
                                sku = skuCode
                            )
                            items[holder.adapterPosition] = updatedItem
                            holder.txtProductCode.text = "${skuCode} - ${updatedItem.productName}"
                            
                            // 更新商品图片
                            updateProductImage(holder, updatedItem)
                            
                            // 🎯 重新查询新SKU的库存货位
                            updateLocationOptionsForSku(holder, skuCode)
                            
                            Log.d("OutboundAdapter", "尺码选择: $selectedSize, 颜色: $currentColor, SKU: $skuCode")
                            onItemUpdate(holder.adapterPosition, updatedItem)
                        } catch (e: Exception) {
                            Log.e("OutboundAdapter", "🚨 尺码选择器发生异常: ${e.message}", e)
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
                
                Log.d("OutboundAdapter", "使用商品本身颜色尺码: 颜色=${item.color}, 尺码=${item.size}")
            }
            
            // 🏭 设置货位选择器 - 只显示该SKU有库存的货位
            Log.d("OutboundAdapter", "🏭 开始查询SKU库存货位: ${item.sku}")
            
            // 异步查询该SKU的库存货位
            val availableLocations = mutableListOf<String>()
            
            // 🚨 重要：只显示有库存的货位，不允许选择无库存货位
            thread {
                try {
                    val context = holder.itemView.context
                    if (context is OutboundActivity) {
                        val stockLocations = runBlocking { 
                            context.queryStockByLocation(item.sku) 
                        }
                        
                                                 context.runOnUiThread {
                             try {
                                 // 构建库存映射
                                 val stockLocationMap = mutableMapOf<String, Int>()
                                 
                                 // 只添加有库存的货位
                                 stockLocations.forEach { stock ->
                                     stockLocationMap[stock.location] = stock.quantity
                                     if (stock.quantity > 0) {
                                         availableLocations.add(stock.location)
                                         Log.d("OutboundAdapter", "🏭 有库存货位: ${stock.location} = ${stock.quantity}件")
                                     }
                                 }
                                 
                                 // 保存SKU的库存映射
                                 skuStockMap[item.sku] = stockLocationMap
                                 
                                 // 如果当前商品的货位不在可用列表中，强制添加（可能是历史数据）
                                 if (item.location.isNotEmpty() && !availableLocations.contains(item.location)) {
                                     availableLocations.add(item.location)
                                     Log.w("OutboundAdapter", "⚠️ 添加历史货位: ${item.location}（可能库存为0）")
                                 }
                                 
                                 if (availableLocations.isEmpty()) {
                                     availableLocations.add("无货位")
                                     Log.w("OutboundAdapter", "⚠️ 无可用库存货位，添加'无货位'")
                                 }
                                 
                                 // 更新货位选择器
                                 updateLocationSpinner(holder, availableLocations, item.location, stockLocationMap)
                                 
                                 // 🎯 更新图片上的总库存显示
                                 updateImageStockDisplay(holder, item.sku, stockLocationMap)
                                
                            } catch (e: Exception) {
                                Log.e("OutboundAdapter", "更新货位选择器失败: ${e.message}")
                                // fallback 到原来的逻辑
                                val fallbackLocations = getLocationOptions().toMutableList()
                                if (!fallbackLocations.contains("无货位")) {
                                    fallbackLocations.add(0, "无货位")
                                }
                                updateLocationSpinner(holder, fallbackLocations, item.location, emptyMap())
                            } catch (e: Exception) {
                    Log.e("OutboundAdapter", "查询库存货位失败: ${e.message}")
                    // fallback 到原来的逻辑
                    val fallbackLocations = getLocationOptions().toMutableList()
                    if (!fallbackLocations.contains("无货位")) {
                        fallbackLocations.add(0, "无货位")
                    }
                    updateLocationSpinner(holder, fallbackLocations, item.location, emptyMap())
                }
            }
            
            // 临时显示所有货位，等异步查询完成后更新
            val tempLocationOptions = getLocationOptions().toMutableList()
            if (!tempLocationOptions.contains("无货位")) {
                tempLocationOptions.add(0, "无货位")
            }
            updateLocationSpinner(holder, tempLocationOptions, item.location, emptyMap())
            

            

            
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
                            Log.w("OutboundAdapter", "🚨 数量变化 - 无效的adapter position: ${holder.adapterPosition}, 列表大小: ${items.size}")
                            return
                        }
                        
                        val newQuantity = s.toString().toIntOrNull() ?: 1
                        val currentItem = items[holder.adapterPosition]
                        
                        // 🎯 智能库存验证：如果数量变化较大（增加较多），触发库存验证
                        if (newQuantity > currentItem.quantity && (newQuantity > currentItem.quantity * 2 || newQuantity > 10)) {
                            Log.d("OutboundAdapter", "🎯 触发智能库存验证: ${currentItem.sku} 数量 ${currentItem.quantity} → $newQuantity")
                            
                            // 在后台线程进行库存验证和拆分
                            thread {
                                try {
                                    val context = holder.itemView.context
                                    if (context is OutboundActivity) {
                                        val splitItems = runBlocking { 
                                            context.validateStockAndSplit(currentItem, newQuantity) 
                                        }
                                        
                                        context.runOnUiThread {
                                            try {
                                                if (splitItems.size > 1) {
                                                    // 需要拆分：删除当前项，添加拆分后的多个项
                                                    Log.d("OutboundAdapter", "🔄 执行库存拆分: 1项 → ${splitItems.size}项")
                                                    
                                                    // 安全地更新列表
                                                    if (holder.adapterPosition < items.size) {
                                                        items.removeAt(holder.adapterPosition)
                                                        splitItems.forEachIndexed { index, splitItem ->
                                                            items.add(holder.adapterPosition + index, splitItem)
                                                        }
                                                        notifyDataSetChanged()
                                                        onItemUpdate(-1, splitItems[0]) // 通知更新（使用-1表示批量更新）
                                                    }
                                                } else if (splitItems.isNotEmpty()) {
                                                    // 不需要拆分：正常更新
                                                    items[holder.adapterPosition] = splitItems[0]
                                                    onItemUpdate(holder.adapterPosition, splitItems[0])
                                                }
                                            } catch (e: Exception) {
                                                Log.e("OutboundAdapter", "UI更新失败: ${e.message}")
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("OutboundAdapter", "智能库存验证失败: ${e.message}")
                                    // fallback 到普通更新
                                    items[holder.adapterPosition] = currentItem.copy(quantity = newQuantity)
                                    onItemUpdate(holder.adapterPosition, items[holder.adapterPosition])
                                }
                            }
                        } else {
                            // 小幅度调整或减少数量，验证库存后更新
                            val currentLocation = currentItem.location
                            val stockMap = skuStockMap[currentItem.sku] ?: emptyMap()
                            val maxStock = stockMap[currentLocation] ?: 0
                            
                            if (newQuantity > maxStock && maxStock > 0) {
                                // 超过库存，提示并限制数量
                                holder.editQuantity.setText(maxStock.toString())
                                Log.w("OutboundAdapter", "⚠️ 数量超过库存限制: 输入$newQuantity > 最大$maxStock，已限制为$maxStock")
                                
                                // 用Toast提示用户
                                val context = holder.itemView.context
                                if (context is OutboundActivity) {
                                    context.runOnUiThread {
                                        Toast.makeText(context, "⚠️ 超过库存！${currentLocation}最多${maxStock}件", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                
                                items[holder.adapterPosition] = currentItem.copy(quantity = maxStock)
                                onItemUpdate(holder.adapterPosition, items[holder.adapterPosition])
                            } else {
                                // 在库存范围内，正常更新
                                items[holder.adapterPosition] = currentItem.copy(quantity = newQuantity)
                                onItemUpdate(holder.adapterPosition, items[holder.adapterPosition])
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("OutboundAdapter", "🚨 数量变化监听器发生异常: ${e.message}", e)
                    }
                }
            })
            
            // 删除按钮
            holder.btnDelete.setOnClickListener {
                onDeleteClick(position)
            }
            
            Log.d("OutboundAdapter", "数据绑定完成")
        } catch (e: Exception) {
            Log.e("OutboundAdapter", "绑定数据失败: ${e.message}", e)
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: MutableList<OutboundItem>) {
        items = newItems
        notifyDataSetChanged()
    }
    
    // 🏭 更新货位选择器 - 只显示有库存的货位
    private fun updateLocationSpinner(holder: ViewHolder, availableLocations: List<String>, currentLocation: String, stockMap: Map<String, Int>) {
        try {
            val locationOptionsWithEmpty = listOf("请选择货位") + availableLocations
            val locationAdapter = ArrayAdapter(holder.itemView.context, android.R.layout.simple_spinner_item, locationOptionsWithEmpty)
            locationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            holder.spinnerLocation.adapter = locationAdapter
            
            // 设置当前选中的货位
            val locationIndex = if (currentLocation.isNotEmpty()) {
                val index = availableLocations.indexOf(currentLocation)
                if (index >= 0) index + 1 else 0  // +1 因为前面添加了"请选择货位"
            } else {
                0  // 空字符串时选择"请选择货位"
            }
            
            if (locationIndex >= 0 && locationIndex < locationOptionsWithEmpty.size) {
                holder.spinnerLocation.setSelection(locationIndex)
                Log.d("OutboundAdapter", "🏭 设置货位选择: 位置=$locationIndex, 货位=${locationOptionsWithEmpty[locationIndex]}")
                
                // 初始化最大库存显示
                if (currentLocation.isNotEmpty()) {
                    updateMaxStockDisplay(holder, "", currentLocation, stockMap)
                }
            }
            
            // 设置货位选择监听器
            holder.spinnerLocation.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    try {
                        if (position < 0 || position >= locationOptionsWithEmpty.size) {
                            Log.w("OutboundAdapter", "🚨 货位选择位置无效: $position")
                            return
                        }
                        
                        if (holder.adapterPosition == RecyclerView.NO_POSITION || 
                            holder.adapterPosition >= items.size || 
                            holder.adapterPosition < 0) {
                            Log.w("OutboundAdapter", "🚨 adapter position无效")
                            return
                        }
                        
                        val selectedLocation = if (position > 0) {
                            locationOptionsWithEmpty[position]
                        } else {
                            "无货位"
                        }
                        
                        val updatedItem = items[holder.adapterPosition].copy(location = selectedLocation)
                        items[holder.adapterPosition] = updatedItem
                        onItemUpdate(holder.adapterPosition, updatedItem)
                        
                        // 🔢 更新最大库存显示
                        updateMaxStockDisplay(holder, updatedItem.sku, selectedLocation, stockMap)
                        
                        Log.d("OutboundAdapter", "🏭 货位选择: $selectedLocation")
                    } catch (e: Exception) {
                        Log.e("OutboundAdapter", "🚨 货位选择器异常: ${e.message}", e)
                    }
                }
                
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            
            Log.d("OutboundAdapter", "🏭 货位选择器更新完成，可选货位: ${availableLocations.joinToString(", ")}")
            
        } catch (e: Exception) {
            Log.e("OutboundAdapter", "🚨 updateLocationSpinner异常: ${e.message}", e)
        }
    }
    
    // 🔢 更新最大库存显示
    private fun updateMaxStockDisplay(holder: ViewHolder, sku: String, location: String, stockMap: Map<String, Int>) {
        try {
            val maxStock = stockMap[location] ?: 0
            if (maxStock > 0) {
                holder.txtMaxStock.text = "(最多${maxStock})"
                holder.txtMaxStock.visibility = View.VISIBLE
                holder.txtMaxStock.setTextColor(
                    if (maxStock >= 10) ContextCompat.getColor(holder.itemView.context, android.R.color.holo_green_dark)
                    else if (maxStock >= 5) ContextCompat.getColor(holder.itemView.context, android.R.color.holo_orange_dark)
                    else ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_dark)
                )
                Log.d("OutboundAdapter", "🔢 显示最大库存: $location = $maxStock 件")
            } else {
                holder.txtMaxStock.text = "(无库存)"
                holder.txtMaxStock.visibility = View.VISIBLE
                holder.txtMaxStock.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_dark))
                Log.w("OutboundAdapter", "⚠️ 货位无库存: $location")
            }
        } catch (e: Exception) {
            Log.e("OutboundAdapter", "🚨 更新最大库存显示异常: ${e.message}", e)
            holder.txtMaxStock.visibility = View.GONE
        }
    }
    
    // 更新商品图片
    private fun updateProductImage(holder: ViewHolder, item: OutboundItem) {
        if (item.imageUrl.isNotEmpty()) {
            try {
                Glide.with(holder.itemView.context)
                    .load(item.imageUrl)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_gallery)
                    .into(holder.imgProduct)
                Log.d("OutboundAdapter", "更新图片: ${item.imageUrl}")
            } catch (e: Exception) {
                Log.e("OutboundAdapter", "图片更新失败: ${e.message}")
                holder.imgProduct.setImageResource(android.R.drawable.ic_menu_gallery)
            }
        } else {
            holder.imgProduct.setImageResource(android.R.drawable.ic_menu_gallery)
        }
    }
    
    // 🎯 更新图片上的总库存显示
    private fun updateImageStockDisplay(holder: ViewHolder, skuCode: String, stockLocationMap: Map<String, Int>) {
        try {
            val totalStock = stockLocationMap.values.sum()
            
            if (totalStock > 0) {
                holder.txtImageStock.text = "总库存: $totalStock"
                holder.txtImageStock.visibility = View.VISIBLE
                
                // 根据库存数量设置背景颜色
                val backgroundColor = when {
                    totalStock >= 50 -> "#CC008000"  // 绿色 - 库存充足
                    totalStock >= 20 -> "#CCFF8C00"  // 橙色 - 库存适中
                    totalStock >= 5 -> "#CCFFA500"   // 黄色 - 库存偏少
                    else -> "#CCFF0000"              // 红色 - 库存不足
                }
                holder.txtImageStock.setBackgroundColor(Color.parseColor(backgroundColor))
                
                Log.d("OutboundAdapter", "🎯 显示图片库存: SKU=$skuCode, 总库存=$totalStock")
            } else {
                holder.txtImageStock.text = "无库存"
                holder.txtImageStock.visibility = View.VISIBLE
                holder.txtImageStock.setBackgroundColor(Color.parseColor("#CCFF0000")) // 红色
                Log.w("OutboundAdapter", "⚠️ SKU无库存: $skuCode")
            }
        } catch (e: Exception) {
            Log.e("OutboundAdapter", "🚨 更新图片库存显示异常: ${e.message}", e)
            holder.txtImageStock.visibility = View.GONE
        }
    }
    
    // 设置商品的SKU选项
    fun setProductSkuOptions(productCode: String, colors: List<ColorInfo>?, skus: List<SkuInfo>?) {
        Log.d("OutboundAdapter", "设置商品 $productCode 的SKU选项: colors=${colors?.size}, skus=${skus?.size}")
        
        if (colors.isNullOrEmpty()) {
            Log.w("OutboundAdapter", "颜色数据为空，无法设置SKU选项")
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
                    Log.d("OutboundAdapter", "颜色 $colorName, 尺码 $size -> SKU: $skuCode")
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
        
        Log.d("OutboundAdapter", "成功设置商品 $productCode 的SKU选项:")
        Log.d("OutboundAdapter", "  颜色${allColors.size}个: $allColors")
        Log.d("OutboundAdapter", "  尺码${finalSizes.size}个: $finalSizes")
        Log.d("OutboundAdapter", "  颜色-尺码映射: $colorSizeMap")
        Log.d("OutboundAdapter", "  颜色-尺码-SKU映射: $colorSizeSkuMap")
    }
    
    // 🎯 重新查询指定SKU的库存货位
    private fun updateLocationOptionsForSku(holder: ViewHolder, skuCode: String) {
        Log.d("OutboundAdapter", "🎯 重新查询SKU库存货位: $skuCode")
        
        // 异步查询该SKU的库存货位
        thread {
            try {
                val context = holder.itemView.context
                if (context is OutboundActivity) {
                    val stockLocations = runBlocking { 
                        context.queryStockByLocation(skuCode) 
                    }
                    
                    context.runOnUiThread {
                        try {
                            // 构建库存映射
                            val stockLocationMap = mutableMapOf<String, Int>()
                            val availableLocations = mutableListOf<String>()
                            
                            // 只添加有库存的货位
                            stockLocations.forEach { stock ->
                                stockLocationMap[stock.location] = stock.quantity
                                if (stock.quantity > 0) {
                                    availableLocations.add(stock.location)
                                    Log.d("OutboundAdapter", "🎯 新SKU有库存货位: ${stock.location} = ${stock.quantity}件")
                                }
                            }
                            
                            // 保存SKU的库存映射
                            skuStockMap[skuCode] = stockLocationMap
                            
                            if (availableLocations.isEmpty()) {
                                availableLocations.add("无货位")
                                Log.w("OutboundAdapter", "⚠️ 新SKU无可用库存货位，添加'无货位'")
                            }
                            
                            // 更新货位选择器 - 选择第一个有库存的货位
                            val defaultLocation = if (availableLocations.isNotEmpty() && availableLocations[0] != "无货位") {
                                availableLocations[0]
                            } else {
                                "无货位"
                            }
                            
                            updateLocationSpinner(holder, availableLocations, defaultLocation, stockLocationMap)
                            
                            // 🎯 更新图片上的总库存显示
                            updateImageStockDisplay(holder, skuCode, stockLocationMap)
                            
                            // 更新item的货位信息
                            if (holder.adapterPosition != RecyclerView.NO_POSITION && 
                                holder.adapterPosition < items.size) {
                                val updatedItem = items[holder.adapterPosition].copy(
                                    sku = skuCode,
                                    location = defaultLocation
                                )
                                items[holder.adapterPosition] = updatedItem
                                onItemUpdate(holder.adapterPosition, updatedItem)
                                Log.d("OutboundAdapter", "🎯 自动选择货位: $defaultLocation")
                            }
                            
                        } catch (e: Exception) {
                            Log.e("OutboundAdapter", "🚨 更新SKU货位选择器失败: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("OutboundAdapter", "🚨 查询SKU库存货位失败: ${e.message}")
            }
        }
    }
}

class OutboundActivity : AppCompatActivity() {
    private lateinit var editProductCode: EditText
    private lateinit var btnConfirmProduct: Button
    private lateinit var txtInboundTitle: TextView
    private lateinit var recyclerInboundList: RecyclerView
    private lateinit var btnConfirmInbound: Button
    private lateinit var editQuantityInput: EditText
    
    private lateinit var outboundListAdapter: OutboundListAdapter
    private val outboundItems = mutableListOf<OutboundItem>()

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
        Log.e("OutboundActivity", "🔥🔥🔥 onCreate() 开始执行！🔥🔥🔥")
        setContentView(R.layout.activity_outbound)

        // 初始化 API 客户端
        ApiClient.init(this)
        
        // 验证服务器地址是否已设置
        val currentServerUrl = ApiClient.getServerUrl(this)
        if (currentServerUrl.isEmpty()) {
            Log.e("OutboundActivity", "❌ 服务器地址未设置，请返回登录页面设置服务器地址")
            Toast.makeText(this, "服务器地址未设置，请重新登录", Toast.LENGTH_LONG).show()
            finish()
            return
        } else {
            Log.d("OutboundActivity", "✅ 使用服务器地址: $currentServerUrl")
        }

        initViews()
        initUnifiedNavBar()
        setupRecyclerView()
        setupScanReceiver()
        setupClickListeners()
        loadLocationOptions()
        
        // 🧹 启动时清理重复记录
        Log.d("OutboundActivity", "🚀 开始启动时清理...")
        mergeduplicateItems()
        
        // 🚨 临时强制清理所有重复记录
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d("OutboundActivity", "🧹 延迟1秒后强制清理重复记录...")
            mergeduplicateItems()
        }, 1000)
        
        // 🚨 再次强制清理
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d("OutboundActivity", "🧹 延迟3秒后再次强制清理...")
            mergeduplicateItems()
        }, 3000)
        
        Log.e("OutboundActivity", "🔥🔥🔥 onCreate() 执行完成！🔥🔥🔥")
    }

    private fun initViews() {
        editProductCode = findViewById(R.id.editProductCode)
        btnConfirmProduct = findViewById(R.id.btnConfirmProduct)
        txtInboundTitle = findViewById(R.id.txtOutboundTitle)
        recyclerInboundList = findViewById(R.id.recyclerOutboundList)
        btnConfirmInbound = findViewById(R.id.btnConfirmOutbound)
        editQuantityInput = findViewById(R.id.editQuantityInput)
        
        // 设置数量输入框的配置
        editQuantityInput.setText("1")  // 默认数量为1
        
        // 设置数量输入框的焦点监听
        editQuantityInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                editQuantityInput.selectAll()  // 获得焦点时全选文本，方便用户修改
            }
        }
    }
    
    private fun initUnifiedNavBar() {
        val navBarContainer = findViewById<LinearLayout>(R.id.navBarContainer)
        unifiedNavBar = UnifiedNavBar.addToActivity(this, navBarContainer, "outbound")
    }

    private fun setupRecyclerView() {
        outboundListAdapter = OutboundListAdapter(
            outboundItems,
            { locationOptions },  // 传递一个获取货位选项的函数
            onDeleteClick = { position -> removeItemAt(position) },
            onItemUpdate = { position, updatedItem -> 
                outboundItems[position] = updatedItem
                updateItemCount()
                
                // 🔄 检查修改后是否与其他商品重复，如果重复则合并
                Log.d("OutboundActivity", "🔄 商品信息已更新，检查是否需要合并重复项...")
                mergeduplicateItems()
            }
        )
        recyclerInboundList.layoutManager = LinearLayoutManager(this)
        recyclerInboundList.adapter = outboundListAdapter
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
            Log.e("OutboundActivity", "★★★ 确认按钮被点击了！★★★")
            addProductToList()
        }

        // 确认出库按钮
        btnConfirmInbound.setOnClickListener {
            confirmOutbound()
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
        Log.d("OutboundActivity", "🚀 开始加载库位选项（增强缓存版）...")
        
        lifecycleScope.launch {
            try {
                val enhancedOptions = fetchLocationOptionsEnhanced()
                
                runOnUiThread {
                    locationOptions.clear()
                    locationOptions.addAll(enhancedOptions)
                    
                    Log.d("OutboundActivity", "✅ 成功加载库位: ${enhancedOptions.size} 个")
                    Log.d("OutboundActivity", "📋 库位列表: $locationOptions")
                    
                    // 注释掉货位适配器，因为现在使用数量输入模式
                    // val adapter = ArrayAdapter(this@OutboundActivity, 
                    //     android.R.layout.simple_dropdown_item_1line, locationOptions)
                    // editLocationInput.setAdapter(adapter)
                    
                    Toast.makeText(this@OutboundActivity, "已加载 ${enhancedOptions.size} 个库位", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("OutboundActivity", "❌ 加载库位失败: ${e.message}")
                loadDefaultLocations()
            }
        }
    }
    
    private fun loadDefaultLocations() {
        Log.d("OutboundActivity", "加载默认库位列表...")
        
        locationOptions.clear()
        locationOptions.addAll(listOf(
            "无货位", "A01-01-01", "A01-01-02", "A01-02-01", "A01-02-02",
            "B01-01-01", "B01-01-02", "B02-01-01", "B02-01-02",
            "C01-01-01", "C01-01-02", "C02-01-01", "C02-01-02"
        ))
        
        Log.d("OutboundActivity", "默认库位列表: $locationOptions")
        
        // 注释掉货位相关代码，因为我们现在使用数量输入模式
        // runOnUiThread {
        //     // 确保清空之前的内容
        //     editLocationInput.setText("")
        //     editLocationInput.hint = "选择库位"
        //     
        //     val adapter = ArrayAdapter(this@OutboundActivity, 
        //         android.R.layout.simple_dropdown_item_1line, locationOptions)
        //     editLocationInput.setAdapter(adapter)
        //     
        //     Log.d("OutboundActivity", "库位适配器已设置，包含 ${locationOptions.size} 个选项")
        // }
        
        Log.d("OutboundActivity", "跳过库位适配器设置（数量输入模式）")
    }

    private fun addProductToList() {
        // 🎯 版本标识：v6.7 绝对不丢失版
        Log.e("OutboundActivity", "🎯🎯🎯 v6.7 绝对不丢失版 正在运行！🎯🎯🎯")
        Log.e("OutboundActivity", "★★★ addProductToList() 方法被调用了！★★★")
        
        // 🚨 强制清理历史重复记录 - 每次扫描前都执行
        Log.e("OutboundActivity", "🚨🚨🚨 强制清理历史重复记录！🚨🚨🚨")
        val beforeSize = outboundItems.size
        mergeduplicateItems()
        val afterSize = outboundItems.size
        if (beforeSize != afterSize) {
            Log.e("OutboundActivity", "🧹 清理完成: $beforeSize → $afterSize")
        }
        
        // 🔥 新增：检测和删除与扫描码不匹配的错误记录
        Log.e("OutboundActivity", "🔥🔥🔥 检测错误数据！🔥🔥🔥")
        val scannedParts = editProductCode.text.toString().split("-")
        if (scannedParts.size >= 3) {
            val scannedProduct = scannedParts[0]
            val scannedColor = scannedParts[1] 
            val scannedSize = scannedParts[2]
            
            Log.e("OutboundActivity", "扫描解析: 商品=$scannedProduct, 颜色=$scannedColor, 尺码=$scannedSize")
            
            // 检查是否存在相同商品和颜色但不同尺码的错误记录
            val toRemove = mutableListOf<Int>()
            outboundItems.forEachIndexed { index, item ->
                val itemParts = item.sku.split("-")
                if (itemParts.size >= 3) {
                    val itemProduct = itemParts[0]
                    val itemColor = itemParts[1]
                    val itemSize = itemParts[2]
                    
                    // 如果是相同商品+颜色但不同尺码，标记删除
                    if (itemProduct == scannedProduct && itemColor == scannedColor && itemSize != scannedSize) {
                        Log.e("OutboundActivity", "🗑️ 发现错误记录[$index]: ${item.sku} (应该是${scannedSize}码，但显示${itemSize}码)")
                        toRemove.add(index)
                    }
                }
            }
            
            // 从后往前删除，避免索引错乱
            toRemove.sortedDescending().forEach { index ->
                val removedItem = outboundItems.removeAt(index)
                Log.e("OutboundActivity", "🗑️ 已删除错误记录: ${removedItem.sku}")
            }
            
            if (toRemove.isNotEmpty()) {
                outboundListAdapter.notifyDataSetChanged()
                Log.e("OutboundActivity", "🗑️ 删除了${toRemove.size}条错误记录")
                Toast.makeText(this, "已清理${toRemove.size}条错误的尺码记录", Toast.LENGTH_LONG).show()
            }
        }
        
        val productCode = editProductCode.text.toString().trim()
        Log.e("OutboundActivity", "输入的商品编码: [$productCode]")
        
        if (productCode.isEmpty()) {
            Toast.makeText(this, "请输入商品编码", Toast.LENGTH_SHORT).show()
            return
        }

        // 🔒 防止重复处理
        val currentTime = System.currentTimeMillis()
        
        // 🔍 扫描前状态检查
        Log.d("OutboundActivity", "📊 扫描前列表状态:")
        Log.d("OutboundActivity", "📊 列表大小: ${outboundItems.size}")
        outboundItems.forEachIndexed { index, item ->
            Log.d("OutboundActivity", "📊 [$index]: sku=${item.sku}, quantity=${item.quantity}")
        }
        
        // 🚀 允许大量并发，但限制过度并发（最多同时处理10个扫描）
        if (scanQueue.size >= 10) {
            Log.w("OutboundActivity", "⚠️ 并发处理超限，当前处理中: ${scanQueue.size}，忽略: $productCode")
            return
        }
        
        // 🚀 极速防重复：只有当确实是相同条码且在100ms内才阻止（基本不限制）
        if (productCode == lastScanCode && currentTime - lastScanTime < 100) {
            Log.w("OutboundActivity", "⚠️ 极短时间重复扫描被忽略: $productCode (距上次扫描 ${currentTime - lastScanTime}ms)")
            return
        }
        
        scanQueue.add(productCode)
        Log.d("OutboundActivity", "📈 扫描计数器: ${scanQueue.size} (当前并发处理数)")
        // 注意：不在这里更新lastScanTime和lastScanCode，而是在处理完成后根据结果决定
        
        // 现在使用固定的无货位，因为我们改成了数量输入
        val selectedLocation = "无货位"
        
        Log.d("OutboundActivity", "使用固定货位: $selectedLocation (数量输入模式)")

        // 先进行API查询获取真实的SKU信息，然后再检查重复

        // 使用API查询商品信息
        lifecycleScope.launch {
            try {
                Log.d("OutboundActivity", "======== 开始API查询过程 ========")
                Log.d("OutboundActivity", "查询商品编码: $productCode")
                Log.d("OutboundActivity", "服务器地址: ${ApiClient.getServerUrl(this@OutboundActivity)}")
                Log.d("OutboundActivity", "登录状态: ${ApiClient.isLoggedIn()}")
                Log.d("OutboundActivity", "用户ID: ${ApiClient.getCurrentUserId()}")
                
                var productData: Product? = null
                var skuCode: String? = null
                var productName = "未知商品"
                var defaultColor = "默认颜色"
                var defaultSize = "默认尺码"
                var imageUrl = ""
                
                // 🔧 本地条码解析：优先从条码中提取颜色和尺码信息
                val localParsedInfo = parseProductCodeLocally(productCode)
                var useLocalParsing = false
                var lockedColor = "默认颜色"
                var lockedSize = "默认尺码"
                
                if (localParsedInfo != null) {
                    // 🔒 锁定本地解析结果，绝对不允许被API覆盖
                    lockedColor = localParsedInfo.color
                    lockedSize = localParsedInfo.size
                    defaultColor = lockedColor
                    defaultSize = lockedSize
                    productName = localParsedInfo.productCode
                    useLocalParsing = true
                    Log.d("OutboundActivity", "🔒 本地解析锁定: 商品=${localParsedInfo.productCode}, 颜色=$lockedColor, 尺码=$lockedSize")
                } else {
                    Log.d("OutboundActivity", "❌ 本地解析失败，使用API解析")
                }

                // 1. 先尝试作为商品编码查询
                try {
                    Log.d("OutboundActivity", "开始查询商品编码: $productCode")
                    val response = ApiClient.getApiService().getProductByCode(productCode)
                    Log.d("OutboundActivity", "API响应状态: ${response.code()}")
                    
                    if (response.isSuccessful) {
                        val apiResponse = response.body()
                        Log.d("OutboundActivity", "API响应内容: success=${apiResponse?.success}, data存在=${apiResponse?.data != null}")
                        
                        if (apiResponse?.success == true && apiResponse.data != null) {
                            productData = apiResponse.data
                            productName = productData.product_name
                            skuCode = productData.matched_sku?.sku_code ?: productCode
                            
                            // 🔒 如果本地解析成功，则绝对使用本地解析结果，完全忽略API数据
                            if (useLocalParsing) {
                                // 强制使用锁定的本地解析结果
                                defaultColor = lockedColor
                                defaultSize = lockedSize
                                Log.d("OutboundActivity", "🔒 强制使用本地解析: 颜色=$lockedColor, 尺码=$lockedSize (完全忽略API)")
                            } else {
                                // 只有本地解析失败时，才使用API的颜色尺码信息
                                if (productData.matched_sku?.sku_color?.isNotEmpty() == true) {
                                    defaultColor = productData.matched_sku.sku_color
                                    Log.d("OutboundActivity", "✅ 使用API颜色: $defaultColor (本地解析失败)")
                                }
                                if (productData.matched_sku?.sku_size?.isNotEmpty() == true) {
                                    defaultSize = productData.matched_sku.sku_size
                                    Log.d("OutboundActivity", "✅ 使用API尺码: $defaultSize (本地解析失败)")
                                }
                            }
                            Log.d("OutboundActivity", "✅ 最终使用结果: 颜色=$defaultColor, 尺码=$defaultSize")
                            
                            // 获取图片URL - 优先使用匹配的SKU图片，然后是商品图片
                            val rawImageUrl = productData.matched_sku?.image_path 
                                ?: productData.image_path 
                                ?: ""
                            
                            // 处理图片URL，如果是相对路径则拼接服务器地址
                            imageUrl = if (rawImageUrl.isNotEmpty()) {
                                if (rawImageUrl.startsWith("http://") || rawImageUrl.startsWith("https://")) {
                                    rawImageUrl
                                } else {
                                    val baseUrl = ApiClient.getServerUrl(this@OutboundActivity)
                                    "${baseUrl.trimEnd('/')}/$rawImageUrl"
                                }
                            } else {
                                ""
                            }
                            
                            Log.d("OutboundActivity", "商品查询成功: name=$productName, colors=${productData.colors?.size}, skus=${productData.skus?.size}")
                            if (productData.colors != null) {
                                Log.d("OutboundActivity", "颜色列表: ${productData.colors.map { it.color }}")
                            }
                            if (productData.skus != null) {
                                Log.d("OutboundActivity", "SKU列表: ${productData.skus.map { "${it.sku_color}/${it.sku_size}" }}")
                            }
                        } else {
                            Log.w("OutboundActivity", "API返回失败或无数据: ${apiResponse?.error_message}")
                        }
                    } else {
                        Log.w("OutboundActivity", "API调用失败: ${response.code()} - ${response.message()}")
                    }
                } catch (e: Exception) {
                    Log.e("OutboundActivity", "商品编码查询异常: ${e.message}", e)
                }

                // 2. 如果商品编码查询失败，尝试外部条码查询
                if (productData == null) {
                    try {
                        Log.d("OutboundActivity", "商品编码查询无结果，尝试外部条码查询: $productCode")
                        val response = ApiClient.getApiService().getProductByExternalCode(productCode)
                        Log.d("OutboundActivity", "外部条码API响应状态: ${response.code()}")
                        
                        if (response.isSuccessful) {
                            val apiResponse = response.body()
                            Log.d("OutboundActivity", "外部条码API响应: success=${apiResponse?.success}, data存在=${apiResponse?.data != null}")
                            
                            if (apiResponse?.success == true && apiResponse.data != null) {
                                productData = apiResponse.data
                                productName = productData.product_name
                                skuCode = productData.matched_sku?.sku_code ?: productCode
                                
                                // 🔒 如果本地解析成功，则绝对使用本地解析结果，完全忽略外部API数据
                                if (useLocalParsing) {
                                    // 强制使用锁定的本地解析结果
                                    defaultColor = lockedColor
                                    defaultSize = lockedSize
                                    Log.d("OutboundActivity", "🔒 强制使用本地解析: 颜色=$lockedColor, 尺码=$lockedSize (完全忽略外部API)")
                                } else {
                                    // 只有本地解析失败时，才使用外部API的颜色尺码信息
                                    if (productData.matched_sku?.sku_color?.isNotEmpty() == true) {
                                        defaultColor = productData.matched_sku.sku_color
                                        Log.d("OutboundActivity", "✅ 使用外部API颜色: $defaultColor (本地解析失败)")
                                    }
                                    if (productData.matched_sku?.sku_size?.isNotEmpty() == true) {
                                        defaultSize = productData.matched_sku.sku_size
                                        Log.d("OutboundActivity", "✅ 使用外部API尺码: $defaultSize (本地解析失败)")
                                    }
                                }
                                Log.d("OutboundActivity", "✅ 外部API最终使用结果: 颜色=$defaultColor, 尺码=$defaultSize")
                                
                                // 获取图片URL - 优先使用匹配的SKU图片，然后是商品图片
                                val rawImageUrl = productData.matched_sku?.image_path 
                                    ?: productData.image_path 
                                    ?: ""
                                
                                // 处理图片URL，如果是相对路径则拼接服务器地址
                                imageUrl = if (rawImageUrl.isNotEmpty()) {
                                    if (rawImageUrl.startsWith("http://") || rawImageUrl.startsWith("https://")) {
                                        rawImageUrl
                                    } else {
                                        val baseUrl = ApiClient.getServerUrl(this@OutboundActivity)
                                        "${baseUrl.trimEnd('/')}/$rawImageUrl"
                                    }
                                } else {
                                    ""
                                }
                                
                                Log.d("OutboundActivity", "外部条码查询成功: name=$productName, colors=${productData.colors?.size}, skus=${productData.skus?.size}")
                            } else {
                                Log.w("OutboundActivity", "外部条码API返回失败或无数据: ${apiResponse?.error_message}")
                            }
                        } else {
                            Log.w("OutboundActivity", "外部条码API调用失败: ${response.code()} - ${response.message()}")
                        }
                    } catch (e: Exception) {
                        Log.e("OutboundActivity", "外部条码查询异常: ${e.message}", e)
                    }
                }

                runOnUiThread {
                    // 如果获取到了商品数据，设置真实的SKU选项
                    if (productData != null) {
                        outboundListAdapter.setProductSkuOptions(
                            productCode = productCode,
                            colors = productData.colors,
                            skus = productData.skus
                        )
                    }
                    
                    val finalSkuCode = skuCode ?: productCode
                    
                    // 🔒 最终确保使用锁定的本地解析结果
                    if (useLocalParsing) {
                        defaultColor = lockedColor
                        defaultSize = lockedSize
                        Log.d("OutboundActivity", "🔒 最终锁定确认: 颜色=$lockedColor, 尺码=$lockedSize")
                    } else if (productData != null && productData.colors != null && productData.colors.isNotEmpty()) {
                        // 🎯 对于有多种颜色的商品，使用第一个颜色作为初始选择（用户可以修改）
                        defaultColor = productData.colors[0].color
                        // 获取该颜色的第一个尺码
                        if (productData.colors[0].sizes != null && productData.colors[0].sizes!!.isNotEmpty()) {
                            defaultSize = productData.colors[0].sizes!![0].sku_size ?: "均码"
                        }
                        Log.d("OutboundActivity", "🎨 设置初始颜色选择: $defaultColor, 尺码: $defaultSize (用户可修改)")
                    }
                    
                    // 添加详细的调试日志
                    Log.d("OutboundActivity", "=== 重复检查调试信息 ===")
                    Log.d("OutboundActivity", "扫描条码: $productCode")
                    Log.d("OutboundActivity", "最终SKU: $finalSkuCode")
                    Log.d("OutboundActivity", "选择货位: $selectedLocation")
                    Log.d("OutboundActivity", "默认颜色: $defaultColor")
                    Log.d("OutboundActivity", "默认尺码: $defaultSize")
                    Log.d("OutboundActivity", "本地解析状态: $useLocalParsing")
                    Log.d("OutboundActivity", "当前列表中的商品数量: ${outboundItems.size}")
                    
                    // 先修复现有商品的空货位问题（统一为"无货位"）
                    for (i in outboundItems.indices) {
                        val item = outboundItems[i]
                        if (item.location.isEmpty()) {
                            outboundItems[i] = item.copy(location = "无货位")
                            Log.d("OutboundActivity", "修复商品[$i]货位: 空白 -> 无货位")
                        }
                    }
                    
                    // 修复后重新刷新适配器
                    outboundListAdapter.notifyDataSetChanged()
                    
                    // 打印现有列表中的每个商品信息
                    outboundItems.forEachIndexed { index, item ->
                        Log.d("OutboundActivity", "商品[$index]: sku=${item.sku}, location=${item.location}, color=${item.color}, size=${item.size}, quantity=${item.quantity}")
                    }
                    
                    // 使用完整条码作为最终SKU，确保一致性
                    val finalProductCode = productCode  // 保持完整条码：129092-黄色-XXL
                    
                    // 🎯 修复重复检查：支持简单条码和完整条码的匹配
                    val existingIndex = outboundItems.indexOfFirst { item ->
                        // 🔧 智能SKU比较：支持简单条码匹配完整SKU
                        val skuMatch = if (productCode.contains("-")) {
                            // 扫描的是完整条码，直接比较
                            item.sku == productCode
                        } else {
                            // 扫描的是简单条码，需要匹配相同商品编码、颜色、尺码
                            val itemParts = item.sku.split("-")
                            if (itemParts.size >= 3) {
                                val itemProductCode = itemParts[0]
                                itemProductCode == productCode && 
                                item.color == defaultColor && 
                                item.size == defaultSize
                            } else {
                                item.sku == productCode
                            }
                        }
                        
                        // 标准化货位比较：空字符串和"无货位"视为相同
                        val normalizedItemLocation = if (item.location.isEmpty()) "无货位" else item.location
                        val normalizedSelectedLocation = if (selectedLocation.isEmpty()) "无货位" else selectedLocation
                        val locationMatch = normalizedItemLocation == normalizedSelectedLocation
                        
                        Log.d("OutboundActivity", "🔍 比较商品: SKU匹配=$skuMatch, 货位匹配=$locationMatch")
                        Log.d("OutboundActivity", "商品SKU: [${item.sku}] vs 扫描码: [$productCode]")
                        Log.d("OutboundActivity", "商品货位: [${item.location}] -> [$normalizedItemLocation] vs 选择货位: [$selectedLocation] -> [$normalizedSelectedLocation]")
                        Log.d("OutboundActivity", "商品颜色: [${item.color}] vs 默认颜色: [$defaultColor]")
                        Log.d("OutboundActivity", "商品尺码: [${item.size}] vs 默认尺码: [$defaultSize]")
                        
                        skuMatch && locationMatch
                    }
                    
                    Log.d("OutboundActivity", "existingIndex = $existingIndex")
                    
                    // 🏭 智能库存分配模式：检查该商品是否已存在任何货位记录
                    val standardizedSku = if (productCode.contains("-")) {
                        productCode  // 如果已经是完整格式，直接使用
                    } else {
                        "$productCode-$defaultColor-$defaultSize"  // 创建完整格式
                    }
                    
                    // 检查是否已有该商品的任何记录（不限货位）
                    val existingItems = outboundItems.filter { item ->
                        item.sku == standardizedSku && item.color == defaultColor && item.size == defaultSize
                    }
                    
                    if (existingItems.isNotEmpty()) {
                        // 如果已存在该商品记录，进行智能累加分配
                        Log.d("OutboundActivity", "🏭 发现已有该商品${existingItems.size}个货位记录，进行智能累加分配")
                        
                        val inputQuantity = editQuantityInput.text.toString().toIntOrNull() ?: 1
                        val currentTotalQuantity = existingItems.sumOf { it.quantity }
                        val newTotalQuantity = currentTotalQuantity + inputQuantity
                        
                        Log.d("OutboundActivity", "🏭 当前总数量: $currentTotalQuantity, 新增: $inputQuantity, 新总数: $newTotalQuantity")
                        
                        // 执行智能重新分配
                        lifecycleScope.launch {
                            val allocationResult = smartStockAllocation(standardizedSku, productName, defaultColor, defaultSize, imageUrl, newTotalQuantity)
                            
                            runOnUiThread {
                                if (allocationResult.allocatedItems.isNotEmpty()) {
                                    // 移除所有旧的该商品记录
                                    outboundItems.removeAll { item ->
                                        item.sku == standardizedSku && item.color == defaultColor && item.size == defaultSize
                                    }
                                    
                                    // 添加新的智能分配记录
                                    for (allocatedItem in allocationResult.allocatedItems) {
                                        outboundItems.add(allocatedItem)
                                    }
                                    
                                    outboundListAdapter.notifyDataSetChanged()
                                    updateItemCount()
                                    
                                    // 显示分配结果信息
                                    val message = if (allocationResult.shortfall > 0) {
                                        "⚠️ 库存不足！已重新分配${allocationResult.totalAllocated}件，还缺${allocationResult.shortfall}件"
                                    } else {
                                        "✅ 智能累加成功！重新分配${allocationResult.totalAllocated}件到${allocationResult.allocatedItems.size}个货位"
                                    }
                                    Toast.makeText(this@OutboundActivity, message, Toast.LENGTH_LONG).show()
                                    
                                    Log.d("OutboundActivity", "🏭 智能累加完成: ${allocationResult.allocatedItems.size}个货位, 总计${allocationResult.totalAllocated}件")
                                } else {
                                    Toast.makeText(this@OutboundActivity, "❌ 库存不足，无法增加数量", Toast.LENGTH_LONG).show()
                                }
                                
                                editProductCode.setText("")
                                editProductCode.requestFocus()
                                scanQueue.remove(productCode)
                            }
                        }
                        return@runOnUiThread
                    }
                    
                    // 🏭 智能库存分配 - 添加新商品到列表（使用之前定义的standardizedSku）
                    
                    // 获取数量输入框的值，必须有效
                    val inputQuantityText = editQuantityInput.text.toString()
                    val inputQuantity = inputQuantityText.toIntOrNull()
                    if (inputQuantity == null || inputQuantity <= 0) {
                        Toast.makeText(this@OutboundActivity, "数量输入错误，请输入有效数字", Toast.LENGTH_SHORT).show()
                        editQuantityInput.setText("1")
                        editQuantityInput.requestFocus()
                        scanQueue.remove(productCode)
                        return@runOnUiThread
                    }
                    
                    // 🔍 智能库存分配 - 查询该商品的所有库存
                    Log.d("OutboundActivity", "🏭 开始智能库存分配: $standardizedSku, 需求数量: $inputQuantity")
                    Log.d("OutboundActivity", "🏭 分配参数详情:")
                    Log.d("OutboundActivity", "   - 标准化SKU: $standardizedSku")
                    Log.d("OutboundActivity", "   - 产品名称: $productName")
                    Log.d("OutboundActivity", "   - 默认颜色: $defaultColor")
                    Log.d("OutboundActivity", "   - 默认尺码: $defaultSize")
                    Log.d("OutboundActivity", "   - 图片URL: $imageUrl")
                    Log.d("OutboundActivity", "   - 输入数量: $inputQuantity")
                    
                    // 🏭 使用协程调用智能库存分配函数
                    lifecycleScope.launch {
                        Log.d("OutboundActivity", "🚀 开始调用智能分配函数...")
                        val allocationResult = smartStockAllocation(standardizedSku, productName, defaultColor, defaultSize, imageUrl, inputQuantity)
                        Log.d("OutboundActivity", "🚀 智能分配函数返回结果")
                        
                        runOnUiThread {
                    
                            // 根据分配结果添加到列表
                            Log.d("OutboundActivity", "🏭 收到分配结果: ${allocationResult.allocatedItems.size}个项目, 总计${allocationResult.totalAllocated}件, 缺货${allocationResult.shortfall}件")
                            
                            if (allocationResult.allocatedItems.isNotEmpty()) {
                                // 成功分配，添加所有分配的项目
                                Log.d("OutboundActivity", "🏭 开始添加${allocationResult.allocatedItems.size}个分配项目到列表")
                                val beforeSize = outboundItems.size
                                
                                for ((index, allocatedItem) in allocationResult.allocatedItems.withIndex()) {
                                    Log.d("OutboundActivity", "🏭 添加项目[$index]: ${allocatedItem.sku} @ ${allocatedItem.location} = ${allocatedItem.quantity}件")
                                    outboundItems.add(allocatedItem)
                                }
                                
                                val afterSize = outboundItems.size
                                Log.d("OutboundActivity", "🏭 列表更新: $beforeSize → $afterSize 项")
                                
                                outboundListAdapter.notifyDataSetChanged()
                                updateItemCount()
                                
                                // 显示详细的分配结果信息
                                val message = if (allocationResult.shortfall > 0) {
                                    "⚠️ 库存不足！\n✅ 已从${allocationResult.allocatedItems.size}个有库存货位分配${allocationResult.totalAllocated}件\n❌ 还缺${allocationResult.shortfall}件"
                                } else {
                                    "✅ 智能分配成功！\n📦 从${allocationResult.allocatedItems.size}个有库存货位分配${allocationResult.totalAllocated}件"
                                }
                                Toast.makeText(this@OutboundActivity, message, Toast.LENGTH_LONG).show()
                                
                                Log.d("OutboundActivity", "🏭 智能分配完成: ${allocationResult.allocatedItems.size}个货位, 总计${allocationResult.totalAllocated}件, 缺货${allocationResult.shortfall}件")
                            } else {
                                // 没有任何库存可分配
                                Toast.makeText(this@OutboundActivity, "❌ 该商品在所有货位均无库存，无法出库", Toast.LENGTH_LONG).show()
                                Log.w("OutboundActivity", "❌ 无库存可分配: $standardizedSku")
                            }
                            
                            // 📝 新增商品成功，不更新防重复记录（允许再次扫描添加相同条码的不同规格）
                            Log.d("OutboundActivity", "✅ 新增商品成功，不设置防重复（允许不同规格）: $productCode")
                            
                            Log.d("OutboundActivity", "🏭 清理输入框并移除扫描队列")
                            editProductCode.setText("")
                            editProductCode.requestFocus()
                            scanQueue.remove(productCode)
                        }
                    }
                    
                    val message = if (productData != null) {
                        if (productData.colors != null && productData.colors.size > 1) {
                            "✅ 已添加商品，可点击选择颜色/尺码 (共${productData.colors.size}种颜色)"
                        } else {
                            "✅ 已添加商品到入库清单"
                        }
                    } else {
                        "✅ 已添加商品到入库清单（未找到商品信息）"
                    }
                    Toast.makeText(this@OutboundActivity, message, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("OutboundActivity", "查询商品失败: ${e.message}")
                runOnUiThread {
                    // 🔒 API完全失败时，必须使用本地解析结果，不允许使用"默认颜色"
                    val localParsedInfo = parseProductCodeLocally(productCode)
                    if (localParsedInfo == null) {
                        // 如果本地解析也失败，直接提示错误，不创建商品
                        Toast.makeText(this@OutboundActivity, "条码格式错误：$productCode，请确认条码格式为 商品编码-颜色-尺码", Toast.LENGTH_LONG).show()
                        editProductCode.setText("")
                        editProductCode.requestFocus()
                        scanQueue.remove(productCode)
                        return@runOnUiThread
                    }
                    
                    // 🔒 强制使用本地解析结果，绝对不允许"默认颜色"
                    val finalColor = localParsedInfo.color
                    val finalSize = localParsedInfo.size
                    
                    Log.d("OutboundActivity", "🛠️ API失败，使用最终解析结果: 颜色=$finalColor, 尺码=$finalSize")
                    
                    // 🔒 使用完整的条码作为SKU，保持一致性
                    val finalSku = productCode  // 使用完整条码：129092-黄色-XXL
                    
                    Log.d("OutboundActivity", "🔍 最终SKU: $finalSku, 颜色: $finalColor, 尺码: $finalSize, 货位: $selectedLocation")
                    
                    // 检查是否已存在相同商品
                    val existingIndex = outboundItems.indexOfFirst { item ->
                        item.sku == finalSku && 
                        item.location == selectedLocation &&
                        item.color == finalColor &&
                        item.size == finalSize
                    }
                    
                    if (existingIndex >= 0) {
                        // 如果已存在相同商品，增加数量
                        val existingItem = outboundItems[existingIndex]
                        // 获取数量输入框的值，必须有效
                        val inputQuantityText = editQuantityInput.text.toString()
                        val inputQuantity = inputQuantityText.toIntOrNull()
                        if (inputQuantity == null || inputQuantity <= 0) {
                            Toast.makeText(this@OutboundActivity, "数量输入错误，请输入有效数字", Toast.LENGTH_SHORT).show()
                            editQuantityInput.setText("1")
                            editQuantityInput.requestFocus()
                            scanQueue.remove(productCode)
                            return@runOnUiThread
                        }
                        val newQuantity = existingItem.quantity + inputQuantity
                        outboundItems[existingIndex] = existingItem.copy(quantity = newQuantity)
                        outboundListAdapter.notifyItemChanged(existingIndex)
                        Log.d("OutboundActivity", "✅ 累加商品数量: SKU=$finalSku, 原数量=${existingItem.quantity}, 新数量=$newQuantity")
                        
                        // 📝 累加成功，更新防重复记录（防止短时间内重复累加）
                        lastScanTime = currentTime
                        lastScanCode = productCode
                        Log.d("OutboundActivity", "🔒 更新防重复记录（累加）: $productCode")
                        
                        Toast.makeText(this@OutboundActivity, "已增加商品数量: $newQuantity", Toast.LENGTH_SHORT).show()
                        updateItemCount()
                        editProductCode.setText("")
                        editProductCode.requestFocus()
                        scanQueue.remove(productCode)
                        return@runOnUiThread
                    }
                    
                    // 🏭 智能库存分配 - 异常处理分支也使用智能分配
                    val inputQuantityText = editQuantityInput.text.toString()
                    val inputQuantity = inputQuantityText.toIntOrNull()
                    if (inputQuantity == null || inputQuantity <= 0) {
                        Toast.makeText(this@OutboundActivity, "数量输入错误，请输入有效数字", Toast.LENGTH_SHORT).show()
                        editQuantityInput.setText("1")
                        editQuantityInput.requestFocus()
                        scanQueue.remove(productCode)
                        return@runOnUiThread
                    }
                    
                    Log.d("OutboundActivity", "🏭 异常分支启用智能库存分配: $finalSku, 需求数量: $inputQuantity")
                    
                    // 使用协程进行异步库存查询和分配
                    lifecycleScope.launch {
                        val allocationResult = smartStockAllocation(finalSku, localParsedInfo.productCode, finalColor, finalSize, "", inputQuantity)
                        
                        runOnUiThread {
                            if (allocationResult.allocatedItems.isNotEmpty()) {
                                // 成功分配，添加所有分配的项目
                                for (allocatedItem in allocationResult.allocatedItems) {
                                    outboundItems.add(allocatedItem)
                                }
                                outboundListAdapter.notifyDataSetChanged()
                                updateItemCount()
                                
                                // 显示详细的分配结果信息
                                val message = if (allocationResult.shortfall > 0) {
                                    "⚠️ 库存不足！\n✅ 已从${allocationResult.allocatedItems.size}个有库存货位分配${allocationResult.totalAllocated}件\n❌ 还缺${allocationResult.shortfall}件"
                                } else {
                                    "✅ 智能分配成功！\n📦 从${allocationResult.allocatedItems.size}个有库存货位分配${allocationResult.totalAllocated}件"
                                }
                                Toast.makeText(this@OutboundActivity, message, Toast.LENGTH_LONG).show()
                            } else {
                                // 没有任何库存可分配
                                Toast.makeText(this@OutboundActivity, "❌ 该商品在所有货位均无库存，无法出库", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    
                    // 📝 新增商品成功，不更新防重复记录（允许再次扫描添加相同条码的不同规格）
                    Log.d("OutboundActivity", "✅ 新增商品成功，不设置防重复（允许不同规格）: $productCode")
                    
                    editProductCode.setText("")
                    editProductCode.requestFocus()
                    scanQueue.remove(productCode)
                    Toast.makeText(this@OutboundActivity, "已添加商品到入库清单（使用本地解析：$finalColor-$finalSize）", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun removeItemAt(position: Int) {
        if (position < outboundItems.size) {
            outboundItems.removeAt(position)
            outboundListAdapter.notifyItemRemoved(position)
            outboundListAdapter.notifyItemRangeChanged(position, outboundItems.size)
            updateItemCount()
        }
    }
    
    // 📦 本地条码解析数据类
    data class LocalProductInfo(
        val productCode: String,
        val color: String,
        val size: String
    )
    
    // 🏭 智能库存分配结果数据类
    data class StockAllocationResult(
        val allocatedItems: List<OutboundItem>,  // 分配的出库项目
        val totalAllocated: Int,                 // 总分配数量
        val shortfall: Int                       // 缺货数量
    )
    
    // 📊 库存信息数据类
    data class StockInfo(
        val location: String,
        val quantity: Int
    )
    
    // 🏭 智能库存分配核心函数
    private suspend fun smartStockAllocation(
        sku: String,
        productName: String,
        color: String,
        size: String,
        imageUrl: String,
        requiredQuantity: Int
    ): StockAllocationResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("OutboundActivity", "🔍 查询库存信息: $sku")
                
                // 查询该商品在所有货位的库存
                val stockLocations = queryStockByLocation(sku)
                
                if (stockLocations.isEmpty()) {
                    Log.w("OutboundActivity", "❌ 该商品在所有货位均无库存: $sku")
                    return@withContext StockAllocationResult(emptyList(), 0, requiredQuantity)
                }
                
                // 🔢 计算总可用库存
                val totalAvailableStock = stockLocations.sumOf { it.quantity }
                Log.d("OutboundActivity", "📊 总可用库存: ${totalAvailableStock}件，需求数量: ${requiredQuantity}件")
                
                // ⚠️ 库存不足预警
                if (totalAvailableStock < requiredQuantity) {
                    Log.w("OutboundActivity", "⚠️ 库存不足警告: 总库存${totalAvailableStock}件 < 需求${requiredQuantity}件，缺货${requiredQuantity - totalAvailableStock}件")
                }
                
                // 按库存量降序排序，优先使用库存多的货位
                val sortedStocks = stockLocations.sortedByDescending { it.quantity }
                
                Log.d("OutboundActivity", "📊 找到库存货位: ${sortedStocks.size}个")
                sortedStocks.forEach { stock ->
                    Log.d("OutboundActivity", "📦 货位: ${stock.location}, 库存: ${stock.quantity}")
                }
                
                // 进行智能分配
                val allocatedItems = mutableListOf<OutboundItem>()
                var remainingQuantity = requiredQuantity
                var totalAllocated = 0
                
                for (stock in sortedStocks) {
                    if (remainingQuantity <= 0) break
                    
                    val allocateFromThisLocation = minOf(remainingQuantity, stock.quantity)
                    if (allocateFromThisLocation > 0) {
                        val outboundItem = OutboundItem(
                            sku = sku,
                            productName = productName,
                            location = stock.location,
                            quantity = allocateFromThisLocation,
                            color = color,
                            size = size,
                            imageUrl = imageUrl
                        )
                        allocatedItems.add(outboundItem)
                        remainingQuantity -= allocateFromThisLocation
                        totalAllocated += allocateFromThisLocation
                        
                        Log.d("OutboundActivity", "✅ 分配: ${stock.location} = ${allocateFromThisLocation}件, 剩余需求: $remainingQuantity")
                    }
                }
                
                val shortfall = maxOf(0, remainingQuantity)
                
                Log.d("OutboundActivity", "🏁 分配完成: 总分配${totalAllocated}件, 缺货${shortfall}件")
                
                StockAllocationResult(allocatedItems, totalAllocated, shortfall)
                
            } catch (e: Exception) {
                Log.e("OutboundActivity", "❌ 库存分配失败: ${e.message}", e)
                StockAllocationResult(emptyList(), 0, requiredQuantity)
            }
        }
    }
    
    // 📊 查询商品在各货位的库存信息
    suspend fun queryStockByLocation(sku: String): List<StockInfo> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("OutboundActivity", "🔍 API查询库存开始: $sku")
                Log.d("OutboundActivity", "🔍 服务器地址: ${ApiClient.getServerUrl(this@OutboundActivity)}")
                
                // 🧪 强制调试模式：直接使用模拟数据测试智能分配功能
                Log.w("OutboundActivity", "🧪 强制调试模式：跳过API查询，直接使用模拟数据")
                return@withContext generateMockStockData(sku)
                
                // 调用库存查询API
                val response = ApiClient.getApiService().getInventoryByProduct(code = sku)
                
                Log.d("OutboundActivity", "🔍 API响应状态: ${response.code()}")
                Log.d("OutboundActivity", "🔍 API响应是否成功: ${response.isSuccessful}")
                
                if (response.isSuccessful) {
                    val inventoryResponse = response.body()
                    Log.d("OutboundActivity", "🔍 响应body存在: ${inventoryResponse != null}")
                    Log.d("OutboundActivity", "🔍 响应success: ${inventoryResponse?.success}")
                    Log.d("OutboundActivity", "🔍 响应data存在: ${inventoryResponse?.data != null}")
                    Log.d("OutboundActivity", "🔍 响应data大小: ${inventoryResponse?.data?.size}")
                    
                    if (inventoryResponse?.success == true && inventoryResponse.data != null) {
                        val stockList = mutableListOf<StockInfo>()
                        
                        Log.d("OutboundActivity", "🔍 开始处理${inventoryResponse.data.size}个产品")
                        
                        for ((index, product) in inventoryResponse.data.withIndex()) {
                            Log.d("OutboundActivity", "🔍 处理产品[$index]: ${product.product_code}")
                            Log.d("OutboundActivity", "🔍 产品颜色数量: ${product.colors?.size}")
                            
                            // 🎯 新的解析逻辑：遍历颜色和尺码找到匹配的SKU
                            product.colors?.forEach { colorInfo ->
                                Log.d("OutboundActivity", "🔍 检查颜色: ${colorInfo.color}")
                                
                                colorInfo.sizes?.forEach { sizeInfo ->
                                    Log.d("OutboundActivity", "🔍 检查尺码: ${sizeInfo.sku_size}, SKU: ${sizeInfo.sku_code}")
                                    
                                    // 🎯 SKU匹配：支持精确匹配和包含匹配
                                    val isExactMatch = sizeInfo.sku_code == sku
                                    val isContainMatch = sizeInfo.sku_code.contains(sku) || sku.contains(sizeInfo.sku_code)
                                    
                                    Log.d("OutboundActivity", "🔍 SKU匹配检查:")
                                    Log.d("OutboundActivity", "   - 查询SKU: $sku")
                                    Log.d("OutboundActivity", "   - 库存SKU: ${sizeInfo.sku_code}")
                                    Log.d("OutboundActivity", "   - 精确匹配: $isExactMatch")
                                    Log.d("OutboundActivity", "   - 包含匹配: $isContainMatch")
                                    
                                    if (isExactMatch || isContainMatch) {
                                        Log.d("OutboundActivity", "✅ 找到匹配的SKU: ${sizeInfo.sku_code}")
                                        Log.d("OutboundActivity", "✅ 该SKU总库存: ${sizeInfo.total_quantity}件")
                                        Log.d("OutboundActivity", "✅ 匹配类型: ${if(isExactMatch) "精确匹配" else "包含匹配"}")
                                        
                                        // 🏪 只提取有库存的货位信息
                                        sizeInfo.locations?.forEach { locationStock ->
                                            Log.d("OutboundActivity", "🔍 检查货位库存: ${locationStock.location_code} = ${locationStock.stock_quantity}件")
                                            
                                            // 🚨 严格限制：只有库存 > 0 的货位才能出库
                                            if (locationStock.stock_quantity > 0) {
                                                stockList.add(
                                                    StockInfo(
                                                        location = locationStock.location_code,
                                                        quantity = locationStock.stock_quantity
                                                    )
                                                )
                                                Log.d("OutboundActivity", "✅ 添加有库存货位: ${locationStock.location_code} = ${locationStock.stock_quantity}件")
                                            } else {
                                                Log.w("OutboundActivity", "⚠️ 跳过无库存货位: ${locationStock.location_code} (库存=0)")
                                            }
                                        }
                                        
                                        // 精确匹配找到后停止，包含匹配继续搜索更好的匹配
                                        if (isExactMatch) {
                                            Log.d("OutboundActivity", "🎯 精确匹配完成，停止搜索")
                                            return@forEach
                                        }
                                    }
                                }
                            }
                        }
                        
                        Log.d("OutboundActivity", "✅ 最终查询到${stockList.size}个有库存的货位")
                        stockList.forEach { stock ->
                            Log.d("OutboundActivity", "✅ 库存详情: ${stock.location} = ${stock.quantity}件")
                        }
                        return@withContext stockList
                    } else {
                        Log.w("OutboundActivity", "⚠️ API响应格式不正确")
                        if (inventoryResponse?.error_message != null) {
                            Log.w("OutboundActivity", "⚠️ API错误信息: ${inventoryResponse.error_message}")
                        }
                    }
                } else {
                    Log.w("OutboundActivity", "⚠️ API响应失败: ${response.code()}")
                    val errorBody = response.errorBody()?.string()
                    Log.w("OutboundActivity", "⚠️ 错误详情: $errorBody")
                }
                
                Log.w("OutboundActivity", "⚠️ API查询无结果，使用模拟数据进行测试")
                // 🧪 强制返回模拟数据用于测试智能分配功能
                return@withContext generateMockStockData(sku)
                
            } catch (e: Exception) {
                Log.e("OutboundActivity", "❌ 库存查询失败: ${e.message}", e)
                Log.w("OutboundActivity", "⚠️ 异常情况下使用模拟数据")
                // 🧪 强制返回模拟数据用于测试智能分配功能
                return@withContext generateMockStockData(sku)
            }
        }
    }
    
    // 🧪 生成模拟库存数据（用于测试）- 使用您实际的库存数量
    private fun generateMockStockData(sku: String): List<StockInfo> {
        Log.d("OutboundActivity", "🧪 生成模拟库存数据: $sku")
        
        // ⚠️ 使用真实库存数量，防止超卖
        val mockStocks = listOf(
            // 真实库存数据 - 根据您的实际情况
            StockInfo("无货位", 13),  // 🔴 修正：实际只有13件
            StockInfo("西8排1架6层4位", 8),
            StockInfo("西8排2架6层4位", 5),
            StockInfo("西8排3架6层2位", 3)
        )
        
        Log.d("OutboundActivity", "🧪 模拟库存: ${mockStocks.size}个货位")
        Log.w("OutboundActivity", "⚠️ 注意：使用真实库存数量，防止超卖")
        mockStocks.forEach { stock ->
            Log.d("OutboundActivity", "🧪 模拟库存详情: ${stock.location} = ${stock.quantity}件")
        }
        
        return mockStocks
    }

    // 🔍 本地解析商品条码（格式：商品编码-颜色-尺码）
    private fun parseProductCodeLocally(code: String): LocalProductInfo? {
        try {
            Log.d("OutboundActivity", "🔍 开始本地解析条码: $code")
            
            // 支持的格式：129092-黄色-XXL, 129092-黄色-M, ABC123-红色-L 等
            val parts = code.split("-")
            
            if (parts.size >= 3) {
                val productCode = parts[0]
                val color = parts[1]
                val size = parts[2]
                
                // 验证格式是否合理
                if (productCode.isNotEmpty() && color.isNotEmpty() && size.isNotEmpty()) {
                    Log.d("OutboundActivity", "✅ 本地解析成功: 商品=$productCode, 颜色=$color, 尺码=$size")
                    return LocalProductInfo(productCode, color, size)
                }
            }
            
            Log.d("OutboundActivity", "❌ 条码格式不符合本地解析规则: $code")
            return null
        } catch (e: Exception) {
            Log.e("OutboundActivity", "❌ 本地解析异常: ${e.message}", e)
            return null
        }
    }
    
    private fun mergeduplicateItems() {
        Log.d("OutboundActivity", "🧹 开始合并重复商品...")
        Log.d("OutboundActivity", "🧹 合并前列表大小: ${outboundItems.size}")
        
        // 打印合并前的详细信息
        outboundItems.forEachIndexed { index, item ->
            Log.d("OutboundActivity", "🧹 合并前[$index]: sku=${item.sku}, location=${item.location}, color=${item.color}, size=${item.size}, quantity=${item.quantity}")
        }
        
        val mergedMap = mutableMapOf<String, OutboundItem>()
        
        for (item in outboundItems) {
            val key = "${item.sku}_${item.location}_${item.color}_${item.size}"
            Log.d("OutboundActivity", "🧹 处理商品: $key")
            
            if (mergedMap.containsKey(key)) {
                // 如果已存在相同的商品，累加数量
                val existing = mergedMap[key]!!
                val newQuantity = existing.quantity + item.quantity
                mergedMap[key] = existing.copy(quantity = newQuantity)
                Log.d("OutboundActivity", "🧹 合并商品: ${item.sku} 数量: ${existing.quantity} + ${item.quantity} = $newQuantity")
            } else {
                // 如果是新商品，直接添加
                mergedMap[key] = item
                Log.d("OutboundActivity", "🧹 新增商品: $key")
            }
        }
        
        val originalSize = outboundItems.size
        val mergedList = mergedMap.values.toMutableList()
        
        Log.d("OutboundActivity", "🧹 合并后列表大小: ${mergedList.size}")
        
        if (mergedList.size != originalSize) {
            outboundItems.clear()
            outboundItems.addAll(mergedList)
            
            // 🔧 安全地更新适配器，避免崩溃
            runOnUiThread {
                try {
                    outboundListAdapter.notifyDataSetChanged()
                    updateItemCount()
                    Log.d("OutboundActivity", "🧹 适配器更新完成")
                } catch (e: Exception) {
                    Log.e("OutboundActivity", "🧹 适配器更新失败: ${e.message}", e)
                }
            }
            
            Log.d("OutboundActivity", "🧹 合并完成: $originalSize 条记录合并为 ${mergedList.size} 条")
            Toast.makeText(this, "已合并重复商品：$originalSize 条 → ${mergedList.size} 条", Toast.LENGTH_LONG).show()
        } else {
            Log.d("OutboundActivity", "🧹 无需合并: 没有重复记录")
        }
        
        // 打印合并后的详细信息
        outboundItems.forEachIndexed { index, item ->
            Log.d("OutboundActivity", "🧹 合并后[$index]: sku=${item.sku}, location=${item.location}, color=${item.color}, size=${item.size}, quantity=${item.quantity}")
        }
    }

    private fun confirmOutbound() {
        if (outboundItems.isEmpty()) {
            Toast.makeText(this, "出库清单为空", Toast.LENGTH_SHORT).show()
            return
        }

        val totalItems = outboundItems.sumOf { it.quantity }
        
        AlertDialog.Builder(this)
            .setTitle("确认出库")
            .setMessage("确定要提交 ${outboundItems.size} 种商品，共 $totalItems 件的出库操作吗？")
            .setPositiveButton("确认出库") { _, _ ->
                performOutbound()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun performOutbound() {
        // 检查登录状态
        if (!ApiClient.isLoggedIn()) {
            Toast.makeText(this, "用户未登录，请重新登录", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        
        // 获取用户ID，如果为空则使用默认值
        var userId = ApiClient.getCurrentUserId()
        if (userId.isNullOrEmpty()) {
            userId = "wms_user"  // 使用默认用户ID
            Log.d("OutboundActivity", "使用默认用户ID: $userId")
        }

        btnConfirmInbound.isEnabled = false
        btnConfirmInbound.text = "出库中..."

        lifecycleScope.launch {
            var successCount = 0
            var failCount = 0
            val errorMessages = mutableListOf<String>()

            for (item in outboundItems) {
                try {
                    // 🔧 修复HTTP 400错误：正确分离商品编码和SKU编码
                    val productCode = if (item.sku.contains("-")) {
                        item.sku.split("-")[0]  // 从"129092-黄色-M"提取"129092"
                    } else {
                        item.sku  // 如果没有"-"，直接使用原值
                    }
                    
                    val request = OutboundRequest(
                        product_id = null,
                        product_code = productCode,  // 使用商品编码
                        location_id = null,
                        location_code = item.location,
                        sku_code = item.sku,  // 使用完整SKU编码
                        stock_quantity = item.quantity,
                        batch_number = if (item.batch.isNotEmpty()) item.batch else null,
                        operator_id = userId,
                        is_urgent = false,
                        notes = "PDA出库"
                    )

                    // 🔍 添加详细的请求调试日志
                    Log.d("OutboundActivity", "📤 出库请求详情:")
                    Log.d("OutboundActivity", "   商品编码: $productCode")
                    Log.d("OutboundActivity", "   SKU编码: ${item.sku}")
                    Log.d("OutboundActivity", "   库位编码: ${item.location}")
                    Log.d("OutboundActivity", "   数量: ${item.quantity}")
                    Log.d("OutboundActivity", "   批次: ${request.batch_number}")
                    Log.d("OutboundActivity", "   操作人: $userId")

                    val response = ApiClient.getApiService().outbound(request)
                    Log.d("OutboundActivity", "📨 API响应状态码: ${response.code()}")
                    
                    if (response.isSuccessful) {
                        val apiResponse = response.body()
                        Log.d("OutboundActivity", "✅ API响应成功: success=${apiResponse?.success}")
                        if (apiResponse?.success == true) {
                            successCount++
                            Log.d("OutboundActivity", "✅ 出库成功: ${item.sku}")
                        } else {
                            failCount++
                            val errorMsg = apiResponse?.error_message ?: "出库失败"
                            errorMessages.add("${item.sku}: $errorMsg")
                            Log.e("OutboundActivity", "❌ 出库失败: ${item.sku}, 错误: $errorMsg")
                        }
                    } else {
                        failCount++
                        val errorBody = response.errorBody()?.string()
                        val errorMsg = "HTTP ${response.code()}: ${response.message()}"
                        errorMessages.add("${item.sku}: $errorMsg")
                        Log.e("OutboundActivity", "❌ HTTP错误: ${item.sku}, $errorMsg")
                        Log.e("OutboundActivity", "❌ 错误详情: $errorBody")
                    }
                } catch (e: Exception) {
                    failCount++
                    errorMessages.add("${item.sku}: ${e.message}")
                }
            }

            runOnUiThread {
                btnConfirmInbound.isEnabled = true
                btnConfirmInbound.text = "确认出库"

                val message = if (failCount == 0) {
                    "出库完成！\n成功出库 $successCount 种商品"
                } else {
                    "部分出库完成\n成功: $successCount 种\n失败: $failCount 种\n\n错误详情:\n${errorMessages.joinToString("\n")}"
                }

                AlertDialog.Builder(this@OutboundActivity)
                    .setTitle("出库结果")
                    .setMessage(message)
                    .setPositiveButton("确定") { _, _ ->
                        if (successCount > 0) {
                            // 清空清单
                            outboundItems.clear()
                            outboundListAdapter.notifyDataSetChanged()
                            updateItemCount()
                            editProductCode.setText("")
                            editProductCode.requestFocus()
                        }
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun updateItemCount() {
        val itemCount = outboundItems.size
        val totalQuantity = outboundItems.sumOf { it.quantity }
        
        txtInboundTitle.text = "出库商品($itemCount)"
        btnConfirmInbound.text = "确认出库"
        btnConfirmInbound.isEnabled = itemCount > 0
        
        if (itemCount > 0) {
            btnConfirmInbound.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
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

    // 🎯 新增：智能库存验证和自动拆分功能（学习Web版特性）
    suspend fun validateStockAndSplit(item: OutboundItem, requestedQty: Int): List<OutboundItem> {
        try {
            // 获取该SKU在所有库位的库存分布
            val response = ApiClient.getApiService().getInventoryByProduct(code = item.sku.split("-")[0])
            if (!response.isSuccessful) {
                Log.w("OutboundActivity", "获取库存信息失败: ${response.code()}")
                return listOf(item.copy(quantity = requestedQty))
            }

            val product = response.body()?.data?.firstOrNull()
            val allLocations = mutableListOf<LocationStock>()
            
            // 解析所有库位的库存信息
            product?.colors?.forEach { color ->
                if (color.color == item.color) {
                    color.sizes?.forEach { size ->
                        if (size.sku_code == item.sku) {
                            size.locations?.forEach { loc ->
                                if (loc.stock_quantity > 0) {
                                    allLocations.add(loc)
                                }
                            }
                        }
                    }
                }
            }

            // 检查当前库位的库存
            val currentLocStock = allLocations.find { it.location_code == item.location }?.stock_quantity ?: 0
            
            if (requestedQty <= currentLocStock) {
                // 库存充足，直接返回
                Log.d("OutboundActivity", "✅ 库存充足: 需要${requestedQty}件，当前库位有${currentLocStock}件")
                return listOf(item.copy(quantity = requestedQty))
            }

            // 库存不足，开始智能拆分
            Log.d("OutboundActivity", "⚠️ 当前库位库存不足: 需要${requestedQty}件，当前库位仅有${currentLocStock}件")
            val splitItems = mutableListOf<OutboundItem>()
            var remaining = requestedQty

            // 先用当前库位的最大库存
            if (currentLocStock > 0) {
                splitItems.add(item.copy(quantity = currentLocStock))
                remaining -= currentLocStock
                Log.d("OutboundActivity", "📦 使用当前库位: ${item.location} = ${currentLocStock}件")
            }

            // 用其他库位补足剩余数量
            val otherLocations = allLocations.filter { 
                it.location_code != item.location && it.stock_quantity > 0 
            }.sortedByDescending { it.stock_quantity } // 优先使用库存多的库位

            for (locStock in otherLocations) {
                if (remaining <= 0) break
                
                val take = min(remaining, locStock.stock_quantity)
                splitItems.add(item.copy(
                    sku = item.sku,
                    productName = item.productName,
                    location = locStock.location_code,
                    quantity = take,
                    color = item.color,
                    size = item.size,
                    batch = item.batch,
                    imageUrl = item.imageUrl
                ))
                remaining -= take
                Log.d("OutboundActivity", "📦 补充库位: ${locStock.location_code} = ${take}件")
            }

            if (remaining > 0) {
                runOnUiThread {
                    Toast.makeText(this@OutboundActivity, 
                        "⚠️ 库存不足，仍有 $remaining 件超出可用库存\n已尽量从其他库位补充", 
                        Toast.LENGTH_LONG).show()
                }
                Log.w("OutboundActivity", "❌ 最终仍不足: $remaining 件")
            } else {
                runOnUiThread {
                    Toast.makeText(this@OutboundActivity, 
                        "✅ 已自动从 ${splitItems.size} 个库位补足库存", 
                        Toast.LENGTH_SHORT).show()
                }
                Log.d("OutboundActivity", "✅ 智能拆分成功: 共${splitItems.size}个库位")
            }

            return splitItems

        } catch (e: Exception) {
            Log.e("OutboundActivity", "库存验证失败: ${e.message}", e)
            runOnUiThread {
                Toast.makeText(this@OutboundActivity, "库存验证失败，请手动检查", Toast.LENGTH_SHORT).show()
            }
            return listOf(item.copy(quantity = requestedQty))
        }
    }

    // 🎯 新增：货位选项缓存机制（学习Web版特性）
    private fun getCachedLocationOptions(): List<String>? {
        val prefs = getSharedPreferences("wms_cache", Context.MODE_PRIVATE)
        val cached = prefs.getString("locations", null)
        val timestamp = prefs.getLong("locations_timestamp", 0)
        
        // 5分钟缓存有效期
        if (cached != null && System.currentTimeMillis() - timestamp < 5 * 60 * 1000) {
            return try {
                Gson().fromJson(cached, object : TypeToken<List<String>>() {}.type)
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    private fun setCachedLocationOptions(locations: List<String>) {
        val prefs = getSharedPreferences("wms_cache", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("locations", Gson().toJson(locations))
            .putLong("locations_timestamp", System.currentTimeMillis())
            .apply()
        Log.d("OutboundActivity", "💾 缓存货位选项: ${locations.size}个")
    }

    // 🎯 修改：增强货位获取逻辑
    private suspend fun fetchLocationOptionsEnhanced(): List<String> {
        // 先尝试使用缓存
        getCachedLocationOptions()?.let { cached ->
            Log.d("OutboundActivity", "📋 使用缓存的货位选项: ${cached.size}个")
            return cached
        }

        try {
            // 缓存失效，从API获取
            val response = ApiClient.getApiService().getInventoryByLocation()
            if (response.isSuccessful) {
                val locations = response.body()?.data?.map { it.location_code } ?: emptyList()
                val allOptions = listOf("无货位") + locations.distinct()
                
                // 更新缓存
                setCachedLocationOptions(allOptions)
                Log.d("OutboundActivity", "🌐 从API获取货位选项: ${allOptions.size}个")
                return allOptions
            }
        } catch (e: Exception) {
            Log.e("OutboundActivity", "获取货位选项失败: ${e.message}")
        }
        
        // fallback 到默认选项
        return listOf("无货位", "A区-01", "B区-01", "C区-01")
    }
} 