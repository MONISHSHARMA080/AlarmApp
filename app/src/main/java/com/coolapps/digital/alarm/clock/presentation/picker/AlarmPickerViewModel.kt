package com.coolapps.digital.alarm.clock.presentation.picker

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coolapps.digital.alarm.clock.error.ErrorHandler
import com.coolapps.digital.alarm.clock.data.billing.EntitlementManager
import com.coolapps.digital.alarm.clock.data.local.AlarmData
import com.coolapps.digital.alarm.clock.data.local.AlarmDataValidationResult
import com.coolapps.digital.alarm.clock.data.local.RepeatDays
import com.coolapps.digital.alarm.clock.data.repository.AlarmSoundRepository
import com.coolapps.digital.alarm.clock.domain.usecase.AlarmsController
import com.coolapps.digital.alarm.clock.data.paywall.PaywallTriggerType
import com.coolapps.digital.alarm.clock.presentation.util.permissions.PermissionUtils
import com.coolapps.digital.alarm.clock.receiver.PlayAlarm
import com.coolapps.digital.alarm.clock.util.Analytics
import com.coolapps.digital.alarm.clock.util.Result
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Calendar

@HiltViewModel(assistedFactory = AlarmPickerViewModel.Factory::class)
class AlarmPickerViewModel @AssistedInject constructor(
		val analytics: Analytics,
		private val alarmManager: AlarmManager,
		private val alarmsController: AlarmsController,
		private val entitlementManager: EntitlementManager,
		private val errorHandler: ErrorHandler,
		private val alarmSoundRepository: AlarmSoundRepository,
		@ApplicationContext val context: Context,
		@Assisted private val alarmData: AlarmData?
) : ViewModel() {

	@AssistedFactory
	interface Factory {
		fun create(alarmData: AlarmData?): AlarmPickerViewModel
	}

	private val _uiState = MutableStateFlow(
		AlarmPickerUiState(
			alarmData = createDefaultAlarm(alarmData),
			initialAlarm = alarmData,
			areAllPermissionsGranted = PermissionUtils.allCriticalPermissionsGranted(context),
			progress = if (alarmData == null) Progress.StartTime else Progress.FullEditor
		)
	)
	val uiState: StateFlow<AlarmPickerUiState> = _uiState.asStateFlow()


	val listOfAlarms: StateFlow<List<AlarmSound>> = alarmSoundRepository
		.getAlarmSoundsStream()
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.Eagerly,
			initialValue = emptyList()
		)

	private val _previewingSound = MutableStateFlow<AlarmSound?>(null)
	val previewingSound = _previewingSound.asStateFlow()

	private val _previewingRandom = MutableStateFlow(false)
	val previewingRandom = _previewingRandom.asStateFlow()

	private val _selectedAlarmSound = MutableStateFlow<AlarmSound?>(null)
	val selectedAlarmSound = _selectedAlarmSound.asStateFlow()

	private val nonCancellableScope = CoroutineScope(NonCancellable)

	val isPremium: StateFlow<Boolean> = entitlementManager.isPremium

	private val playAlarm = PlayAlarm(context, analytics)

	init {
		// if we have an initial alarm then get it's start alarm sound name
		_uiState.value.alarmData.sound?.let { initialSoundUri ->
			viewModelScope.launch(Dispatchers.IO) {
				_selectedAlarmSound.value = alarmSoundRepository.resolveSound(initialSoundUri)
			}
		}
		// Paywall/RevenueCat disabled in this repo
		// observePremiumAccess()
	}

	fun consumePaywallTrigger() {
		_uiState.update { it.copy(paywallTriggerType = null) }
	}

	fun onSetAlarmClicked() {
		analytics.captureEvent("set alarm clicked", mapOf("Ui state" to _uiState.value.toString()))

		val current = _uiState.value
		val alarmToUse = current.alarmData.rollOverIfTimeIntervalPassed()
		val validationResult = alarmToUse.validate()

		_uiState.update { it.copy(alarmData = alarmToUse, validationResult = validationResult) }
		logD("validation result after setAlarmCLicked is $validationResult,current.areAllPermissionsGranted:${current.areAllPermissionsGranted}  ")

		if (validationResult !is AlarmDataValidationResult.Success) {
			captureUiStateAndSendAnalytics(_uiState.value)
			return
		}
		val areWeMissingSomePermission =PermissionUtils.allCriticalPermissionsGranted(context)
		if (!areWeMissingSomePermission) {
			val missing = PermissionUtils.getRequiredPermissionSteps(context)
			_uiState.update { it.copy(showPermissionDialog = true, missingSteps = missing) }
			logD("permissions not grated and result after checking is $missing")
			captureUiStateAndSendAnalytics(_uiState.value)
			return
		}

		viewModelScope.launch {
			setNewOrUpdateAlarm(alarmToUse, current.initialAlarm)
			_uiState.update { it.copy(alarmOperationCompletedGoBack = true) }
			captureUiStateAndSendAnalytics(_uiState.value)
		}
	}

	fun consumeAlarmOperationCompleted() {
		_uiState.update { it.copy(alarmOperationCompletedGoBack = false) }
	}

	fun consumeSoundSelectionCompleted() {
		_uiState.update { it.copy(soundSelectionCompletedGoBack = false) }
	}

	fun previewSound(sound: AlarmSound?) {
		val soundToPlay = sound ?: listOfAlarms.value.randomOrNull() ?: return
		val sameItemTapped = (sound == null && _previewingRandom.value) || (sound != null && _previewingSound.value?.soundUri == sound.soundUri)

		if (sameItemTapped) {
			stopPreview()
			return
		}

		stopPreview()
		playAlarm.play(soundToPlay.soundUri)
		_previewingSound.value = soundToPlay
		_previewingRandom.value = (sound == null)
	}

	fun stopPreview() {
		playAlarm.stop()
		_previewingSound.value = null
		_previewingRandom.value = false
	}

	fun captureUiStateAndSendAnalytics(state: AlarmPickerUiState) {
		val isNotificationsEnabled = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
		captureEvent(
			"alarmPickerUi_state_change",
			mapOf(
				"are all permission granted" to state.areAllPermissionsGranted,
				"validation error message" to state.validationResult.toString(),
				"ui_state" to uiState.value.toString(),
				"did user choose random alarmSound" to (state.alarmData.sound == null),
				"notification permission granted" to isNotificationsEnabled
			)
		)
	}

	fun updateProgress(newProgress: Progress) {
		_uiState.update { it.copy(progress = newProgress) }
		captureUiStateAndSendAnalytics(_uiState.value)
	}

	private fun updateAlarmData(transform: (AlarmData) -> AlarmData) {
		_uiState.update { state ->
			val transformed = transform(state.alarmData)
			val corrected = transformed.rollOverIfTimeIntervalPassed()

			val newAlarmTime = Calendar.getInstance().apply {
				timeInMillis = corrected.alarmTime
			}.truncatedToMinute().timeInMillis

			val finalAlarm = corrected.copy(alarmTime = newAlarmTime)
			state.copy(
				alarmData = finalAlarm,
				validationResult = finalAlarm.validate()
			)
		}
	}

	fun updateStartTime(newStartTime: Calendar) {
		updateAlarmData {
			it.copy(alarmTime = newStartTime.apply {
			}.truncatedToMinute().timeInMillis)
		}
		captureUiStateAndSendAnalytics(_uiState.value)
	}

	fun onAlarmSoundSelected(sound: AlarmSound?) {
		// Paywall disabled in this repo
		// val isSoundPickerPremium = analytics.featureFlagsData.value?.isSoundPickerPremium ?: true
		// if (isSoundPickerPremium && !isPremium.value && sound != null) {
		// 	_uiState.update {
		// 		it.copy(
		// 			paywallTriggerType = PaywallTriggerType.PremiumFeature,
		// 			pendingSound = sound
		// 		)
		// 	}
		// 	return
		// }

		_selectedAlarmSound.value = sound
		_uiState.update {
			it.copy(
				alarmData = it.alarmData.copy(sound = sound?.soundUri?.toString()),
				soundSelectionCompletedGoBack = true
			)
		}
		captureUiStateAndSendAnalytics(_uiState.value)
		previewSound(sound)
	}

	/** creates a default alarm data; either selects a time if [alarm] is null or else returns [alarm]*/
	private fun createDefaultAlarm(alarm: AlarmData?): AlarmData {
		logD("alarm is $alarm")
		return when(alarm){
			null ->{
				val alarmTime = Calendar.getInstance().apply {
					add(Calendar.MINUTE, 1)
				}.truncatedToMinute().timeInMillis
				logD(" alarmTime:${alarmsController.getTimeInHumanReadableFormatProtectFrom0Included(alarmTime)} ")

				AlarmData(
					alarmTime = alarmTime,
					message = "",
					sound = null,
					isReadyToUse = true
				)
			}
			else -> alarm
		}
	}

	fun checkPermissions(context: Context) {
		viewModelScope.launch {
			val liveCheck = PermissionUtils.allCriticalPermissionsGranted(context)
			_uiState.update { it.copy(areAllPermissionsGranted = liveCheck) }
			captureUiStateAndSendAnalytics(_uiState.value)
		}
	}

	fun dismissPermissionDialog() {
		_uiState.update { it.copy(showPermissionDialog = false) }
		captureUiStateAndSendAnalytics(_uiState.value)
		checkPermissions(context)
	}

	fun onDeleteClicked() {
		val alarmData: AlarmData  = uiState.value.initialAlarm ?: return
		nonCancellableScope.launch {
			alarmsController.deleteAlarmHandler(alarmData, context, alarmManager).fold(
				onSuccess = {
					analytics.captureEvent("alarm successfully deleted", mapOf("alarmId" to alarmData.id))
					_uiState.update { it.copy(alarmOperationCompletedGoBack = true) }
					captureUiStateAndSendAnalytics(_uiState.value)
				},
				onError = { error ->
					logD("error while deleting alarm: ${error.internalErrorMessage}")
					errorHandler.handleError(Result.Failure(error))
				}
			)
		}
	}

	fun updateDate(calVersion: Calendar) {
		val currentAlarm = _uiState.value.alarmData

		val newAlarmDate = Calendar.getInstance().apply {
			timeInMillis = currentAlarm.alarmTime
		}.withDate(calVersion)

		val updated = currentAlarm.copy(
			alarmTime = newAlarmDate.timeInMillis,
			repeatDays = null
		).rollOverIfTimeIntervalPassed()

		_uiState.update {
			it.copy(
				alarmData = updated,
				validationResult = updated.validate()
			)
		}
		captureUiStateAndSendAnalytics(_uiState.value)
	}

	fun updateRingDuration(newDuration: Long) {
		// Paywall disabled in this repo
		// val isSnoozePremium = analytics.featureFlagsData.value?.isSnoozePremium ?: false
		// if (isSnoozePremium && !isPremium.value) {
		// 	_uiState.update {
		// 		it.copy(
		// 			paywallTriggerType = PaywallTriggerType.PremiumFeature,
		// 			pendingRingDuration = newDuration
		// 		)
		// 	}
		// 	return
		// }
		applyRingDuration(newDuration)
	}

	private fun applyRingDuration(newDuration: Long) {
		_uiState.update { state ->
			val updated = state.alarmData.copy(ringDurationInMin = newDuration).rollOverIfTimeIntervalPassed()
			state.copy(
				alarmData = updated,
				validationResult = updated.validate()
			)
		}
	}

	fun updateMessage(newMessage: String) {
		_uiState.update { it.copy(alarmData = it.alarmData.copy(message = newMessage)) }
	}

	fun updateAlarmVolume(alarmVolume: Float?) {
		// Paywall disabled in this repo
		// val isCustomAlarmVolumePremium = analytics.featureFlagsData.value?.isCustomAlarmVolumePremium ?: false
		// if (alarmVolume != null && isCustomAlarmVolumePremium && !isPremium.value) {
		// 	_uiState.update { it.copy(paywallTriggerType = PaywallTriggerType.PremiumFeature) }
		// 	return
		// }
		_uiState.update {
			it.copy(alarmData = it.alarmData.copy(alarmVolume = alarmVolume?.coerceIn(0f, 1f)))
		}
	}

	fun captureEvent(name:String, properties: Map<String, Any>){
		viewModelScope.launch {
			analytics.captureEvent(name, properties)
		}
	}

	fun screen(screenName:String, properties: Map<String, Any>? = null): Unit {
		viewModelScope.launch {
			analytics.screen(screenName, properties)
		}
	}

	private fun setNewOrUpdateAlarm(newAlarmData: AlarmData, oldAlarm: AlarmData? ){
		viewModelScope.launch {
			logD("deleting the alarm $oldAlarm")
			val alarmScheduledResult = alarmsController.startAlarmSeriesHandler(
				alarm = newAlarmData.copy(id = oldAlarm?.id ?: 0),
				alarmManager, context
			)
			alarmScheduledResult.fold(
				onSuccess = {
					launch {
						val eventText = if (oldAlarm != null) "alarm_edited"  else "new_alarm_set"
						analytics.captureEvent(eventText,
							mapOf(
								"alarmData" to newAlarmData.toString(),
								"oldAlarm" to oldAlarm.toString(),
							)
						)
					}
				},
				onError = { error ->
					errorHandler.handleError(Result.Failure(error))
					logD("there is a error in editing new alarm-->${error.internalErrorMessage}")
				}
			)
		}
	}

	fun onRepeatDayClicked(day: DayOfWeek) {
		// Paywall disabled in this repo
		// viewModelScope.launch {
		// 	val isWeeklyRepeatPremium = analytics.featureFlagsData.value?.isWeeklyRepeatPremium ?: true
		// 	when{
		// 		!isWeeklyRepeatPremium || isPremium.value ->{
		// 			toggleRepeatDay(day)
		// 		}
		// 		else ->{
		// 			_uiState.update {
		// 				it.copy(
		// 					paywallTriggerType = PaywallTriggerType.PremiumFeature,
		// 					pendingRepeatDay = day
		// 				)
		// 			}
		// 		}
		// 	}
		// }
		toggleRepeatDay(day)
	}

	fun toggleRepeatDay(day: DayOfWeek) {
		_uiState.update { state ->
			val current = state.alarmData
			val currentSet = current.repeatDays?.toSet() ?: emptySet()
			val newSet = if (day in currentSet) currentSet - day else currentSet + day
			val newRepeatDays = RepeatDays.of(newSet)

			val rebasedAlarm = if (newRepeatDays == null) {
				val today = LocalDate.now()
				val newTime = Calendar.getInstance().apply {
					timeInMillis = current.alarmTime
					set(Calendar.YEAR, today.year)
					set(Calendar.MONTH, today.monthValue - 1)
					set(Calendar.DAY_OF_MONTH, today.dayOfMonth)
				}.truncatedToMinute()
				current.copy(
					alarmTime = newTime.timeInMillis,
					repeatDays = null
				)
			} else {
				val nextDate = newRepeatDays.nextRepeatDate(LocalDate.now()) ?: LocalDate.now()
				val newTime = Calendar.getInstance().apply {
					timeInMillis = current.alarmTime
					set(Calendar.YEAR, nextDate.year)
					set(Calendar.MONTH, nextDate.monthValue - 1)
					set(Calendar.DAY_OF_MONTH, nextDate.dayOfMonth)
				}.truncatedToMinute()
				current.copy(
					alarmTime = newTime.timeInMillis,
					repeatDays = newRepeatDays
				)
			}

			val corrected = rebasedAlarm.rollOverIfTimeIntervalPassed()
			state.copy(alarmData = corrected, validationResult = corrected.validate())
		}
		captureUiStateAndSendAnalytics(_uiState.value)
	}

	private fun observePremiumAccess() {
		viewModelScope.launch {
			entitlementManager.isPremium.collect { isPremium ->
				if (isPremium) {
					val pendingDay = _uiState.value.pendingRepeatDay
					val pendingSound = _uiState.value.pendingSound
					val pendingRingDuration = _uiState.value.pendingRingDuration

					if (pendingDay != null) {
						toggleRepeatDay(pendingDay)
					}

					if (pendingRingDuration != null) {
						applyRingDuration(pendingRingDuration)
					}

					if (pendingSound != null) {
						_selectedAlarmSound.value = pendingSound
						_uiState.update {
							it.copy(
								alarmData = it.alarmData.copy(sound = pendingSound.soundUri.toString()),
								soundSelectionCompletedGoBack = true
							)
						}
					}

					if (pendingDay != null || pendingSound != null || pendingRingDuration != null) {
						_uiState.update {
							it.copy(
								paywallTriggerType = null,
								pendingRepeatDay = null,
								pendingSound = null,
								pendingRingDuration = null
							)
						}
					}
				}
			}
		}
	}

	override fun onCleared() {
		super.onCleared()
		stopPreview()
		playAlarm.destroy()
	}
	private fun Calendar.truncatedToMinute(): Calendar = apply {
		set(Calendar.SECOND, 0)
		set(Calendar.MILLISECOND, 0)
	}

	private fun Calendar.withDate(other: Calendar): Calendar = apply {
		set(Calendar.YEAR, other.get(Calendar.YEAR))
		set(Calendar.MONTH, other.get(Calendar.MONTH))
		set(Calendar.DAY_OF_MONTH, other.get(Calendar.DAY_OF_MONTH))
		truncatedToMinute()
	}
	private fun logD(message: String): Unit {
		Log.d("AAAAA", "[AlarmPickerViewModel] $message")
	}


}