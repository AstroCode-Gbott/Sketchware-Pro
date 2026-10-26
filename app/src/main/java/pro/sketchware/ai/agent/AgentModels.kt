package pro.sketchware.ai.agent

enum class TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

data class AgentTaskItem(
    val id: String,
    val title: String,
    val milestone: String = "General",
    val status: TaskStatus = TaskStatus.PENDING,
    val notes: String = ""
)

data class AgentPlan(
    val goal: String = "",
    val tasks: List<AgentTaskItem> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class ChatEntryType {
    USER,
    ASSISTANT,
    TOOL_CALL,
    TOOL_RESULT,
    STATUS,
    ERROR
}

data class AgentChatEntry(
    val id: String,
    val type: ChatEntryType,
    val title: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis()
)
