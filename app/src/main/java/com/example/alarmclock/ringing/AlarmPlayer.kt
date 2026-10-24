package com.example.alarmclock.ringing

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.alarmclock.domain.Alarm
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Воспроизведение через STREAM_ALARM: этот поток не глушится
 * беззвучным режимом и режимом "не беспокоить".
 */
@Singleton
class AlarmPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var rampRunnable: Runnable? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var previousVolume: Int? = null

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun start(alarm: Alarm) {
        stop()
        try {
            forceAlarmVolume(alarm.volume)
            startSound(alarm)
            if (alarm.vibrate) startVibration()
        } catch (t: Throwable) {
            Log.e(TAG, "Ошибка запуска звука, пробую системный рингтон", t)
            fallbackSound()
        }
    }

    private fun startSound(alarm: Alarm) {
        val uri: Uri = alarm.soundUri?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(context, uri)
            isLooping = true
            prepare()
            start()
        }

        if (alarm.volumeRampSec > 0) rampUp(alarm)
    }

    /** Плавное нарастание громкости внутри плеера, без правки системной. */
    private fun rampUp(alarm: Alarm) {
        val steps = 20
        val intervalMs = (alarm.volumeRampSec * 1000L / steps).coerceAtLeast(50L)
        var step = 0
        val runnable = object : Runnable {
            override fun run() {
                step++
                val fraction = (step.toFloat() / steps).coerceIn(0.05f, 1f)
                try {
                    player?.setVolume(fraction, fraction)
                } catch (_: IllegalStateException) { return }
                if (step < steps) handler.postDelayed(this, intervalMs)
            }
        }
        player?.setVolume(0.05f, 0.05f)
        rampRunnable = runnable
        handler.postDelayed(runnable, intervalMs)
    }

    /**
     * Поднимаем системную громкость будильника и запоминаем прежнюю,
     * чтобы вернуть её после выключения.
     */
    private fun forceAlarmVolume(percent: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val target = (max * percent / 100f).roundToInt().coerceIn(1, max)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
    }

    /** Вызывается охраной при попытке убавить громкость. */
    fun restoreVolumeIfLowered(percent: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val target = (max * percent / 100f).roundToInt().coerceIn(1, max)
        if (audioManager.getStreamVolume(AudioManager.STREAM_ALARM) < target) {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
        }
    }

    private fun startVibration() {
        val pattern = longArrayOf(0, 500, 500, 500, 1000)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(VibratorManager::class.java)
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val effect = VibrationEffect.createWaveform(pattern, 0)
        vibrator?.vibrate(effect)
    }

    private fun fallbackSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: return
            player = MediaPlayer.create(context, uri)?.apply {
                isLooping = true
                start()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Запасной звук тоже не запустился", t)
        }
    }

    fun stop() {
        rampRunnable?.let(handler::removeCallbacks)
        rampRunnable = null
        try {
            player?.stop()
            player?.release()
        } catch (_: Throwable) {
        }
        player = null
        vibrator?.cancel()
        vibrator = null
        previousVolume?.let {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, it, 0)
        }
        previousVolume = null
    }

    companion object { private const val TAG = "AlarmPlayer" }
}
