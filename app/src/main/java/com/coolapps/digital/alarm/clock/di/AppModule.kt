package com.coolapps.digital.alarm.clock.di

import android.app.AlarmManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.coolapps.digital.alarm.clock.data.preferences.Settings
import com.coolapps.digital.alarm.clock.data.preferences.SettingsSerializer
import com.coolapps.digital.alarm.clock.error.ErrorHandler
import com.coolapps.digital.alarm.clock.data.local.AlarmDao
import com.coolapps.digital.alarm.clock.data.local.AlarmDatabase
import com.coolapps.digital.alarm.clock.data.repository.AlarmRepositoryImpl
import com.coolapps.digital.alarm.clock.domain.repository.AlarmRepository
import com.coolapps.digital.alarm.clock.util.Analytics
import com.coolapps.digital.alarm.clock.util.NotificationHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers


@Module
@InstallIn(SingletonComponent::class)
object AppModule {

	@Provides
	@Singleton
	fun provideDatabase(@ApplicationContext context: Context): AlarmDatabase {
		return Room.databaseBuilder<AlarmDatabase>(
			context = context,
			name = "alarm-database"
		)
			.setDriver(AndroidSQLiteDriver())
			.fallbackToDestructiveMigration(dropAllTables = true)
			.build()
	}

	@Provides
	@Singleton
	fun provideProtoDataStore(@ApplicationContext context: Context): DataStore<Settings> {
		return DataStoreFactory.create(
			serializer = SettingsSerializer,
			produceFile = { context.dataStoreFile("user_settings.pb") },
			corruptionHandler = ReplaceFileCorruptionHandler { Settings.getDefaultInstance() },
		)
	}

	@Provides
	fun provideAlarmDao(db: AlarmDatabase): AlarmDao = db.alarmDao()

	@Provides
	@Singleton
	fun provideAlarmRepository(repository: AlarmRepositoryImpl): AlarmRepository = repository


	@Provides
	fun provideAlarmManager(@ApplicationContext context: Context): AlarmManager {
		return context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
	}

	@Provides
	@Singleton
	fun provideAnalytics(@ApplicationContext context: Context): Analytics {
		return Analytics(context)
	}

	@Provides
	fun provideNotificationHandler(@ApplicationContext context: Context): NotificationHandler {
		return NotificationHandler(context)
	}

	@Provides
	fun provideErrorHandler(notificationHandler: NotificationHandler, analytics: Analytics): ErrorHandler {
		return ErrorHandler(notificationHandler, analytics)
	}
}
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

	@Provides
	@IoDispatcher
	fun provideIoDispatcher(): CoroutineDispatcher =
		Dispatchers.IO
}