package com.aitextassistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.aitextassistant.data.PreferencesManager
import com.aitextassistant.model.OperationMode
import com.aitextassistant.model.TriggerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class TextAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "TextAccessibilityService"
        
        @Volatile
        var isRunning = false
            private set
        
        private val lastProcessedEvents = ConcurrentHashMap<String, Long>()
        private const val DEBOUNCE_MS = 500L
        private const val CLIPBOARD_DEBOUNCE_MS = 1000L
        
        @Volatile
        var instance: TextAccessibilityService? = null
    }
    
    private lateinit var prefsManager: PreferencesManager
    private lateinit var clipboardManager: ClipboardManager
    private var lastClipboardText: String = ""
    private var lastClipboardTime: Long = 0
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var currentSelectedText: String = ""
    private var lastSelectedText: String = ""
    private var lastSelectionTime: Long = 0
    
    private val handler = Handler(Looper.getMainLooper())
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "服务创建")
        
        prefsManager = PreferencesManager.getInstance(this)
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        instance = this
        
        startClipboardMonitoring()
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "服务已连接")
        isRunning = true
        
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_LONG_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_SELECTED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED
            
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            canRetrieveWindowContent = true
        }
        
        startFloatingButtonService()
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                handleTextSelectionEvent(event)
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                handleClickEvent(event)
            }
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                handleLongClickEvent(event)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handleWindowContentChanged(event)
            }
        }
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "服务中断")
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "服务解绑")
        isRunning = false
        instance = null
        stopFloatingButtonService()
        return super.onUnbind(intent)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "服务销毁")
        isRunning = false
        instance = null
        serviceScope.cancel()
        stopClipboardMonitoring()
        stopFloatingButtonService()
    }
    
    private fun handleTextSelectionEvent(event: AccessibilityEvent) {
        val source = event.source ?: return
        
        try {
            val selectedText = extractSelectedText(source)
            
            if (selectedText.isNotBlank() && selectedText != lastSelectedText) {
                if (shouldProcessEvent("selection", selectedText)) {
                    lastSelectedText = selectedText
                    currentSelectedText = selectedText
                    lastSelectionTime = System.currentTimeMillis()
                    
                    Log.d(TAG, "检测到文本选择: $selectedText")
                    
                    if (prefsManager.getVibrateOnTrigger()) {
                        vibrate()
                    }
                    
                    showFloatingButton(selectedText)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理文本选择事件失败", e)
        } finally {
            source.recycle()
        }
    }
    
    private fun handleClickEvent(event: AccessibilityEvent) {
        handler.postDelayed({
            hideFloatingButton()
        }, 200)
    }
    
    private fun handleLongClickEvent(event: AccessibilityEvent) {
        val source = event.source ?: return
        try {
            val text = source.text?.toString() ?: ""
            if (text.isNotBlank()) {
                Log.d(TAG, "检测到长按: $text")
            }
        } finally {
            source.recycle()
        }
    }
    
    private fun handleWindowContentChanged(event: AccessibilityEvent) {
    }
    
    private fun startClipboardMonitoring() {
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
    }
    
    private fun stopClipboardMonitoring() {
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
    }
    
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastClipboardTime < CLIPBOARD_DEBOUNCE_MS) {
            return@OnPrimaryClipChangedListener
        }
        
        val clipData = clipboardManager.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val item = clipData.getItemAt(0)
            val text = item.text?.toString() ?: ""
            
            if (text.isNotBlank() && text != lastClipboardText && text != lastSelectedText) {
                lastClipboardText = text
                lastClipboardTime = currentTime
                
                Log.d(TAG, "检测到复制操作: $text")
                
                if (prefsManager.getVibrateOnTrigger()) {
                    vibrate()
                }
                
                showFloatingButton(text)
            }
        }
    }
    
    private fun startFloatingButtonService() {
        if (!prefsManager.getShowFloatingButton()) return
        
        val intent = Intent(this, FloatingButtonService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
    
    private fun stopFloatingButtonService() {
        val intent = Intent(this, FloatingButtonService::class.java)
        stopService(intent)
    }
    
    private fun showFloatingButton(text: String) {
        if (!prefsManager.getShowFloatingButton()) return
        
        val intent = Intent(FloatingButtonService.ACTION_SHOW_BUTTON).apply {
            setPackage(packageName)
            putExtra(FloatingButtonService.EXTRA_SELECTED_TEXT, text)
        }
        sendBroadcast(intent)
    }
    
    private fun hideFloatingButton() {
        val intent = Intent(FloatingButtonService.ACTION_HIDE_BUTTON).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
    
    private fun extractSelectedText(node: AccessibilityNodeInfo): String {
        val text = node.text?.toString() ?: ""
        
        if (text.isNotEmpty()) {
            val selectionStart = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                node.textSelectionStart
            } else {
                -1
            }
            
            val selectionEnd = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                node.textSelectionEnd
            } else {
                -1
            }
            
            if (selectionStart >= 0 && selectionEnd > selectionStart && selectionEnd <= text.length) {
                return text.substring(selectionStart, selectionEnd)
            }
        }
        
        return text
    }
    
    private fun shouldProcessEvent(eventType: String, content: String): Boolean {
        val key = "$eventType:$content"
        val currentTime = System.currentTimeMillis()
        val lastTime = lastProcessedEvents[key] ?: 0
        
        return if (currentTime - lastTime > DEBOUNCE_MS) {
            lastProcessedEvents[key] = currentTime
            cleanupOldEvents()
            true
        } else {
            false
        }
    }
    
    private fun cleanupOldEvents() {
        val currentTime = System.currentTimeMillis()
        lastProcessedEvents.entries.removeIf { entry ->
            currentTime - entry.value > DEBOUNCE_MS * 2
        }
    }
    
    private fun vibrate() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        } catch (e: Exception) {
            Log.e(TAG, "振动失败", e)
        }
    }
    
    private fun getCurrentPackageName(): String {
        return rootInActiveWindow?.packageName?.toString() ?: ""
    }
    
    fun performAIOperation(text: String, mode: OperationMode) {
        serviceScope.launch {
            val intent = Intent(this@TextAccessibilityService, ResultDialogActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(ResultDialogActivity.EXTRA_TEXT, text)
                putExtra(ResultDialogActivity.EXTRA_MODE, mode.name)
            }
            startActivity(intent)
        }
    }
}
