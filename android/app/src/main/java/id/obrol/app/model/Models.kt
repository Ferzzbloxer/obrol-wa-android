package id.obrol.app.model

data class ChatMessage(
    val id: String,
    val sender: String,
    val text: String,
    val fromMe: Boolean,
    val timestamp: Long,
    val kind: String
)

data class ChatItem(
    val jid: String,
    val name: String,
    val lastText: String,
    val timestamp: Long,
    val unread: Int = 0
)