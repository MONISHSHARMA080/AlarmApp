package com.coolApps.MultipleAlarmClock.data.local

import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(entities = [AlarmData::class], version = 1)
@ColumnTypeConverters(RepeatDaysConverter::class)
abstract class AlarmDatabase : RoomDatabase() {
	abstract fun alarmDao(): AlarmDao
}
