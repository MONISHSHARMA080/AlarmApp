package com.coolapps.digital.alarm.clock.presentation.onboarding

import com.coolapps.digital.alarm.clock.data.preferences.OnboardingFlow
import com.coolapps.digital.alarm.clock.presentation.util.permissions.PermissionStep

enum class DisplaySate2 {
	Problem,
	UserStruggles,
	UserProfile,
	Permission,
	PrePaywall,
	OnboardingPaywall;

	override fun toString(): String {
		return when(this){
			Problem -> "Problem"
			UserStruggles -> "UserStruggles"
			UserProfile -> "UserProfile"
			Permission -> "Permission"
			PrePaywall -> "PrePaywall"
			OnboardingPaywall ->"OnboardingPaywall"
		}
	}
}


data class OnboardingUiState2(
	val displaySate: DisplaySate2 = DisplaySate2.Problem,
	val askForNotificationPermission: Boolean = false,
	val missingSteps: List<PermissionStep> = emptyList(),
	val allCriticalGranted: Boolean = false,
	val onboardingFlow: OnboardingFlow = OnboardingFlow.ONBOARDING_FLOW_UNSPECIFIED
){
	override fun toString(): String {
		return "OnboardingUiState2: DisplayState: $displaySate , askForNotificationPermission: $askForNotificationPermission, missingSteps: $missingSteps, allCriticalGranted: $allCriticalGranted, onboardingFlow: $onboardingFlow"
	}
}
