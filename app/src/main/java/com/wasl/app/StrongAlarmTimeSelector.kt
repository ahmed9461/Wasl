package com.wasl.app

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val strongAlarmTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

@Composable
internal fun StrongAlarmTimeSelector(
    time: LocalTime,
    enabled: Boolean,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "strong-alarm-time",
) {
    val context = LocalContext.current
    OutlinedButton(
        modifier = modifier.testTag(testTag),
        enabled = enabled,
        onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    onTimeChange(LocalTime.of(hour, minute))
                },
                time.hour,
                time.minute,
                DateFormat.is24HourFormat(context),
            ).show()
        },
    ) {
        Text("وقت المنبه: ${time.format(strongAlarmTimeFormatter)}")
    }
}
