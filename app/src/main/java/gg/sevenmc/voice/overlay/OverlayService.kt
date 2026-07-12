package gg.sevenmc.voice.overlay

import android.animation.ValueAnimator
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.core.view.isVisible
import gg.sevenmc.voice.R
import gg.sevenmc.voice.databinding.OverlayFrameBinding
import gg.sevenmc.voice.service.VoiceChatService
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var binding: OverlayFrameBinding
    private var overlayView: View? = null
    private var isExpanded = false

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private val CLICK_THRESHOLD = 10f

    private lateinit var params: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupOverlay()
    }

    private fun setupOverlay() {
        binding = OverlayFrameBinding.inflate(LayoutInflater.from(this))
        overlayView = binding.root

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 200
        }

        windowManager.addView(overlayView, params)
        showBubble()
        setupTouchListeners()
        setupButtonListeners()
    }

    private fun showBubble() {
        binding.bubbleContainer.isVisible = true
        binding.panelContainer.isVisible = false
        isExpanded = false
        params.width = WindowManager.LayoutParams.WRAP_CONTENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        windowManager.updateViewLayout(overlayView, params)
    }

    private fun showPanel() {
        binding.bubbleContainer.isVisible = false
        binding.panelContainer.isVisible = true
        isExpanded = true
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        windowManager.updateViewLayout(overlayView, params)
    }

    private fun setupTouchListeners() {
        binding.bubbleContainer.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    windowManager.updateViewLayout(overlayView, params)
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = abs(event.rawX - initialTouchX)
                    val dy = abs(event.rawY - initialTouchY)
                    if (dx < CLICK_THRESHOLD && dy < CLICK_THRESHOLD) {
                        showPanel()
                    } else {
                        snapToEdge()
                    }
                    true
                }
                else -> false
            }
        }

        binding.panelContainer.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                showBubble()
                true
            } else false
        }
    }

    private fun snapToEdge() {
        val display = windowManager.currentWindowMetrics.bounds
        val screenWidth = display.width()
        val targetX = if (params.x + 80 < screenWidth / 2) 20 else screenWidth - 100
        val animator = ValueAnimator.ofInt(params.x, targetX)
        animator.duration = 200
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener {
            params.x = it.animatedValue as Int
            windowManager.updateViewLayout(overlayView, params)
        }
        animator.start()
    }

    private fun setupButtonListeners() {
        binding.btnMute.setOnClickListener {
            val intent = Intent(this, VoiceChatService::class.java)
            sendBroadcast(Intent("gg.sevenmc.voice.TOGGLE_MUTE"))
            updateMuteIcon()
        }

        binding.btnDeafen.setOnClickListener {
            sendBroadcast(Intent("gg.sevenmc.voice.TOGGLE_DEAFEN"))
            updateDeafenIcon()
        }

        binding.btnDisconnect.setOnClickListener {
            val intent = Intent(this, VoiceChatService::class.java).apply {
                action = VoiceChatService.ACTION_DISCONNECT
            }
            startService(intent)
            stopSelf()
        }

        binding.btnCollapse.setOnClickListener {
            showBubble()
        }
    }

    private fun updateMuteIcon() {
        val isMuted = !binding.btnMute.isSelected
        binding.btnMute.isSelected = isMuted
        binding.btnMute.setImageResource(
            if (isMuted) R.drawable.ic_mic_off else R.drawable.ic_mic
        )
        binding.ivBubbleMicStatus.setImageResource(
            if (isMuted) R.drawable.ic_mic_off else R.drawable.ic_mic
        )
    }

    private fun updateDeafenIcon() {
        val isDeafened = !binding.btnDeafen.isSelected
        binding.btnDeafen.isSelected = isDeafened
        binding.btnDeafen.setImageResource(
            if (isDeafened) R.drawable.ic_headset_off else R.drawable.ic_headset
        )
    }

    fun setSpeaking(speaking: Boolean) {
        binding.ivBubble.setBackgroundResource(
            if (speaking) R.drawable.bg_bubble_speaking else R.drawable.bg_bubble_idle
        )
    }

    override fun onDestroy() {
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
        super.onDestroy()
    }
}
