package com.coolapps.digital.alarm.clock.domain.usecase

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.coolapps.digital.alarm.clock.error.ErrorHandler
import com.coolapps.digital.alarm.clock.data.local.AlarmData
import com.coolapps.digital.alarm.clock.data.local.AlarmDataValidationResult
import com.coolapps.digital.alarm.clock.domain.model.AlarmControllerErrorSet
import com.coolapps.digital.alarm.clock.domain.model.CancelAlarmError
import com.coolapps.digital.alarm.clock.domain.model.CancelAlarmHandlerError
import com.coolapps.digital.alarm.clock.domain.model.DeleteAlarmHandlerError
import com.coolapps.digital.alarm.clock.domain.model.GetPendingIntentForAlarmError
import com.coolapps.digital.alarm.clock.domain.model.ResetAlarmError
import com.coolapps.digital.alarm.clock.domain.model.ScheduleAlarmError
import com.coolapps.digital.alarm.clock.domain.model.StartAlarmSeriesHandlerError
import com.coolapps.digital.alarm.clock.domain.model.UpdateAlarmInDbError
import com.coolapps.digital.alarm.clock.domain.repository.AlarmRepository
import com.coolapps.digital.alarm.clock.presentation.trigger.AlarmActivityIntentData
import com.coolapps.digital.alarm.clock.receiver.AlarmInfoNotification
import com.coolapps.digital.alarm.clock.receiver.AlarmReceiver
import com.coolapps.digital.alarm.clock.util.Analytics
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.coolapps.digital.alarm.clock.util.Result as ResultCustom

const val ALARM_ACTION = "com.coolapps.digital.alarm.clock.ALARM_TRIGGERED"


