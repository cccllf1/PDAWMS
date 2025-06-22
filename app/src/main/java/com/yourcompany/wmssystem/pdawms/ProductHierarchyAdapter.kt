package com.yourcompany.wmssystem.pdawms

import android.content.Context
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
        val imgProduct: ImageView = itemView.findViewById(R.id.imgProduct)
        val txtStockBadge: TextView = itemView.findViewById(R.id.txtStockBadge)
        val txtProductName: TextView = itemView.findViewById(R.id.txtProductName)
        val txtSearchType: TextView = itemView.findViewById(R.id.txtSearchType)
        val txtProductCode: TextView = itemView.findViewById(R.id.txtProductCode)
        val txtColorCount: TextView = itemView.findViewById(R.id.txtColorCount)
        val expandIcon: ImageView = itemView.findViewById(R.id.expandIcon)
        val cardView: View = itemView
    }

    // 颜色ViewHolder
    class ColorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgColor: ImageView = itemView.findViewById(R.id.imgColor)
        val txtColorName: TextView = itemView.findViewById(R.id.txtColorName)
        val txtColorStock: TextView = itemView.findViewById(R.id.txtColorStock)
        val txtSkuCount: TextView = itemView.findViewById(R.id.txtSkuCount)
        val expandIcon: ImageView = itemView.findViewById(R.id.expandIcon)
        val cardView: View = itemView
    }

    // SKU ViewHolder
    class SkuViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgSku: ImageView = itemView.findViewById(R.id.imgSku)
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
        return when (viewType) {
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
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is HierarchyItem.ProductItem -> bindProductItem(holder as ProductViewHolder, item)
            is HierarchyItem.ColorItem -> bindColorItem(holder as ColorViewHolder, item)
            is HierarchyItem.SkuItem -> bindSkuItem(holder as SkuViewHolder, item)
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
        holder.expandIcon.setImageResource(
            if (item.isExpanded) R.drawable.ic_launcher_foreground else R.drawable.ic_launcher_foreground
        )
        
        // 加载商品图片
        loadImage(holder.imgProduct, product.image_path)
        
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
        holder.expandIcon.setImageResource(
            if (item.isExpanded) R.drawable.ic_launcher_foreground else R.drawable.ic_launcher_foreground
        )
        
        // 加载颜色图片
        loadImage(holder.imgColor, color.image_path)
        
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
        
        // 加载SKU图片
        loadImage(holder.imgSku, sku.image_path)
        
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
        
        val dialog = ExternalCodesDialogFragment.newInstance(sku, productCode, color)
        dialog.show(activity.supportFragmentManager, "ExternalCodesDialog")
    }

    private fun loadImage(imageView: ImageView, imagePath: String?) {
        val imageUrl = getImageUrl(imagePath)
        if (imageUrl.isNotEmpty()) {
            Glide.with(context)
                .load(imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_launcher_foreground)
                .error(R.drawable.ic_launcher_foreground)
                .into(imageView)
        } else {
            imageView.setImageResource(R.drawable.ic_launcher_foreground)
        }
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