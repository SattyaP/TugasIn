package com.nrp_231111017_satyagardaprasetyo.tugasin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.jakewharton.threetenabp.AndroidThreeTen
import com.nrp_231111017_satyagardaprasetyo.tugasin.utils.TaskDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.threeten.bp.LocalDate
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.temporal.ChronoUnit
import java.util.Locale
import android.app.Application

class TasksActivity : AppCompatActivity() {
    private lateinit var rvNext7Days: RecyclerView
    private lateinit var rvNext30Days: RecyclerView
    private lateinit var rvOverdue: RecyclerView
    private lateinit var rvFuture: RecyclerView
    private lateinit var rvToday: RecyclerView
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var dbHelper: TaskDatabaseHelper
    private lateinit var syncBtn: ImageButton
    private lateinit var adapter: TaskAdapter
    private lateinit var reminderCard: CardView
    private lateinit var tvReminderSubject: TextView
    private lateinit var tvReminderDate: TextView
    private lateinit var tvReminderTime: TextView
    private lateinit var tvReminderTitle: TextView
    private lateinit var updateUIReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidThreeTen.init(this)
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
        rvToday = findViewById(R.id.rvToday)

        reminderCard = findViewById<CardView>(R.id.upcomingReminderCard)
        tvReminderSubject = findViewById<TextView>(R.id.tvReminderSubject)
        tvReminderTitle = findViewById<TextView>(R.id.tvReminderTitle)
        tvReminderDate = findViewById<TextView>(R.id.tvReminderDate)
        tvReminderTime = findViewById<TextView>(R.id.tvReminderTime)

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

//        TEST
//        val btn = findViewById<Button>(R.id.btnTestNotification)
//        btn.setOnClickListener {
//            val request = OneTimeWorkRequestBuilder<ReminderWorker>().build()
//            WorkManager.getInstance(applicationContext).enqueue(request)
//        }

        updateUIReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d("TasksActivity", "Received UPDATE_UI broadcast")
                loadCredentialsAndTasks(findViewById(R.id.loadingOverlay))
            }
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(
            updateUIReceiver,
            IntentFilter("com.tugasin.UPDATE_UI")
        )
    }

    private fun loadCredentialsAndTasks(loadingOverlay: FrameLayout) {
        val credentials = getSavedCredentials()
        if (credentials != null) {
            val (email, password) = credentials
            findViewById<TextView>(R.id.tvGreeting).text = "Halo, ${email.split("@")[0]}!"

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    withContext(Dispatchers.Main) {
                        loadingOverlay.visibility = View.VISIBLE
                    }

                    val allTasks = dbHelper.getAllTasks(this@TasksActivity)

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
                        "Today" to allTasks.filter {
                            it.taskType.equals(
                                "Today",
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

                    withContext(Dispatchers.Main) {
                        updateRecyclerView(rvOverdue, taskLists["Recently overdue"] ?: emptyList())
                        updateRecyclerView(rvToday, taskLists["Today"] ?: emptyList())
                        updateRecyclerView(rvNext7Days, taskLists["Next 7 days"] ?: emptyList())
                        updateRecyclerView(rvNext30Days, taskLists["Next 30 days"] ?: emptyList())
                        updateRecyclerView(rvFuture, taskLists["Future"] ?: emptyList())

                        loadingOverlay.visibility = View.GONE
                    }

                    checkUpcomingReminders()

                } catch (e: Exception) {
                    Log.e("TASK_SYNC_ERROR", "Error loading tasks", e)
                    withContext(Dispatchers.Main) {
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
        disableMenu()
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

                enableMenu()
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
            adapter = TaskAdapter(tasks.toMutableList(), { task -> }, R.layout.fragment_tasks)
            recyclerView.layoutManager =
                LinearLayoutManager(this@TasksActivity, LinearLayoutManager.HORIZONTAL, false)
            recyclerView.adapter = adapter
        } else {
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
                    overridePendingTransition(0, 0)
                    return@setOnItemSelectedListener true
                }

                R.id.navigation_tasks -> {
                    overridePendingTransition(0, 0)
                    return@setOnItemSelectedListener true
                }

                R.id.navigation_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    overridePendingTransition(0, 0)
                    return@setOnItemSelectedListener true
                }

                else -> false
            }
        }
        bottomNavigation.selectedItemId = R.id.navigation_tasks
    }

    private val updateUiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            checkUpcomingReminders()
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(updateUiReceiver, IntentFilter("com.tugasin.UPDATE_UI"))
        checkUpcomingReminders()
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(updateUiReceiver)
    }

    private fun checkUpcomingReminders() {
        val allTasks = dbHelper.getAllTasks(this)
        val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
        val currentYear = LocalDate.now().year
        val currentDate = LocalDate.now()

        val upcomingReminders = allTasks.filter { task ->
            try {
                val datePart = task.date.split(',')[0].trim()
                val fullDateStr = "$datePart $currentYear"
                val reminderDate = LocalDate.parse(fullDateStr, dateFormatter)
                val daysUntilDue = ChronoUnit.DAYS.between(currentDate, reminderDate)

                daysUntilDue <= 0 || daysUntilDue in listOf(1L, 3L)
            } catch (e: Exception) {
                Log.e("ReminderDebug", "Error parsing task '${task.date}': ${e.message}")
                false
            }
        }.sortedBy { task ->
            try {
                val datePart = task.date.split(',')[0].trim()
                val fullDateStr = "$datePart $currentYear"
                LocalDate.parse(fullDateStr, dateFormatter)
            } catch (e: Exception) {
                LocalDate.MAX
            }
        }

        if (upcomingReminders.isNotEmpty()) {
            val firstReminder = upcomingReminders.first()
            try {
                val datePart = firstReminder.date.split(',')[0].trim()
                val timePart = firstReminder.date.split(',')[1].trim()
                val fullDateStr = "$datePart $currentYear"
                val reminderDate = LocalDate.parse(fullDateStr, dateFormatter)
                val daysUntilDue = ChronoUnit.DAYS.between(currentDate, reminderDate)

                tvReminderTitle.text = "Upcoming Reminder (H-$daysUntilDue)"
                tvReminderSubject.text = firstReminder.name
                tvReminderDate.text = datePart
                tvReminderTime.text = timePart
            } catch (e: Exception) {
                Log.e("ReminderDebug", "Error displaying reminder: ${e.message}")
            }
        } else {
            tvReminderTitle.text = "No Upcoming Reminders"
            tvReminderDate.text = "No Date"
            tvReminderTime.text = "No Time"
            tvReminderSubject.text = "No Task"
        }
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

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(updateUIReceiver)
    }
}
