package com.coolapps.digital.alarm.clock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.coolapps.digital.alarm.clock.R
import com.coolapps.digital.alarm.clock.presentation.logD
import com.coolapps.digital.alarm.clock.presentation.navigation.Screen
import com.coolapps.digital.alarm.clock.util.Analytics
import com.coolapps.digital.alarm.clock.util.NotificationChannelType
import com.coolapps.digital.alarm.clock.util.NotificationHandler
import com.coolapps.digital.alarm.clock.util.OfflineNotificationTimeSlot
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject

@AndroidEntryPoint
class ScheduledNotificationReceiver : BroadcastReceiver() {

	@Inject
	lateinit var notificationHandler: NotificationHandler

	@Inject
	lateinit var analytics: Analytics

	override fun onReceive(context: Context, intent: Intent) {
		when (intent.action) {
			ACTION_TRIAL_REMINDER -> handleTrialReminder(context)
			ACTION_SHOW_OFFLINE_NOTIFICATION -> handleOfflinePush(context, intent)
		}
	}

	private fun handleTrialReminder(context: Context) {
		val title = context.getString(R.string.trial_reminder_title)
		val message = context.getString(R.string.trial_reminder_message)

		val notification = notificationHandler.build(
			notificationChannel = NotificationChannelType.Engagement,
			notificationTitle = title,
			notificationText = message,
			targetScreen = Screen.AlarmContainer,
		)
		notificationHandler.show(notification)
		logD("in ScheduledNotificationReceiver (Trial Reminder)")

		analytics.captureEvent(
			"trial_reminder_notification_shown",
			mapOf("source" to "ScheduledNotificationReceiver")
		)
	}

	private fun handleOfflinePush(context: Context, intent: Intent) {
		val slotName = intent.getStringExtra(EXTRA_SLOT_NAME)
		val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

		val slot = OfflineNotificationTimeSlot.fromSlotName(slotName)
		val content = if (notificationId in 1..slot.notifications.size) {
			slot.getNotificationById(notificationId)
		} else {
			slot.getRandomNotification()
		}

		val title = context.getString(content.titleRes)
		val message = context.getString(content.messageRes)

		val notification = notificationHandler.build(
			notificationChannel = NotificationChannelType.PushNotification,
			notificationTitle = title,
			notificationText = message,
		)

		notificationHandler.show(notification)

		analytics.captureEvent(
			"push_notification_displayed",
			mapOf(
				"slot" to slot.slotName,
				"title" to title,
				"message" to message,
				"source" to "ScheduledNotificationReceiver"
			)
		)
	}

	companion object {
		const val ACTION_TRIAL_REMINDER = "com.coolapps.digital.alarm.clock.ACTION_TRIAL_REMINDER"
		const val ACTION_SHOW_OFFLINE_NOTIFICATION = "com.coolapps.digital.alarm.clock.ACTION_SHOW_OFFLINE_NOTIFICATION"
		
		const val EXTRA_SLOT_NAME = "extra_slot_name"
		const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

		fun createOfflinePushIntent(
			context: Context,
			slot: OfflineNotificationTimeSlot? = null,
			notificationId: Int? = null,
		): Intent {
			return Intent(context, ScheduledNotificationReceiver::class.java).apply {
				action = ACTION_SHOW_OFFLINE_NOTIFICATION
				if (slot != null) {
					putExtra(EXTRA_SLOT_NAME, slot.slotName)
				}
				if (notificationId != null) {
					putExtra(EXTRA_NOTIFICATION_ID, notificationId)
				}
			}
		}
	}
}
