package com.coolapps.digital.alarm.clock.domain.usecase

import com.coolapps.digital.alarm.clock.data.local.AlarmData
import com.coolapps.digital.alarm.clock.domain.repository.AlarmRepository

class ToggleAlarmUseCase(private val alarmRepository: AlarmRepository) {
    suspend operator fun invoke(alarmData: AlarmData, isReadyToUse: Boolean) {
        alarmRepository.updateAlarm(alarmData.copy(isReadyToUse = isReadyToUse))
    }
}
