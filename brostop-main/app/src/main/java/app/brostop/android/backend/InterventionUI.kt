package app.brostop.android.backend

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.*
import app.brostop.android.Defaults
import app.brostop.android.LogTags
import app.brostop.android.PrefsConstants
import app.brostop.android.R
import kotlin.random.Random

/**
 * Manages overlay UI for roasts, plea bargains, and lockdowns.
 * 
 * Responsibilities:
 * - Display/hide overlay views
 * - Handle roast screen with plea bargain
 * - Handle lockdown screen with countdown
 * - Manage emergency unlock modes
 */
class InterventionUI(
    private val context: Context,
    private val memeProvider: MemeProvider,
    private val penaltyManager: PenaltyManager
) {
    
    private val prefs = context.getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var countdownTimer: CountDownTimer? = null
    private var shameSentence: String = ""
    
    // Callbacks
    var onPleaWin: ((bonusDuration: Int) -> Unit)? = null
    var onPleaLose: (() -> Unit)? = null
    var onExitApp: (() -> Unit)? = null
    var onEmergencyUnlock: ((isGlobal: Boolean, packageName: String) -> Unit)? = null
    
    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    
    /**
     * Show roast overlay with optional plea bargain
     */
    fun showRoast(isGlobal: Boolean, preservedPackage: String) {
        if (overlayView != null) return
        
        val inflater = LayoutInflater.from(context)
        overlayView = inflater.inflate(R.layout.overlay_roast, null)
        
        val gender = prefs.getString(PrefsConstants.KEY_USER_GENDER, Defaults.DEFAULT_GENDER) 
            ?: Defaults.DEFAULT_GENDER
        val isPleaEnabled = prefs.getBoolean(PrefsConstants.KEY_PLEA_ENABLED, Defaults.DEFAULT_PLEA_ENABLED)
        val mercyUsed = prefs.getBoolean(PrefsConstants.KEY_MERCY_USED, false)
        
        // Set title based on gender
        val tvTitle = overlayView!!.findViewById<TextView>(R.id.tvTitle)
        tvTitle.text = if (gender == "he") "🛑 BRO STOP." else "🛑 SIS STOP."
        
        // Set personalized roast
        val tvRoast = overlayView!!.findViewById<TextView>(R.id.tvRoastMessage)
        tvRoast.text = memeProvider.getRoast()
        
        val btnOkay = overlayView!!.findViewById<Button>(R.id.btnOkay)
        val layoutPlea = overlayView!!.findViewById<LinearLayout>(R.id.layoutPleaButtons)
        val tvBonusInfo = overlayView!!.findViewById<TextView>(R.id.tvBonusInfo)
        
        // Show plea bargain if enabled and not used yet
        if (isPleaEnabled && !mercyUsed) {
            btnOkay.visibility = View.GONE
            layoutPlea.visibility = View.VISIBLE
            
            val bonusDuration = prefs.getInt(PrefsConstants.KEY_BONUS_DURATION, Defaults.DEFAULT_BONUS_DURATION)
            tvBonusInfo.visibility = View.VISIBLE
            tvBonusInfo.text = "🎲 Win: Get $bonusDuration extra minutes • 💀 Lose: Instant lockdown"
            
            val btnPleaNo = overlayView!!.findViewById<Button>(R.id.btnPleaNo)
            val btnPleaYes = overlayView!!.findViewById<Button>(R.id.btnPleaYes)
            
            btnPleaNo.setOnClickListener {
                hide()
                onExitApp?.invoke()
            }
            
            btnPleaYes.setOnClickListener {
                handlePleaBargain(bonusDuration, tvRoast, layoutPlea, tvBonusInfo)
            }
        } else {
            btnOkay.visibility = View.VISIBLE
            layoutPlea.visibility = View.GONE
            tvBonusInfo.visibility = View.GONE
            
            btnOkay.setOnClickListener {
                hide()
                onExitApp?.invoke()
            }
        }
        
        addToWindow(needsInput = false)
    }
    
    /**
     * Handle plea bargain logic (20% win chance)
     */
    private fun handlePleaBargain(
        bonusDuration: Int,
        tvRoast: TextView,
        layoutPlea: LinearLayout,
        tvBonusInfo: TextView
    ) {
        Log.d(LogTags.PLEA, "🎲 Plea bargain initiated")
        
        prefs.edit().putBoolean(PrefsConstants.KEY_MERCY_USED, true).apply()
        
        val wonGamble = Random.nextInt(100) < 20 // 20% chance
        
        layoutPlea.visibility = View.GONE
        tvBonusInfo.visibility = View.GONE
        
        if (wonGamble) {
            Log.i(LogTags.PLEA, "✅ User WON plea bargain")
            tvRoast.text = memeProvider.getRoast("plea_won")
            tvRoast.setTextColor(Color.GREEN)
            
            Handler(Looper.getMainLooper()).postDelayed({
                hide()
                onPleaWin?.invoke(bonusDuration)
            }, 2000)
        } else {
            Log.i(LogTags.PLEA, "❌ User LOST plea bargain")
            tvRoast.text = memeProvider.getRoast("2nd_chance_fail")
            tvRoast.setTextColor(Color.RED)
            
            Handler(Looper.getMainLooper()).postDelayed({
                hide()
                onPleaLose?.invoke()
            }, 2000)
        }
    }
    
    /**
     * Show lockdown screen with countdown
     */
    fun showLockdown(unblockTime: Long, isGlobal: Boolean, packageName: String) {
        if (overlayView != null) return
        
        Log.i(LogTags.PENALTY, "🔓 Entering lockdown state")
        
        val inflater = LayoutInflater.from(context)
        overlayView = inflater.inflate(R.layout.overlay_blocked, null)
        
        val emergencyMode = prefs.getString(PrefsConstants.KEY_EMERGENCY_MODE, Defaults.DEFAULT_EMERGENCY_MODE) 
            ?: Defaults.DEFAULT_EMERGENCY_MODE
        
        val tvTimer = overlayView!!.findViewById<TextView>(R.id.tvTimer)
        val btnEmergency = overlayView!!.findViewById<Button>(R.id.btnEmergency)
        val tvSubtitle = overlayView!!.findViewById<TextView>(R.id.tvSubtitle)
        
        // Start countdown
        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(unblockTime - System.currentTimeMillis(), 1000) {
            override fun onTick(ms: Long) {
                val min = (ms / 1000) / 60
                val sec = (ms / 1000) % 60
                tvTimer.text = String.format("%02d:%02d", min, sec)
            }
            
            override fun onFinish() {
                penaltyManager.pardon(isGlobal, packageName)
                hide()
                onExitApp?.invoke()
            }
        }.start()
        
        // Setup emergency unlock based on mode
        when (emergencyMode) {
            "weak" -> {
                tvSubtitle.text = "(Tap to unlock)"
                btnEmergency.setOnClickListener {
                    countdownTimer?.cancel()
                    hide()
                    onEmergencyUnlock?.invoke(isGlobal, packageName)
                }
            }
            "shame" -> {
                tvSubtitle.text = "(Requires Shame Protocol)"
                btnEmergency.setOnClickListener { showShameInput(isGlobal, packageName) }
            }
            "impossible" -> {
                btnEmergency.visibility = View.GONE
                tvSubtitle.text = "No escape. Wait it out."
            }
        }
        
        addToWindow(needsInput = emergencyMode == "shame")
    }
    
    /**
     * Show shame protocol input
     */
    private fun showShameInput(isGlobal: Boolean, packageName: String) {
        overlayView?.findViewById<LinearLayout>(R.id.layoutBlockMain)?.visibility = View.GONE
        overlayView?.findViewById<LinearLayout>(R.id.layoutShameInput)?.visibility = View.VISIBLE
        
        val shameSentences = listOf(
            "I am wasting my time scrolling.",
            "I lack self-control and discipline.",
            "I am weak and easily distracted."
        )
        shameSentence = shameSentences.random()
        
        val tvShameSentence = overlayView?.findViewById<TextView>(R.id.tvShameSentence)
        val etShameInput = overlayView?.findViewById<EditText>(R.id.etShameInput)
        val btnSubmit = overlayView!!.findViewById<Button>(R.id.btnSubmitShame)
        
        tvShameSentence?.text = shameSentence
        
        btnSubmit.setOnClickListener {
            if (etShameInput?.text.toString() == shameSentence) {
                countdownTimer?.cancel()
                hide()
                onEmergencyUnlock?.invoke(isGlobal, packageName)
            } else {
                Toast.makeText(context, "❌ Incorrect. Type it exactly.", Toast.LENGTH_SHORT).show()
                etShameInput?.setText("")
            }
        }
    }
    
    /**
     * Hide overlay
     */
    fun hide() {
        countdownTimer?.cancel()
        overlayView?.let {
            try {
                windowManager?.removeView(it)
                Log.i(LogTags.INTERVENTION, "✅ Overlay removed")
            } catch (e: Exception) {
                Log.e(LogTags.INTERVENTION, "❌ Failed to remove overlay", e)
            }
        }
        overlayView = null
    }
    
    /**
     * Add overlay to window
     */
    private fun addToWindow(needsInput: Boolean) {
        if (overlayView == null) return
        
        if (!Settings.canDrawOverlays(context)) {
            Log.e(LogTags.INTERVENTION, "❌ Cannot add overlay - permission not granted")
            return
        }
        
        val baseFlags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        val finalFlags = if (needsInput) {
            baseFlags
        } else {
            baseFlags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            finalFlags,
            PixelFormat.TRANSLUCENT
        )
        
        params.gravity = Gravity.CENTER
        
        try {
            windowManager?.addView(overlayView, params)
            Log.i(LogTags.INTERVENTION, "✅ Overlay added to window")
        } catch (e: Exception) {
            Log.e(LogTags.INTERVENTION, "❌ Failed to add overlay", e)
        }
    }
    
    /**
     * Check if overlay is currently showing
     */
    fun isShowing(): Boolean = overlayView != null
}
