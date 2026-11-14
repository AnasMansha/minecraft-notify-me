package com.notifyme.app

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** Tier 4: loud looping alarm on the alarm stream, vibration, flashlight strobe and text-to-speech. */
object AlarmController {

    private val _active = MutableStateFlow<List<String>>(emptyList())
    val active: StateFlow<List<String>> = _active

    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var tts: TextToSpeech? = null
    private var torchThread: HandlerThread? = null
    private var torchHandler: Handler? = null
    private var torchCamera: String? = null
    private var cameraManager: CameraManager? = null
    private val autoStop = Runnable { stopEffects() }

    private val alarmAttrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun start(context: Context, item: AlertItem) {
        val app = context.applicationContext
        if (!_active.value.contains(item.id)) _active.value = _active.value + item.id
        val settings = Store.settings.value
        if (player == null) {
            vibrate(app)
            if (settings.torch) startTorch(app)
            if (settings.tts) speak(app, "${item.title()}. ${item.message}")
            main.postDelayed({ if (_active.value.isNotEmpty()) startSound(app) }, if (settings.tts) 3_500L else 0L)
        } else if (settings.tts) {
            speak(app, "${item.blockName}. ${item.message}")
        }
        main.removeCallbacks(autoStop)
        main.postDelayed(autoStop, 10 * 60_000L) // never ring forever
    }

    fun stop(id: String) {
        if (!_active.value.contains(id)) return
        _active.value = _active.value - id
        if (_active.value.isEmpty()) stopEffects()
    }

    fun stopAll() {
        _active.value = emptyList()
        stopEffects()
    }

    private fun startSound(context: Context) {
        if (player != null) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(alarmAttrs)
                setDataSource(context, uri)
                isLooping = true
                prepare()
                setVolume(0.4f, 0.4f)
                start()
            }
            // ramp volume up over ~20 s
            var v = 0.4f
            val ramp = object : Runnable {
                override fun run() {
                    val p = player ?: return
                    v = (v + 0.1f).coerceAtMost(1f)
                    p.setVolume(v, v)
                    if (v < 1f) main.postDelayed(this, 3_000L)
                }
            }
            main.postDelayed(ramp, 3_000L)
        } catch (e: Exception) {
            player = null
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(context: Context) {
        val v = context.getSystemService(Vibrator::class.java) ?: return
        vibrator = v
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 400, 800, 400, 1500, 600), 0), alarmAttrs)
    }

    private fun speak(context: Context, text: String) {
        val existing = tts
        if (existing != null) {
            existing.speak(text, TextToSpeech.QUEUE_ADD, Bundle(), "alarm")
            return
        }
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.getDefault()
                engine?.setAudioAttributes(alarmAttrs)
                engine?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "alarm")
            }
        }
        tts = engine
    }

    private fun startTorch(context: Context) {
        try {
            val cm = context.getSystemService(CameraManager::class.java) ?: return
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            cameraManager = cm
            torchCamera = id
            val t = HandlerThread("NotifyMe-Torch").apply { start() }
            torchThread = t
            val h = Handler(t.looper)
            torchHandler = h
            var on = false
            h.post(object : Runnable {
                override fun run() {
                    on = !on
                    try {
                        cm.setTorchMode(id, on)
                    } catch (_: Exception) {
                    }
                    h.postDelayed(this, if (on) 120L else 380L)
                }
            })
        } catch (_: Exception) {
        }
    }

    private fun stopEffects() {
        main.removeCallbacksAndMessages(null)
        player?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        val cm = cameraManager
        val id = torchCamera
        torchHandler?.let { h ->
            h.removeCallbacksAndMessages(null)
            // switch off on the torch thread itself so it cannot be flipped back on afterwards
            if (cm != null && id != null) h.post {
                try {
                    cm.setTorchMode(id, false)
                } catch (_: Exception) {
                }
            }
        }
        torchThread?.quitSafely()
        torchThread = null
        torchHandler = null
    }
}
