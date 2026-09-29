package moe.lizi.kusuri

import android.app.Application
import moe.lizi.kusuri.alarm.ReminderChannels
import moe.lizi.kusuri.di.AppContainer

class KusuriApplication : Application() {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // 通知渠道必须在任何进程(含接收器唤起)尽早创建。
        ReminderChannels.ensure(this)
    }
}
