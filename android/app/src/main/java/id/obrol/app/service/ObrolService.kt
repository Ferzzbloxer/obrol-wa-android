package id.obrol.app.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import id.obrol.app.ObrolApp
import obrolcore.Client
import obrolcore.MobileListener
import obrolcore.Obrolcore

class ObrolService : Service(), MobileListener {
    private val binder = LocalBinder()
    var client: Client? = null
    var activeListener: MobileListener? = null

    inner class LocalBinder : Binder() {
        fun getService(): ObrolService = this@ObrolService
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(1, createForegroundNotification("Starting Obrol..."))
        
        val dataDir = applicationContext.filesDir.absolutePath
        client = Obrolcore.newClient(dataDir, this)
        client?.start()
    }

    private fun createForegroundNotification(status: String): Notification {
        return NotificationCompat.Builder(this, ObrolApp.CHANNEL_ID)
            .setContentTitle("Obrol WhatsApp")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStatus(status: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, createForegroundNotification(status))
        activeListener?.onStatus(status)
    }

    override fun onQR(pngBytes: ByteArray?) { activeListener?.onQR(pngBytes) }
    override fun onQRTimeout() { activeListener?.onQRTimeout() }
    override fun onConnected() { activeListener?.onConnected() }
    override fun onDisconnected() { activeListener?.onDisconnected() }
    override fun onNewMessage(chatJID: String?, msgID: String?, sender: String?, text: String?, fromMe: Boolean, ts: Long, kind: String?) {
        activeListener?.onNewMessage(chatJID, msgID, sender, text, fromMe, ts, kind)
    }
    override fun onReceipt(chatJID: String?, msgID: String?, status: Long) {
        activeListener?.onReceipt(chatJID, msgID, status)
    }
    override fun onCall(chatJID: String?, isVideo: Boolean, phase: String?) {
        activeListener?.onCall(chatJID, isVideo, phase)
    }

    override fun onDestroy() {
        client?.disconnect()
        super.onDestroy()
    }
}