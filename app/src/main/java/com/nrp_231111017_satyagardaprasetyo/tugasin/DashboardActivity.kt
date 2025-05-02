package com.nrp_231111017_satyagardaprasetyo.tugasin

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.nrp_231111017_satyagardaprasetyo.tugasin.utils.TaskDatabaseHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DashboardActivity : AppCompatActivity() {

    private lateinit var tvTime: TextView
    private lateinit var tvDate: TextView
    private lateinit var rvTasks: RecyclerView
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var dbHelper: TaskDatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // Hide action bar
        supportActionBar?.hide()

        // Initialize views
        tvTime = findViewById(R.id.tvTime)
        tvDate = findViewById(R.id.tvDate)
        rvTasks = findViewById(R.id.rvTasks)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        dbHelper = TaskDatabaseHelper(this)

        updateTimeAndDate()

        updateTimeRunnable.run()

        setupBottomNavigation()

        val credentials = getSavedCredentials()
        if (credentials != null) {
            val (email, password) = credentials
            findViewById<TextView>(R.id.tvGreeting).text = "Halo, ${email.split("@")[0]}!"

            lifecycleScope.launch {
                dbHelper.syncTasksIfNeeded(
                    this@DashboardActivity,
                    email,
                    password
                )

                val filteredTasks = dbHelper
                    .getAllTasks(this@DashboardActivity)
                    .filter { it.taskType.equals("Recently overdue", ignoreCase = true) }
                    .toMutableList()

                val adapter = TaskAdapter(filteredTasks) { task -> }
                rvTasks.layoutManager = LinearLayoutManager(this@DashboardActivity)
                rvTasks.adapter = adapter
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

    private fun setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    // Already on home
                    return@setOnItemSelectedListener true
                }

                R.id.navigation_tasks -> {
                    // Navigate to tasks
                    // startActivity(Intent(this, TasksActivity::class.java))
                    return@setOnItemSelectedListener true
                }

                R.id.navigation_profile -> {
                    // Navigate to profile
                    // startActivity(Intent(this, ProfileActivity::class.java))
                    return@setOnItemSelectedListener true
                }

                else -> false
            }
        }

        // Set home as selected
        bottomNavigation.selectedItemId = R.id.navigation_home
    }
}