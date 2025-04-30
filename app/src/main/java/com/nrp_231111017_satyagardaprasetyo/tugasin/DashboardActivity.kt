package com.nrp_231111017_satyagardaprasetyo.tugasin

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DashboardActivity : AppCompatActivity() {

    private lateinit var tvTime: TextView
    private lateinit var tvDate: TextView
    private lateinit var rvTasks: RecyclerView
    private lateinit var bottomNavigation: BottomNavigationView

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

        // Set current time and date
        updateTimeAndDate()

        updateTimeRunnable.run()

        // Set up RecyclerView
        setupTasksList()

        // Set up bottom navigation
        setupBottomNavigation()
    }

    private fun updateTimeAndDate() {
        val calendar = Calendar.getInstance()

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        tvTime.text = timeFormat.format(calendar.time)

        // Format date (day of week, dd month yyyy)
        val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID"))
        tvDate.text = dateFormat.format(calendar.time)
    }

    private fun setupTasksList() {
        lifecycleScope.launch {
            try {
                val email = intent.getStringExtra("EMAIL") ?: ""
                val password = intent.getStringExtra("PASSWORD") ?: ""
                val response  = ApiClient.apiService.getTasks(mapOf("username" to email, "password" to password))

                val allTasks = response.tasks.values.flatten()
                print(allTasks)

                if (allTasks.isEmpty()) {
                    Toast.makeText(this@DashboardActivity, "No tasks available", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val adapter = TaskAdapter(allTasks) { task ->
                    // Handle task click
                }

                rvTasks.layoutManager = LinearLayoutManager(this@DashboardActivity)
                rvTasks.adapter = adapter

            } catch (e: Exception) {
                Log.e("API_ERROR", "Failed to fetch tasks", e)
                Toast.makeText(this@DashboardActivity, "Failed to fetch tasks", Toast.LENGTH_SHORT).show()
            }
        }
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