package com.coolapps.digital.alarm.clock.presentation.picker

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.coolapps.digital.alarm.clock.R
import com.coolapps.digital.alarm.clock.data.local.AlarmData
import com.coolapps.digital.alarm.clock.data.local.AlarmDataValidationResult
import com.coolapps.digital.alarm.clock.presentation.AlarmContainerBoundsTransform
import com.coolapps.digital.alarm.clock.presentation.AlarmTimeBoundsTransform
import com.coolapps.digital.alarm.clock.presentation.logD
import com.coolapps.digital.alarm.clock.data.paywall.PaywallTriggerType
import com.coolapps.digital.alarm.clock.presentation.util.permissions.AlarmPermissionDialog
import java.util.Calendar

// ─── Public entry-point screens ───────────────────────────────────────────────

/**
 * Edit-alarm route entry point.
 * Owns the Scaffold with sharedBounds so the card-to-screen hero transition
 * works. Always shows Progress.FullEditor — no stepper inside.
 */
@OptIn(
	ExperimentalMaterial3Api::class,
	ExperimentalMaterial3ExpressiveApi::class,
	ExperimentalSharedTransitionApi::class
)
@Composable
fun EditAlarmScreen(
		alarmSetProceed: () -> Unit,
		settingAlarmCancelled: () -> Unit,
		onNavigateToSoundList: () -> Unit,
		onNavigateToMissionList: () -> Unit,
		onShowPaywall: (PaywallTriggerType) -> Unit,
		alarmData: AlarmData,
		viewModel: AlarmPickerViewModel,
		sharedTransitionScope: SharedTransitionScope? = null,
		animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
	val uiState by viewModel.uiState.collectAsState()
	val isPremium by viewModel.isPremium.collectAsState()
	val selectedSound by viewModel.selectedAlarmSound.collectAsState()
	val view = LocalView.current
	val context = LocalContext.current

	LaunchedEffect(uiState, isPremium) {
		logD("ui state:$uiState")
		logD("isPremium:$isPremium")
	}
	LaunchedEffect(uiState.alarmOperationCompletedGoBack) {
		if (uiState.alarmOperationCompletedGoBack) {
			viewModel.consumeAlarmOperationCompleted()
			alarmSetProceed()
		}
	}
	// Paywall disabled in this repo
	// LaunchedEffect(uiState.paywallTriggerType) {
	// 	uiState.paywallTriggerType?.let { trigger ->
	// 		onShowPaywall(trigger)
	// 		viewModel.consumePaywallTrigger()
	// 	}
	// }

	LifecycleResumeEffect(Unit) {
		viewModel.checkPermissions(context)
		onPauseOrDispose { }
	}

	if (uiState.showPermissionDialog) {
		AlarmPermissionDialog(
			uiState.missingSteps,
			onAllCriticalGranted = { viewModel.dismissPermissionDialog() },
			onDismiss = { viewModel.dismissPermissionDialog() },
			onTrackEvent = { event, prop -> viewModel.captureEvent(event, prop) }
		)
	}

	var showCalendar by remember { mutableStateOf(false) }
	if (showCalendar) {
		DatePickerModal(
			onDateSelected = { date ->
				if (date != null) {
					val cal = Calendar.getInstance().apply { timeInMillis = date }
					viewModel.updateDate(cal)
				}
				showCalendar = false
			},
			onDismiss = { showCalendar = false }
		)
	}

	val horizontalPadding = rememberAdaptiveHorizontalPadding()

	// Build the optional sharedBounds scaffold modifier
	val scaffoldModifier: Modifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
		with(sharedTransitionScope) {
			Modifier.sharedBounds(
				sharedContentState = rememberSharedContentState(key = "alarm_card_${alarmData.id}"),
				animatedVisibilityScope = animatedVisibilityScope,
				clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(0.dp)),
				boundsTransform = AlarmContainerBoundsTransform,
				resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds
			)
		}
	} else {
		Modifier
	}

	Scaffold(
		modifier = scaffoldModifier,
		contentWindowInsets = WindowInsets.safeDrawing,
		topBar = {
			ExpressiveAlarmTopBar(
				onBackClick = { settingAlarmCancelled() }
			)
		},
		bottomBar = {
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.background(colorScheme.background)
					.navigationBarsPadding()
					.padding(16.dp)
					.padding(bottom = 20.dp),
				contentAlignment = Alignment.Center
			) {
				Row(
					modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					// Delete button (edit mode — always FullEditor, never new alarm)
					CancelAndDeleteButton(
						currentProgress = Progress.FullEditor,
						isNewAlarm = false,
						onClick = { viewModel.onDeleteClicked() }
					)

					val isInactiveEdit = uiState.initialAlarm?.isReadyToUse == false
					val canSetAlarm = uiState.validationResult == AlarmDataValidationResult.Success || isInactiveEdit
					PrimaryActionButton(
						currentProgress = Progress.FullEditor,
						uiState = uiState,
						onAction = {
							if (canSetAlarm) {
								viewModel.onSetAlarmClicked()
								view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
							}
						}
					)
				}
			}
		}
	) { screenPadding ->
		AlarmEditorBody(
			modifier = Modifier
				.fillMaxSize()
				.padding(screenPadding)
				.consumeWindowInsets(screenPadding)
				.padding(horizontal = horizontalPadding),
			uiState = uiState,
			viewModel = viewModel,
			selectedSoundTitle = selectedSound?.title,
			alarmId = alarmData.id,
			showCalendarRequest = { showCalendar = true },
			onNavigateToSoundList = onNavigateToSoundList,
			onNavigateToMissionList = onNavigateToMissionList,
			sharedTransitionScope = sharedTransitionScope,
			animatedVisibilityScope = animatedVisibilityScope,
			animateScroll = false
		)
	}
}

