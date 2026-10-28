package io.github.yasarmahomedabbas.immichtv

import android.app.Application

class ImmichTvApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