class AlarmsController @Inject constructor(
		private val alarmRepository: AlarmRepository,
		private val alarmManager: AlarmManager,
		val analytics: Analytics,
		private val errorHandler: ErrorHandler,
		@ApplicationContext  val context: Context
) {
	var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val alarmInfoNotificationClass:Class<out BroadcastReceiver> = AlarmInfoNotification::class.java
	private val alarmReceiverClass:Class<out BroadcastReceiver> = AlarmReceiver::class.java

	data class PendingIntentCreated(
			/** [pendingIntentToGiveUserUpcomingAlarmInfoWhenAsked] is for what happens when the user clicks on the upcoming alarm notification in the notification shade, we have to give info about alarm */
			val pendingIntentToGiveUserUpcomingAlarmInfoWhenAsked: PendingIntent?,
			val pendingIntentForAlarm: PendingIntent
	){
		override fun toString(): String {
			return "pendingIntentForAlarmNotificationInfo--$pendingIntentToGiveUserUpcomingAlarmInfoWhenAsked --  pendingIntentForAlarm--$pendingIntentForAlarm"
		}
	}

	private	fun scheduleAlarm(
			alarmManager:AlarmManager, componentActivity: Context, receiverClass:Class<out BroadcastReceiver> = AlarmReceiver::class.java, alarmData: AlarmData, alarmTriggerTime:Long
	): ResultCustom<Unit, ScheduleAlarmError>{
		return ResultCustom.runCatching({ exception ->AlarmControllerErrorSet.Unknown(internalErrorMessage = exception.toString()) }){
			val res =alarmData.validate()
			if (res != AlarmDataValidationResult.Success) return ResultCustom.Failure(errorClass = AlarmControllerErrorSet.ValidationFailed (internalErrorMessage ="AlarmData validation failed, and res:$res" ))
			val resultForAlarmOpr = getPendingIntentForAlarm(receiverClass = receiverClass, context = componentActivity, alarmTriggerTime, alarmData = alarmData)
			val piForAlarm = resultForAlarmOpr.fold(
				onSuccess = {pi -> pi},
				onError = {failureRes->
					return when(failureRes){
						is AlarmControllerErrorSet.Unknown -> ResultCustom.Failure(errorClass = failureRes )
						is AlarmControllerErrorSet.PendingIntentAlreadyExist -> ResultCustom.Failure(errorClass = failureRes )
					}
				}
			)
			val alarmClockInfoObject = AlarmManager.AlarmClockInfo(alarmTriggerTime, piForAlarm.pendingIntentToGiveUserUpcomingAlarmInfoWhenAsked)
			alarmManager.setAlarmClock(alarmClockInfoObject, piForAlarm.pendingIntentForAlarm)
			logD("Alarm successfully scheduled.")
		}
	}

	private	fun getPendingIntentForAlarm(
			receiverClass:Class<out BroadcastReceiver>, context: Context, alarmTriggerTime:Long, alarmData: AlarmData,
	): ResultCustom<PendingIntentCreated, GetPendingIntentForAlarmError> {
		return ResultCustom.runCatching(
		{ exception ->AlarmControllerErrorSet.Unknown(internalErrorMessage = exception.toString()) }
		){
			val alarmId = alarmData.id
			val intent = intentAsAlarmActionData(alarmData = alarmData, alarmTriggerTime = alarmTriggerTime, receiverClass = receiverClass)
			val pendingIntentForAlarm = PendingIntent.getBroadcast(context, alarmId, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
			// meaning that the pending intent does not exist, and it is safe to create one
			var pendingIntentForAlarmInfo: PendingIntent
			val intentForAlarmMetaData:Intent = intent.clone() as Intent
			intentForAlarmMetaData.setClass(context, alarmInfoNotificationClass)
			pendingIntentForAlarmInfo = PendingIntent.getBroadcast(
				context, alarmTriggerTime.toInt(), intentForAlarmMetaData,
				PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
			)
			return ResultCustom.Success(PendingIntentCreated(pendingIntentForAlarmInfo, pendingIntentForAlarm))
		}
	}

	/** handle setting the alarms and if fails then cancel it and update the DB state not running else running */
	suspend fun startAlarmSeriesHandler(
			alarm: AlarmData, alarmManager: AlarmManager, activityContext: Context, receiverClass:Class<out BroadcastReceiver> = AlarmReceiver::class.java, now: Calendar = Calendar.getInstance(),
	): ResultCustom<AlarmData, StartAlarmSeriesHandlerError> {
		return ResultCustom.runCatching(
			{ exception -> AlarmControllerErrorSet.Unknown(internalErrorMessage = exception.toString()) }
		) {
			val rolledAlarm = alarm.copy(isReadyToUse = true).rollOverIfTimeIntervalPassed(now)
			rolledAlarm.validate().let {
				if (it != AlarmDataValidationResult.Success) return ResultCustom.Failure(errorClass = AlarmControllerErrorSet.ValidationFailed(internalErrorMessage = "AlarmData validation failed, and res:$it"))
			}
			val upsertResult = alarmRepository.upsertAlarm(rolledAlarm)
			val insertedAlarmData: AlarmData =	if (upsertResult == -1L){
				rolledAlarm // updated the alarm
			}else {
				rolledAlarm.copy(id = upsertResult.toInt())
			}
			insertedAlarmData.validate().let {
				if (it != AlarmDataValidationResult.Success) return ResultCustom.Failure(errorClass = AlarmControllerErrorSet.ValidationFailed(internalErrorMessage = "AlarmData validation failed, and res:$it"))
			}
			val timeReturned = insertedAlarmData.getNextAlarmTriggerTime(now) ?: return ResultCustom.Failure(errorClass = AlarmControllerErrorSet.ValidationFailed(internalErrorMessage = "[startAlarmSeriesHandler] Can't get first alarm to start the series\n alarmData:$insertedAlarmData"))
			scheduleAlarm(alarmData = insertedAlarmData, alarmManager = alarmManager, alarmTriggerTime = timeReturned, componentActivity = activityContext, receiverClass = receiverClass).fold(
				onSuccess = {},
				onError = { failureRes ->
					if (failureRes is AlarmControllerErrorSet.ValidationFailed){
						alarmRepository.deleteAlarm(insertedAlarmData)
					}
					this@AlarmsController.cancelAlarmHandler(insertedAlarmData, activityContext, alarmManager)
					return when (failureRes) {
						is AlarmControllerErrorSet.Unknown -> ResultCustom.Failure(errorClass = failureRes)
						is AlarmControllerErrorSet.ValidationFailed -> ResultCustom.Failure(errorClass = failureRes)
						is AlarmControllerErrorSet.PendingIntentNotFound -> ResultCustom.Failure(errorClass = failureRes)
						is AlarmControllerErrorSet.PendingIntentAlreadyExist -> ResultCustom.Failure(errorClass = failureRes)
					}
				}
			)
			insertedAlarmData
		}
	}


	/** tries to cancel the alarm and update the Db state, if error  */
	suspend fun cancelAlarmHandler(
			alarmData: AlarmData, context: Context, alarmManager: AlarmManager
	): ResultCustom<Unit, CancelAlarmHandlerError> {
		return ResultCustom.runCatching(
			{ exception ->AlarmControllerErrorSet.Unknown(internalErrorMessage = exception.toString()) }
		){
			this@AlarmsController.updateAlarmStateInDb(alarmData.copy(isReadyToUse = false)).fold(
				onSuccess = {},
				onError = { failureRes ->
					return when (failureRes) {
						is AlarmControllerErrorSet.Unknown -> ResultCustom.Failure(errorClass = failureRes)
						is AlarmControllerErrorSet.DatabaseOperationFailed -> ResultCustom.Failure(errorClass = failureRes)
					}
				}
			)
			cancelAlarm(alarmData, context,alarmManager).fold(
				onSuccess = {},
				onError = { failureRes ->
					alarmRepository.upsertAlarm(alarmData.copy(isReadyToUse = false))
					return when (failureRes) {
						is AlarmControllerErrorSet.Unknown -> ResultCustom.Failure(errorClass = failureRes)
						is AlarmControllerErrorSet.CancellingAlarmError -> ResultCustom.Failure(errorClass = failureRes)
						is AlarmControllerErrorSet.ValidationFailed -> ResultCustom.Failure(errorClass = failureRes)
					}
				}
			)
		}
	}

	/** tries to delete the alarm and update the Db state, if error  */
	suspend fun deleteAlarmHandler(
			alarmData: AlarmData, context: Context, alarmManager: AlarmManager
	): ResultCustom<Unit, DeleteAlarmHandlerError> {
		return ResultCustom.runCatching(
			{ exception ->AlarmControllerErrorSet.Unknown(internalErrorMessage = exception.toString()) }
		){
			// first update the Db as it is more visible to the user, and there is a race condition here as we can't do it in parallel, as are accessing the db in failure in both
			// the alarm was not in the DB so return
			// since the alarm is not in the DB we can't insert it, and also we can't just insert it as it wasn't there and the user did not want it

			val alarmDeletionTask  = scope.async(Dispatchers.IO) {alarmRepository.deleteAlarm(alarmData)}
			val cancellationResult =cancelAlarm(alarmData, context,alarmManager)
			val rowsAffected = alarmDeletionTask.await()
			if (rowsAffected == 0) return ResultCustom.Failure(errorClass = AlarmControllerErrorSet.DatabaseOperationFailed(internalErrorMessage = "No such alarm in the Db to delete, alarmData:$alarmData, rowsAffected:$rowsAffected"))
			cancellationResult.fold(
				onSuccess = {},
				onError = { failureRes ->
					// since we can't delete it then we should just put it back and tell the user to try to cancel it again
					logD("error when cancelling alarm while deleting alarm is $failureRes")
					if ( failureRes is AlarmControllerErrorSet.Unknown) {
						alarmRepository.upsertAlarm(alarmData.copy(isReadyToUse = false))
					}
					return when (failureRes) {
						is AlarmControllerErrorSet.Unknown -> ResultCustom.Failure(errorClass = failureRes)
						is AlarmControllerErrorSet.CancellingAlarmError -> ResultCustom.Failure(errorClass = failureRes)
						is AlarmControllerErrorSet.ValidationFailed -> ResultCustom.Failure(errorClass = failureRes)
					}
				}
			)
		}
	}

	/** cancels scheduled alarm*/
	private fun cancelAlarm(
			alarmData: AlarmData, context: Context, alarmManager: AlarmManager
	): ResultCustom<Unit, CancelAlarmError > {
		return ResultCustom.runCatching(
			{ exception ->AlarmControllerErrorSet.Unknown(internalErrorMessage = exception.toString()) }
		){
			logD("the time value gotten is ${getTimeInHumanReadableFormatProtectFrom0Included(alarmData.alarmTime)}")
			val intent = intentAsAlarmActionData(alarmData = alarmData, receiverClass = alarmReceiverClass, alarmTriggerTime = alarmData.alarmTime)
			cancelPendingIntentReceiver(intent, context,  alarmManager, alarmData.id)
			cancelPendingIntentReceiver(intent, context,   alarmManager, alarmData.alarmTime.toInt())
			logD("the alarmData is $alarmData")
		}
	}

	private fun cancelPendingIntentReceiver(
			baseIntent: Intent, context: Context, alarmManager: AlarmManager, intentRequestCode: Int
	){
		val pendingIntent = PendingIntent.getBroadcast(context, intentRequestCode, baseIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
		pendingIntent?.let { alarmManager.cancel(it) }
	}

	private suspend fun updateAlarmStateInDb(
			alarmData: AlarmData
	): ResultCustom<Unit, UpdateAlarmInDbError> {
		return ResultCustom.runCatching(
			{ exception ->AlarmControllerErrorSet.Unknown(internalErrorMessage = exception.toString()) }
		){
			val rowsAffected = alarmRepository.updateAlarm(alarmData)
			if (rowsAffected == 0) return ResultCustom.Failure(errorClass = AlarmControllerErrorSet.DatabaseOperationFailed(internalErrorMessage = "No rows affected for alarmData:$alarmData"))
		}
	}

	// schedule the next alarm and if error notify the user
	suspend fun  scheduleNextAlarmInSeries(alarmIntent: AlarmActivityIntentData) {
		// TODO: this class shouldn't handle error as that  should be done in the alarm Receiver
		logD("alarmIntent:$alarmIntent")
		val alarmData: AlarmData? = alarmRepository.getAlarmById(alarmIntent.alarmIdInDb)

		if (alarmData == null) {
			logD("AlarmData not found for ID: ${alarmIntent.alarmIdInDb}")
			// ERROR: this shouldn't be true
			return
		}
		logD("Fired at: ${getTimeInHumanReadableFormatProtectFrom0Included(alarmIntent.alarmTriggerTime)}")

		if (alarmData.repeatDays == null) {
			logD("Alarm has no repeat days. Marking as not ready.")
			this.updateAlarmStateInDb(alarmData.copy(isReadyToUse = false)).fold(
				onSuccess = { logD("Alarm done") },
				onError = { failureRes ->
					logD("DB update failed for finished alarm: $failureRes")
					errorHandler.handleError(ResultCustom.Failure(failureRes))
				}
			)
			return
		}
		logD("Advancing to next valid day for repeatDays: ${alarmData.repeatDays}")

		val afterFired = Calendar.getInstance().apply { timeInMillis = alarmIntent.alarmTriggerTime + 1 }
		val newAlarm = alarmData.rollOverIfTimeIntervalPassed(afterFired).copy(isReadyToUse = true)

		val validationRes = newAlarm.validate()
		if (validationRes != AlarmDataValidationResult.Success) {
			logD("New alarm for next repeat day failed validation: $validationRes")
			errorHandler.handleError(ResultCustom.Failure(AlarmControllerErrorSet.ValidationFailed(internalErrorMessage = validationRes.errorMessage())))
			this.updateAlarmStateInDb(alarmData.copy(isReadyToUse = false))
			return
		}

		this.updateAlarmStateInDb(newAlarm).fold(
			onSuccess = {},
			onError = { failureRes ->
				logD("DB update failed for next repeat day alarm: $failureRes")
				errorHandler.handleError(ResultCustom.Failure(failureRes))
				return
			}
		)
		scheduleAlarm(
			alarmManager = alarmManager,
			componentActivity = context,
			receiverClass = alarmReceiverClass,
			alarmData = newAlarm,
			alarmTriggerTime = newAlarm.alarmTime
		).fold(
			onSuccess = { logD("Scheduled next repeat-day alarm successfully") },
			onError = { failureRes ->
				logD("Error scheduling next repeat-day alarm: ${failureRes.internalErrorMessage}")
				errorHandler.handleError(ResultCustom.Failure(failureRes))
				this.updateAlarmStateInDb(newAlarm.copy(isReadyToUse = false))
				cancelAlarm(newAlarm, context, alarmManager)
			}
		)
	}

	suspend fun resetAlarmsHandler(
			alarmData:AlarmData, alarmManager: AlarmManager, activityContext: Context, now: Calendar = Calendar.getInstance()
	): ResultCustom<Unit, ResetAlarmError> {
		return ResultCustom.runCatching(
			{ exception ->AlarmControllerErrorSet.Unknown(internalErrorMessage = exception.toString()) }
		){
			val newAlarm =	alarmData.rollOverIfTimeIntervalPassed(now).copy(isReadyToUse = true)
			val newTriggerTime = newAlarm.getNextAlarmTriggerTime(now) ?:
				return ResultCustom.Failure(errorClass = AlarmControllerErrorSet.ValidationFailed(internalErrorMessage = "[resetAlarmsHandler]Can't get first alarm to start the series\n alarmData:$newAlarm"))

			val validationRes = newAlarm.validate()
			if (validationRes != AlarmDataValidationResult.Success){ return ResultCustom.Failure(errorClass = AlarmControllerErrorSet.ValidationFailed(internalErrorMessage = validationRes.errorMessage())) }

			val updatingAlarmStateJob = this.scope.async {this@AlarmsController.updateAlarmStateInDb(newAlarm)  }

			// using schedule alarm and not the series handler cause we could be in the middle of alarm time and would need to set it again not from start but from a specific time
			// TODO better approach would be to allow the seriesHandler to loop through current time and get the next time or a optional param called trigger time or call calculateNextAlarmInfo every time this would remove the need for resetAlarmsHandler as the start series handler  would handle it
			scheduleAlarm(
				alarmManager =alarmManager, componentActivity = activityContext, receiverClass = alarmReceiverClass, alarmData = newAlarm, alarmTriggerTime =newTriggerTime
			).fold(
				onSuccess = {},
				onError = { failureRes ->
					logD("the scheduleAlarm() failed with error $failureRes here cancelling and returning")
//					updatingAlarmStateJob.await()// don't care about the error
					updatingAlarmStateJob.cancel()// don't care about the error
					this.updateAlarmStateInDb(newAlarm.copy(isReadyToUse = false))
					cancelAlarm(newAlarm, activityContext, alarmManager) // if this fails then god help us
					return when (failureRes) {
						is AlarmControllerErrorSet.Unknown -> ResultCustom.Failure(errorClass = failureRes)
						is AlarmControllerErrorSet.ValidationFailed -> ResultCustom.Failure(errorClass = failureRes)
						is AlarmControllerErrorSet.PendingIntentNotFound -> ResultCustom.Failure(errorClass = failureRes)
						is AlarmControllerErrorSet.PendingIntentAlreadyExist -> ResultCustom.Failure(errorClass = failureRes)
					}
				}
			)
			updatingAlarmStateJob.await()
		}
	}

	// gives you an intent with the data class as extras
	private fun intentAsAlarmActionData( action:String = ALARM_ACTION, alarmData: AlarmData, alarmTriggerTime: Long, receiverClass:Class<out BroadcastReceiver>): Intent{
		val intent = Intent(ALARM_ACTION)
		val intentData = AlarmActivityIntentData(
			alarmIdInDb = alarmData.id, alarmTime = alarmData.alarmTime, alarmTriggerTime = alarmTriggerTime,
			message = alarmData.message, ringDurationInMin = alarmData.ringDurationInMin
		)
		intent.setClass(context, receiverClass)
		intent.putExtra("intentData", intentData)
		intent.putExtra("alarmIdInDb", alarmData.id)
		return intent
	}

	fun getTimeInHumanReadableFormatProtectFrom0Included(t:Long): String{
		if (t == 0L) return "--the time here(probablyFromTheIntent) is 0--"
		return SimpleDateFormat("h:mm:ss a yyyy-MM-dd", Locale.getDefault()).format(Date(t))
	}

	private  fun  logD(msg: String): Unit{
		Log.d("AAAAAA", "[AlarmController] $msg")
	}
}