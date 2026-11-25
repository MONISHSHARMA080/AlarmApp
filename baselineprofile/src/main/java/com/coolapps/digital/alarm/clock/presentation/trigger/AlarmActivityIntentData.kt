package com.coolapps.digital.alarm.clock.presentation.trigger

import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
data class AlarmActivityIntentData(
    val alarmIdInDb: Int,
    val alarmTime: Long,
    val alarmTriggerTime: Long,
    val message: String,
    val ringDurationInMin: Long = 2,
) : Parcelable
