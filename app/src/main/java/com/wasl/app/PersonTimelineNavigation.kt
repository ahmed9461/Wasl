package com.wasl.app

import androidx.compose.runtime.staticCompositionLocalOf
import com.wasl.domain.PersonId

internal val LocalOpenPersonTimeline = staticCompositionLocalOf<(PersonId) -> Unit> { { } }
