package com.nrp_231111017_satyagardaprasetyo.tugasin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.nrp_231111017_satyagardaprasetyo.tugasin.utils.TaskDatabaseHelper

class ProfileActivity : AppCompatActivity() {
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var dbHelper: TaskDatabaseHelper
    private lateinit var imageView: ImageView
    private val REQUEST_CODE_PICK_IMAGE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        supportActionBar?.hide()

        imageView = findViewById(R.id.profile_image)
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

        findViewById<CardView>(R.id.profile_image_container).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "image/*"
            startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE)
        }

        loadSavedImage()

        val btnLogout = findViewById<ConstraintLayout>(R.id.btn_logout)
        btnLogout.setOnClickListener {
            val alertDialog = androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes") { _, _ ->
                    dbHelper.clearTasks(this)
                    dbHelper.logout(this)

                    getSharedPreferences("profile", MODE_PRIVATE).edit()
                        .remove("image_uri")
                        .apply()

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

        val btnEditProfile = findViewById<ConstraintLayout>(R.id.btn_edit_profile)
        btnEditProfile.setOnClickListener {
            val intent = Intent(this, EditProfileActivity::class.java)
            startActivity(intent)
        }

        val credentials = getSavedCredentials()
        if (credentials != null) {
            findViewById<TextView>(R.id.tv_username).text = credentials.first
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                imageView.setImageURI(uri)

                getSharedPreferences("profile", MODE_PRIVATE)
                    .edit().putString("image_uri", uri.toString()).apply()
            }
        }
    }

    private fun getSavedCredentials(): Triple<String, String, String>? {
        val db = dbHelper.readableDatabase
        val cursor = db.query("credentials", null, null, null, null, null, null)
        var result: Triple<String, String, String>? = null

        if (cursor.moveToFirst()) {
            val username = cursor.getString(cursor.getColumnIndexOrThrow("username"))
            val email = cursor.getString(cursor.getColumnIndexOrThrow("email"))
            val password = cursor.getString(cursor.getColumnIndexOrThrow("password"))
            result = Triple(username, email, password)
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

    private fun saveImageUri(uri: Uri) {
        val prefs = getSharedPreferences("profile", MODE_PRIVATE)
        prefs.edit().putString("image_uri", uri.toString()).apply()
    }

    private fun loadSavedImage() {
        val uriString = getSharedPreferences("profile", MODE_PRIVATE).getString("image_uri", null)
        uriString?.let {
            try {
                val uri = Uri.parse(it)
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, flags)
                imageView.setImageURI(uri)
            } catch (e: SecurityException) {
                imageView.setImageResource(R.drawable.avatar)
            }
        }
    }

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            findViewById<ImageView>(R.id.profile_image).setImageURI(it)
            saveImageUri(it)
        }
    }

    override fun onResume() {
        super.onResume()
        val credentials = getSavedCredentials()
        if (credentials != null) {
            findViewById<TextView>(R.id.tv_username).text = credentials.first
        }
    }
}