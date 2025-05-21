package com.nrp_231111017_satyagardaprasetyo.tugasin

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.nrp_231111017_satyagardaprasetyo.tugasin.utils.TaskDatabaseHelper
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter

class TaskDetailActivity : AppCompatActivity() {
    private lateinit var dbHelper: TaskDatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_detail)

        dbHelper = TaskDatabaseHelper(this)

        val taskId = intent.getIntExtra("taskId", -1)

        if (taskId != -1) {
            val task = dbHelper.getTaskById(taskId)
            if (task != null) {
                findViewById<TextView>(R.id.tvSubject).text = task.name
                findViewById<TextView>(R.id.tvCourse).text = "Course: ${task.course}"
                findViewById<TextView>(R.id.tvTaskType).text = "Type: ${task.taskType}"

                findViewById<TextView>(R.id.tvDate).text = "Date: ${task.date.split(" ")[0]}"
                findViewById<TextView>(R.id.tvTime).text =
                    "Time: ${task.date.split(" ").getOrElse(1) { "-" }}"

                findViewById<TextView>(R.id.tvUrl).text = "URL: ${task.url}"

                val isOverdue = LocalDateTime.parse(task.date, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        .isBefore(LocalDateTime.now())

                findViewById<TextView>(R.id.tvStatus).text =
                    if (isOverdue) "Overdue" else "Upcoming"
                findViewById<TextView>(R.id.tvStatus).setTextColor(
                    ContextCompat.getColor(this, if (isOverdue) R.color.red else R.color.black)
                )
            } else {
                Toast.makeText(this, "Task not found", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            Toast.makeText(this, "Invalid task ID", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
