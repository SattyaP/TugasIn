package com.nrp_231111017_satyagardaprasetyo.tugasin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.DynamicColors
import com.nrp_231111017_satyagardaprasetyo.tugasin.utils.TaskDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class DashboardActivity : AppCompatActivity() {

    private lateinit var tvTime: TextView
    private lateinit var tvDate: TextView
    private lateinit var rvTasks: RecyclerView
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var dbHelper: TaskDatabaseHelper
    private lateinit var syncBtn: ImageButton
    private lateinit var adapter: TaskAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        DynamicColors.applyToActivitiesIfAvailable(application)

        // Hide action bar
        supportActionBar?.hide()

        // Initialize views
        tvTime = findViewById(R.id.tvTime)
        tvDate = findViewById(R.id.tvDate)
        rvTasks = findViewById(R.id.rvTasks)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        dbHelper = TaskDatabaseHelper(this)
        syncBtn = findViewById<ImageButton>(R.id.syncBtn)

        updateTimeAndDate()

        updateTimeRunnable.run()

        setupBottomNavigation()

        val loadingOverlay = findViewById<FrameLayout>(R.id.loadingOverlay)
        val tvNoTasksMessage = findViewById<TextView>(R.id.tvNoTasksMessage)

        val credentials = getSavedCredentials()
        if (credentials != null) {
            val (email, password) = credentials
            findViewById<TextView>(R.id.tvGreeting).text = "Halo, ${email.split("@")[0]}!"

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    loadingOverlay.visibility = View.VISIBLE
                    rvTasks.visibility = View.GONE
                    disableMenu()

                    val isSynced =
                        dbHelper.syncTasksIfNeeded(this@DashboardActivity, email, password)

                    Log.d(
                        "TASK_DEBUG",
                        if (isSynced) "Tasks synced successfully" else "No new tasks to sync"
                    )

                    val allTasks = dbHelper.getAllTasks(this@DashboardActivity)
                    Log.d("TASK_DEBUG", "Total tasks: ${allTasks.size}")

                    val filteredTasks = allTasks
                        .filter { it.taskType.equals("Recently overdue", ignoreCase = true) }
                        .toMutableList()
                    Log.d("TASK_DEBUG", "Filtered tasks: ${filteredTasks.size}")

                    withContext(Dispatchers.Main) {
                        if (filteredTasks.isEmpty()) {
                            tvNoTasksMessage.visibility = View.VISIBLE
                            rvTasks.visibility = View.GONE
                        } else {
                            adapter = TaskAdapter(
                                filteredTasks,
                                { task ->  },
                                R.layout.item_task,
                                { url ->
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    startActivity(intent)
                                },
                                isDashboard = true
                            )
                            rvTasks.layoutManager = LinearLayoutManager(this@DashboardActivity)
                            rvTasks.adapter = adapter

                            rvTasks.visibility = View.VISIBLE
                            tvNoTasksMessage.visibility = View.GONE
                        }

                        loadingOverlay.visibility = View.GONE
                        enableMenu()
                    }
                } catch (e: Exception) {
                    Log.e("TASK_SYNC_ERROR", "Error syncing or loading tasks", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@DashboardActivity,
                            "Failed to load tasks",
                            Toast.LENGTH_SHORT
                        ).show()
                        loadingOverlay.visibility = View.GONE
                        rvTasks.visibility = View.VISIBLE
                    }
                }
            }

            syncBtn.setOnClickListener {
                val alertDialog = androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Sync Tasks")
                    .setMessage("Are you sure you want to sync tasks?")
                    .setPositiveButton("Yes") { _, _ ->
                        loadingOverlay.visibility = View.VISIBLE
                        rvTasks.visibility = View.GONE
                        disableMenu()

                        lifecycleScope.launch {
                            try {
                                Log.d("TASK_SYNC", "Starting manual sync...")
                                val isSynced =
                                    dbHelper.syncTasksManual(
                                        this@DashboardActivity,
                                        email,
                                        password
                                    )

                                Log.d(
                                    "TASK_DEBUG",
                                    if (isSynced) "Tasks synced successfully" else "No new tasks to sync"
                                )

                                withContext(Dispatchers.Main) {
                                    loadingOverlay.visibility = View.GONE
                                    rvTasks.visibility = View.VISIBLE

                                    Toast.makeText(
                                        this@DashboardActivity,
                                        if (isSynced) "Tasks synced successfully" else "No new tasks to sync",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    if (isSynced) {
                                        val updatedTasks = dbHelper.getAllTasks(this@DashboardActivity)
                                            .filter { it.taskType.equals("Recently overdue", ignoreCase = true) }
                                            .toMutableList()

                                        adapter.updateTasks(updatedTasks)
                                    }
                                }

                                enableMenu()
                            } catch (e: Exception) {
                                Log.e("TASK_SYNC_ERROR", "Error syncing tasks", e)
                                withContext(Dispatchers.Main) {
                                    loadingOverlay.visibility = View.GONE
                                    Toast.makeText(
                                        this@DashboardActivity,
                                        "Failed to sync tasks",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }
                    .setNegativeButton("No") { dialog, _ ->
                        // Dismiss the dialog
                        dialog.dismiss()
                    }
                    .create()

                alertDialog.show()
            }
        }
    }

    private fun updateTimeAndDate() {
        val calendar = Calendar.getInstance()

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        tvTime.text = timeFormat.format(calendar.time)

        val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID"))
        tvDate.text = dateFormat.format(calendar.time)
    }

    private fun getSavedCredentials(): Pair<String, String>? {
        val db = dbHelper.readableDatabase
        val cursor = db.query("credentials", null, null, null, null, null, null)
        var result: Pair<String, String>? = null

        if (cursor.moveToFirst()) {
            val email = cursor.getString(cursor.getColumnIndexOrThrow("email"))
            val password = cursor.getString(cursor.getColumnIndexOrThrow("password"))
            result = Pair(email, password)
        }
        cursor.close()
        return result
    }

    private val handler = Handler(Looper.getMainLooper())
    private val updateTimeRunnable = object : Runnable {
        override fun run() {
            updateTimeAndDate()
            handler.postDelayed(this, 1000)
        }
    }

    fun setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    // Already on home
                    overridePendingTransition(0, 0)
                    return@setOnItemSelectedListener true
                }

                R.id.navigation_tasks -> {
                    val intent = Intent(this, TasksActivity::class.java)
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                    return@setOnItemSelectedListener true
                }

                R.id.navigation_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    overridePendingTransition(0,0)
                    return@setOnItemSelectedListener true
                }

                else -> false
            }
        }

        // Set home as selected
        bottomNavigation.selectedItemId = R.id.navigation_home
    }

    fun disableMenu() {
        val menu = bottomNavigation.menu
        for (i in 0 until menu.size()) {
            menu.getItem(i).isEnabled = false
        }

        syncBtn.isEnabled = false
    }

    fun enableMenu() {
        val menu = bottomNavigation.menu
        for (i in 0 until menu.size()) {
            menu.getItem(i).isEnabled = true
        }

        syncBtn.isEnabled = true
    }
}