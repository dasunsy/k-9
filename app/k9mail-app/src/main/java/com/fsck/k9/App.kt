package com.fsck.k9

import android.app.Application

class App : Application() {

    override fun onCreate() {
        k9EarlyInit(this)
        super.onCreate()
        k9Init(this)
    }

}
