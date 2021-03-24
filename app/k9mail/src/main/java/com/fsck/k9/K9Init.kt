package com.fsck.k9

import android.app.Application
import android.content.Context
import com.fsck.k9.activity.MessageCompose
import com.fsck.k9.controller.MessagingController
import com.fsck.k9.external.MessageProvider
import com.fsck.k9.ui.base.ThemeManager

val appConfig = AppConfig(
    componentsToDisable = listOf(MessageCompose::class.java)
)

fun k9EarlyInit(application: Application) {
    Core.earlyInit(application)
}

fun k9Init(application: Application) {
    DI.start(application, coreModules + uiModules + appModules)

    K9.init(application)
    Core.init(application)
    MessageProvider.init()

    DI.get(ThemeManager::class.java).init()
    DI.get(MessagingListenerProvider::class.java).listeners.forEach { listener ->
        DI.get(MessagingController::class.java).addListener(listener)
    }
}