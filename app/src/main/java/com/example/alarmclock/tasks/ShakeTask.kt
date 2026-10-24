package com.example.alarmclock.tasks

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.alarmclock.domain.TaskType
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Счётчик встряхиваний по акселерометру.
 *
 * Сырые данные шумные, поэтому: убираем гравитацию фильтром верхних частот,
 * считаем пик только при пересечении порога и требуем минимальный интервал
 * между засчитанными встряхиваниями — иначе одно движение даёт 5 засчётов.
 */
class ShakeTask @Inject constructor() : WakeUpTask {

    override val type = TaskType.SHAKE

    override fun isAvailable(context: Context): Boolean {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        return sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    }

    @Composable
    override fun Render(state: TaskSessionState, callbacks: TaskCallbacks) {
        val context = LocalContext.current
        val required = 15 * state.difficulty
        var count by remember(state.index) { mutableIntStateOf(0) }
        val onSolved = rememberUpdatedState(callbacks::onSolved)

        DisposableEffect(state.index) {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val detector = ShakeDetector { 
                count++
                if (count >= required) onSolved.value.invoke()
            }
            sm.registerListener(detector, sensor, SensorManager.SENSOR_DELAY_GAME)
            onDispose { sm.unregisterListener(detector) }
        }

        val progress by animateFloatAsState(
            targetValue = (count.toFloat() / required).coerceIn(0f, 1f),
            label = "shake"
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Встряхните телефон", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Text("$count / $required", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(24.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(12.dp)
            )
        }
    }

    /** Выделен отдельно, чтобы покрывался юнит-тестом без Android. */
    class ShakeDetector(private val onShake: () -> Unit) : SensorEventListener {

        private var gravityX = 0f
        private var gravityY = 0f
        private var gravityZ = 0f
        private var lastShakeAt = 0L
        private var armed = true

        override fun onSensorChanged(event: SensorEvent) {
            val alpha = 0.8f
            gravityX = alpha * gravityX + (1 - alpha) * event.values[0]
            gravityY = alpha * gravityY + (1 - alpha) * event.values[1]
            gravityZ = alpha * gravityZ + (1 - alpha) * event.values[2]

            val lx = event.values[0] - gravityX
            val ly = event.values[1] - gravityY
            val lz = event.values[2] - gravityZ
            val magnitude = sqrt(lx * lx + ly * ly + lz * lz)

            val now = System.currentTimeMillis()
            if (magnitude > THRESHOLD && armed && now - lastShakeAt > MIN_INTERVAL_MS) {
                armed = false
                lastShakeAt = now
                onShake()
            }
            // Перезаряжаем только когда телефон вернулся в покой:
            // защищает от засчёта одного движения несколько раз.
            if (magnitude < REARM_THRESHOLD) armed = true
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

        companion object {
            const val THRESHOLD = 12f
            const val REARM_THRESHOLD = 4f
            const val MIN_INTERVAL_MS = 120L
        }
    }
}
