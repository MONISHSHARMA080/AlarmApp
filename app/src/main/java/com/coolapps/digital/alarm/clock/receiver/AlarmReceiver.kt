package com.coolapps.digital.alarm.clock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.IntentCompat
import com.coolapps.digital.alarm.clock.presentation.trigger.AlarmActivityIntentData
import com.coolapps.digital.alarm.clock.domain.usecase.AlarmsController
import com.coolapps.digital.alarm.clock.error.ErrorHandler
import com.coolapps.digital.alarm.clock.di.IoDispatcher
import com.coolapps.digital.alarm.clock.util.Analytics
import com.coolapps.digital.alarm.clock.receiver.AlarmService
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var alarmsController: AlarmsController
    @Inject lateinit var errorHandler: ErrorHandler
    @Inject lateinit var analytics: Analytics

	@Inject @IoDispatcher
	lateinit var ioDispatcher: CoroutineDispatcher


    override fun onReceive(context: Context, intent: Intent) {
		val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
        logD("onReceive: intent action = ${intent.action}")
        val pendingResult = goAsync()
		scope.launch {
			try {
				coroutineScope {
					launch {
						startAlarmService(context, intent)
					}

					launch {
						val intentData = IntentCompat.getParcelableExtra(intent, "intentData", AlarmActivityIntentData::class.java)?:return@launch
						alarmsController.scheduleNextAlarmInSeries(intentData)
					}
				}
			} catch (e: Exception) {
				logD("Error in onReceive: ${e.message}")

				analytics.captureEvent(
					"Error in AlarmReceiver",
					mapOf(
						"exception" to e.toString(),
						"stackTrace" to e.stackTraceToString()
					)
				)
			} finally {
				pendingResult.finish()
			}
		}
	}

    private fun startAlarmService(context: Context, intent: Intent) {
        try {
            logD("Starting AlarmService")
            val serviceIntent =
                    Intent(context, AlarmService::class.java).apply {
                        putExtras(intent)
                        action = AlarmService.ACTION_START_ALARM
                    }
            context.startForegroundService(serviceIntent)
        } catch (e: Exception) {
            logD("Failed to start AlarmService: $e")
        }
    }

    private fun logD(message: String) {
        Log.d("AAAAA", "[AlarmReceiver] $message")
    }
}
