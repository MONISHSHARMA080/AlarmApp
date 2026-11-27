package com.coolapps.digital.alarm.clock.presentation.onboarding.flow

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.coolapps.digital.alarm.clock.R
import com.coolapps.digital.alarm.clock.presentation.onboarding.ButtonState
import com.coolapps.digital.alarm.clock.presentation.onboarding.components.CreateFirstAlarmScreen
import com.coolapps.digital.alarm.clock.presentation.onboarding.components.PermissionScreen
import com.coolapps.digital.alarm.clock.presentation.onboarding.flow.components.AlarmNotificationPreviewScreen
import com.coolapps.digital.alarm.clock.presentation.onboarding.flow.components.WakeUpTimeScreen

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WakeUpOnboardingScreen(
	onOnboardingFinished: () -> Unit = {},
) {
	val viewModel: WakeUpOnboardingViewModel = hiltViewModel()
	val uiState by viewModel.displayState.collectAsStateWithLifecycle()

	val backStack = rememberNavBackStack(OnboardingRoute.WakeUpTime)
	val currentRoute = (backStack.lastOrNull() as? OnboardingRoute) ?: OnboardingRoute.WakeUpTime

	LaunchedEffect(currentRoute) {
		viewModel.analytics.screen("Onboarding_${currentRoute.stepName.lowercase()}")
	}

	var buttonState by remember { mutableStateOf(ButtonState.Enabled) }
	var childBackOverride by remember { mutableStateOf<(() -> Unit)?>(null) }

	LaunchedEffect(currentRoute) {
		if (currentRoute != OnboardingRoute.CreateFirstAlarm) {
			childBackOverride = null
		}
		buttonState = when (currentRoute) {
			OnboardingRoute.CreateFirstAlarm -> ButtonState.Hidden
			OnboardingRoute.Permission -> if (uiState.allCriticalGranted) ButtonState.Enabled else ButtonState.Disabled
			OnboardingRoute.AlarmPreview -> if (uiState.alarmData != null) ButtonState.Enabled else ButtonState.Hidden
			OnboardingRoute.WakeUpTime -> ButtonState.Enabled
		}
	}

	val navigateNext: () -> Unit = {
		viewModel.onNextClicked(currentRoute)?.let(backStack::add)
	}

	val navigateBack: () -> Unit = {
		if (backStack.size > 1) {
			viewModel.onPreviousClicked(currentRoute)
			backStack.removeLastOrNull()
		}
	}

	val view = LocalView.current
	val motionScheme = MaterialTheme.motionScheme

	val animatedProgress by animateFloatAsState(
		targetValue = currentRoute.progress,
		animationSpec = motionScheme.defaultSpatialSpec(),
		label = "wakeUpOnboardingProgress",
	)

	Scaffold(
		topBar = {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.statusBarsPadding()
					.padding(horizontal = 8.dp, vertical = 8.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				IconButton(
					enabled = childBackOverride != null || backStack.size > 1,
					onClick = dropUnlessResumed {
						view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
						childBackOverride?.invoke() ?: navigateBack()
					},
				) {
					Icon(
						imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
						contentDescription = stringResource(R.string.alarm_picker_previous),
					)
				}
				Spacer(modifier = Modifier.width(8.dp))
				LinearProgressIndicator(
					progress = { animatedProgress },
					modifier = Modifier
						.fillMaxWidth()
						.padding(end = 12.dp),
				)
			}
		},
	) { innerPadding ->
		val stepSpatialSpec = motionScheme.defaultSpatialSpec<IntOffset>()
		Box(
			modifier = Modifier
				.fillMaxSize()
				.padding(innerPadding)
				.consumeWindowInsets(innerPadding),
		) {
			NavDisplay(
				backStack = backStack,
				onBack = navigateBack,
				modifier = Modifier.fillMaxSize(),
				entryDecorators = listOf(
					rememberSaveableStateHolderNavEntryDecorator(),
					rememberViewModelStoreNavEntryDecorator(),
				),
				transitionSpec = {
					slideInHorizontally(stepSpatialSpec) { it } togetherWith
						slideOutHorizontally(stepSpatialSpec) { -it }
				},
				popTransitionSpec = {
					slideInHorizontally(stepSpatialSpec) { -it } togetherWith
						slideOutHorizontally(stepSpatialSpec) { it }
				},
				predictivePopTransitionSpec = {
					slideInHorizontally(stepSpatialSpec) { -it } togetherWith
						slideOutHorizontally(stepSpatialSpec) { it }
				},
				entryProvider = entryProvider {
					entry<OnboardingRoute.WakeUpTime> {
						WakeUpTimeScreen(
							initialHour = uiState.wakeUpHour,
							initialMinute = uiState.wakeUpMinute,
							onTimeChanged = viewModel::onWakeUpTimeChanged,
							onButtonStateChange = { buttonState = it },
						)
					}

					entry<OnboardingRoute.Permission> {
						PermissionScreen(
							missingSteps = uiState.missingSteps,
							refreshPermissionUiState = viewModel::refreshPermissions,
							allCriticalGranted = uiState.allCriticalGranted,
							onButtonStateChange = { buttonState = it },
						)
					}

					entry<OnboardingRoute.CreateFirstAlarm> {
						val editorMode = remember { viewModel.resolveEditorMode() }
						val onNext = dropUnlessResumed { navigateNext() }
						val onCancelled = dropUnlessResumed { navigateBack() }
						CreateFirstAlarmScreen(
							onAlarmSetProceed = { saved ->
								viewModel.onAlarmSaved(saved)
								onNext()
							},
							editorMode = editorMode,
							onBackOverrideChanged = { childBackOverride = it },
							onCancelled = onCancelled,
						)
					}

					entry<OnboardingRoute.AlarmPreview> {
						AlarmNotificationPreviewScreen(
							alarmData = uiState.alarmData,
							onButtonStateChange = { buttonState = it },
						)
					}
				},
			)

			AnimatedVisibility(
				visible = buttonState != ButtonState.Hidden,
				modifier = Modifier.align(Alignment.BottomCenter),
				enter = fadeIn(motionScheme.defaultEffectsSpec()) +
					slideInVertically(motionScheme.fastSpatialSpec()) { it },
				exit = fadeOut(motionScheme.defaultEffectsSpec<Float>()) +
					slideOutVertically(motionScheme.fastSpatialSpec<IntOffset>()) { it },
			) {
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.background(
							brush = Brush.verticalGradient(
								0f to colorScheme.background.copy(alpha = 0f),
								0.35f to colorScheme.background,
								1f to colorScheme.background,
							),
						)
						.navigationBarsPadding()
						.padding(26.dp)
						.padding(bottom = 20.dp),
					contentAlignment = Alignment.Center,
				) {
					Column(
						modifier = Modifier
							.widthIn(max = 560.dp)
							.fillMaxWidth(),
						horizontalAlignment = Alignment.CenterHorizontally,
						verticalArrangement = Arrangement.spacedBy(8.dp),
					) {
						Button(
							onClick = dropUnlessResumed {
								view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
								if (currentRoute == OnboardingRoute.AlarmPreview) {
									viewModel.finishOnboarding(onOnboardingFinished)
								} else {
									navigateNext()
								}
							},
							enabled = buttonState == ButtonState.Enabled,
							modifier = Modifier
								.fillMaxWidth()
								.height(56.dp),
							shape = shapes.extraLarge,
							colors = ButtonDefaults.buttonColors(
								containerColor = colorScheme.primaryContainer,
								contentColor = colorScheme.onPrimaryContainer,
							),
						) {
							Text(
								text = stringResource(
									if (currentRoute == OnboardingRoute.AlarmPreview) {
										R.string.onboarding_preview_done
									} else {
										R.string.permission_continue
									},
								),
								style = typography.titleMedium,
							)
						}
					}
				}
			}
		}
	}
}
