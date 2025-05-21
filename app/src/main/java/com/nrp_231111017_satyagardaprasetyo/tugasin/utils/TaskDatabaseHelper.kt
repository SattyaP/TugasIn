package com.nrp_231111017_satyagardaprasetyo.tugasin.utils

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import android.widget.Toast
import com.nrp_231111017_satyagardaprasetyo.tugasin.ApiClient
import com.nrp_231111017_satyagardaprasetyo.tugasin.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter
import java.util.Locale

class TaskDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION
) {

    companion object {
        private const val DATABASE_NAME = "tasks.db"
        private const val DATABASE_VERSION = 3

        const val TABLE_NAME = "tasks"
        const val COLUMN_NAME = "name"
        const val COLUMN_URL = "url"
        const val COLUMN_COURSE = "course"
        const val COLUMN_DATE = "date"
        const val TASK_TYPE = "taskType"

        const val TABLE_CREDENTIALS = "credentials"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            $COLUMN_NAME TEXT,
            url TEXT,
            $COLUMN_COURSE TEXT,  
            date TEXT,
            taskType TEXT
        )
    """.trimIndent()
        )

        db.execSQL(
            """
        CREATE TABLE IF NOT EXISTS $TABLE_CREDENTIALS (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            email TEXT,
            password TEXT
        )
    """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_CREDENTIALS")
        db?.let { onCreate(it) }
    }

    fun insertTask(context: Context, task: Task) {
        val dbHelper = TaskDatabaseHelper(context)
        val db = dbHelper.writableDatabase

        val course = task.course

        // Correctly assign the taskType to the proper column
        val values = android.content.ContentValues().apply {
            put(TaskDatabaseHelper.COLUMN_NAME, task.name)
            put(TaskDatabaseHelper.COLUMN_URL, task.url)
            put(TaskDatabaseHelper.COLUMN_COURSE, course)
            put(TaskDatabaseHelper.COLUMN_DATE, task.date)
            put(TaskDatabaseHelper.TASK_TYPE, task.taskType)  // Correctly assign taskType
        }

        db.insert(TaskDatabaseHelper.TABLE_NAME, null, values)
        db.close()
    }

    fun saveTasksFromApiResponse(context: Context, jsonString: String) {
        val jsonObject = JSONObject(jsonString)
        val tasksObject = jsonObject.getJSONObject("tasks")

        val keys = tasksObject.keys()
        while (keys.hasNext()) {
            val taskType = keys.next()
            val taskArray = tasksObject.getJSONArray(taskType)

            for (i in 0 until taskArray.length()) {
                val taskJson = taskArray.getJSONObject(i)

                val task = Task(
                    id = 0,
                    name = taskJson.getString("name"),
                    url = taskJson.getString("url"),
                    course = taskJson.getString("course"),
                    date = taskJson.getString("date"),
                    taskType = taskType
                )

                insertTask(context, task)
            }
        }
    }

    fun getAllTasks(context: Context): List<Task> {
        val dbHelper = TaskDatabaseHelper(context)
        val db = dbHelper.readableDatabase

        val cursor = db.query(TaskDatabaseHelper.TABLE_NAME, null, null, null, null, null, null)
        val taskList = mutableListOf<Task>()

        while (cursor.moveToNext()) {
            val task = Task(
                id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                name = cursor.getString(cursor.getColumnIndexOrThrow(TaskDatabaseHelper.COLUMN_NAME)),
                url = cursor.getString(cursor.getColumnIndexOrThrow(TaskDatabaseHelper.COLUMN_URL)),
                course = cursor.getString(cursor.getColumnIndexOrThrow(TaskDatabaseHelper.COLUMN_COURSE)),
                date = cursor.getString(cursor.getColumnIndexOrThrow(TaskDatabaseHelper.COLUMN_DATE)),
                taskType = cursor.getString(cursor.getColumnIndexOrThrow(TaskDatabaseHelper.TASK_TYPE))
            )
            taskList.add(task)
        }

        cursor.close()
        db.close()
        return taskList
    }

    fun isTaskTableEmpty(context: Context): Boolean {
        val dbHelper = TaskDatabaseHelper(context)
        val db = dbHelper.readableDatabase

        val cursor = db.rawQuery("SELECT COUNT(*) FROM ${TaskDatabaseHelper.TABLE_NAME}", null)
        var count = 0
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }

        cursor.close()
        db.close()

        return count == 0
    }

    suspend fun syncTasksIfNeeded(context: Context, email: String, password: String): Boolean {
        Log.d("SYNC_TASKS", "Checking if tasks need to be synced")
        if (isTaskTableEmpty(context)) {
            try {
                val credentials = mapOf("username" to email, "password" to password)
                val response = ApiClient.apiService.getTasks(credentials)
                Log.d("API_RESPONSE", "Response: $response")

                if (response.isSuccessful) {
                    val tasksResponse = response.body()
                    tasksResponse?.tasks?.forEach { (taskType, taskList) ->
                        taskList.forEach { task ->
                            val taskWithType = task.copy(taskType = taskType)
                            insertTask(context, taskWithType)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Tasks synced successfully", Toast.LENGTH_SHORT)
                            .show()
                    }
                    return true
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to sync tasks", Toast.LENGTH_SHORT).show()
                    }
                    Log.e("API_ERROR", "Error: ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e("API_ERROR", "Failed to fetch tasks", e)
            }
        }
        return false
    }

    fun getDueTasks(): List<Task> {
        val db = readableDatabase
        val taskList = mutableListOf<Task>()
        val cursor = db.query("tasks", null, null, null, null, null, null)

        val formatter = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH)
        val now = LocalDateTime.now()

        if (cursor.moveToFirst()) {
            do {
                val dateString = cursor.getString(cursor.getColumnIndexOrThrow("date"))
                try {
                    val taskDate = LocalDateTime.parse(dateString, formatter)

                    if (!taskDate.isAfter(now)) {
                        val task = Task(
                            id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                            url = cursor.getString(cursor.getColumnIndexOrThrow("url")),
                            course = cursor.getString(cursor.getColumnIndexOrThrow("course")),
                            date = dateString,
                            taskType = cursor.getString(cursor.getColumnIndexOrThrow("taskType"))
                        )
                        taskList.add(task)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } while (cursor.moveToNext())
        }

        cursor.close()
        return taskList
    }

    fun getTaskById(id: Int): Task? {
        val db = readableDatabase
        val cursor = db.query("tasks", null, "id = ?", arrayOf(id.toString()), null, null, null)
        return if (cursor.moveToFirst()) {
            Task(
                id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                url = cursor.getString(cursor.getColumnIndexOrThrow("url")),
                course = cursor.getString(cursor.getColumnIndexOrThrow("course")),
                date = cursor.getString(cursor.getColumnIndexOrThrow("date")),
                taskType = cursor.getString(cursor.getColumnIndexOrThrow("taskType"))
            )
        } else {
            null
        }.also {
            cursor.close()
        }
    }

    fun clearTasks(context: Context) {
        val dbHelper = TaskDatabaseHelper(context)
        val db = dbHelper.writableDatabase
        db.delete(TaskDatabaseHelper.TABLE_NAME, null, null)
        db.close()
    }

    fun logout(context: Context) {
        val dbHelper = TaskDatabaseHelper(context)
        val db = dbHelper.writableDatabase
        db.delete(TaskDatabaseHelper.TABLE_CREDENTIALS, null, null)
        db.close()
    }

    suspend fun syncTasksManual(context: Context, email: String, password: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                clearTasks(context)
                val credentials = mapOf("username" to email, "password" to password)
                Log.d("API_DEBUG", "Trying to call getTasks with: $credentials")

                val response = ApiClient.apiService.getTasks(credentials)

                Log.d("API_RESPONSE", "Response: $response")

                if (response.isSuccessful) {
                    val tasksResponse = response.body()
                    if (tasksResponse != null) {

                        // Process the tasks
                        tasksResponse.tasks.forEach { (taskType, taskList) ->
                            taskList.forEach { task ->
                                val taskWithType = task.copy(taskType = taskType)
                                insertTask(context, taskWithType)
                            }
                        }

                        // Return true if tasks synced successfully
                        return@withContext true
                    } else {
                        Log.e("API_ERROR", "Empty response body")
                    }
                }
            } catch (e: Exception) {
                Log.e("MANUAL_SYNC_ERROR", "Failed to sync manually", e)
            }
            return@withContext false
        }
    }

    fun getTotalTasks(context: Context): Int {
        val dbHelper = TaskDatabaseHelper(context)
        val db = dbHelper.readableDatabase

        val cursor = db.rawQuery("SELECT COUNT(*) FROM ${TaskDatabaseHelper.TABLE_NAME}", null)

        var count = 0
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }

        cursor.close()
        db.close()

        return count
    }

    fun getTaskCountByType(context: Context, taskType: String): Int {
        val dbHelper = TaskDatabaseHelper(context)
        val db = dbHelper.readableDatabase

        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM ${TaskDatabaseHelper.TABLE_NAME} WHERE ${TaskDatabaseHelper.TASK_TYPE} = ?",
            arrayOf(taskType)
        )

        var count = 0
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }

        cursor.close()
        db.close()

        return count
    }
}

private fun Any.forEach(any: Any) {}
