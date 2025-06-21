package com.yourcompany.wmssystem.pdawms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

// All old data classes and adapters related to the dialog are removed from this file.
// (ExpandableSkuInfo, SkuDetailAdapter, ProductColorDetailAdapter, etc.)

class InventoryActivity : AppCompatActivity() {

    private lateinit var editSearch: EditText
    private lateinit var btnSearch: Button
    private lateinit var txtInventoryTitle: TextView
    private lateinit var recyclerInventoryList: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var inventoryAdapter: InventoryAdapter
    private val productList = mutableListOf<Product>()
    private lateinit var unifiedNavBar: UnifiedNavBar
    
    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            getScanData(intent)?.let {
                editSearch.setText(it)
                searchInventory(it)
            }
        }
    }

    private fun getScanData(intent: Intent?): String? {
        return when (intent?.action) {
            "android.intent.action.SCANRESULT" -> intent.getStringExtra("value")
            "android.intent.ACTION_DECODE_DATA" -> intent.getStringExtra("barcode_string")
            "com.symbol.datawedge.api.RESULT_ACTION" -> intent.getStringExtra("com.symbol.datawedge.data_string")
            "com.honeywell.decode.intent.action.SCAN_RESULT" -> intent.getStringExtra("SCAN_RESULT")
            "nlscan.action.SCANNER_RESULT" -> intent.getStringExtra("SCAN_BARCODE1")
            "scan.rcv.message" -> intent.getStringExtra("barocode")
            else -> null
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inventory)
        Log.d("WMS_INVENTORY", "📦 Inventory page started")
        initViews()
        setupRecyclerView()
        setupEventListeners()
        val navBarContainer = findViewById<LinearLayout>(R.id.navBarContainer)
        unifiedNavBar = UnifiedNavBar.addToActivity(this, navBarContainer, "inventory")
        loadInitialInventory()
    }

    override fun onResume() {
        super.onResume()
        registerScanReceiver()
    }

    override fun onPause() {
        super.onPause()
        unregisterScanReceiver()
    }

    private fun registerScanReceiver() {
        val filter = IntentFilter().apply {
            listOf(
                "android.intent.action.SCANRESULT", "android.intent.ACTION_DECODE_DATA",
                "com.symbol.datawedge.api.RESULT_ACTION", "com.honeywell.decode.intent.action.SCAN_RESULT",
                "nlscan.action.SCANNER_RESULT", "scan.rcv.message"
            ).forEach { addAction(it) }
        }
        registerReceiver(scanReceiver, filter)
    }
    
    private fun unregisterScanReceiver() {
        try {
            unregisterReceiver(scanReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w("InventoryActivity", "Receiver not registered or already unregistered.")
        }
    }

    private fun initViews() {
        editSearch = findViewById(R.id.editSearch) 
        btnSearch = findViewById(R.id.btnSearch)
        txtInventoryTitle = findViewById(R.id.txtInventoryTitle)
        recyclerInventoryList = findViewById(R.id.recyclerInventoryList)
        progressBar = findViewById(R.id.progressBar)
    }
    
    private fun setupRecyclerView() {
        inventoryAdapter = InventoryAdapter(productList) { product ->
            onProductClick(product)
        }
        recyclerInventoryList.layoutManager = LinearLayoutManager(this)
        recyclerInventoryList.adapter = inventoryAdapter
    }
    
    private fun setupEventListeners() {
        btnSearch.setOnClickListener {
            val query = editSearch.text.toString().trim()
            searchInventory(query)
        }
    }

    private fun searchInventory(query: String) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = ApiClient.getApiService().searchProducts(query = query)
                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse != null && apiResponse.success) {
                        updateInventoryList(apiResponse.data?.products ?: emptyList())
                    } else {
                        Toast.makeText(this@InventoryActivity, "搜索失败: ${apiResponse?.error_message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                     Toast.makeText(this@InventoryActivity, "网络错误: ${response.code()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@InventoryActivity, "网络请求异常: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun onProductClick(product: Product) {
        val dialogFragment = ProductDetailsDialogFragment.newInstance(product)
        dialogFragment.show(supportFragmentManager, "ProductDetailsDialog")
    }
    
    private fun loadInitialInventory() {
        searchInventory("")
    }

    private fun updateInventoryList(products: List<Product>) {
        inventoryAdapter.updateProducts(products)
        val totalProducts = products.size
        val totalStock = products.sumOf { it.product_total_quantity ?: 0 }
        updateInventoryTitle(totalProducts, totalStock)
    }

    private fun updateInventoryTitle(totalProducts: Int, totalStock: Int) {
        if (totalProducts > 0 || totalStock > 0) {
            txtInventoryTitle.text = getString(R.string.inventory_summary_title, totalProducts, totalStock)
        } else {
            txtInventoryTitle.text = getString(R.string.inventory_no_results)
        }
    }
}

// --- Adapters for the main Inventory List ---
// These adapters are unchanged and remain here.

class InventoryAdapter(
    private var productList: MutableList<Product>,
    private val onProductClick: (Product) -> Unit
) : RecyclerView.Adapter<InventoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtProductName: TextView = view.findViewById(R.id.txtProductName)
        val txtProductCode: TextView = view.findViewById(R.id.txtProductCode)
        val txtProductTotalStock: TextView = view.findViewById(R.id.txtProductTotalStock)
        val txtProductStats: TextView = view.findViewById(R.id.txtProductStats)
        val recyclerSkuImages: RecyclerView = view.findViewById(R.id.recyclerSkuImages)
        val txtNoSku: TextView = view.findViewById(R.id.txtNoSku)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inventory, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val product = productList[position]
        val context = holder.itemView.context

        holder.itemView.setOnClickListener { onProductClick(product) }

        holder.txtProductName.text = product.product_name
        holder.txtProductCode.text = context.getString(R.string.product_code_label, product.product_code)
        holder.txtProductTotalStock.text = context.getString(R.string.product_total_stock_label, product.product_total_quantity ?: 0)
        holder.txtProductStats.text = context.getString(
            R.string.product_stats_label,
            product.total_sku_count ?: 0,
            product.total_location_count ?: 0
        )

        val colorsWithImages = product.colors
            ?.filter { !it.image_path.isNullOrEmpty() }
            ?.sortedByDescending { it.color_total_quantity ?: 0 } ?: emptyList()

        if (colorsWithImages.isNotEmpty()) {
            holder.recyclerSkuImages.visibility = View.VISIBLE
            holder.txtNoSku.visibility = View.GONE
            holder.recyclerSkuImages.layoutManager = LinearLayoutManager(holder.itemView.context, LinearLayoutManager.HORIZONTAL, false)
            holder.recyclerSkuImages.adapter = ColorImageAdapter(colorsWithImages)
        } else {
            holder.recyclerSkuImages.visibility = View.GONE
            holder.txtNoSku.visibility = View.VISIBLE
        }
    }

    override fun getItemCount(): Int = productList.size

    fun updateProducts(newProducts: List<Product>) {
        productList.clear()
        productList.addAll(newProducts)
        notifyDataSetChanged()
    }
}

class ColorImageAdapter(
    private var items: List<ColorInfo>
) : RecyclerView.Adapter<ColorImageAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgColor: ImageView = view.findViewById(R.id.imgSku)
        val txtColorStock: TextView = view.findViewById(R.id.txtSkuStock)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inventory_sku_image, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.txtColorStock.text = (item.color_total_quantity ?: 0).toString()

        val imageUrl = ApiClient.processImageUrl(item.image_path, holder.itemView.context)
        Glide.with(holder.itemView.context)
            .load(imageUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_gallery)
            .thumbnail(0.1f)
            .override(200,200)
            .centerCrop()
            .into(holder.imgColor)
    }

    override fun getItemCount(): Int = items.size
} 