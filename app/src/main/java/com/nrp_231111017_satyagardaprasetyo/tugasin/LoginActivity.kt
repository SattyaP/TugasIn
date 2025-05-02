package com.nrp_231111017_satyagardaprasetyo.tugasin

import android.content.ContentValues
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nrp_231111017_satyagardaprasetyo.tugasin.databinding.ActivityLoginBinding
import com.nrp_231111017_satyagardaprasetyo.tugasin.utils.TaskDatabaseHelper

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var dbHelper: TaskDatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = TaskDatabaseHelper(this)
        supportActionBar?.hide()

        binding.btnMasuk.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Email and password cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveCredentials(email, password)

            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        val credentials = getSavedCredentials()
        if (credentials != null) {
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
        }
    }

    private fun saveCredentials(email: String, password: String) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("email", email)
            put("password", password)
        }
        db.insertWithOnConflict("credentials", null, values, SQLiteDatabase.CONFLICT_REPLACE)
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
}