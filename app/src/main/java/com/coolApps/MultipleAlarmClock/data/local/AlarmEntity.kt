package com.coolApps.MultipleAlarmClock.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Serializable
@Entity(indices = [Index(value = ["alarmTime"])], )
data class AlarmData(
	@PrimaryKey(autoGenerate = true) val id: Int = 0,
	@ColumnInfo(name = "alarmTime") val alarmTime: Long,
	@ColumnInfo(name = "message") val message: String,
	val sound: String?,
	@ColumnInfo(name = "is_ready_to_use") val isReadyToUse: Boolean,
	@ColumnInfo(name = "repeat_days", defaultValue = "NULL") val repeatDays: RepeatDays? = null,
	@ColumnInfo(name = "is_force_loud_volume", defaultValue = "0") val isForceLoudVolume: Boolean = false,
	@ColumnInfo(name = "ring_duration_in_min", defaultValue = "2") val ringDurationInMin: Long = 2
){
	private fun getDateTimeFormatted(time:Long):String{
		return SimpleDateFormat("hh:mm a dd/MM/yyyy", Locale.getDefault()).format(time)
	}
	override fun toString(): String {
		return "AlarmData: alarmTime:${getDateTimeFormatted(alarmTime)}, message:$message ringDurationInMin:$ringDurationInMin alarmSoundUri:$sound repeatDays:$repeatDays, id:$id, isREadyToUse:$isReadyToUse, isForceLoudVolume:$isForceLoudVolume"
	}

	val alarmTimeCalendar: Calendar get() = Calendar.getInstance().apply { timeInMillis = alarmTime }

	fun rollOverIfTimeIntervalPassed(now: Calendar = Calendar.getInstance()): AlarmData{
		val alarmCalendar = Calendar.getInstance().apply { timeInMillis = alarmTime }
		while (alarmCalendar.timeInMillis < now.timeInMillis) {
			alarmCalendar.add(Calendar.DAY_OF_YEAR, 1)
		}
		repeatDays?.let { days ->
			val landedOn = alarmCalendar.toLocalDate()
			val nextValid = days.nextRepeatDate(landedOn) ?: landedOn
			val deltaDays = ChronoUnit.DAYS.between(landedOn, nextValid).toInt()
			if (deltaDays > 0) {
				alarmCalendar.add(Calendar.DAY_OF_YEAR, deltaDays)
			}
		}
		return copy(alarmTime = alarmCalendar.timeInMillis)
	}

	private fun Calendar.toLocalDate(): LocalDate = LocalDate.of(get(Calendar.YEAR), get(Calendar.MONTH) + 1, get(Calendar.DAY_OF_MONTH))

	fun getNextAlarmTriggerTime(now: Calendar = Calendar.getInstance()): Long? {
		return if (alarmTime > now.timeInMillis) alarmTime else null
	}

	fun validate():AlarmDataValidationResult {
		if (ringDurationInMin !in 1..30) {
			return AlarmDataValidationResult.RingDuration("Expected a value between 1 to 30 minutes, but Got $ringDurationInMin")
		}
		val now = Calendar.getInstance()
		val alarmCalendar = Calendar.getInstance().apply { timeInMillis = alarmTime }
		if (alarmCalendar.timeInMillis < now.timeInMillis){
			return AlarmDataValidationResult.IntervalNotInFuture("AlarmTime:${timeFormatted(alarmTime)} should be in future ahead of now:${ timeFormatted(now.timeInMillis) }.")
		}
		if (repeatDays != null) {
			val localDate = alarmCalendar.toLocalDate()
			if (!repeatDays.isSet(localDate.dayOfWeek)) {
				return AlarmDataValidationResult.WeekdayMismatch("Alarm date weekday (${localDate.dayOfWeek}) is not in configured repeatDays ($repeatDays)")
			}
		}
		return AlarmDataValidationResult.Success
	}

	private fun timeFormatted(x: Long): String {
		return SimpleDateFormat("h:mm:ss a yyyy-MM-dd", Locale.getDefault()).format(Date(x))
	}
}


sealed class AlarmDataValidationResult(
		private val errorMessage: String? = null
){
	data object Success: AlarmDataValidationResult()
	data class IntervalNotInFuture( val errorMessage: String) : AlarmDataValidationResult(errorMessage)
	data class RingDuration( val errorMessage: String) : AlarmDataValidationResult(errorMessage)
	data class WeekdayMismatch( val errorMessage: String) : AlarmDataValidationResult(errorMessage)

	override fun toString(): String {
		var err = errorMessage
		err = if (err?.isEmpty() == true){
			""
		}else{
			"ErrorMessage: $err"
		}
		val className = when(this){
			Success ->"Success"
			is IntervalNotInFuture ->"IntervalNotInFuture"
			is RingDuration -> "RingDuration"
			is WeekdayMismatch -> "WeekdayMismatch"
		}
		return "$className, $err "
	}

	fun isFailure(): Boolean{
		return this !is Success
	}

	fun errorMessage():String{
		return when(this){
			Success ->""
			is IntervalNotInFuture -> this.errorMessage
			is RingDuration -> this.errorMessage
			is WeekdayMismatch -> this.errorMessage
		}
	}
}
