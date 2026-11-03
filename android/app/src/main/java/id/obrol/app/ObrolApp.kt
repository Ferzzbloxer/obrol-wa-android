package id.obrol.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class ObrolApp : Application() {
    companion object {
        const val CHANNEL_ID = "obrol_foreground"
        const val MSG_CHANNEL_ID = "obrol_messages"
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Obrol Background Connection",
                NotificationManager.IMPORTANCE_LOW
            )
            val msgChannel = NotificationChannel(
                MSG_CHANNEL_ID,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(msgChannel)
        }
    }
}