package com.yourcompany.wmssystem.pdawms

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

// 层级项目类型
sealed class HierarchyItem {
    data class ProductItem(
        val product: Product,
        val searchType: String,
        var isExpanded: Boolean = false
    ) : HierarchyItem()
    
    data class ColorItem(
        val color: ColorInfo,
        val productCode: String,
        var isExpanded: Boolean = false
    ) : HierarchyItem()
    
    data class SkuItem(
        val sku: SkuInfo,
        val productCode: String,
        val color: String
    ) : HierarchyItem()
}

class ProductHierarchyAdapter(
    private val context: Context,
    private val items: MutableList<HierarchyItem> = mutableListOf()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_PRODUCT = 0
        private const val TYPE_COLOR = 1
        private const val TYPE_SKU = 2
        private const val BASE_URL = "http://192.168.11.252:8610"
    }

    // 产品ViewHolder
    class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgProduct: TextView = itemView.findViewById(R.id.imgProduct)
        val txtStockBadge: TextView = itemView.findViewById(R.id.txtStockBadge)
        val txtProductName: TextView = itemView.findViewById(R.id.txtProductName)
        val txtSearchType: TextView = itemView.findViewById(R.id.txtSearchType)
        val txtProductCode: TextView = itemView.findViewById(R.id.txtProductCode)
        val txtColorCount: TextView = itemView.findViewById(R.id.txtColorCount)
        val expandIcon: TextView = itemView.findViewById(R.id.expandIcon)
        val cardView: View = itemView
    }

    // 颜色ViewHolder
    class ColorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgColor: TextView = itemView.findViewById(R.id.imgColor)
        val txtColorName: TextView = itemView.findViewById(R.id.txtColorName)
        val txtColorStock: TextView = itemView.findViewById(R.id.txtColorStock)
        val txtSkuCount: TextView = itemView.findViewById(R.id.txtSkuCount)
        val expandIcon: TextView = itemView.findViewById(R.id.expandIcon)
        val cardView: View = itemView
    }

    // SKU ViewHolder
    class SkuViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgSku: TextView = itemView.findViewById(R.id.imgSku)
        val txtSkuCode: TextView = itemView.findViewById(R.id.txtSkuCode)
        val txtSkuSize: TextView = itemView.findViewById(R.id.txtSkuSize)
        val txtSkuStock: TextView = itemView.findViewById(R.id.txtSkuStock)
        val txtExternalCodes: TextView = itemView.findViewById(R.id.txtExternalCodes)
        val cardView: View = itemView
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is HierarchyItem.ProductItem -> TYPE_PRODUCT
            is HierarchyItem.ColorItem -> TYPE_COLOR
            is HierarchyItem.SkuItem -> TYPE_SKU
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return try {
            when (viewType) {
                TYPE_PRODUCT -> {
                    val view = LayoutInflater.from(context).inflate(R.layout.item_product_hierarchy, parent, false)
                    ProductViewHolder(view)
                }
                TYPE_COLOR -> {
                    val view = LayoutInflater.from(context).inflate(R.layout.item_color_hierarchy, parent, false)
                    ColorViewHolder(view)
                }
                TYPE_SKU -> {
                    val view = LayoutInflater.from(context).inflate(R.layout.item_sku_hierarchy, parent, false)
                    SkuViewHolder(view)
                }
                else -> throw IllegalArgumentException("Unknown view type: $viewType")
            }
        } catch (e: Exception) {
            Log.e("ProductHierarchyAdapter", "❌ 创建ViewHolder失败 (viewType=$viewType): ${e.message}", e)
            
            // 创建一个简单的备用布局，防止崩溃
            val fallbackView = TextView(context).apply {
                text = "布局加载失败"
                setPadding(16, 16, 16, 16)
                setTextColor(android.graphics.Color.RED)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            
            // 返回一个简单的ViewHolder
            object : RecyclerView.ViewHolder(fallbackView) {}
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        try {
            when (val item = items[position]) {
                is HierarchyItem.ProductItem -> {
                    if (holder is ProductViewHolder) {
                        bindProductItem(holder, item)
                    } else {
                        // 备用ViewHolder，显示基本信息
                        (holder.itemView as? TextView)?.text = "产品: ${item.product.product_name}"
                    }
                }
                is HierarchyItem.ColorItem -> {
                    if (holder is ColorViewHolder) {
                        bindColorItem(holder, item)
                    } else {
                        (holder.itemView as? TextView)?.text = "颜色: ${item.color.color}"
                    }
                }
                is HierarchyItem.SkuItem -> {
                    if (holder is SkuViewHolder) {
                        bindSkuItem(holder, item)
                    } else {
                        (holder.itemView as? TextView)?.text = "SKU: ${item.sku.sku_code}"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ProductHierarchyAdapter", "❌ 绑定ViewHolder失败 (position=$position): ${e.message}", e)
            // 如果绑定失败，至少显示一个错误信息
            (holder.itemView as? TextView)?.text = "数据绑定失败"
        }
    }

    private fun bindProductItem(holder: ProductViewHolder, item: HierarchyItem.ProductItem) {
        val product = item.product
        
        holder.txtProductName.text = product.product_name.ifEmpty { product.product_code }
        holder.txtSearchType.text = item.searchType
        holder.txtProductCode.text = "产品代码: ${product.product_code}"
        
        val totalStock = product.product_total_quantity ?: 0
        holder.txtStockBadge.text = totalStock.toString()
        
        val colorCount = product.colors?.size ?: 0
        holder.txtColorCount.text = "颜色: ${colorCount}种"
        
        // 设置展开图标
        holder.expandIcon.text = if (item.isExpanded) "▲" else "▼"
        
        // 设置商品图片emoji（简化版本）
        holder.imgProduct.text = "📦"
        
        // 点击展开/收起颜色
        holder.cardView.setOnClickListener {
            val currentPosition = holder.adapterPosition
            if (currentPosition != RecyclerView.NO_POSITION) {
                toggleProductExpansion(item, currentPosition)
            }
        }
    }

    private fun bindColorItem(holder: ColorViewHolder, item: HierarchyItem.ColorItem) {
        val color = item.color
        
        holder.txtColorName.text = color.color
        holder.txtColorStock.text = "库存: ${color.color_total_quantity ?: 0}件"
        
        val skuCount = color.sizes?.size ?: 0
        holder.txtSkuCount.text = "SKU: ${skuCount}个"
        
        // 设置展开图标
        holder.expandIcon.text = if (item.isExpanded) "▲" else "▼"
        
        // 设置颜色图片emoji（简化版本）
        holder.imgColor.text = "🎨"
        
        // 点击展开/收起SKU
        holder.cardView.setOnClickListener {
            val currentPosition = holder.adapterPosition
            if (currentPosition != RecyclerView.NO_POSITION) {
                toggleColorExpansion(item, currentPosition)
            }
        }
    }

    private fun bindSkuItem(holder: SkuViewHolder, item: HierarchyItem.SkuItem) {
        val sku = item.sku
        
        holder.txtSkuCode.text = sku.sku_code
        holder.txtSkuSize.text = "尺寸: ${sku.sku_size ?: "未知"}"
        holder.txtSkuStock.text = "库存: ${sku.sku_total_quantity ?: 0}件"
        
        // 显示外部条码
        val externalCodes = getExternalCodes(sku)
        if (externalCodes.isNotEmpty()) {
            holder.txtExternalCodes.visibility = View.VISIBLE
            holder.txtExternalCodes.text = "外部条码: ${externalCodes.joinToString(", ")}"
        } else {
            holder.txtExternalCodes.visibility = View.VISIBLE
            holder.txtExternalCodes.text = "外部条码: 无"
        }
        
        // 设置SKU图片emoji（简化版本）
        holder.imgSku.text = "📋"
        
        // 整个SKU卡片点击进入外部条码管理
        holder.cardView.setOnClickListener {
            showExternalCodeManagementDialog(sku, item.productCode, item.color)
        }
    }

    private fun toggleProductExpansion(productItem: HierarchyItem.ProductItem, position: Int) {
        productItem.isExpanded = !productItem.isExpanded
        
        if (productItem.isExpanded) {
            // 展开：插入颜色项目
            val colorItems = productItem.product.colors?.map { color ->
                HierarchyItem.ColorItem(color, productItem.product.product_code)
            } ?: emptyList()
            
            items.addAll(position + 1, colorItems)
            notifyItemChanged(position)
            notifyItemRangeInserted(position + 1, colorItems.size)
        } else {
            // 收起：移除所有子项目
            val removeCount = removeChildItems(position + 1)
            notifyItemChanged(position)
            if (removeCount > 0) {
                notifyItemRangeRemoved(position + 1, removeCount)
            }
        }
    }

    private fun toggleColorExpansion(colorItem: HierarchyItem.ColorItem, position: Int) {
        colorItem.isExpanded = !colorItem.isExpanded
        
        if (colorItem.isExpanded) {
            // 展开：插入SKU项目
            val skuItems = colorItem.color.sizes?.map { sku ->
                HierarchyItem.SkuItem(sku, colorItem.productCode, colorItem.color.color)
            } ?: emptyList()
            
            items.addAll(position + 1, skuItems)
            notifyItemChanged(position)
            notifyItemRangeInserted(position + 1, skuItems.size)
        } else {
            // 收起：移除SKU项目
            val removeCount = removeChildItems(position + 1)
            notifyItemChanged(position)
            if (removeCount > 0) {
                notifyItemRangeRemoved(position + 1, removeCount)
            }
        }
    }

    private fun removeChildItems(startPosition: Int): Int {
        var removeCount = 0
        while (startPosition < items.size) {
            val nextItem = items[startPosition]
            if (nextItem is HierarchyItem.ProductItem) {
                break // 遇到下一个产品，停止删除
            }
            items.removeAt(startPosition)
            removeCount++
        }
        return removeCount
    }

    private fun showExternalCodeManagementDialog(sku: SkuInfo, productCode: String, color: String) {
        val activity = context as? androidx.fragment.app.FragmentActivity ?: return
        
        // 检查Activity状态，避免在onSaveInstanceState后显示对话框
        if (activity.isFinishing || activity.isDestroyed) {
            Log.w("ProductHierarchyAdapter", "Activity正在结束或已销毁，跳过显示对话框")
            return
        }
        
        try {
            val dialog = ExternalCodesDialogFragment.newInstance(sku, productCode, color)
            dialog.showNow(activity.supportFragmentManager, "ExternalCodesDialog")
        } catch (e: Exception) {
            Log.e("ProductHierarchyAdapter", "显示外部条码对话框失败: ${e.message}", e)
            // 这里无法显示Toast，因为在Adapter中，只能记录日志
        }
    }


    
    private fun isValidImageUrl(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        if (url.contains(" ")) return false // URL不应包含空格
        return true
    }

    private fun getImageUrl(imagePath: String?): String {
        return if (!imagePath.isNullOrEmpty() && !imagePath.startsWith("http")) {
            "$BASE_URL$imagePath"
        } else {
            imagePath ?: ""
        }
    }

    private fun getExternalCodes(sku: SkuInfo): List<String> {
        return sku.external_codes?.map { it.external_code } ?: emptyList()
    }

    override fun getItemCount(): Int = items.size

    fun setProduct(product: Product, searchType: String) {
        items.clear()
        items.add(HierarchyItem.ProductItem(product, searchType))
        notifyDataSetChanged()
    }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }
} 