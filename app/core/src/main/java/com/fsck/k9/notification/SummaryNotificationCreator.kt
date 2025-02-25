package com.fsck.k9.notification

import android.app.Notification
import androidx.core.app.NotificationCompat
import com.fsck.k9.Account
import com.fsck.k9.K9
import com.fsck.k9.K9.NotificationQuickDelete
import com.fsck.k9.notification.NotificationChannelManager.ChannelType
import com.fsck.k9.notification.NotificationGroupKeys.getGroupKey
import com.fsck.k9.notification.NotificationIds.getNewMailSummaryNotificationId

internal open class SummaryNotificationCreator(
    private val notificationHelper: NotificationHelper,
    private val actionCreator: NotificationActionCreator,
    private val lockScreenNotificationCreator: LockScreenNotificationCreator,
    private val singleMessageNotificationCreator: SingleMessageNotificationCreator,
    private val resourceProvider: NotificationResourceProvider
) {

    fun buildSummaryNotification(account: Account, notificationData: NotificationData, silent: Boolean): Notification {
        val builder = when {
            notificationData.isSingleMessageNotification -> {
                val holder = notificationData.holderForLatestNotification
                createSingleMessageNotification(account, holder)
            }
            else -> {
                createInboxStyleSummaryNotification(account, notificationData)
            }
        }

        if (notificationData.containsStarredMessages()) {
            builder.priority = NotificationCompat.PRIORITY_HIGH
        }

        val notificationId = getNewMailSummaryNotificationId(account)
        val deletePendingIntent = actionCreator.createDismissAllMessagesPendingIntent(account, notificationId)
        builder.setDeleteIntent(deletePendingIntent)

        lockScreenNotificationCreator.configureLockScreenNotification(builder, notificationData)

        val notificationSetting = account.notificationSetting
        notificationHelper.configureNotification(
            builder = builder,
            ringtone = if (notificationSetting.isRingEnabled) notificationSetting.ringtone else null,
            vibrationPattern = if (notificationSetting.isVibrateEnabled) notificationSetting.vibration else null,
            ledColor = if (notificationSetting.isLedEnabled) notificationSetting.ledColor else null,
            ledSpeed = NotificationHelper.NOTIFICATION_LED_BLINK_SLOW,
            ringAndVibrate = !silent
        )

        return builder.build()
    }

    private fun createSingleMessageNotification(
        account: Account,
        holder: NotificationHolder
    ): NotificationCompat.Builder {
        val notificationId = getNewMailSummaryNotificationId(account)
        val builder = singleMessageNotificationCreator.createSingleMessageNotificationBuilder(account, holder, notificationId)
        builder.setGroupSummary(true)

        return builder
    }

    private fun createInboxStyleSummaryNotification(
        account: Account,
        notificationData: NotificationData
    ): NotificationCompat.Builder {
        val latestNotification = notificationData.holderForLatestNotification
        val newMessagesCount = notificationData.newMessagesCount
        val accountName = notificationHelper.getAccountName(account)
        val title = resourceProvider.newMessagesTitle(newMessagesCount)
        val summary = if (notificationData.hasSummaryOverflowMessages()) {
            resourceProvider.additionalMessages(notificationData.getSummaryOverflowMessagesCount(), accountName)
        } else {
            accountName
        }
        val groupKey = getGroupKey(account)

        val builder = notificationHelper.createNotificationBuilder(account, ChannelType.MESSAGES)
            .setSmallIcon(resourceProvider.iconNewMail)
            .setColor(account.chipColor)
            .setWhen(System.currentTimeMillis())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EMAIL)
            .setNumber(newMessagesCount)
            .setTicker(latestNotification.content.summary)
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setContentTitle(title)
            .setSubText(accountName)

        val style = createInboxStyle(builder)
            .setBigContentTitle(title)
            .setSummaryText(summary)

        for (content in notificationData.getContentForSummaryNotification()) {
            style.addLine(content.summary)
        }
        builder.setStyle(style)

        addMarkAllAsReadAction(builder, notificationData)
        addDeleteAllAction(builder, notificationData)
        addWearActions(builder, notificationData)

        val notificationId = getNewMailSummaryNotificationId(account)
        val messageReferences = notificationData.getAllMessageReferences()
        val contentIntent = actionCreator.createViewMessagesPendingIntent(account, messageReferences, notificationId)
        builder.setContentIntent(contentIntent)

        return builder
    }

    private fun addMarkAllAsReadAction(builder: NotificationCompat.Builder, notificationData: NotificationData) {
        val icon = resourceProvider.iconMarkAsRead
        val title = resourceProvider.actionMarkAsRead()
        val account = notificationData.account
        val messageReferences = notificationData.getAllMessageReferences()
        val notificationId = getNewMailSummaryNotificationId(account)
        val markAllAsReadPendingIntent =
            actionCreator.createMarkAllAsReadPendingIntent(account, messageReferences, notificationId)

        builder.addAction(icon, title, markAllAsReadPendingIntent)
    }

    private fun addDeleteAllAction(builder: NotificationCompat.Builder, notificationData: NotificationData) {
        if (K9.notificationQuickDeleteBehaviour !== NotificationQuickDelete.ALWAYS) {
            return
        }

        val icon = resourceProvider.iconDelete
        val title = resourceProvider.actionDelete()
        val account = notificationData.account
        val notificationId = getNewMailSummaryNotificationId(account)
        val messageReferences = notificationData.getAllMessageReferences()
        val action = actionCreator.createDeleteAllPendingIntent(account, messageReferences, notificationId)

        builder.addAction(icon, title, action)
    }

    private fun addWearActions(builder: NotificationCompat.Builder, notificationData: NotificationData) {
        val wearableExtender = NotificationCompat.WearableExtender()

        addMarkAllAsReadWearAction(wearableExtender, notificationData)

        if (isDeleteActionAvailableForWear()) {
            addDeleteAllWearAction(wearableExtender, notificationData)
        }

        if (isArchiveActionAvailableForWear(notificationData.account)) {
            addArchiveAllWearAction(wearableExtender, notificationData)
        }

        builder.extend(wearableExtender)
    }

    private fun addMarkAllAsReadWearAction(
        wearableExtender: NotificationCompat.WearableExtender,
        notificationData: NotificationData
    ) {
        val icon = resourceProvider.wearIconMarkAsRead
        val title = resourceProvider.actionMarkAllAsRead()
        val account = notificationData.account
        val messageReferences = notificationData.getAllMessageReferences()
        val notificationId = getNewMailSummaryNotificationId(account)
        val action = actionCreator.createMarkAllAsReadPendingIntent(account, messageReferences, notificationId)
        val markAsReadAction = NotificationCompat.Action.Builder(icon, title, action).build()

        wearableExtender.addAction(markAsReadAction)
    }

    private fun addDeleteAllWearAction(
        wearableExtender: NotificationCompat.WearableExtender,
        notificationData: NotificationData
    ) {
        val icon = resourceProvider.wearIconDelete
        val title = resourceProvider.actionDeleteAll()
        val account = notificationData.account
        val messageReferences = notificationData.getAllMessageReferences()
        val notificationId = getNewMailSummaryNotificationId(account)
        val action = actionCreator.createDeleteAllPendingIntent(account, messageReferences, notificationId)
        val deleteAction = NotificationCompat.Action.Builder(icon, title, action).build()

        wearableExtender.addAction(deleteAction)
    }

    private fun addArchiveAllWearAction(
        wearableExtender: NotificationCompat.WearableExtender,
        notificationData: NotificationData
    ) {
        val icon = resourceProvider.wearIconArchive
        val title = resourceProvider.actionArchiveAll()
        val account = notificationData.account
        val messageReferences = notificationData.getAllMessageReferences()
        val notificationId = getNewMailSummaryNotificationId(account)
        val action = actionCreator.createArchiveAllPendingIntent(account, messageReferences, notificationId)
        val archiveAction = NotificationCompat.Action.Builder(icon, title, action).build()

        wearableExtender.addAction(archiveAction)
    }

    private fun isDeleteActionAvailableForWear(): Boolean {
        return isDeleteActionEnabled() && !K9.isConfirmDeleteFromNotification
    }

    private fun isDeleteActionEnabled(): Boolean {
        return K9.notificationQuickDeleteBehaviour != NotificationQuickDelete.NEVER
    }

    private fun isArchiveActionAvailableForWear(account: Account): Boolean {
        return account.archiveFolderId != null
    }

    protected open fun createInboxStyle(builder: NotificationCompat.Builder?): NotificationCompat.InboxStyle {
        return NotificationCompat.InboxStyle(builder)
    }
}
