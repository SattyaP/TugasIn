package com.nrp_231111017_satyagardaprasetyo.tugasin

import android.os.Build
import android.app.NotificationManager
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import android.app.Application
import com.jakewharton.threetenabp.AndroidThreeTen
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.nrp_231111017_satyagardaprasetyo.tugasin.utils.TaskDatabaseHelper

class ReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    private val taskHelper = TaskDatabaseHelper(context)

    override fun doWork(): Result {
        Log.d("ReminderWorker", "Worker triggered")

        val dueTasks = taskHelper.getDueTasks()
        Log.d("ReminderWorker", "Due tasks: ${dueTasks.size}")

//        TEST
//        val task = Task(
//            id = 25,
//            name = "Widget Implementation",
//            url = "https://example.com",
//            course = "Pemrograman Perangkat Bergerak 1",
//            date = "2023-10-10 10:00",
//            taskType = "Recently overdue"
//        )
//        showNotification(task)

        for (task in dueTasks) {
            Log.d("ReminderWorker", "Task: ${task.name}")
            showNotification(task)
            Thread.sleep(1000)
        }

        val intent = Intent("com.tugasin.UPDATE_UI")
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)

        return Result.success()
    }

    private fun showNotification(task: Task) {
        val intent = Intent(applicationContext, TaskDetailActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("taskId", task.id)
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext, task.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "task_reminders"
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Task Reminders",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Channel for task reminder alerts"
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.icon)
            .setContentTitle("Reminder: ${task.name}")
            .setContentText(task.date)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(task.id, builder.build())
    }
}
