package com.coolapps.digital.alarm.clock.presentation.onboarding.flow

import androidx.navigation3.runtime.NavKey
import com.coolapps.digital.alarm.clock.data.local.AlarmData
import com.coolapps.digital.alarm.clock.presentation.util.permissions.PermissionStep
import kotlinx.serialization.Serializable

@Serializable
sealed interface OnboardingRoute : NavKey {
	val stepName: String
	val progress: Float

	@Serializable
	data object WakeUpTime : OnboardingRoute {
		override val stepName = "WakeUpTime"
		override val progress = 1f / 4f
	}

	@Serializable
	data object Permission : OnboardingRoute {
		override val stepName = "Permission"
		override val progress = 2f / 4f
	}

	@Serializable
	data object CreateFirstAlarm : OnboardingRoute {
		override val stepName = "CreateFirstAlarm"
		override val progress = 3f / 4f
	}

	@Serializable
	data object AlarmPreview : OnboardingRoute {
		override val stepName = "AlarmPreview"
		override val progress = 4f / 4f
	}
}

data class WakeUpOnboardingUiState(
	val wakeUpHour: Int = 7,
	val wakeUpMinute: Int = 0,
	val missingSteps: List<PermissionStep> = emptyList(),
	val allCriticalGranted: Boolean = false,
	val savedAlarmId: Int? = null,
	val alarmData: AlarmData? = null,
)
