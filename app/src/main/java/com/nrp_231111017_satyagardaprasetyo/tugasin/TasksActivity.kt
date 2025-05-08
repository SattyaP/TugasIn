package com.nrp_231111017_satyagardaprasetyo.tugasin

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.nrp_231111017_satyagardaprasetyo.tugasin.utils.TaskDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TasksActivity : AppCompatActivity() {
    private lateinit var rvNext7Days: RecyclerView
    private lateinit var rvNext30Days: RecyclerView
    private lateinit var rvOverdue: RecyclerView
    private lateinit var rvFuture: RecyclerView
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var dbHelper: TaskDatabaseHelper
    private lateinit var syncBtn: ImageButton
    private lateinit var adapter: TaskAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasks)

        supportActionBar?.hide()

        // Initializing UI components
        bottomNavigation = findViewById(R.id.bottomNavigation)
        dbHelper = TaskDatabaseHelper(this)
        syncBtn = findViewById(R.id.btnSync)
        rvNext7Days = findViewById(R.id.rvNext7Days)
        rvNext30Days = findViewById(R.id.rvNext30Days)
        rvOverdue = findViewById(R.id.rvOverdue)
        rvFuture = findViewById(R.id.rvFuture)

        setupBottomNavigation()

        val loadingOverlay = findViewById<FrameLayout>(R.id.loadingOverlay)

        loadCredentialsAndTasks(loadingOverlay)

        syncBtn.setOnClickListener {
            val alertDialog = androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Sync Tasks")
                .setMessage("Are you sure you want to sync tasks?")
                .setPositiveButton("Yes") { _, _ ->
                    handleSyncTasks(loadingOverlay)
                }
                .setNegativeButton("No") { dialog, _ ->
                    dialog.dismiss()
                }
                .create()

            alertDialog.show()
        }
    }

    private fun loadCredentialsAndTasks(loadingOverlay: FrameLayout) {
        val credentials = getSavedCredentials()
        if (credentials != null) {
            val (email, password) = credentials
            // Set the greeting text on the main thread
            findViewById<TextView>(R.id.tvGreeting).text = "Halo, ${email.split("@")[0]}!"

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // Start the loading overlay visibility on the main thread
                    withContext(Dispatchers.Main) {
                        loadingOverlay.visibility = View.VISIBLE
                    }

                    // Load tasks in the background thread
                    val allTasks = dbHelper.getAllTasks(this@TasksActivity)

                    // Filter tasks by type in the background
                    val taskLists = mapOf(
                        "Recently overdue" to allTasks.filter {
                            it.taskType.equals(
                                "Recently overdue",
                                ignoreCase = true
                            )
                        },
                        "Next 7 days" to allTasks.filter {
                            it.taskType.equals(
                                "Next 7 days",
                                ignoreCase = true
                            )
                        },
                        "Next 30 days" to allTasks.filter {
                            it.taskType.equals(
                                "Next 30 days",
                                ignoreCase = true
                            )
                        },
                        "Future" to allTasks.filter {
                            it.taskType.equals(
                                "Future",
                                ignoreCase = true
                            )
                        }
                    )

                    // Updating UI must be done on the main thread
                    withContext(Dispatchers.Main) {
                        updateRecyclerView(rvOverdue, taskLists["Recently overdue"] ?: emptyList())
                        updateRecyclerView(rvNext7Days, taskLists["Next 7 days"] ?: emptyList())
                        updateRecyclerView(rvNext30Days, taskLists["Next 30 days"] ?: emptyList())
                        updateRecyclerView(rvFuture, taskLists["Future"] ?: emptyList())

                        // Hide the loading overlay after the UI updates
                        loadingOverlay.visibility = View.GONE
                    }
                } catch (e: Exception) {
                    Log.e("TASK_SYNC_ERROR", "Error loading tasks", e)
                    withContext(Dispatchers.Main) {
                        // Hide the loading overlay on the main thread if an error occurs
                        loadingOverlay.visibility = View.GONE
                        Toast.makeText(
                            this@TasksActivity,
                            "Failed to load tasks",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun handleSyncTasks(loadingOverlay: FrameLayout) {
        loadingOverlay.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                Log.d("TASK_SYNC", "Starting manual sync...")

                val credentials = getSavedCredentials()
                val isSynced = credentials?.let { (email, password) ->
                    dbHelper.syncTasksManual(this@TasksActivity, email, password)
                } ?: false

                Log.d(
                    "TASK_DEBUG",
                    if (isSynced) "Tasks synced successfully" else "No new tasks to sync"
                )

                withContext(Dispatchers.Main) {
                    loadingOverlay.visibility = View.GONE
                    Toast.makeText(
                        this@TasksActivity,
                        if (isSynced) "Tasks synced successfully" else "No new tasks to sync",
                        Toast.LENGTH_SHORT
                    ).show()

                    if (isSynced) {
                        loadCredentialsAndTasks(loadingOverlay)
                    }
                }
            } catch (e: Exception) {
                Log.e("TASK_SYNC_ERROR", "Error syncing tasks", e)
                withContext(Dispatchers.Main) {
                    loadingOverlay.visibility = View.GONE
                    Toast.makeText(this@TasksActivity, "Failed to sync tasks", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    private fun updateRecyclerView(recyclerView: RecyclerView, tasks: List<Task>) {
        if (recyclerView.adapter == null) {
            // Create a new adapter if it's not already set
            adapter = TaskAdapter(tasks.toMutableList(), { task -> }, R.layout.fragment_tasks)
            recyclerView.layoutManager =
                LinearLayoutManager(this@TasksActivity, LinearLayoutManager.HORIZONTAL, false)
            recyclerView.adapter = adapter
        } else {
            // Update the existing adapter's data
            (recyclerView.adapter as TaskAdapter).updateTasks(tasks)
        }
        recyclerView.visibility = View.VISIBLE
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

    private fun setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    overridePendingTransition(0,0)
                    return@setOnItemSelectedListener true
                }

                R.id.navigation_tasks -> {
                    overridePendingTransition(0,0)
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
        bottomNavigation.selectedItemId = R.id.navigation_tasks
    }
}
