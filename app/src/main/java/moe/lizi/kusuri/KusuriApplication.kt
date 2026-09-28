package moe.lizi.kusuri

import android.app.Application
import moe.lizi.kusuri.di.AppContainer

class KusuriApplication : Application() {

    val container: AppContainer by lazy { AppContainer(this) }
}
