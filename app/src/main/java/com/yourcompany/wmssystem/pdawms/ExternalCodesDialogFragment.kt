package com.yourcompany.wmssystem.pdawms

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class ExternalCodesDialogFragment : DialogFragment() {
    
    private lateinit var txtDialogTitle: TextView
    private lateinit var txtSkuInfo: TextView
    private lateinit var editNewCode: EditText
    private lateinit var btnAddCode: Button
    private lateinit var txtCodeCount: TextView
    private lateinit var recyclerCodes: RecyclerView
    private lateinit var btnCancel: Button
    private lateinit var btnConfirm: Button
    
    private lateinit var externalCodesAdapter: ExternalCodesAdapter
    private var skuInfo: SkuInfo? = null
    private var productCode: String = ""
    private var color: String = ""
    
    companion object {
        fun newInstance(sku: SkuInfo, productCode: String, color: String): ExternalCodesDialogFragment {
            val fragment = ExternalCodesDialogFragment()
            val args = Bundle().apply {
                putString("sku_code", sku.sku_code)
                putString("sku_size", sku.sku_size)
                putInt("sku_quantity", sku.sku_total_quantity ?: 0)
                putString("product_code", productCode)
                putString("color", color)
                
                // 传递外部条码列表
                val externalCodes = sku.external_codes?.map { it.external_code }?.toTypedArray() ?: arrayOf()
                putStringArray("external_codes", externalCodes)
            }
            fragment.arguments = args
            return fragment
        }
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return dialog
    }
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_external_codes, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        editNewCode.requestFocus()
        ScanFocusManager.setFocusedActivity(requireActivity(), true)
        setupData()
        setupRecyclerView()
        setupClickListeners()
    }
    
    private fun initViews(view: View) {
        txtDialogTitle = view.findViewById(R.id.txtDialogTitle)
        txtSkuInfo = view.findViewById(R.id.txtSkuInfo)
        editNewCode = view.findViewById(R.id.editNewCode)
        btnAddCode = view.findViewById(R.id.btnAddCode)
        txtCodeCount = view.findViewById(R.id.txtCodeCount)
        recyclerCodes = view.findViewById(R.id.recyclerCodes)
        btnCancel = view.findViewById(R.id.btnCancel)
        btnConfirm = view.findViewById(R.id.btnConfirm)
    }
    
    private fun setupData() {
        val args = arguments ?: return
        
        val skuCode = args.getString("sku_code", "")
        val skuSize = args.getString("sku_size", "")
        val skuQuantity = args.getInt("sku_quantity", 0)
        productCode = args.getString("product_code", "")
        color = args.getString("color", "")
        
        // 设置标题
        txtDialogTitle.text = "外部条码管理: $skuCode"
        
        // 设置SKU信息
        txtSkuInfo.text = "商品: $productCode\n颜色: $color\n尺码: $skuSize"
        
        // 创建SKU信息对象
        val externalCodes = args.getStringArray("external_codes")?.map { 
            ExternalCode(it) 
        } ?: emptyList()
        
        skuInfo = SkuInfo(
            sku_code = skuCode,
            sku_color = color,
            sku_size = skuSize,
            image_path = null,
            stock_quantity = skuQuantity,
            sku_total_quantity = skuQuantity,
            locations = null,
            external_codes = externalCodes
        )
    }
    
    private fun setupRecyclerView() {
        // 先初始化空的适配器
        externalCodesAdapter = ExternalCodesAdapter(
            codes = mutableListOf(),
            onDeleteClick = { position -> deleteExternalCode(position) }
        )
        
        recyclerCodes.layoutManager = LinearLayoutManager(context)
        recyclerCodes.adapter = externalCodesAdapter
        
        // 从API获取最新的外部条码数据
        loadExternalCodes()
    }
    
    private fun loadExternalCodes() {
        val skuCode = skuInfo?.sku_code ?: return
        
        lifecycleScope.launch {
            try {
                Log.d("ExternalCodes", "加载外部条码: SKU=$skuCode")
                val response = ApiClient.getApiService().getSkuExternalCodes(skuCode)
                
                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse?.success == true && apiResponse.data != null) {
                        // 更新UI显示外部条码
                        activity?.runOnUiThread {
                            val codes = apiResponse.data.map { it.external_code }
                            
                            externalCodesAdapter.codes.clear()
                            externalCodesAdapter.codes.addAll(codes)
                            externalCodesAdapter.notifyDataSetChanged()
                            updateCodeCount()
                            
                            Log.d("ExternalCodes", "加载到${codes.size}个外部条码: $codes")
                        }
                    } else {
                        Log.d("ExternalCodes", "获取外部条码失败: ${apiResponse?.error_message}")
                        activity?.runOnUiThread {
                            updateCodeCount()
                        }
                    }
                } else {
                    Log.e("ExternalCodes", "获取外部条码HTTP失败: ${response.code()}")
                    activity?.runOnUiThread {
                        updateCodeCount()
                    }
                }
            } catch (e: Exception) {
                Log.e("ExternalCodes", "获取外部条码异常", e)
                activity?.runOnUiThread {
                    updateCodeCount()
                }
            }
        }
    }
    
    private fun setupClickListeners() {
        btnAddCode.setOnClickListener { addExternalCode() }
        btnCancel.setOnClickListener { dismiss() }
        btnConfirm.setOnClickListener { dismiss() }
    }
    
    private fun addExternalCode() {
        val newCode = editNewCode.text.toString().trim()
        if (newCode.isEmpty()) {
            Toast.makeText(context, "请输入外部条码", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 检查是否已存在
        if (externalCodesAdapter.codes.contains(newCode)) {
            Toast.makeText(context, "该条码已存在", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 调用API添加外部条码
        lifecycleScope.launch {
            try {
                val skuCode = skuInfo?.sku_code ?: return@launch
                Log.d("ExternalCodes", "添加外部条码: SKU=$skuCode, Code=$newCode")
                
                val requestBody = mapOf(
                    "external_code" to newCode,
                    "operator_id" to "684c5acd5cf064a67653d0c0"  // TODO: 从登录信息获取实际的用户ID
                )
                
                val response = ApiClient.getApiService().addExternalCode(skuCode, requestBody)
                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse?.success == true) {
                        // 添加成功，更新UI
                        activity?.runOnUiThread {
                            externalCodesAdapter.addCode(newCode)
                            editNewCode.setText("")
                            updateCodeCount()
                            Toast.makeText(context, "添加成功", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        activity?.runOnUiThread {
                            Toast.makeText(context, "添加失败: ${apiResponse?.error_message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    activity?.runOnUiThread {
                        Toast.makeText(context, "添加失败: HTTP ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ExternalCodes", "添加外部条码异常", e)
                activity?.runOnUiThread {
                    Toast.makeText(context, "添加失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun deleteExternalCode(position: Int) {
        val codeToDelete = externalCodesAdapter.codes[position]
        
        // 调用API删除外部条码
        lifecycleScope.launch {
            try {
                val skuCode = skuInfo?.sku_code ?: return@launch
                Log.d("ExternalCodes", "删除外部条码: SKU=$skuCode, Code=$codeToDelete")
                
                val requestBody = mapOf(
                    "operator_id" to "684c5acd5cf064a67653d0c0"  // TODO: 从登录信息获取实际的用户ID
                )
                
                val response = ApiClient.getApiService().deleteExternalCode(skuCode, codeToDelete, requestBody)
                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse?.success == true) {
                        // 删除成功，更新UI
                        activity?.runOnUiThread {
                            externalCodesAdapter.removeCode(position)
                            updateCodeCount()
                            Toast.makeText(context, "删除成功", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        activity?.runOnUiThread {
                            Toast.makeText(context, "删除失败: ${apiResponse?.error_message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    activity?.runOnUiThread {
                        Toast.makeText(context, "删除失败: HTTP ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ExternalCodes", "删除外部条码异常", e)
                activity?.runOnUiThread {
                    Toast.makeText(context, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun updateCodeCount() {
        val count = externalCodesAdapter.codes.size
        txtCodeCount.text = "($count)"
    }
}

// 外部条码适配器
class ExternalCodesAdapter(
    val codes: MutableList<String>,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<ExternalCodesAdapter.ViewHolder>() {
    
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtExternalCode: TextView = itemView.findViewById(R.id.txtExternalCode)
        val btnDelete: Button = itemView.findViewById(R.id.btnDelete)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_external_code, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val code = codes[position]
        holder.txtExternalCode.text = code
        
        holder.btnDelete.setOnClickListener {
            onDeleteClick(position)
        }
    }
    
    override fun getItemCount(): Int = codes.size
    
    fun addCode(code: String) {
        codes.add(code)
        notifyItemInserted(codes.size - 1)
    }
    
    fun removeCode(position: Int) {
        if (position >= 0 && position < codes.size) {
            codes.removeAt(position)
            notifyItemRemoved(position)
        }
    }
} 