/**
 * New-alarm route entry point.
 * Owns the Scaffold with StartTime → FullEditor stepper.
 * No sharedBounds on the scaffold. AnimatedContent stepper is kept as-is.
 */
@OptIn(
	ExperimentalMaterial3Api::class,
	ExperimentalMaterial3ExpressiveApi::class,
	ExperimentalSharedTransitionApi::class
)
@Composable
fun NewAlarmScreen(
		alarmSetProceed: () -> Unit,
		settingAlarmCancelled: () -> Unit,
		onNavigateToSoundList: () -> Unit,
		onNavigateToMissionList: () -> Unit,
		onShowPaywall: (PaywallTriggerType) -> Unit,
		linearProgressBar: (@Composable () -> Unit)? = null,
		viewModel: AlarmPickerViewModel,
		// kept for API compatibility but unused in this route (no shared-element)
		sharedTransitionScope: SharedTransitionScope? = null,
		animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
	val fromOnboarding = linearProgressBar != null
	logD("fromOnboarding:$fromOnboarding")

	val uiState by viewModel.uiState.collectAsState()
	val isPremium by viewModel.isPremium.collectAsState()
	val selectedSound by viewModel.selectedAlarmSound.collectAsState()
	val view = LocalView.current
	val context = LocalContext.current

	LaunchedEffect(uiState, isPremium) {
		logD("ui state:$uiState")
		logD("isPremium:$isPremium")
	}
	LaunchedEffect(uiState.alarmOperationCompletedGoBack) {
		if (uiState.alarmOperationCompletedGoBack) {
			viewModel.consumeAlarmOperationCompleted()
			alarmSetProceed()
		}
	}
	// Paywall disabled in this repo
	// LaunchedEffect(uiState.paywallTriggerType) {
	// 	uiState.paywallTriggerType?.let { trigger ->
	// 		onShowPaywall(trigger)
	// 		viewModel.consumePaywallTrigger()
	// 	}
	// }

	LifecycleResumeEffect(Unit) {
		viewModel.checkPermissions(context)
		onPauseOrDispose { }
	}

	if (uiState.showPermissionDialog) {
		AlarmPermissionDialog(
			uiState.missingSteps,
			onAllCriticalGranted = { viewModel.dismissPermissionDialog() },
			onDismiss = { viewModel.dismissPermissionDialog() },
			onTrackEvent = { event, prop -> viewModel.captureEvent(event, prop) }
		)
	}

	var showCalendar by remember { mutableStateOf(false) }
	if (showCalendar) {
		DatePickerModal(
			onDateSelected = { date ->
				if (date != null) {
					val cal = Calendar.getInstance().apply { timeInMillis = date }
					viewModel.updateDate(cal)
				}
				showCalendar = false
			},
			onDismiss = { showCalendar = false }
		)
	}

	val horizontalPadding = rememberAdaptiveHorizontalPadding()
	val currentProgress = if (fromOnboarding) uiState.progress else Progress.FullEditor

	val alarmTimePickerState = key(currentProgress, uiState.alarmData.alarmTime) {
		rememberTimePickerState(
			initialHour = uiState.alarmData.alarmTimeCalendar.get(Calendar.HOUR_OF_DAY),
			initialMinute = uiState.alarmData.alarmTimeCalendar.get(Calendar.MINUTE),
			is24Hour = false
		)
	}

	Scaffold(
		contentWindowInsets = WindowInsets.safeDrawing,
		topBar = {
			if (!fromOnboarding) {
				ExpressiveAlarmTopBar(
					onBackClick = { settingAlarmCancelled() }
				)
			}
		},
		bottomBar = {
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.background(colorScheme.background)
					.navigationBarsPadding()
					.padding(16.dp)
					.padding(bottom = 20.dp),
				contentAlignment = Alignment.Center
			) {
				Row(
					modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					CancelAndDeleteButton(
						currentProgress = currentProgress,
						isNewAlarm = true,
						fromOnboarding = fromOnboarding,
						onClick = {
							when (currentProgress) {
								Progress.StartTime -> settingAlarmCancelled()
								Progress.FullEditor -> {
									if (fromOnboarding) {
										if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
											view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
										}
										viewModel.updateProgress(Progress.StartTime)
									} else {
										settingAlarmCancelled()
									}
								}
							}
						}
					)

					PrimaryActionButton(
						currentProgress = currentProgress,
						uiState = uiState,
						onAction = {
							if (fromOnboarding) {
								when (currentProgress) {
									Progress.StartTime -> {
										val selectedAlarmTime = (uiState.alarmData.alarmTimeCalendar.clone() as Calendar).apply {
											set(Calendar.HOUR_OF_DAY, alarmTimePickerState.hour)
											set(Calendar.MINUTE, alarmTimePickerState.minute)
										}
										view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
										viewModel.updateStartTime(selectedAlarmTime)
										viewModel.updateProgress(Progress.FullEditor)
									}
									Progress.FullEditor -> {
										val isInactiveEdit = uiState.initialAlarm?.isReadyToUse == false
										val canSetAlarm = uiState.validationResult == AlarmDataValidationResult.Success || isInactiveEdit
										if (canSetAlarm) {
											viewModel.onSetAlarmClicked()
											view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
										}
									}
								}
							} else {
								val isInactiveEdit = uiState.initialAlarm?.isReadyToUse == false
								val canSetAlarm = uiState.validationResult == AlarmDataValidationResult.Success || isInactiveEdit
								if (canSetAlarm) {
									viewModel.onSetAlarmClicked()
									view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
								}
							}
						}
					)
				}
			}
		}
	) { screenPadding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(screenPadding)
				.consumeWindowInsets(screenPadding),
		) {
			val stepperSpatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
			val stepperEffectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
			AnimatedContent(
				targetState = currentProgress,
				modifier = Modifier.weight(1f).fillMaxWidth(),
				transitionSpec = {
					val direction = if (targetState.ordinal > initialState.ordinal) {
						AnimatedContentTransitionScope.SlideDirection.Left
					} else {
						AnimatedContentTransitionScope.SlideDirection.Right
					}
					slideIntoContainer(
						towards = direction,
						animationSpec = stepperSpatialSpec
					) + fadeIn(
						animationSpec = stepperEffectsSpec
					) togetherWith
							slideOutOfContainer(
								towards = direction,
								animationSpec = stepperSpatialSpec
							) + fadeOut(
						animationSpec = stepperEffectsSpec
					)
				},
				contentAlignment = Alignment.Center,
				label = "alarm_picker_navigation"
			) { progress ->
				when (progress) {
					Progress.StartTime -> {
						TimePickerWithoutDialog(
							state = alarmTimePickerState,
							modifier = Modifier.padding(horizontal = horizontalPadding),
							uiState = uiState,
							onDisabledTimeSelected = { onDisabledTimeSelected(view) }
						)
					}
					Progress.FullEditor -> {
						AlarmEditorBody(
							modifier = Modifier
								.fillMaxSize()
								.padding(horizontal = horizontalPadding),
							uiState = uiState,
							viewModel = viewModel,
							selectedSoundTitle = selectedSound?.title,
							alarmId = null,
							showCalendarRequest = { showCalendar = true },
							onNavigateToSoundList = onNavigateToSoundList,
							onNavigateToMissionList = onNavigateToMissionList,
							// NewAlarmScreen never participates in the card hero transition
							sharedTransitionScope = null,
							animatedVisibilityScope = null,
							animateScroll = true
						)
					}
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveAlarmTopBar(
	onBackClick: () -> Unit,
	title: String = ""
) {
	val interactionSource = remember { MutableInteractionSource() }
	val isPressed by interactionSource.collectIsPressedAsState()
	val cornerRadius by animateDpAsState(
		targetValue = if (isPressed) 22.dp else 14.dp,
		animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
		label = "top_bar_back_corner"
	)
	val scale by animateFloatAsState(
		targetValue = if (isPressed) 0.92f else 1f,
		animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
		label = "top_bar_back_scale"
	)

	CenterAlignedTopAppBar(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 8.dp),
		colors = TopAppBarDefaults.topAppBarColors(
			containerColor = Color.Transparent,
			scrolledContainerColor = Color.Transparent
		),
		title = {
			if (title.isNotEmpty()) {
				Text(
					text = title,
					style = typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
					color = colorScheme.onSurface
				)
			}
		},
		navigationIcon = {
			Surface(
				onClick = onBackClick,
				interactionSource = interactionSource,
				shape = RoundedCornerShape(cornerRadius),
				color = colorScheme.surfaceContainerHigh,
				contentColor = colorScheme.onSurface,
				modifier = Modifier
					.padding(start = 4.dp)
					.size(44.dp)
					.graphicsLayer {
						scaleX = scale
						scaleY = scale
					}
			) {
				Box(contentAlignment = Alignment.Center) {
					Icon(
						imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
						contentDescription = stringResource(R.string.alarm_picker_back_desc)
					)
				}
			}
		}
	)
}

// ─── Private body composable ──────────────────────────────────────────────────

/**
 * The TimeRow + SettingsCard + spacers layout, shared between both screens.
 * sharedTransitionScope / animatedVisibilityScope are forwarded to TimeRow
 * only when this is called from EditAlarmScreen (non-null).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun AlarmEditorBody(
		modifier: Modifier = Modifier,
		uiState: AlarmPickerUiState,
		viewModel: AlarmPickerViewModel,
		selectedSoundTitle: String?,
		alarmId: Int?,
		showCalendarRequest: () -> Unit,
		onNavigateToSoundList: () -> Unit,
		onNavigateToMissionList: () -> Unit,
		sharedTransitionScope: SharedTransitionScope? = null,
		animatedVisibilityScope: AnimatedVisibilityScope? = null,
		animateScroll: Boolean
) {
	val view = LocalView.current
	var showTimePicker by remember { mutableStateOf(false) }

	Box(
		modifier = modifier,
		contentAlignment = Alignment.TopCenter
	) {
		Column(
			modifier = Modifier
				.widthIn(max = 600.dp)
				.fillMaxSize()
				.padding(horizontal = 2.dp),
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			Spacer(modifier = Modifier.weight(0.38f))
			TimeRow(
				uiState = uiState,
				onStartTimeChange = { viewModel.updateStartTime(it) },
				onDisabledTimeSelected = { onDisabledTimeSelected(view) },
				sharedTransitionScope = sharedTransitionScope,
				animatedVisibilityScope = animatedVisibilityScope,
				alarmId = alarmId,
				boundsTransform = AlarmTimeBoundsTransform,
				showStartTimePicker = showTimePicker,
				onShowStartTimePickerChange = { showTimePicker = it }
			)
			Spacer(modifier = Modifier.weight(0.12f))
			// Edit button outside / above the settings card – same action as clicking the time
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 8.dp, vertical = 4.dp),
				horizontalArrangement = Arrangement.End,
				verticalAlignment = Alignment.CenterVertically
			) {
				val editInteractionSource = remember { MutableInteractionSource() }
				val isEditPressed by editInteractionSource.collectIsPressedAsState()
				val editCorner by animateDpAsState(
					targetValue = if (isEditPressed) 14.dp else 28.dp,
					animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
					label = "edit_corner_editor"
				)
				val editScale by animateFloatAsState(
					targetValue = if (isEditPressed) 0.92f else 1f,
					animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
					label = "edit_scale_editor"
				)
				FilledTonalIconButton(
					onClick = {
						view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
						showTimePicker = true
					},
					interactionSource = editInteractionSource,
					shape = RoundedCornerShape(editCorner),
					modifier = Modifier
						.size(48.dp)
						.graphicsLayer {
							scaleX = editScale
							scaleY = editScale
						}
				) {
					Icon(
						imageVector = Icons.Rounded.Edit,
						contentDescription = stringResource(R.string.alarm_picker_edit_time_desc),
						modifier = Modifier.size(20.dp)
					)
				}
			}
			Spacer(modifier = Modifier.height(4.dp))
			SettingsCard(
				uiState = uiState,
				updateRingDuration = { viewModel.updateRingDuration(it) },
				messageValueChanged = { viewModel.updateMessage(it) },
				updateAlarmVolume = { viewModel.updateAlarmVolume(it) },
				calenderButtonClicked = showCalendarRequest,
				selectSoundButtonClicked = onNavigateToSoundList,
				selectMissionButtonClicked = {
					viewModel.captureEvent("mission_row_clicked", mapOf("is_edit" to (alarmId != null)))
					onNavigateToMissionList()
				},
				repeatDayToggled = { day -> viewModel.onRepeatDayClicked(day) },
				selectedSoundName = selectedSoundTitle ?: stringResource(R.string.alarm_picker_sound_random),
				modifier = Modifier.weight(1f, fill = false),
				animateScroll = animateScroll
			)
			Spacer(modifier = Modifier.weight(0.04f))
		}
	}
}

// ─── Shared bottom-bar buttons (unchanged logic) ──────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CancelAndDeleteButton(
        currentProgress: Progress,
        modifier: Modifier = Modifier,
        isNewAlarm: Boolean,
        fromOnboarding: Boolean = false,
        onClick: () -> Unit,
) {
  val isDeleteMode = currentProgress == Progress.FullEditor && !isNewAlarm
  val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
  val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()

  AnimatedContent(
          targetState = isDeleteMode,
          transitionSpec = {
            fadeIn(effectsSpec) togetherWith fadeOut(effectsSpec) using SizeTransform { _, _ -> spatialSpec }
          },
          label = "cancel_delete_button_animation"
  ) { targetIsDelete ->
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cornerRadius by animateDpAsState(
            targetValue = if (isPressed) 16.dp else 28.dp,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "cancel_delete_corner"
    )
    val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.96f else 1f,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "cancel_delete_scale"
    )

    if (targetIsDelete) {
      TextButton(
              onClick = onClick,
              interactionSource = interactionSource,
              modifier = modifier
                      .height(56.dp)
                      .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                      },
              contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
              shape = RoundedCornerShape(cornerRadius),
              colors = ButtonDefaults.textButtonColors(
                      containerColor = colorScheme.errorContainer.copy(alpha = 0.45f),
                      contentColor = colorScheme.error
              )
      ) {
        Text(
                text = stringResource(R.string.alarm_picker_delete_alarm),
                style = typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
        )
      }
    } else {
      OutlinedButton(
              onClick = onClick,
              interactionSource = interactionSource,
              modifier = modifier
                      .height(56.dp)
                      .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                      },
              contentPadding = PaddingValues(horizontal = 28.dp, vertical = 0.dp),
              shape = RoundedCornerShape(cornerRadius)
      ) {
        AnimatedContent(
                targetState = currentProgress,
                transitionSpec = {
                  fadeIn(effectsSpec) togetherWith fadeOut(effectsSpec) using SizeTransform { _, _ -> spatialSpec }
                },
                label = "cancel_button_text"
        ) { progress ->
          Text(
			  text = when (progress) {
				Progress.StartTime -> stringResource(R.string.alarm_picker_cancel)
				Progress.FullEditor -> if (isNewAlarm && !fromOnboarding) {
					stringResource(R.string.alarm_picker_cancel)
				} else {
					stringResource(R.string.alarm_picker_previous)
				}
			  },
			  style = typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PrimaryActionButton(
        currentProgress: Progress,
        uiState: AlarmPickerUiState,
        onAction: () -> Unit,
        modifier: Modifier = Modifier
) {
  val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
  val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()

  AnimatedContent(
          targetState = currentProgress,
          transitionSpec = {
            fadeIn(effectsSpec) togetherWith fadeOut(effectsSpec) using SizeTransform { _, _ -> spatialSpec }
          },
          label = "primary_action_button_animation"
  ) { progress ->
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cornerRadius by animateDpAsState(
            targetValue = if (isPressed) 16.dp else 28.dp,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "primary_cta_corner"
    )
    val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.96f else 1f,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "primary_cta_scale"
    )

    when (progress) {
      Progress.StartTime -> {
        Button(
                onClick = onAction,
                interactionSource = interactionSource,
                colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary
                ),
                modifier = modifier
                        .height(56.dp)
                        .graphicsLayer {
                          scaleX = scale
                          scaleY = scale
                        },
                contentPadding = PaddingValues(horizontal = 36.dp, vertical = 0.dp),
                shape = RoundedCornerShape(cornerRadius)
        ) {
          Text(
                  stringResource(R.string.alarm_picker_ok),
                  style = typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold),
          )
        }
      }

      Progress.FullEditor -> {
        val isInactiveEdit = uiState.initialAlarm?.isReadyToUse == false
        val canSetAlarm = uiState.validationResult == AlarmDataValidationResult.Success || isInactiveEdit
        val containerColor by animateColorAsState(
                targetValue = when {
                  canSetAlarm -> colorScheme.primary
                  uiState.validationResult.isFailure() -> colorScheme.errorContainer
                  else -> colorScheme.primary
                },
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "primary_cta_container_color"
        )
        val contentColor by animateColorAsState(
                targetValue = when {
                  canSetAlarm -> colorScheme.onPrimary
                  uiState.validationResult.isFailure() -> colorScheme.onErrorContainer
                  else -> colorScheme.onPrimary
                },
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "primary_cta_content_color"
        )

        Button(
                onClick = onAction,
                interactionSource = interactionSource,
                colors = ButtonDefaults.buttonColors(
                        containerColor = containerColor,
                        contentColor = contentColor
                ),
                modifier = modifier
                        .height(56.dp)
                        .graphicsLayer {
                          scaleX = scale
                          scaleY = scale
                        },
                contentPadding = PaddingValues(horizontal = 36.dp, vertical = 0.dp),
                shape = RoundedCornerShape(cornerRadius)
        ) {
          AnimatedContent(
                  targetState = canSetAlarm,
                  transitionSpec = {
                    fadeIn(effectsSpec) togetherWith fadeOut(effectsSpec) using SizeTransform { _, _ -> spatialSpec }
                  },
                  label = "button_text"
          ) { isValid ->
            Text(
                    when {
                      isValid -> stringResource(R.string.alarm_picker_btn_set)
                      else -> stringResource(R.string.alarm_picker_btn_fix)
                    },
                    style = typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold),
            )
          }
        }
      }
    }
  }
}

fun onDisabledTimeSelected(view: View) {
	view.performHapticFeedback(HapticFeedbackConstants.REJECT)
}

@Composable fun rememberAdaptiveHorizontalPadding(percent: Float = 0.0066f, min: Dp = 14.dp, max: Dp = 30.dp): Dp {
  val density = LocalDensity.current
  val screenWidthDp = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
  return (screenWidthDp * percent).coerceIn(min, max)
}
