package id.obrol.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import id.obrol.app.model.ChatItem
import id.obrol.app.model.ChatMessage
import id.obrol.app.service.ObrolService
import obrolcore.MobileListener

class MainActivity : ComponentActivity(), MobileListener {

    private var service: ObrolService? = null
    private var isBound = false

    private val qrBitmapState = mutableStateOf<android.graphics.Bitmap?>(null)
    private val isConnectedState = mutableStateOf(false)
    private val activeMessages = mutableStateListOf<ChatMessage>()
    private val currentChatJid = mutableStateOf<String?>(null)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as ObrolService.LocalBinder
            service = localBinder.getService()
            service?.activeListener = this@MainActivity
            isBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = Intent(this, ObrolService::class.java)
        startService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (!isConnectedState.value) {
                        LoginView(qrBitmapState.value)
                    } else if (currentChatJid.value == null) {
                        ChatListView(
                            onSelectChat = { jid -> currentChatJid.value = jid }
                        )
                    } else {
                        ConversationView(
                            chatJID = currentChatJid.value!!,
                            messages = activeMessages,
                            onBack = { currentChatJid.value = null },
                            onSend = { text ->
                                service?.client?.sendText(currentChatJid.value, text)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (isBound) unbindService(connection)
        super.onDestroy()
    }

    override fun onQR(pngBytes: ByteArray?) {
        if (pngBytes != null) {
            qrBitmapState.value = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
        }
    }
    override fun onQRTimeout() { qrBitmapState.value = null }
    override fun onConnected() { isConnectedState.value = true }
    override fun onDisconnected() { isConnectedState.value = false }
    override fun onStatus(status: String?) {}
    override fun onNewMessage(chatJID: String?, msgID: String?, sender: String?, text: String?, fromMe: Boolean, ts: Long, kind: String?) {
        if (chatJID == currentChatJid.value && text != null) {
            activeMessages.add(ChatMessage(msgID ?: "", sender ?: "", text, fromMe, ts, kind ?: "text"))
        }
    }
    override fun onReceipt(chatJID: String?, msgID: String?, status: Long) {}
    override fun onCall(chatJID: String?, isVideo: Boolean, phase: String?) {}
}

@Composable
fun LoginView(qrBitmap: android.graphics.Bitmap?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Connect Obrol to WhatsApp", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        if (qrBitmap != null) {
            Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "QR Code")
        } else {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun ChatListView(onSelectChat: (String) -> Unit) {
    val sampleChats = listOf(
        ChatItem("6281234567890@s.whatsapp.net", "Mom", "See you later!", 1710000000),
        ChatItem("120363000000000001@g.us", "Project Team", "Meeting at 9am", 1710002000)
    )

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(sampleChats) { chat ->
            ListItem(
                headlineContent = { Text(chat.name) },
                supportingContent = { Text(chat.lastText) },
                modifier = Modifier.clickable { onSelectChat(chat.jid) }
            )
            Divider()
        }
    }
}

@Composable
fun ConversationView(
    chatJID: String,
    messages: List<ChatMessage>,
    onBack: () -> Unit,
    onSend: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(chatJID) },
            navigationIcon = {
                Button(onClick = onBack) { Text("Back") }
            }
        )
        LazyColumn(modifier = Modifier.weight(1f).padding(8.dp)) {
            items(messages) { msg ->
                val alignment = if (msg.fromMe) Alignment.End else Alignment.Start
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
                    Card(modifier = Modifier.padding(4.dp)) {
                        Text(msg.text, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            TextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f))
            Button(onClick = { if (text.isNotBlank()) { onSend(text); text = "" } }) {
                Text("Send")
            }
        }
    }
}