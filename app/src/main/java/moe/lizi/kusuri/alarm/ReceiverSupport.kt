package moe.lizi.kusuri.alarm

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import moe.lizi.kusuri.KusuriApplication
import moe.lizi.kusuri.di.AppContainer

/** 接收器没有生命周期作用域:用进程级作用域配合 goAsync 保证工作完成。 */
internal val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

internal fun Context.appContainer(): AppContainer =
    (applicationContext as KusuriApplication).container
