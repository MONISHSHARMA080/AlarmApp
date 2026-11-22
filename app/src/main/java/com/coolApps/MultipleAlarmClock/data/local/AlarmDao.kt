package com.coolApps.MultipleAlarmClock.data.local

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {

	@Insert
	suspend fun insert(alarmData: AlarmData): Long

	@Query("DELETE FROM AlarmData WHERE alarmTime = :value")
	suspend fun deleteAlarm(value: Long): Int

	@Query("DELETE FROM AlarmData WHERE id = :id")
	suspend fun deleteAlarm(id: Int): Int

	@Delete
	suspend fun deleteAlarm(alarm: AlarmData): Int

	@Query("SELECT * FROM AlarmData")
	suspend fun getAllAlarms(): List<AlarmData>

	@Query("SELECT * FROM AlarmData ORDER BY alarmTime ASC")
	fun getAllAlarmsFlow(): Flow<List<AlarmData>>

	@Query("SELECT * FROM AlarmData WHERE alarmTime = :value LIMIT 1")
	suspend fun getAlarmByValues(value: Long): AlarmData?

	/**-1L when update occurs and rest is the row ID*/
	@Upsert
	suspend fun updateOrInsert(alarmData: AlarmData): Long

	@Query("UPDATE AlarmData SET is_ready_to_use = :isReadyToUse WHERE alarmTime = :alarmTime")
	suspend fun updateReadyToUseInAlarm(alarmTime: Long, isReadyToUse: Boolean)

	@Update
	suspend fun updateAlarm(alarmData: AlarmData): Int  // Returns number of rows updated

	@Query("SELECT * FROM AlarmData WHERE id = :id")
	suspend fun getAlarmById(id: Int): AlarmData?

	@Query(" UPDATE AlarmData SET is_ready_to_use = :isReadyToUse , alarmTime = :value WHERE id = :id")
	suspend fun updateAlarmForReset(id: Int, value: Long, isReadyToUse: Boolean)

	@Update
	suspend fun updateAlarmForReset(alarmData: AlarmData): Int

	@Query("DELETE  FROM AlarmData")
	suspend fun deleteAllAlarmsFromDb()

	@Query("""
        SELECT *
        FROM AlarmData
        WHERE is_ready_to_use = 1
          AND alarmTime > :now
        ORDER BY alarmTime ASC
        LIMIT 1
    """)
	suspend fun getNextUpcomingAlarm(now: Long): AlarmData?

	@Query("""
        SELECT MAX(alarmTime)
        FROM AlarmData
        WHERE is_ready_to_use = 0
          AND alarmTime <= :now
    """)
	suspend fun getLastCompletedAlarmTime(now: Long): Long?
}
