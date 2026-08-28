package com.wasl.app

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun rememberAndroidVoiceDictationBridge(): VoiceDictationBridge {
    val context = LocalContext.current
    val adapter = remember(context) {
        VoiceDictationAdapter(
            recognizerAvailable = {
                voiceRecognitionIntent(VoiceDictationRequest())
                    .resolveActivity(context.packageManager) != null
            },
        )
    }
    var resultConsumer by remember {
        mutableStateOf<((VoiceDictationOutcome) -> Unit)?>(null)
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val consumer = resultConsumer
        resultConsumer = null
        consumer?.invoke(
            adapter.outcome(
                accepted = result.resultCode == Activity.RESULT_OK,
                candidates = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS),
            ),
        )
    }

    return remember(adapter, launcher) {
        object : VoiceDictationBridge {
            override fun launch(
                onResult: (VoiceDictationOutcome) -> Unit,
            ): VoiceDictationLaunchResult {
                if (!adapter.isAvailable()) {
                    return VoiceDictationLaunchResult.UNAVAILABLE
                }
                resultConsumer = onResult
                return runCatching {
                    launcher.launch(voiceRecognitionIntent(adapter.request()))
                    VoiceDictationLaunchResult.LAUNCHED
                }.getOrElse {
                    resultConsumer = null
                    VoiceDictationLaunchResult.FAILED
                }
            }
        }
    }
}

private fun voiceRecognitionIntent(request: VoiceDictationRequest): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, request.language)
        putExtra(RecognizerIntent.EXTRA_PROMPT, request.prompt)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, request.maxResults)
    }
