package com.taomic.agent.uikit.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * SpeechRecognizer 封装（ADR 0004：Android SpeechRecognizer 起步）。
 *
 * 使用流程：
 * 1. [isAvailable] 检查系统 ASR 是否可用
 * 2. [startListening] 开始录音
 * 3. 回调 [onResult] 收到识别结果
 * 4. [destroy] 释放资源
 *
 * 注意：SpeechRecognizer 必须在主线程创建和操作。
 */
class SpeechRecognizerHelper(
    context: Context,
    private val onResult: (text: String) -> Unit,
    private val onError: (errorCode: Int) -> Unit = {},
    private val onReadyForSpeech: () -> Unit = {},
    private val onEndOfSpeech: () -> Unit = {},
) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var isListening = false

    val isAvailable: Boolean
        get() = try {
            SpeechRecognizer.isRecognitionAvailable(appContext)
        } catch (_: Exception) {
            false
        }

    fun startListening() {
        if (isListening) return
        if (!isAvailable) {
            Log.w(TAG, "SpeechRecognizer not available on this device")
            onError(SpeechRecognizer.ERROR_CLIENT)
            return
        }

        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                this@SpeechRecognizerHelper.onReadyForSpeech()
                Log.d(TAG, "ready for speech")
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
                this@SpeechRecognizerHelper.onEndOfSpeech()
            }

            override fun onError(error: Int) {
                isListening = false
                if (error != SpeechRecognizer.ERROR_NO_MATCH) {
                    Log.w(TAG, "ASR error: $error")
                    onError(error)
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim()
                if (!text.isNullOrBlank()) {
                    Log.i(TAG, "ASR result: \"$text\"")
                    onResult(text)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim()
                if (!text.isNullOrBlank()) {
                    Log.d(TAG, "ASR partial: \"$text\"")
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer?.startListening(intent)
        Log.i(TAG, "started listening")
    }

    fun stopListening() {
        if (!isListening) return
        recognizer?.stopListening()
        isListening = false
        Log.i(TAG, "stopped listening")
    }

    fun destroy() {
        stopListening()
        recognizer?.destroy()
        recognizer = null
        Log.i(TAG, "destroyed")
    }

    companion object {
        const val TAG: String = "SpeechRecognizer"
    }
}
