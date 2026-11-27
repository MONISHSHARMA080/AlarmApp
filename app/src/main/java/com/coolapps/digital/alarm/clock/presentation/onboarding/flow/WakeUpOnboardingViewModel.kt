package com.coolapps.digital.alarm.clock.presentation.onboarding.flow

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coolapps.digital.alarm.clock.R
import com.coolapps.digital.alarm.clock.data.local.AlarmData
import com.coolapps.digital.alarm.clock.data.preferences.Settings
import com.coolapps.digital.alarm.clock.data.preferences.copy
import com.coolapps.digital.alarm.clock.domain.repository.AlarmRepository
import com.coolapps.digital.alarm.clock.presentation.picker.AlarmEditorMode
import com.coolapps.digital.alarm.clock.presentation.util.permissions.PermissionUtils
import com.coolapps.digital.alarm.clock.util.Analytics
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@HiltViewModel
class WakeUpOnboardingViewModel @Inject constructor(
	val analytics: Analytics,
	alarmRepository: AlarmRepository,
	private val settingsDataStore: DataStore<Settings>,
	@ApplicationContext private val context: Context,
) : ViewModel() {

	private val _displayState = MutableStateFlow(WakeUpOnboardingUiState())

	val displayState: StateFlow<WakeUpOnboardingUiState> = combine(
		_displayState,
		alarmRepository.getAlarmsStream(),
	) { uiState, alarmList ->
		val alarm = uiState.savedAlarmId?.let { id ->
			alarmList.firstOrNull { it.id == id } ?: uiState.alarmData
		}
		uiState.copy(alarmData = alarm)
	}.stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(5_000),
		initialValue = WakeUpOnboardingUiState(),
	)

	init {
		refreshPermissions()
	}

	fun refreshPermissions() {
		val missing = PermissionUtils.getRequiredPermissionSteps(context)
		val allCriticalGranted = PermissionUtils.allCriticalPermissionsGranted(context)
		_displayState.update {
			it.copy(missingSteps = missing, allCriticalGranted = allCriticalGranted)
		}
	}

	fun onWakeUpTimeChanged(hour: Int, minute: Int) {
		_displayState.update { it.copy(wakeUpHour = hour, wakeUpMinute = minute) }
	}

	fun onAlarmSaved(alarm: AlarmData) {
		_displayState.update { it.copy(savedAlarmId = alarm.id, alarmData = alarm) }
	}

	fun resolveEditorMode(): AlarmEditorMode {
		val saved = displayState.value.alarmData ?: _displayState.value.alarmData
		return if (saved != null) AlarmEditorMode.Edit(saved) else AlarmEditorMode.Create(buildPresetAlarm())
	}

	private fun buildPresetAlarm(): AlarmData {
		val state = _displayState.value
		val zoneId = ZoneId.systemDefault()
		val today = LocalDate.now(zoneId)
		val millis = today.atTime(LocalTime.of(state.wakeUpHour, state.wakeUpMinute))
			.atZone(zoneId)
			.toInstant()
			.toEpochMilli()

		val rolled = AlarmData(
			alarmTime = millis,
			message = context.getString(R.string.onboarding_wakeup_default_alarm_message),
			sound = null,
			isReadyToUse = true,
		).rollOverIfTimeIntervalPassed()

		if (rolled.getNextAlarmTriggerTime() != null) return rolled

		val tomorrowMillis = Instant.ofEpochMilli(rolled.alarmTime)
			.atZone(zoneId)
			.plusDays(1)
			.toInstant()
			.toEpochMilli()
		return rolled.copy(alarmTime = tomorrowMillis)
	}

	fun onNextClicked(currentStep: OnboardingRoute): OnboardingRoute? {
		val currentState = _displayState.value
		analytics.captureEvent(
			"onboarding_next_clicked",
			mapOf("step" to currentStep.stepName, "onBoardingUiState" to displayState.value.toString()),
		)

		if (currentStep == OnboardingRoute.WakeUpTime) {
			analytics.captureEvent(
				"onboarding_wakeup_time_submitted",
				mapOf(
					"hour" to currentState.wakeUpHour,
					"minute" to currentState.wakeUpMinute,
				),
			)
		}

		return when (currentStep) {
			OnboardingRoute.WakeUpTime -> OnboardingRoute.Permission
			OnboardingRoute.Permission -> OnboardingRoute.CreateFirstAlarm
			OnboardingRoute.CreateFirstAlarm -> OnboardingRoute.AlarmPreview
			OnboardingRoute.AlarmPreview -> null
		}
	}

	fun onPreviousClicked(currentStep: OnboardingRoute) {
		analytics.captureEvent("onboarding_previous_clicked", mapOf("step" to currentStep.stepName))
	}

	fun finishOnboarding(onFinished: () -> Unit = {}) {
		analytics.captureEvent("onboarding_finished", emptyMap())
		viewModelScope.launch {
			withContext(NonCancellable) {
				settingsDataStore.updateData { data ->
					if (data.installEpochTimeMs == 0L) {
						data.copy {
							isFirstLaunch = false
							installEpochTimeMs = System.currentTimeMillis()
						}
					} else {
						data.copy {
							isFirstLaunch = false
						}
					}
				}
			}
			onFinished()
		}
	}
}
