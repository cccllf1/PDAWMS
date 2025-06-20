package com.yourcompany.wmssystem.pdawms

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
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

        val colorAdapter = ProductColorAdapter(requireContext(), viewModel)
        binding.recyclerColorDetails.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = colorAdapter
        }
        
        binding.txtDialogTitle.text = "${product.product_name} (${product.product_code})"
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
        dialog?.window?.setLayout((resources.displayMetrics.widthPixels * 0.98).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// ADAPTERS

class ProductColorAdapter(
    private val context: Context,
    private val viewModel: ProductDetailViewModel
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

            // load image
            val url = ApiClient.processImageUrl(color.image_path, context)
            Glide.with(context)
                .load(url)
                .placeholder(R.drawable.ic_launcher_background)
                .into(binding.imgColor)
            
            val skuAdapter = SkuDetailAdapter(context, viewModel)
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
    private val viewModel: ProductDetailViewModel
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
                     
                     locationBinding.btnInbound.setOnClickListener { Toast.makeText(context, "入库: ${location.location_code}", Toast.LENGTH_SHORT).show() }
                     locationBinding.btnOutbound.setOnClickListener { Toast.makeText(context, "出库: ${location.location_code}", Toast.LENGTH_SHORT).show() }
                     locationBinding.btnStocktake.setOnClickListener { Toast.makeText(context, "盘点: ${location.location_code}", Toast.LENGTH_SHORT).show() }

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