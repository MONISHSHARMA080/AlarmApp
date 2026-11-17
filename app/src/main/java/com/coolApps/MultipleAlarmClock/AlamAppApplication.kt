package com.coolApps.MultipleAlarmClock

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import jakarta.inject.Inject

@HiltAndroidApp
class AlarmApp : Application(), Configuration.Provider {

	@Inject
	lateinit var workerFactory: HiltWorkerFactory
//	private val coroutineScope = CoroutineScope(Dispatchers.Main)

	override fun onCreate() {
		super.onCreate()
//		coroutineScope.launch {
//			Purchases.logLevel = LogLevel.DEBUG
//			Purchases.configure(
//				PurchasesConfiguration.Builder(this@AlarmApp,BuildConfig.REVENUECAT_API_KEY)
//					.preferredUILocaleOverride(Locale.getDefault().language)
//					.build()
//			)
//		}
	}

	override val workManagerConfiguration: Configuration get() = Configuration.Builder()
			.setWorkerFactory(workerFactory)
			.build()

}
