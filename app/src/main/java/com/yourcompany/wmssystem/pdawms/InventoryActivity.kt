package com.yourcompany.wmssystem.pdawms

import android.app.AlertDialog
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

// Renamed to avoid conflict with ApiModels.kt. This is a display-specific class.
data class InventoryDisplayItem(
    val sku: String,
    val productName: String,
    val totalStock: Int,
    val locationCount: Int,
    val color: String,
    val size: String,
    val imageUrl: String?,
    val locationStocks: Map<String, Int>
)

// --- Nested Adapter for SKU Images ---
class SkuImageAdapter(
    private var items: List<SkuInfo>,
    private val onSkuClick: (SkuInfo) -> Unit
) : RecyclerView.Adapter<SkuImageAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgSku: ImageView = view.findViewById(R.id.imgSku)
        val txtSkuStock: TextView = view.findViewById(R.id.txtSkuStock)
        val txtSkuInfo: TextView = view.findViewById(R.id.txtSkuInfo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inventory_sku_image, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.txtSkuStock.text = (item.sku_total_quantity ?: 0).toString()
        holder.txtSkuInfo.text = "${item.sku_color ?: ""}-${item.sku_size ?: ""}"

        val imageUrl = ApiClient.processImageUrl(item.image_path, holder.itemView.context)
        Glide.with(holder.itemView.context)
            .load(imageUrl)
            .placeholder(R.drawable.ic_launcher_background)
            .error(R.drawable.ic_launcher_background)
            .into(holder.imgSku)

        holder.itemView.setOnClickListener { onSkuClick(item) }
    }

    override fun getItemCount(): Int = items.size
}

// --- New Nested Adapter for Color Images ---
class ColorImageAdapter(
    private var items: List<ColorInfo>
    // No longer needs a click listener
) : RecyclerView.Adapter<ColorImageAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgColor: ImageView = view.findViewById(R.id.imgSku) // Reusing the ID from item_inventory_sku_image
        val txtColorStock: TextView = view.findViewById(R.id.txtSkuStock) // Reusing the ID
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inventory_sku_image, parent, false) // Reusing the layout
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.txtColorStock.text = (item.color_total_quantity ?: 0).toString()

        val imageUrl = ApiClient.processImageUrl(item.image_path, holder.itemView.context)
        Glide.with(holder.itemView.context)
            .load(imageUrl)
            .placeholder(R.drawable.ic_launcher_background)
            .error(R.drawable.ic_launcher_background)
            .into(holder.imgColor)

        // Set itemView to not be clickable to avoid intercepting card clicks
        holder.itemView.isClickable = false
    }

    override fun getItemCount(): Int = items.size
}


// --- Main Adapter for Product Cards ---
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

        val colorsWithImages = product.colors?.filter { !it.image_path.isNullOrEmpty() } ?: emptyList()

        if (colorsWithImages.isNotEmpty()) {
            holder.recyclerSkuImages.visibility = View.VISIBLE
            holder.txtNoSku.visibility = View.GONE
            holder.recyclerSkuImages.layoutManager = LinearLayoutManager(holder.itemView.context, LinearLayoutManager.HORIZONTAL, false)
            // Use the new ColorImageAdapter without a click listener
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
            showProductDetails(product)
        }
        recyclerInventoryList.layoutManager = LinearLayoutManager(this)
        recyclerInventoryList.adapter = inventoryAdapter
        updateInventoryTitle()
    }

    private fun setupEventListeners() {
        btnSearch.setOnClickListener {
            val code = editSearch.text.toString().trim()
            searchInventory(code)
        }
    }

    private fun loadInitialInventory() {
        searchInventory(null)
    }
    
    private fun searchInventory(query: String?) {
        Log.d("WMS_INVENTORY", "🚀 Starting inventory search with query: '$query'")
        showLoading(true)

        lifecycleScope.launch {
            try {
                Log.d("WMS_INVENTORY", "📞 Calling API: searchProducts(query=$query)")
                val response = ApiClient.getApiService().searchProducts(query = query)
                Log.d("WMS_INVENTORY", "✅ API call successful. Response received.")

                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse != null && apiResponse.success) {
                        val products = apiResponse.data?.products ?: emptyList()
                        Log.d("WMS_INVENTORY", "👍 Success response. Found ${products.size} products.")
                        
                        inventoryAdapter.updateProducts(products)
                        updateInventoryTitle()

                        if (products.isEmpty()) {
                            Log.w("WMS_INVENTORY", "⚠️ Inventory list is empty. Check API response or query.")
                            Toast.makeText(this@InventoryActivity, R.string.inventory_no_results, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val errorMsg = apiResponse?.error_message ?: "Unknown error"
                        Log.e("WMS_INVENTORY", "❌ API returned success=false. Message: $errorMsg")
                        Toast.makeText(this@InventoryActivity, getString(R.string.inventory_api_error, errorMsg), Toast.LENGTH_LONG).show()
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "No error body"
                    Log.e("WMS_INVENTORY", "❌ API call failed. Code: ${response.code()}, Message: ${response.message()}, Body: $errorBody")
                    Toast.makeText(this@InventoryActivity, getString(R.string.inventory_network_error, response.code()), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("WMS_INVENTORY", "🔥 Exception during inventory search: ${e.message}", e)
                Toast.makeText(this@InventoryActivity, getString(R.string.inventory_request_exception, e.message), Toast.LENGTH_LONG).show()
            } finally {
                showLoading(false)
                Log.d("WMS_INVENTORY", "🏁 Search finished.")
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        recyclerInventoryList.visibility = if (isLoading) View.GONE else View.VISIBLE
    }

    private fun updateInventoryTitle() {
        txtInventoryTitle.text = getString(R.string.inventory_list_title, inventoryAdapter.itemCount)
    }
    
    private fun showProductDetails(product: Product) {
        val details = StringBuilder()
        details.append(getString(R.string.product_name_label)).append(": ").append(product.product_name).append("\n")
        details.append(getString(R.string.product_code_label, product.product_code)).append("\n")
        details.append(getString(R.string.product_total_stock_label, product.product_total_quantity ?: 0)).append("\n\n")
        details.append("--- ${getString(R.string.sku_list_header)} ---\n\n")

        if (product.skus.isNullOrEmpty()) {
            details.append("此商品无SKU信息")
        } else {
            product.skus.forEach { sku ->
                details.append(sku.sku_code).append(" (").append(getString(R.string.total_stock_label)).append(": ").append(sku.sku_total_quantity ?: 0).append(")\n")
                if (sku.locations.isNullOrEmpty()) {
                    details.append("  - 无具体库位信息\n")
                } else {
                    sku.locations.forEach { location ->
                        details.append("  - ${location.location_code}: ${location.stock_quantity}\n")
                    }
                }
                details.append("\n")
            }
        }

        val scrollView = ScrollView(this)
        val textView = TextView(this)
        textView.text = details.toString()
        textView.setPadding(48, 24, 48, 24)
        scrollView.addView(textView)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.product_details_title))
            .setView(scrollView)
            .setPositiveButton(getString(R.string.close_button), null)
            .show()
    }
} 