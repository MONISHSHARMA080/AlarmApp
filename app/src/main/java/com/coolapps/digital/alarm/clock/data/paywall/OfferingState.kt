package com.coolapps.digital.alarm.clock.data.paywall

import com.revenuecat.purchases.Offering

sealed interface OfferingState {
	data object Loading : OfferingState
	data class Loaded(val offering: Offering?) : OfferingState
	data object Failed : OfferingState
}