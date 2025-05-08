package com.nrp_231111017_satyagardaprasetyo.tugasin

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.nrp_231111017_satyagardaprasetyo.tugasin.utils.TaskDatabaseHelper

class ProfileActivity : AppCompatActivity() {
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var dbHelper: TaskDatabaseHelper

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        supportActionBar?.hide()

        bottomNavigation = findViewById(R.id.bottomNavigation)
        dbHelper = TaskDatabaseHelper(this)

        val totalOverdue = findViewById<TextView>(R.id.tv_overdue_count)
        val totalTasks = findViewById<TextView>(R.id.tv_total_count)

        val overdue = dbHelper.getTaskCountByType(this, "Recently overdue")
        val total = dbHelper.getTotalTasks(this)

        val btnInfo = findViewById<ImageButton>(R.id.btn_info)

        btnInfo.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.activity_about, null)
            val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create()

            dialog.show()
        }

        totalOverdue.text = overdue.toString()
        totalTasks.text = total.toString()

        setupBottomNavigation()

        val btnLogout = findViewById<ConstraintLayout>(R.id.btn_logout)
        btnLogout.setOnClickListener {
            val alertDialog = androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes") { _, _ ->
                    dbHelper.clearTasks(this)
                    dbHelper.logout(this)

                    val intent = Intent(this, LoginActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("No") { dialog, _ ->
                    dialog.dismiss()
                }
                .create()

            alertDialog.show()
        }

        val credentials = getSavedCredentials()
        if (credentials != null) {
            findViewById<TextView>(R.id.tv_username).text = credentials.first.split("@")[0]
        }
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
                    startActivity(Intent(this, TasksActivity::class.java))
                    overridePendingTransition(0, 0)
                    return@setOnItemSelectedListener true
                }

                R.id.navigation_profile -> {
                    overridePendingTransition(0, 0)
                    return@setOnItemSelectedListener true
                }

                else -> false
            }
        }
        bottomNavigation.selectedItemId = R.id.navigation_profile
    }
}