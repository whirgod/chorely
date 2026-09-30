package at.woergoetter.chorely.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.woergoetter.chorely.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One reminder time for the whole app, off until the user sets one. Per-chore times are
 * rejected in BACKLOG.md.
 *
 * Switching reminders on asks for a time first and stores nothing until one is picked, so
 * "on" never means "on at a time the app made up". Picking one is also the moment to ask
 * for `POST_NOTIFICATIONS`: the user has just said they want a notification, which is the
 * one point at which the system prompt makes sense to them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val time by viewModel.reminderTime.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var picking by rememberSaveable { mutableStateOf(false) }

    // Re-read on every resume rather than once: the fix for a blocked notification is in
    // system settings, and coming back from there is a resume, not a recomposition.
    var notificationsAllowed by remember { mutableStateOf(notificationsAllowed(context)) }
    LifecycleResumeEffect(context) {
        notificationsAllowed = notificationsAllowed(context)
        onPauseOrDispose {}
    }
    val askPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { notificationsAllowed = notificationsAllowed(context) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            val current = time
            ListItem(
                headlineContent = { Text(stringResource(R.string.daily_reminder)) },
                supportingContent = {
                    Text(
                        if (current == null) {
                            stringResource(R.string.reminder_off)
                        } else {
                            stringResource(R.string.reminder_at, formatTime(context, current))
                        },
                    )
                },
                trailingContent = {
                    Switch(
                        checked = current != null,
                        onCheckedChange = { on ->
                            if (on) picking = true else viewModel.onReminderTimeChanged(null)
                        },
                    )
                },
                // Tapping the row while on changes the time; while off it does what the
                // switch does, so the row never has a tap that silently does nothing.
                modifier = Modifier.clickable { picking = true },
            )

            if (current != null && !notificationsAllowed) {
                NotificationsBlocked(onOpenSettings = { context.startActivity(notificationSettings(context)) })
            }
        }
    }

    if (picking) {
        ReminderTimeDialog(
            initial = time ?: DIAL_START,
            is24Hour = DateFormat.is24HourFormat(context),
            onConfirm = { picked ->
                picking = false
                viewModel.onReminderTimeChanged(picked)
                if (needsPermissionRequest(context)) {
                    askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun NotificationsBlocked(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(R.string.notifications_blocked),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.open_notification_settings)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(
    initial: LocalTime,
    is24Hour: Boolean,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = is24Hour,
    )
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.daily_reminder)) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    ) {
        TimePicker(state = state)
    }
}

/**
 * Where the dial starts when no time is stored. Only the picker's starting position — the
 * user still has to confirm it, and nothing is stored until they do.
 */
private val DIAL_START: LocalTime = LocalTime.of(18, 0)

/** The device's own 12/24-hour choice, which `DateTimeFormatter`'s localized styles ignore. */
private fun formatTime(context: Context, time: LocalTime): String {
    val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
    val locale = Locale.getDefault()
    return time.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale))
}

/**
 * Covers both ways a digest can be silenced: the API 33+ runtime permission, and the user
 * switching the app's notifications off in system settings, which every API level allows.
 * `SystemDigestNotifier` refuses to post on either, so this is the same question it asks.
 */
private fun notificationsAllowed(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

private fun needsPermissionRequest(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

/**
 * The system screen for this app's notifications, rather than a second permission request:
 * after two denials the system stops showing its prompt and denies silently, and this screen
 * is the one route back that works at every API level from minSdk up.
 */
private fun notificationSettings(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
