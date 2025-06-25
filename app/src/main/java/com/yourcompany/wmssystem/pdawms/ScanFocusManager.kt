package com.yourcompany.wmssystem.pdawms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.widget.EditText
import java.lang.ref.WeakReference

/**
 * 扫码焦点管理器 - 解决扫码输入到错误窗口的问题
 * 
 * 使用方法：
 * 1. 在Activity的onCreate中调用 ScanFocusManager.register(this, primaryEditText)
 * 2. 在Activity的onDestroy中调用 ScanFocusManager.unregister(this)
 * 3. 可选：设置自定义扫码处理逻辑
 */
object ScanFocusManager {
    
    private const val TAG = "ScanFocusManager"
    
    // 存储已注册的Activity和对应的扫码接收器
    private val registeredActivities = mutableMapOf<String, ActivityInfo>()
    
    // 当前有焦点的Activity
    private var currentFocusedActivity: String? = null
    
    data class ActivityInfo(
        val activityRef: WeakReference<Activity>,
        val primaryEditText: WeakReference<EditText>,
        val scanReceiver: BroadcastReceiver,
        val customHandler: ((String, String?) -> Unit)? = null
    )
    
    /**
     * 注册Activity的扫码处理
     * @param activity 要注册的Activity
     * @param primaryEditText 主要的输入框（扫码默认填入这里）
     * @param customHandler 自定义扫码处理函数 (scanData, action) -> Unit
     */
    fun register(
        activity: Activity, 
        primaryEditText: EditText,
        customHandler: ((String, String?) -> Unit)? = null
    ) {
        val activityName = activity.javaClass.simpleName
        Log.d(TAG, "📝 注册Activity: $activityName")
        
        // 创建扫码接收器
        val scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // 如果已有其他Activity持有焦点，则忽略；
                // 若 currentFocusedActivity 为 null，默认允许当前 Activity 处理扫码
                if (currentFocusedActivity != null && currentFocusedActivity != activityName) {
                    Log.d(TAG, "🚫 $activityName 无焦点，忽略扫码: ${intent?.action}")
                    return
                }
                
                val scanData = extractScanData(intent)
                if (!scanData.isNullOrEmpty()) {
                    Log.d(TAG, "📱 $activityName 处理扫码: $scanData (Action: ${intent?.action})")
                    handleScanData(activityName, scanData, intent?.action)
                }
            }
        }
        
        // 注册广播接收器
        val intentFilter = IntentFilter().apply {
            addAction("android.intent.action.SCANRESULT")
            addAction("android.intent.ACTION_DECODE_DATA")
            addAction("com.symbol.datawedge.api.RESULT_ACTION")
            addAction("com.honeywell.decode.intent.action.SCAN_RESULT")
            addAction("nlscan.action.SCANNER_RESULT")
            addAction("scan.rcv.message")
        }
        
        try {
            activity.registerReceiver(scanReceiver, intentFilter)
            Log.d(TAG, "✅ $activityName 注册扫码接收器成功")
        } catch (e: Exception) {
            Log.e(TAG, "❌ $activityName 注册扫码接收器失败: ${e.message}")
            return
        }
        
        // 存储Activity信息
        registeredActivities[activityName] = ActivityInfo(
            activityRef = WeakReference(activity),
            primaryEditText = WeakReference(primaryEditText),
            scanReceiver = scanReceiver,
            customHandler = customHandler
        )
        
        Log.d(TAG, "📝 $activityName 注册完成，当前已注册: ${registeredActivities.keys}")
    }
    
    /**
     * 注销Activity的扫码处理
     */
    fun unregister(activity: Activity) {
        val activityName = activity.javaClass.simpleName
        Log.d(TAG, "📤 注销Activity: $activityName")
        
        registeredActivities[activityName]?.let { info ->
            try {
                activity.unregisterReceiver(info.scanReceiver)
                Log.d(TAG, "✅ $activityName 注销扫码接收器成功")
            } catch (e: Exception) {
                Log.e(TAG, "❌ $activityName 注销扫码接收器失败: ${e.message}")
            }
        }
        
        registeredActivities.remove(activityName)
        
        if (currentFocusedActivity == activityName) {
            currentFocusedActivity = null
        }
        
        Log.d(TAG, "📤 $activityName 注销完成，剩余已注册: ${registeredActivities.keys}")
    }
    
    /**
     * 设置当前有焦点的Activity
     */
    fun setFocusedActivity(activity: Activity, hasFocus: Boolean) {
        val activityName = activity.javaClass.simpleName
        
        if (hasFocus) {
            currentFocusedActivity = activityName
            Log.d(TAG, "🎯 $activityName 获得焦点")
            
            // 确保主输入框获得焦点
            registeredActivities[activityName]?.primaryEditText?.get()?.let { editText ->
                editText.post {
                    editText.requestFocus()
                }
            }
        } else {
            if (currentFocusedActivity == activityName) {
                currentFocusedActivity = null
            }
            Log.d(TAG, "😴 $activityName 失去焦点")
        }
    }
    
    /**
     * 从Intent中提取扫码数据
     */
    private fun extractScanData(intent: Intent?): String? {
        return when (intent?.action) {
            "android.intent.action.SCANRESULT" -> {
                intent.getStringExtra("value") ?: intent.getStringExtra("SCAN_RESULT")
            }
            "android.intent.ACTION_DECODE_DATA" -> {
                intent.getStringExtra("barcode_string") ?: intent.getStringExtra("data")
            }
            "com.symbol.datawedge.api.RESULT_ACTION" -> {
                intent.getStringExtra("com.symbol.datawedge.data_string")
            }
            "com.honeywell.decode.intent.action.SCAN_RESULT" -> {
                intent.getStringExtra("data") ?: intent.getStringExtra("SCAN_RESULT")
            }
            "nlscan.action.SCANNER_RESULT" -> {
                intent.getStringExtra("SCAN_RESULT") ?: intent.getStringExtra("SCAN_BARCODE1")
            }
            "scan.rcv.message" -> {
                intent.getStringExtra("barocode") ?: intent.getStringExtra("barcode")
            }
            else -> null
        }
    }
    
    /**
     * 处理扫码数据
     */
    private fun handleScanData(activityName: String, scanData: String, action: String?) {
        val info = registeredActivities[activityName] ?: return
        val activity = info.activityRef.get() ?: return
        
        activity.runOnUiThread {
            if (info.customHandler != null) {
                // 使用自定义处理器
                info.customHandler.invoke(scanData, action)
                return@runOnUiThread
            }

            // 优先写入当前获得焦点的 EditText（包含 Dialog 内部的输入框）
            val currentFocus = activity.currentFocus
            if (currentFocus is EditText) {
                currentFocus.setText(scanData)
                currentFocus.setSelection(scanData.length)
                Log.d(TAG, "📝 填入当前焦点 EditText: $scanData")
            } else {
                // 否则回退到注册时提供的主输入框
                val fallbackEdit = info.primaryEditText.get()
                if (fallbackEdit != null) {
                    fallbackEdit.requestFocus()
                    fallbackEdit.setText(scanData)
                    fallbackEdit.setSelection(scanData.length)
                    Log.d(TAG, "📝 回退填入主输入框: $scanData")
                }
            }
        }
    }
    
    /**
     * 获取当前有焦点的Activity名称
     */
    fun getCurrentFocusedActivity(): String? = currentFocusedActivity
    
    /**
     * 获取已注册的Activity列表
     */
    fun getRegisteredActivities(): Set<String> = registeredActivities.keys.toSet()
} 