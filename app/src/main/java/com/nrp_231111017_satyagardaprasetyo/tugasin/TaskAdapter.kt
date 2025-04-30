package com.nrp_231111017_satyagardaprasetyo.tugasin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TaskAdapter(
    private val tasks: List<Task>,
    private val onTaskClick: (Task) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCourseCode: TextView = itemView.findViewById(R.id.tvCourseCode)
        val tvTaskTitle: TextView = itemView.findViewById(R.id.tvTaskTitle)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]

        val part = task.course.split(" ")[0] + " " + task.course.split(" ")[1]
        holder.tvCourseCode.text = task.course.split(part)[1].trim()
        holder.tvTaskTitle.text = task.name
        holder.tvDate.text = task.date.split(',')[0].trim()
        holder.tvTime.text = task.date.split(',')[1].trim()

        holder.itemView.setOnClickListener {
            onTaskClick(task)
        }
    }

    override fun getItemCount(): Int = tasks.size
}