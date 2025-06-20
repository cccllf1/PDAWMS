package com.yourcompany.wmssystem.pdawms

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.yourcompany.wmssystem.pdawms.databinding.DialogProductDetailsBinding
import com.yourcompany.wmssystem.pdawms.databinding.ItemProductColorDetailBinding
import com.yourcompany.wmssystem.pdawms.databinding.ItemSkuLocationDetailBinding
import com.yourcompany.wmssystem.pdawms.databinding.ItemLocationActionsBinding
import com.bumptech.glide.Glide
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

// 仓库操作类型
enum class WarehouseAction { INBOUND, OUTBOUND, STOCKTAKE }

class ProductDetailsDialogFragment : DialogFragment() {

    private var _binding: DialogProductDetailsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProductDetailViewModel by activityViewModels()

    companion object {
        private const val ARG_PRODUCT_JSON = "product_json"
        fun newInstance(product: Product): ProductDetailsDialogFragment {
            val args = Bundle()
            args.putString(ARG_PRODUCT_JSON, Gson().toJson(product))
            val fragment = ProductDetailsDialogFragment()
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogProductDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val productJson = arguments?.getString(ARG_PRODUCT_JSON)
        val product = productJson?.let { Gson().fromJson(it, Product::class.java) }

        if (product == null) {
            Toast.makeText(context, "Error: Product data not found.", Toast.LENGTH_LONG).show()
            dismiss()
            return
        }

        val colorAdapter = ProductColorAdapter(requireContext(), viewModel, { sku, location, action ->
            showWarehouseDialog(sku, location, action)
        })
        binding.recyclerColorDetails.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = colorAdapter
        }
        
        val titleText = if (product.product_name == product.product_code) {
            product.product_name
        } else {
            "${product.product_name} (${product.product_code})"
        }
        binding.txtDialogTitle.text = titleText
        val productTotalQty = product.product_total_quantity ?: 0
        binding.txtProductTotal.text = "总库存: ${productTotalQty}件"
        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnCloseDialog.setOnClickListener { dismiss() }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            colorAdapter.submitList(state.colors.toList())
            colorAdapter.notifyDataSetChanged()
            if(state.errorMessage != null) {
                Toast.makeText(context, state.errorMessage, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
        
        viewModel.loadColors(product.colors ?: emptyList())
    }

    override fun onStart() {
        super.onStart()
        // Set dialog to occupy full screen width
        dialog?.window?.setLayout((resources.displayMetrics.widthPixels * 1.0).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    //region Warehouse Dialog & API

    private fun showWarehouseDialog(sku: SkuInfo, location: LocationStock, action: WarehouseAction) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_warehouse_action, null)
        val txtSku = dialogView.findViewById<TextView>(R.id.txtDialogSku)
        val txtLocation = dialogView.findViewById<TextView>(R.id.txtDialogLocation)
        val edtQty = dialogView.findViewById<EditText>(R.id.edtDialogQuantity)

        txtSku.text = "SKU: ${sku.sku_code}"
        txtLocation.text = "库位: ${location.location_code}"

        val title = when(action) {
            WarehouseAction.INBOUND -> "入库"
            WarehouseAction.OUTBOUND -> "出库"
            WarehouseAction.STOCKTAKE -> "盘点调整"
        }

        val alert = AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("确认") { _, _ ->
                val qtyStr = edtQty.text.toString()
                val qty = qtyStr.toIntOrNull()
                if (qty == null || qty <= 0) {
                    Toast.makeText(requireContext(), "请输入有效数量", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    callWarehouseApi(sku, location, qty, action)
                }
            }
            .setNegativeButton("取消", null)
            .create()

        alert.show()
    }

    private suspend fun callWarehouseApi(sku: SkuInfo, location: LocationStock, qty: Int, action: WarehouseAction) {
        try {
            val api = ApiClient.getApiService()
            val operatorId = ApiClient.getCurrentUserId() ?: "anonymous"

            when(action) {
                WarehouseAction.INBOUND -> {
                    val req = InboundRequest(sku.sku_code, location.location_code, qty, operatorId, null, false, null)
                    val resp = api.inbound(req)
                    if (resp.isSuccessful && resp.body()?.success == true) {
                        val inv = resp.body()!!.inventory
                        if (inv != null) {
                            updateQuantities(inv.sku_code, inv.location_code, inv.sku_location_quantity, inv.sku_total_quantity)
                        }
                        Toast.makeText(requireContext(), "入库成功", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.setError(resp.body()?.error_message ?: "入库失败")
                    }
                }
                WarehouseAction.OUTBOUND -> {
                    val req = OutboundRequest(sku.sku_code, location.location_code, qty, operatorId, null, false, null)
                    val resp = api.outbound(req)
                    if (resp.isSuccessful && resp.body()?.success == true) {
                        val inv = resp.body()!!.inventory
                        if (inv != null) {
                            updateQuantities(inv.sku_code, inv.location_code, inv.sku_location_quantity, inv.sku_total_quantity)
                        }
                        Toast.makeText(requireContext(), "出库成功", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.setError(resp.body()?.error_message ?: "出库失败")
                    }
                }
                WarehouseAction.STOCKTAKE -> {
                    // For stocktake we use adjustInventory
                    val req = InventoryAdjustRequest(sku.sku_code, location.location_code, qty, operatorId, null, false, null)
                    val resp = api.adjustInventory(req)
                    if (resp.isSuccessful && resp.body()?.success == true) {
                        val data = resp.body()!!.data
                        if (data != null) {
                           updateQuantities(data.sku_code, data.location_code, data.current_quantity, data.current_quantity /* total? need re-fetch; using same */)
                        }
                        Toast.makeText(requireContext(), "盘点成功", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.setError(resp.body()?.error_message ?: "盘点失败")
                    }
                }
            }
        } catch (e: Exception) {
            viewModel.setError(e.message ?: "网络异常")
        }
    }

    private fun updateQuantities(skuCode: String, locationCode: String, newSkuLocQty: Int, newSkuTotalQty: Int) {
        val current = viewModel.state.value ?: return
        val updatedColors = current.colors.map { color ->
            val updatedSizes = color.sizes?.map { sku ->
                if (sku.sku_code == skuCode) {
                    val updatedLocations = sku.locations?.map { loc ->
                        if (loc.location_code == locationCode) loc.copy(stock_quantity = newSkuLocQty) else loc
                    }
                    sku.copy(locations = updatedLocations, sku_total_quantity = newSkuTotalQty)
                } else sku
            }?.filterNotNull()
            color.copy(sizes = updatedSizes)
        }
        viewModel.loadColors(updatedColors)
    }

    //endregion
}

// ADAPTERS

class ProductColorAdapter(
    private val context: Context,
    private val viewModel: ProductDetailViewModel,
    private val actionCallback: (SkuInfo, LocationStock, WarehouseAction) -> Unit
) : ListAdapter<ColorInfo, ProductColorAdapter.ViewHolder>(ProductColorDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProductColorDetailBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val color = getItem(position)
        holder.bind(color)
    }
    
    inner class ViewHolder(private val binding: ItemProductColorDetailBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(color: ColorInfo) {
            binding.txtColorName.text = color.color
            val totalQty = color.color_total_quantity ?: 0
            binding.txtColorStock.text = "总库存: ${totalQty}"

            // load image
            val url = ApiClient.processImageUrl(color.image_path, context)
            Glide.with(context)
                .load(url)
                .placeholder(R.drawable.ic_launcher_background)
                .into(binding.imgColor)
            
            val skuAdapter = SkuDetailAdapter(context, viewModel, actionCallback)
            binding.recyclerSkuDetails.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = skuAdapter
            }
            skuAdapter.submitList(color.sizes ?: emptyList())
        }
    }
}

class ProductColorDiffCallback : DiffUtil.ItemCallback<ColorInfo>() {
    override fun areItemsTheSame(oldItem: ColorInfo, newItem: ColorInfo) = oldItem.color == newItem.color
    override fun areContentsTheSame(oldItem: ColorInfo, newItem: ColorInfo) = oldItem == newItem
}

class SkuDetailAdapter(
    private val context: Context,
    private val viewModel: ProductDetailViewModel,
    private val actionCallback: (SkuInfo, LocationStock, WarehouseAction) -> Unit
) : ListAdapter<SkuInfo, SkuDetailAdapter.ViewHolder>(SkuDetailDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSkuLocationDetailBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val sku = getItem(position)
        val state = viewModel.state.value
        holder.bind(sku, state)
    }

    inner class ViewHolder(private val binding: ItemSkuLocationDetailBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(sku: SkuInfo, state: DialogState?) {
            binding.txtSkuSize.text = sku.sku_size?.takeIf { it.isNotBlank() } ?: "N/A"
            binding.txtSkuQuantity.text = "${sku.sku_total_quantity ?: 0}件"
            binding.txtSkuLocationCount.text = "占${sku.locations?.size ?: 0}位"

            val isExpanded = state?.expandedSkuCode == sku.sku_code
            binding.imgExpand.rotation = if (isExpanded) 90f else 0f
            binding.layoutLocations.visibility = if (isExpanded) View.VISIBLE else View.GONE
            
            binding.skuRow.setOnClickListener { viewModel.onSkuClicked(sku.sku_code) }

            if (isExpanded) {
                binding.layoutLocations.removeAllViews()
                sku.locations?.forEach { location ->
                     val locationBinding = ItemLocationActionsBinding.inflate(LayoutInflater.from(context), binding.layoutLocations, false)
                     locationBinding.txtLocationInfo.text = "库位: ${location.location_code}"
                     locationBinding.txtLocationQuantity.text = "${location.stock_quantity}件"
                     
                     val isLocationExpanded = state?.expandedLocationCode == location.location_code
                     locationBinding.actionsLayout.visibility = if (isLocationExpanded) View.VISIBLE else View.GONE

                     locationBinding.locationRow.setOnClickListener { viewModel.onLocationClicked(location.location_code) }
                     
                     locationBinding.btnInbound.setOnClickListener { actionCallback(sku, location, WarehouseAction.INBOUND) }
                     locationBinding.btnOutbound.setOnClickListener { actionCallback(sku, location, WarehouseAction.OUTBOUND) }
                     locationBinding.btnStocktake.setOnClickListener { actionCallback(sku, location, WarehouseAction.STOCKTAKE) }

                     binding.layoutLocations.addView(locationBinding.root)
                }
            }
        }
    }
}

class SkuDetailDiffCallback : DiffUtil.ItemCallback<SkuInfo>() {
    override fun areItemsTheSame(oldItem: SkuInfo, newItem: SkuInfo) = oldItem.sku_code == newItem.sku_code
    override fun areContentsTheSame(oldItem: SkuInfo, newItem: SkuInfo) = oldItem == newItem
} 