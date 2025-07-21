package com.nrp_231111017_satyagardaprasetyo.tugasin

import android.content.ContentValues
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nrp_231111017_satyagardaprasetyo.tugasin.utils.TaskDatabaseHelper

class EditProfileActivity : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etPhone: EditText
    private lateinit var btnSave: Button
    private lateinit var dbHelper: TaskDatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        etUsername = findViewById(R.id.etUsername)
        etPhone = findViewById(R.id.etPhone)
        btnSave = findViewById(R.id.btnSave)
        dbHelper = TaskDatabaseHelper(this)

        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM credentials LIMIT 1", null)

        if (cursor.moveToFirst()) {
            val username = cursor.getString(cursor.getColumnIndexOrThrow("username"))
            val phone = cursor.getString(cursor.getColumnIndexOrThrow("phone"))
            etUsername.setText(username ?: "")
            etPhone.setText(phone ?: "")
        }

        cursor.close()
        db.close()

        btnSave.setOnClickListener {
            val newUsername = etUsername.text.toString().trim()
            val newPhone = etPhone.text.toString().trim()

            if (newUsername.isNotEmpty() && newPhone.isNotEmpty()) {
                val dbWritable = dbHelper.writableDatabase
                val values = ContentValues().apply {
                    put("username", newUsername)
                    put("phone", newPhone)
                }

                val updated = dbWritable.update(
                    "credentials",
                    values,
                    "id = (SELECT id FROM credentials LIMIT 1)",
                    null
                )

                dbWritable.close()

                if (updated > 0) {
                    Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Fields cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }
    }
}
