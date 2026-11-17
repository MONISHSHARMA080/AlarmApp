package com.coolApps.MultipleAlarmClock

import android.app.AlarmManager
import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.coolApps.MultipleAlarmClock.data.local.AlarmData
import com.coolApps.MultipleAlarmClock.data.local.RepeatDays
import com.coolApps.MultipleAlarmClock.di.DispatcherModule
import com.coolApps.MultipleAlarmClock.di.IoDispatcher
import com.coolApps.MultipleAlarmClock.domain.repository.AlarmRepository
import com.coolApps.MultipleAlarmClock.domain.usecase.AlarmsController
import com.coolApps.MultipleAlarmClock.util.Result
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

@HiltAndroidTest
@UninstallModules(DispatcherModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class AlarmSeriesRepeatDaysTest {

	@get:Rule
	val hiltRule = HiltAndroidRule(this)

	@BindValue
	@JvmField
	val fakeAlarmRepository: AlarmRepository = FakeAlarmRepository()

	private val testDispatcher = StandardTestDispatcher()

	@BindValue
	@JvmField
	@IoDispatcher
	val ioDispatcher: CoroutineDispatcher =
		testDispatcher

	@Inject
	lateinit var controller: AlarmsController

	private lateinit var context: Context
	private lateinit var alarmManager: AlarmManager
	private lateinit var shadowAlarmManager: ShadowAlarmManager

	private val testWeeks = 2

	@Before
	fun setUp() {
		hiltRule.inject()
		context = ApplicationProvider.getApplicationContext()
		alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
		shadowAlarmManager = shadowOf(alarmManager)
		ShadowAlarmManager.setAutoSchedule(true)
	}

	private fun generateRandomRepeatDays(): RepeatDays {
		val allDays = DayOfWeek.entries
		val randomDaysCount = Random.nextInt(1, 8)
		val randomDays = allDays.shuffled().take(randomDaysCount).toSet()
		return RepeatDays.of(randomDays)!!
	}

	private fun getNextValidStartCalendar(repeatDays: RepeatDays, hour: Int): Calendar {
		var currentDate = LocalDate.now().plusDays(1)
		while (!repeatDays.isSet(currentDate.dayOfWeek)) {
			currentDate = currentDate.plusDays(1)
		}
		return Calendar.getInstance().apply {
			set(Calendar.YEAR, currentDate.year)
			set(Calendar.MONTH, currentDate.monthValue - 1)
			set(Calendar.DAY_OF_MONTH, currentDate.dayOfMonth)
			set(Calendar.HOUR_OF_DAY, hour)
			set(Calendar.MINUTE, 0)
			set(Calendar.SECOND, 0)
			set(Calendar.MILLISECOND, 0)
		}
	}

	private fun generateExpectedAlarms(
		baseAlarmTime: Long,
		repeatDays: RepeatDays,
		weeksToTest: Int,
		startFromNow: Long
	): List<Long> {
		val expectedAlarms = mutableListOf<Long>()
		val daysToTest = weeksToTest * 7
		val baseCal = Calendar.getInstance().apply { timeInMillis = baseAlarmTime }

		for (i in 0 until daysToTest) {
			val currentCal = (baseCal.clone() as Calendar).apply {
				add(Calendar.DAY_OF_YEAR, i)
			}
			val currentLocalDate = LocalDate.of(
				currentCal.get(Calendar.YEAR),
				currentCal.get(Calendar.MONTH) + 1,
				currentCal.get(Calendar.DAY_OF_MONTH)
			)
			if (repeatDays.isSet(currentLocalDate.dayOfWeek)) {
				val trigger = currentCal.timeInMillis
				if (trigger > startFromNow) {
					expectedAlarms.add(trigger)
				}
			}
		}
		return expectedAlarms
	}

	private fun verifyAlarmsWithNestedLoops(
		expectedAlarmList: List<Long>,
		startDate: LocalDate,
		repeatDays: RepeatDays,
		testWeeks: Int
	) {
		val expectedAlarmsByDay = expectedAlarmList.groupBy { triggerTime ->
			val cal = Calendar.getInstance().apply { timeInMillis = triggerTime }
			LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
		}

		var totalFiredCount = 0
		for (week in 0 until testWeeks) {
			for (dayOffset in 0 until 7) {
				val currentDate = startDate.plusDays(week * 7L + dayOffset)
				if (repeatDays.isSet(currentDate.dayOfWeek)) {
					val alarmsToday = expectedAlarmsByDay[currentDate] ?: emptyList()
					for (expectedTrigger in alarmsToday) {
						val scheduled = shadowAlarmManager.peekNextScheduledAlarm()
						assertThat(scheduled).isNotNull()
						assertThat(scheduled!!.triggerAtMs).isEqualTo(expectedTrigger)

						val delta = expectedTrigger - SystemClock.uptimeMillis()

						shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(delta))
						testDispatcher.scheduler.advanceUntilIdle()
						shadowOf(Looper.getMainLooper()).idle()
						testDispatcher.scheduler.advanceUntilIdle()

						totalFiredCount++
					}
				}
			}
		}
		assertThat(totalFiredCount).isEqualTo(expectedAlarmList.size)
	}

	@Test
	fun `start alarm series form beginning`() = runTest {
		val repeatDays = generateRandomRepeatDays()
		val startCal = getNextValidStartCalendar(repeatDays, 17)

		val alarmTime = startCal.timeInMillis

		val alarm = AlarmData(
			alarmTime = alarmTime,
			message = "test alarm repeat days",
			isReadyToUse = false,
			repeatDays = repeatDays,
			sound = null
		)

		val now = alarmTime - 200L
		val expectedAlarmList = generateExpectedAlarms(alarmTime, repeatDays, testWeeks, now)

		SystemClock.setCurrentTimeMillis(now)
		logD("test AlarmData:$alarm, expectedAlarms:${expectedAlarmList.size}")

		val result = controller.startAlarmSeriesHandler(
			alarm = alarm,
			alarmManager = alarmManager,
			activityContext = context
		)
		assertThat(result).isInstanceOf(Result.Success::class.java)

		val storedAlarm = fakeAlarmRepository.getAllAlarms().single()
		assertThat(storedAlarm.isReadyToUse).isTrue()
		assertThat(storedAlarm.alarmTime).isEqualTo(alarmTime)

		val startDate = LocalDate.of(startCal.get(Calendar.YEAR), startCal.get(Calendar.MONTH) + 1, startCal.get(Calendar.DAY_OF_MONTH))
		verifyAlarmsWithNestedLoops(expectedAlarmList, startDate, repeatDays, testWeeks)
	}

	@Test
	fun `start alarm series form the middle`() = runTest {
		val repeatDays = generateRandomRepeatDays()
		val startCal = getNextValidStartCalendar(repeatDays, 5)

		val alarmTime = startCal.timeInMillis

		val alarm = AlarmData(
			alarmTime = alarmTime,
			message = "test alarm form middle",
			isReadyToUse = false,
			repeatDays = repeatDays,
			sound = null
		)

		// now is after today's occurrence, so start should roll to the next valid day
		val now = alarmTime + Duration.ofHours(12).toMillis()
		val nowCalendar = Calendar.getInstance().apply { timeInMillis = now }

		val expectedAlarmList = generateExpectedAlarms(alarmTime, repeatDays, testWeeks, now)
		check(expectedAlarmList.isNotEmpty())

		SystemClock.setCurrentTimeMillis(now)

		val result = controller.startAlarmSeriesHandler(
			alarm = alarm,
			alarmManager = alarmManager,
			activityContext = context,
			now = nowCalendar
		)
		assertThat(result).isInstanceOf(Result.Success::class.java)

		val storedAlarm = fakeAlarmRepository.getAllAlarms().single()
		assertThat(storedAlarm.isReadyToUse).isTrue()
		assertThat(storedAlarm.alarmTime).isEqualTo(expectedAlarmList.first())

		val firstCal = Calendar.getInstance().apply { timeInMillis = expectedAlarmList.first() }
		val startDate = LocalDate.of(firstCal.get(Calendar.YEAR), firstCal.get(Calendar.MONTH) + 1, firstCal.get(Calendar.DAY_OF_MONTH))
		verifyAlarmsWithNestedLoops(expectedAlarmList, startDate, repeatDays, testWeeks)
	}

	@Test
	fun `resetAlarmsHandler when current time is before alarm series start`() = runTest {
		val repeatDays = generateRandomRepeatDays()
		val startCal = getNextValidStartCalendar(repeatDays, 10)

		val alarmTime = startCal.timeInMillis

		val alarm = AlarmData(
			alarmTime = alarmTime, message = "test before start",
			isReadyToUse = false, repeatDays = repeatDays, sound = null
		)
		val alarmId = fakeAlarmRepository.saveAlarm(alarm).toInt()
		val savedAlarm = alarm.copy(id = alarmId)

		val now = alarmTime - Duration.ofHours(1).toMillis()
		val expectedAlarmList = generateExpectedAlarms(alarmTime, repeatDays, testWeeks, now)

		SystemClock.setCurrentTimeMillis(now)
		val nowCalendar = Calendar.getInstance().apply { timeInMillis = now }

		val result = controller.resetAlarmsHandler(savedAlarm, alarmManager, context, nowCalendar)
		assertThat(result).isInstanceOf(Result.Success::class.java)

		val startDate = LocalDate.of(startCal.get(Calendar.YEAR), startCal.get(Calendar.MONTH) + 1, startCal.get(Calendar.DAY_OF_MONTH))
		verifyAlarmsWithNestedLoops(expectedAlarmList, startDate, repeatDays, testWeeks)
	}

	@Test
	fun `resetAlarmsHandler when current time is in the middle of alarm series`() = runTest {
		val repeatDays = generateRandomRepeatDays()
		val startCal = getNextValidStartCalendar(repeatDays, 10)

		val alarmTime = startCal.timeInMillis

		val alarm = AlarmData(
			alarmTime = alarmTime, message = "test in middle",
			isReadyToUse = false, repeatDays = repeatDays, sound = null
		)
		val alarmId = fakeAlarmRepository.saveAlarm(alarm).toInt()
		val savedAlarm = alarm.copy(id = alarmId)

		// now is after the stored occurrence
		val now = alarmTime + Duration.ofHours(12).toMillis()
		SystemClock.setCurrentTimeMillis(now)
		val nowCalendar = Calendar.getInstance().apply { timeInMillis = now }

		val expectedAlarmList = generateExpectedAlarms(alarmTime, repeatDays, testWeeks, now)
		check(expectedAlarmList.isNotEmpty())

		val result = controller.resetAlarmsHandler(savedAlarm, alarmManager, context, nowCalendar)
		assertThat(result).isInstanceOf(Result.Success::class.java)

		val firstCal = Calendar.getInstance().apply { timeInMillis = expectedAlarmList.first() }
		val startDate = LocalDate.of(firstCal.get(Calendar.YEAR), firstCal.get(Calendar.MONTH) + 1, firstCal.get(Calendar.DAY_OF_MONTH))
		verifyAlarmsWithNestedLoops(expectedAlarmList, startDate, repeatDays, testWeeks)
	}

	@Test
	fun `resetAlarmsHandler when current time is past the end time`() = runTest {
		val repeatDays = generateRandomRepeatDays()
		val startCal = getNextValidStartCalendar(repeatDays, 10)

		val alarmTime = startCal.timeInMillis

		val alarm = AlarmData(
			alarmTime = alarmTime, message = "test past end",
			isReadyToUse = false, repeatDays = repeatDays, sound = null
		)
		val alarmId = fakeAlarmRepository.saveAlarm(alarm).toInt()
		val savedAlarm = alarm.copy(id = alarmId)

		// a week in the future ensures multiple occurrences are stale
		val now = alarmTime + Duration.ofDays(7).toMillis()
		val expectedAlarmList = generateExpectedAlarms(alarmTime, repeatDays, testWeeks, now)
		check(expectedAlarmList.isNotEmpty())

		SystemClock.setCurrentTimeMillis(now)
		val nowCalendar = Calendar.getInstance().apply { timeInMillis = now }

		val result = controller.resetAlarmsHandler(savedAlarm, alarmManager, context, nowCalendar)
		assertThat(result).isInstanceOf(Result.Success::class.java)

		val firstCal = Calendar.getInstance().apply { timeInMillis = expectedAlarmList.first() }
		val startDate = LocalDate.of(firstCal.get(Calendar.YEAR), firstCal.get(Calendar.MONTH) + 1, firstCal.get(Calendar.DAY_OF_MONTH))
		verifyAlarmsWithNestedLoops(expectedAlarmList, startDate, repeatDays, testWeeks)
	}

	@Test
	fun `scheduleNextAlarmInSeries skips weekend for Monday Friday alarm`() = runTest {
		val repeatDays = RepeatDays.of(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY))!!

		val fridayCal = Calendar.getInstance().apply {
			set(Calendar.HOUR_OF_DAY, 5)
			set(Calendar.MINUTE, 0)
			set(Calendar.SECOND, 0)
			set(Calendar.MILLISECOND, 0)
			while (get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY || timeInMillis < System.currentTimeMillis()) {
				add(Calendar.DAY_OF_YEAR, 1)
			}
		}

		val alarmTime = fridayCal.timeInMillis
		val alarm = AlarmData(
			alarmTime = alarmTime,
			message = "weekday alarm",
			isReadyToUse = false,
			repeatDays = repeatDays,
			sound = null
		)

		val now = alarmTime - 200L
		SystemClock.setCurrentTimeMillis(now)

		val result = controller.startAlarmSeriesHandler(
			alarm = alarm,
			alarmManager = alarmManager,
			activityContext = context
		)
		assertThat(result).isInstanceOf(Result.Success::class.java)

		val storedId = fakeAlarmRepository.getAllAlarms().single().id
		var scheduled = shadowAlarmManager.peekNextScheduledAlarm()
		assertThat(scheduled).isNotNull()
		assertThat(scheduled!!.triggerAtMs).isEqualTo(alarmTime)

		var delta = alarmTime - SystemClock.uptimeMillis()
		shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(delta))
		testDispatcher.scheduler.advanceUntilIdle()
		shadowOf(Looper.getMainLooper()).idle()
		testDispatcher.scheduler.advanceUntilIdle()

		scheduled = shadowAlarmManager.peekNextScheduledAlarm()
		assertThat(scheduled).isNotNull()
		val expectedMonday = Calendar.getInstance().apply {
			timeInMillis = alarmTime
			add(Calendar.DAY_OF_YEAR, 3)
		}.timeInMillis
		assertThat(scheduled!!.triggerAtMs).isEqualTo(expectedMonday)

		val updated = fakeAlarmRepository.getAlarmById(storedId)
		assertThat(updated).isNotNull()
		assertThat(updated!!.alarmTime).isEqualTo(expectedMonday)
		assertThat(updated.isReadyToUse).isTrue()
	}

	private fun logD(str: String){
		Log.d("AAAA", "[Test] $str")
	}
	fun getTimeInHumanReadableFormatProtectFrom0Included(t:Long): String{
		if (t == 0L) return "--the time here(probablyFromTheIntent) is 0--"
		return SimpleDateFormat("h:mm:ss a yyyy-MM-dd", Locale.getDefault()).format(Date(t))
	}
	fun geTimeWithoutDate(t:Long): String{
		if (t == 0L) return "--the time here(probablyFromTheIntent) is 0--"
		return SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date(t))
	}
}
