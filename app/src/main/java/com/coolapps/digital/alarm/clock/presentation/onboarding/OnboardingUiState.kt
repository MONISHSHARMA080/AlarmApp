package com.coolapps.digital.alarm.clock.presentation.onboarding

import com.coolapps.digital.alarm.clock.data.local.AlarmData
import com.coolapps.digital.alarm.clock.data.preferences.OnboardingFlow
import com.coolapps.digital.alarm.clock.presentation.util.permissions.PermissionStep

enum class ButtonState {
	Enabled,
	Disabled,
	Hidden
}

enum class DisplaySate {
	Problem,
	UserStruggles,
	UserProfile,
	Permission,
	FirstAlarmIntro,
	CreateFirstAlarm,
	AlarmResult,
	PrePaywall, // prepare screen for hard paywall
	OnboardingPaywall;

	override fun toString(): String {
		return when(this){
			Problem -> "Problem"
			UserStruggles -> "UserStruggles"
			UserProfile -> "UserProfile"
			Permission -> "Permission"
			FirstAlarmIntro -> "FirstAlarmIntro"
			CreateFirstAlarm -> "CreateFirstAlarm"
			AlarmResult -> "AlarmResult"
			PrePaywall -> "PrePaywall"
			OnboardingPaywall ->"OnboardingPaywall"
		}
	}
}


data class OnboardingUiState(
	val displaySate: DisplaySate = DisplaySate.Problem,
	val askForNotificationPermission: Boolean = false,
	val missingSteps: List<PermissionStep> = emptyList(),
	val alarmData: AlarmData? = null,
	val allCriticalGranted: Boolean = false,
	val onboardingFlow: OnboardingFlow = OnboardingFlow.ONBOARDING_FLOW_UNSPECIFIED
){
	override fun toString(): String {
		return "OnboardingUiState: DisplayState: $displaySate , askForNotificationPermission: $askForNotificationPermission, alarmData: $alarmData, missingSteps: $missingSteps, allCriticalGranted: $allCriticalGranted, onboardingFlow: $onboardingFlow"
	}
}