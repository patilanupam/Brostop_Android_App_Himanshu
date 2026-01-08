package app.brostop.android

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * App Selection Activity - let user choose which apps to block.
 * 
 * Responsibilities:
 * - Display all installed launchable apps
 * - Allow user to toggle which apps to block
 * - Save selected apps to KEY_BLOCKED_PACKAGES
 * - Show progress bar while loading apps
 */
class AppSelectionActivity : AppCompatActivity() {

    private lateinit var adapter: AppAdapter
    private val selectedPackages = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_selection)

        val rvApps = findViewById<RecyclerView>(R.id.rvApps)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val btnSave = findViewById<Button>(R.id.btnSaveTargets)

        // Show progress bar initially
        progressBar.visibility = View.VISIBLE
        rvApps.visibility = View.GONE

        // Load previously saved apps using centralized constants
        val prefs = getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val savedSet = prefs.getStringSet(PrefsConstants.KEY_BLOCKED_PACKAGES, emptySet()) ?: emptySet()
        selectedPackages.addAll(savedSet)

        rvApps.layoutManager = LinearLayoutManager(this)

        // Fetch Apps in Background
        Thread {
            val apps = getInstalledApps(savedSet)
            runOnUiThread {
                adapter = AppAdapter(apps) { pkg, isChecked ->
                    if (isChecked) selectedPackages.add(pkg) else selectedPackages.remove(pkg)
                }
                rvApps.adapter = adapter
                progressBar.visibility = View.GONE
                rvApps.visibility = View.VISIBLE
            }
        }.start()

        btnSave.setOnClickListener {
            // Save selected packages using centralized constant
            prefs.edit().putStringSet(PrefsConstants.KEY_BLOCKED_PACKAGES, selectedPackages).apply()
            finish()
        }
    }

    private fun getInstalledApps(selectedSet: Set<String>): List<AppInfo> {
        val apps = mutableListOf<AppInfo>()
        val packageManager = packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
        intent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)

        val allApps = packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { pkg ->
                pkg != packageName  // Exclude self
                    && !isKeyboardApp(pkg)  // Exclude keyboard
                    && isLaunchable(pkg)  // Only launchable apps
            }

        // Selected apps first, then alphabetical
        val selectedApps = allApps.filter { it in selectedSet }
        val unselectedApps = allApps.filter { it !in selectedSet }

        (selectedApps + unselectedApps).forEach { pkg ->
            try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                val appIcon = packageManager.getApplicationIcon(pkg)
                apps.add(AppInfo(appName, pkg, appIcon, pkg in selectedSet))
            } catch (e: Exception) {
                android.util.Log.e("AppSelectionActivity", "Failed to load app: $pkg", e)
            }
        }

        return apps
    }

    private fun isLaunchable(pkg: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        return intent != null
    }

    private fun isKeyboardApp(pkg: String): Boolean {
        val inputMethodSettings = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: ""
        return inputMethodSettings.contains(pkg)
    }
}

/**
 * RecyclerView adapter for displaying apps with toggle switches.
 */
class AppAdapter(
    private val apps: List<AppInfo>,
    private val onToggle: (String, Boolean) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    inner class AppViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        private val imgAppIcon = itemView.findViewById<ImageView>(R.id.imgAppIcon)
        private val tvAppName = itemView.findViewById<TextView>(R.id.tvAppName)
        private val switchApp = itemView.findViewById<SwitchMaterial>(R.id.switchApp)

        fun bind(app: AppInfo) {
            imgAppIcon.setImageDrawable(app.icon)
            tvAppName.text = app.name
            
            // Clear listener before updating state to prevent recycled view triggers
            switchApp.setOnCheckedChangeListener(null)
            switchApp.isChecked = app.isSelected

            // Reattach listener after state is set
            switchApp.setOnCheckedChangeListener { _, isChecked ->
                app.isSelected = isChecked
                onToggle(app.packageName, isChecked)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(apps[position])
    }

    override fun getItemCount() = apps.size
}
