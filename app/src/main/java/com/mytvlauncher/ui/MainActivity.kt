package com.mytvlauncher.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mytvlauncher.R
import com.mytvlauncher.databinding.ActivityMainBinding
import com.mytvlauncher.model.AppEntry
import com.mytvlauncher.model.DockItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private lateinit var appLoader: AppLoader
    private val handler = Handler(Looper.getMainLooper())

    private var apps: List<AppEntry> = emptyList()
    private var dockItems: List<DockItem> = emptyList()
    private val focusedViews = mutableListOf<View>()

    private val columns = 5
    private val cardWidth = 160
    private val cardHeight = 90
    private val cardMargin = 16

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefs = getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)
        appLoader = AppLoader(packageManager)
        
        setupUI()
        loadApps()
        startClock()
    }

    private fun setupUI() {
        binding.searchPill.setOnClickListener {
            showToast("搜索功能开发中")
        }
        
        binding.appsScroll.setOnTouchListener { _, _ ->
            hideUI()
            true
        }
    }

    private fun loadApps() {
        apps = appLoader.loadLaunchableApps()
        renderAppGrid()
        loadDock()
    }

    private fun renderAppGrid() {
        binding.appsFlow.removeAllViews()
        
        var currentRow: LinearLayout? = null
        var cellsInRow = 0
        
        for (app in apps) {
            if (currentRow == null || cellsInRow >= columns) {
                currentRow = createNewRow()
                binding.appsFlow.addView(currentRow)
                cellsInRow = 0
            }
            
            val card = createAppCard(app)
            currentRow!!.addView(card)
            focusedViews.add(card)
            cellsInRow++
        }
    }

    private fun createNewRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(cardMargin, cardMargin / 2, cardMargin, cardMargin / 2)
        }
    }

    private fun createAppCard(app: AppEntry): View {
        return LayoutInflater.from(this)
            .inflate(R.layout.item_app_card, binding.appsFlow, false).apply {
                setTag(app.packageName)
                
                findViewById<ImageView>(R.id.app_icon).setImageDrawable(app.icon)
                findViewById<TextView>(R.id.app_name).text = app.label
                
                setOnClickListener {
                    launchApp(app.packageName)
                }
                
                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        findViewById<FrameLayout>(R.id.card_frame)
                            .background = getDrawable(R.drawable.app_card_focus)
                        animateFocus(this, true)
                    } else {
                        findViewById<FrameLayout>(R.id.card_frame)
                            .background = getDrawable(R.drawable.app_card)
                        animateFocus(this, false)
                    }
                }
                
                setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        handleCardKey(this, keyCode)
                        true
                    } else {
                        false
                    }
                }
            }
    }

    private fun handleCardKey(view: View, keyCode: Int) {
        val position = focusedViews.indexOf(view)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (position > 0) focusedViews[position - 1].requestFocus()
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (position < focusedViews.size - 1) focusedViews[position + 1].requestFocus()
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                val row = position / columns
                val prevRow = position - columns
                if (prevRow >= 0) focusedViews[prevRow].requestFocus()
                else binding.searchPill.requestFocus()
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                val nextRow = position + columns
                if (nextRow < focusedViews.size) {
                    focusedViews[nextRow].requestFocus()
                } else {
                    if (dockItems.isNotEmpty()) {
                        binding.dockRow.getChildAt(0)?.requestFocus()
                    }
                }
            }
        }
    }

    private fun loadDock() {
        binding.dockRow.removeAllViews()
        
        val favoritePackages = prefs.getStringSet("favorite_apps", emptySet()) ?: emptySet()
        dockItems = apps.filter { it.packageName in favoritePackages }
            .map { DockItem(it.packageName, it.label, it.icon) }
            .take(8)
        
        if (dockItems.isEmpty()) {
            dockItems = apps.take(8).map { DockItem(it.packageName, it.label, it.icon) }
        }
        
        for ((index, item) in dockItems.withIndex()) {
            val dockView = LayoutInflater.from(this)
                .inflate(R.layout.item_dock_item, binding.dockRow, false).apply {
                    setTag(item.packageName)
                    findViewById<ImageView>(R.id.dock_icon).setImageDrawable(item.icon)
                    
                    setOnClickListener {
                        launchApp(item.packageName)
                    }
                    
                    setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) {
                            findViewById<ImageView>(R.id.dock_icon)
                                .background = getDrawable(R.drawable.dock_item_focus)
                        } else {
                            findViewById<ImageView>(R.id.dock_icon)
                                .background = getDrawable(R.drawable.dock_item)
                        }
                    }
                    
                    setOnKeyListener { _, keyCode, event ->
                        if (event.action == KeyEvent.ACTION_DOWN) {
                            handleDockKey(keyCode, index)
                            true
                        } else {
                            false
                        }
                    }
                }
            binding.dockRow.addView(dockView)
        }
    }

    private fun handleDockKey(keyCode: Int, index: Int) {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (index > 0) binding.dockRow.getChildAt(index - 1)?.requestFocus()
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (index < dockItems.size - 1) binding.dockRow.getChildAt(index + 1)?.requestFocus()
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (apps.isNotEmpty()) {
                    focusedViews.firstOrNull()?.requestFocus()
                }
            }
        }
    }

    private fun launchApp(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            showToast("无法启动应用: ${e.message}")
        }
    }

    private fun startClock() {
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        
        fun updateClock() {
            val time = format.format(System.currentTimeMillis())
            binding.tvClock.text = time
            
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val greeting = when {
                hour < 12 -> "早上好"
                hour < 18 -> "下午好"
                else -> "晚上好"
            }
            binding.tvGreeting.text = greeting
        }
        
        updateClock()
        handler.postDelayed(object : Runnable {
            override fun run() {
                updateClock()
                handler.postDelayed(this, 60000)
            }
        }, 60000)
    }

    private fun hideUI() {
        handler.removeCallbacksAndMessages(null)
        binding.topBar.alpha = 0f
        binding.dockContainer.alpha = 0f
        handler.postDelayed({
            binding.topBar.visibility = View.GONE
            binding.dockContainer.visibility = View.GONE
        }, 300)
    }

    private fun showUI() {
        binding.topBar.visibility = View.VISIBLE
        binding.dockContainer.visibility = View.VISIBLE
        binding.topBar.alpha = 1f
        binding.dockContainer.alpha = 1f
    }

    private fun animateFocus(view: View, focused: Boolean) {
        val targetScale = if (focused) 1.08f else 1.0f
        view.animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .setDuration(150)
            .start()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                goHome()
                true
            }
            KeyEvent.KEYCODE_SETTINGS -> {
                showToast("设置功能开发中")
                true
            }
            KeyEvent.KEYCODE_INFORMATION -> {
                showToast("关于功能开发中")
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun goHome() {
        val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN)
        homeIntent.addCategory(android.content.Intent.CATEGORY_HOME)
        homeIntent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(homeIntent)
    }

    override fun onResume() {
        super.onResume()
        showUI()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
    }
}
