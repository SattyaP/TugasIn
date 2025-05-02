package com.nrp_231111017_satyagardaprasetyo.tugasin

data class TasksResponse(
    val username: String,
    val message: String,
    val tasks: Map<String, List<Task>>
) {
    operator fun get(s: String): Any {
        return tasks[s] ?: emptyList<Task>()
    }
}
