package app.brostop.android

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.MenuItem
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/**
 * Main dashboard activity.
 * 
 * Responsibilities:
 * - Enforce setup gate (redirect to SetupActivity if not done)
 * - Display dashboard with permission status
 * - Navigate to configuration activities
 * - Check for app updates
 */
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var imgAccessibilityStatus: ImageView
    private lateinit var imgOverlayStatus: ImageView

    private companion object {
        private const val UPDATE_JSON_URL = "https://gist.githubusercontent.com/panwardev687/cc392fb365d855d250784227b41ffd34/raw/version.json"
        private const val NOTIFICATION_CHANNEL_ID = "update_channel"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            checkForUpdates()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Rule 1: Setup Gate - enforce setup completion
        val prefs = getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val isSetupDone = prefs.getBoolean(PrefsConstants.KEY_IS_SETUP_DONE, false)

        if (!isSetupDone) {
            Log.d("MainActivity", "Setup not done, redirecting to SetupActivity")
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        setupUI(prefs)
        askNotificationPermission()
    }

    private fun setupUI(prefs: android.content.SharedPreferences) {
        // Setup navigation drawer
        drawerLayout = findViewById(R.id.drawer_layout)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)

        val toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, 0, 0)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // Setup status indicators
        imgAccessibilityStatus = findViewById(R.id.imgAccessibilityStatus)
        imgOverlayStatus = findViewById(R.id.imgOverlayStatus)

        // Welcome greeting
        val userName = prefs.getString(PrefsConstants.KEY_USER_NAME, "User") ?: "User"
        val gender = prefs.getString(PrefsConstants.KEY_USER_GENDER, Defaults.DEFAULT_GENDER) ?: Defaults.DEFAULT_GENDER
        val genderedGreeting = if (gender == "he") "Bro" else "Sis"
        findViewById<TextView>(R.id.tvWelcome).text = "$userName $genderedGreeting"

        // Setup buttons
        findViewById<Button>(R.id.btnPermission).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnOverlayPermission).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SetupActivity::class.java))
        }

        findViewById<Button>(R.id.btnSelectApps).setOnClickListener {
            startActivity(Intent(this, AppSelectionActivity::class.java))
        }

        findViewById<Button>(R.id.btnSystemConfig).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_privacy_policy -> {
                val url = "https://www.notion.so/Privacy-policy-BroStop-2d3a90b313ff80e0933df927f018d490"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            R.id.action_rate_us -> {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
                } catch (e: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
                }
            }
            R.id.action_app_guide -> {
                val url = "https://www.notion.so/BroStop-App-Usage-Guide-2d3a90b313ff80f986d8faf4b280ab03?source=copy_link"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                checkForUpdates()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkForUpdates() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val jsonString = URL(UPDATE_JSON_URL).readText()
                val json = JSONObject(jsonString)

                val latestVersionCode = json.getInt("latestVersionCode")
                val currentVersionCode = packageManager.getPackageInfo(packageName, 0).versionCode

                if (latestVersionCode > currentVersionCode) {
                    val releaseNotes = json.getString("releaseNotes")
                    val updateUrl = json.getString("updateUrl")
                    withContext(Dispatchers.Main) {
                        showUpdateNotification(releaseNotes, updateUrl)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to check for updates", e)
            }
        }
    }

    private fun showUpdateNotification(releaseNotes: String, updateUrl: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "App Updates",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl))
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check_circle)
            .setContentTitle("New Update Available!")
            .setContentText(releaseNotes)
            .setStyle(NotificationCompat.BigTextStyle().bigText(releaseNotes))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1, notification)
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun updatePermissionStatus() {
        imgOverlayStatus.setImageResource(
            if (Settings.canDrawOverlays(this)) R.drawable.ic_check_circle else R.drawable.ic_cancel
        )

        imgAccessibilityStatus.setImageResource(
            if (isAccessibilityServiceEnabled()) R.drawable.ic_check_circle else R.drawable.ic_cancel
        )
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${packageName}/${ScrollGuardService::class.java.canonicalName}"
        return try {
            val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            colonSplitter.any { it.equals(service, ignoreCase = true) }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to check accessibility service", e)
            false
        }
    }
}
