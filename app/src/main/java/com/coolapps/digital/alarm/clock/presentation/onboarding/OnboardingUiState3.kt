package com.coolapps.digital.alarm.clock.presentation.onboarding

import com.coolapps.digital.alarm.clock.data.preferences.OnboardingFlow
import com.coolapps.digital.alarm.clock.presentation.util.permissions.PermissionStep

enum class DisplaySate3 {
	Problem,
	UserStruggles,
	UserProfile,
	Permission,
	PrePaywall, // prepare screen for hard paywall
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


data class OnboardingUiState3(
	val displaySate: DisplaySate3 = DisplaySate3.Problem,
	val askForNotificationPermission: Boolean = false,
	val missingSteps: List<PermissionStep> = emptyList(),
	val allCriticalGranted: Boolean = false,
	val onboardingFlow: OnboardingFlow = OnboardingFlow.ONBOARDING_FLOW_UNSPECIFIED
){
	override fun toString(): String {
		return "OnboardingUiState3: DisplayState: $displaySate , askForNotificationPermission: $askForNotificationPermission, missingSteps: $missingSteps, allCriticalGranted: $allCriticalGranted, onboardingFlow: $onboardingFlow"
	}
}
