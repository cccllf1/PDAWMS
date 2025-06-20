package com.yourcompany.wmssystem.pdawms

import android.app.Activity
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * 统一导航栏组件 - 类似Web版本的MobileNavBar
 * 在各个页面顶部显示统一的Tab导航按钮
 */
class UnifiedNavBar(
    private val activity: Activity,
    private val container: LinearLayout,
    private val currentPage: String
) {
    
    // 导航按钮
    private lateinit var btnNavInventory: Button
    private lateinit var btnNavInbound: Button
    private lateinit var btnNavOutbound: Button
    private lateinit var btnNavScan: Button
    private lateinit var tvNavTitle: TextView
    
    // 页面标题映射
    private val pageTitles = mapOf(
        "inventory" to "库存管理",
        "inbound" to "入库管理", 
        "outbound" to "出库管理",
        "scan" to "扫码录入"
    )
    
    init {
        initNavBar()
        updateCurrentPage()
        setupClickListeners()
    }
    
    /**
     * 初始化导航栏
     */
    private fun initNavBar() {
        val inflater = LayoutInflater.from(activity)
        val navBarView = inflater.inflate(R.layout.unified_nav_bar, container, false)
        
        // 添加到容器的顶部
        container.addView(navBarView, 0)
        
        // 获取控件引用
        tvNavTitle = navBarView.findViewById(R.id.tvNavTitle)
        btnNavInventory = navBarView.findViewById(R.id.btnNavInventory)
        btnNavInbound = navBarView.findViewById(R.id.btnNavInbound)
        btnNavOutbound = navBarView.findViewById(R.id.btnNavOutbound)
        btnNavScan = navBarView.findViewById(R.id.btnNavScan)
    }
    
    /**
     * 更新当前页面状态
     */
    private fun updateCurrentPage() {
        // 设置页面标题
        tvNavTitle.text = pageTitles[currentPage] ?: "WMS系统"
        
        // 重置所有按钮状态
        resetAllButtons()
        
        // 设置当前页面按钮为选中状态
        when (currentPage) {
            "inventory" -> btnNavInventory.isSelected = true
            "inbound" -> btnNavInbound.isSelected = true
            "outbound" -> btnNavOutbound.isSelected = true
            "scan" -> btnNavScan.isSelected = true
        }
    }
    
    /**
     * 重置所有按钮状态
     */
    private fun resetAllButtons() {
        btnNavInventory.isSelected = false
        btnNavInbound.isSelected = false
        btnNavOutbound.isSelected = false
        btnNavScan.isSelected = false
    }
    
    /**
     * 设置点击事件监听器
     */
    private fun setupClickListeners() {
        btnNavInventory.setOnClickListener {
            if (currentPage != "inventory") {
                navigateToPage(InventoryActivity::class.java)
            }
        }
        
        btnNavInbound.setOnClickListener {
            if (currentPage != "inbound") {
                navigateToPage(InboundActivity::class.java)
            }
        }
        
        btnNavOutbound.setOnClickListener {
            if (currentPage != "outbound") {
                navigateToPage(OutboundActivity::class.java)
            }
        }
        
        btnNavScan.setOnClickListener {
            if (currentPage != "scan") {
                navigateToPage(ScanActivity::class.java)
            }
        }
    }
    
    /**
     * 页面跳转
     */
    private fun navigateToPage(targetActivity: Class<*>) {
        val intent = Intent(activity, targetActivity)
        // 清除当前Activity的回退栈，避免返回时重复跳转
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        activity.startActivity(intent)
        activity.overridePendingTransition(0, 0) // 禁用Activity切换动画
    }
    
    companion object {
        /**
         * 便捷方法：在Activity中快速添加统一导航栏
         * @param activity 当前Activity
         * @param container 导航栏容器
         * @param currentPage 当前页面标识
         */
        fun addToActivity(activity: Activity, container: LinearLayout, currentPage: String): UnifiedNavBar {
            return UnifiedNavBar(activity, container, currentPage)
        }
    }
} 