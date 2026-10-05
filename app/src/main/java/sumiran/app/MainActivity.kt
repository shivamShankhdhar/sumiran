package sumiran.app

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class ChantSession(val date: String, val count: Int)

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("sumiran", MODE_PRIVATE) }
    var hindi by mutableStateOf(false); private set
    var onboarded by mutableStateOf(false); private set
    var mantraIndex by mutableStateOf(0); private set
    var goal by mutableStateOf(216); private set
    var reminderHour by mutableStateOf(-1); private set
    var reminderMinute by mutableStateOf(0); private set
    var todayCount by mutableStateOf(0); private set
    var totalCount by mutableStateOf(0); private set
    var isListening by mutableStateOf(false); private set
    var voiceStatus by mutableStateOf(""); private set
    var sessions by mutableStateOf(listOf<ChantSession>()); private set
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var restartVoice = false
    private var lastVoiceCountAt = 0L
    private var voiceErrors = 0
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) startVoice() else voiceStatus = if (hindi) "माइक्रोफ़ोन की अनुमति चाहिए" else "Microphone permission is needed"
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        load()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("daily_chant", "Daily chant reminder", NotificationManager.IMPORTANCE_DEFAULT)
        )
        setContent { SumiranApp(this) }
    }

    private fun load() {
        hindi = prefs.getBoolean("hindi", false)
        onboarded = prefs.getBoolean("onboarded", false)
        mantraIndex = prefs.getInt("mantra", 0).coerceIn(0, 2)
        goal = prefs.getInt("goal", 216).coerceAtLeast(1)
        reminderHour = prefs.getInt("reminder_hour", -1)
        reminderMinute = prefs.getInt("reminder_minute", 0)
        totalCount = prefs.getInt("total", 0)
        todayCount = if (prefs.getString("count_date", "") == todayKey()) prefs.getInt("today", 0) else 0
        sessions = runCatching {
            val array = JSONArray(prefs.getString("sessions", "[]"))
            (0 until array.length()).map { array.getJSONObject(it) }.map { ChantSession(it.getString("date"), it.getInt("count")) }
        }.getOrDefault(emptyList())
    }

    fun setLanguage(value: Boolean) {
        hindi = value
        prefs.edit().putBoolean("hindi", value).apply()
        if (isListening) { stopVoice(); startVoice() }
    }
    fun finishOnboarding() { onboarded = true; prefs.edit().putBoolean("onboarded", true).apply() }
    fun setMantra(index: Int) {
        mantraIndex = index
        prefs.edit().putInt("mantra", index).apply()
        if (isListening) { stopVoice(); startVoice() }
    }
    fun updateGoal(value: Int) { goal = value.coerceAtLeast(1); prefs.edit().putInt("goal", goal).apply() }

    fun refreshDay() {
        val today = todayKey()
        if (prefs.getString("count_date", "") != today) {
            todayCount = 0
            prefs.edit().putString("count_date", today).putInt("today", 0).apply()
        }
    }
    fun addChant() {
        refreshDay()
        todayCount++
        totalCount++
        val today = todayKey()
        sessions = listOf(ChantSession(today, todayCount)) + sessions.filterNot { it.date == today }
        saveCount()
    }
    fun undoChant() {
        refreshDay()
        if (todayCount == 0) return
        todayCount--
        totalCount = (totalCount - 1).coerceAtLeast(0)
        val today = todayKey()
        sessions = (if (todayCount > 0) listOf(ChantSession(today, todayCount)) else emptyList()) + sessions.filterNot { it.date == today }
        saveCount()
    }
    private fun saveCount() {
        val array = JSONArray()
        sessions.take(365).forEach { array.put(JSONObject().put("date", it.date).put("count", it.count)) }
        prefs.edit().putString("count_date", todayKey()).putInt("today", todayCount)
            .putInt("total", totalCount).putString("sessions", array.toString()).apply()
    }

    fun toggleVoice() { if (isListening) stopVoice() else startVoice() }
    private fun startVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audioPermission.launch(Manifest.permission.RECORD_AUDIO); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            voiceStatus = if (hindi) "आवाज़ पहचान उपलब्ध नहीं है" else "Speech recognition is unavailable"
            return
        }
        restartVoice = true
        voiceErrors = 0
        isListening = true
        voiceStatus = if (hindi) "सुन रहा है..." else "Listening..."
        if (recognizer == null) recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { voiceStatus = if (hindi) "सुन रहा है..." else "Listening..." }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onResults(results: Bundle?) {
                    voiceErrors = 0
                    val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                        .joinToString(" ").lowercase().replace("[\\sः:.,!?]".toRegex(), "")
                    val expected = when (mantraIndex) {
                        1 -> listOf("ramram", "रामराम", "श्रीराम", "sriram")
                        2 -> listOf("harekrishna", "हरेकृष्ण", "हरेकृष्णा")
                        else -> listOf("omnamahshivaya", "omnamahshivay", "ओमनमःशिवाय", "ओमनमशिवाय", "ॐनमःशिवाय", "ॐनमशिवाय", "ओमनमशिवाया")
                    }
                    if (expected.any { heard.contains(it.replace("ः", "")) } && System.currentTimeMillis() - lastVoiceCountAt > 1200) {
                        lastVoiceCountAt = System.currentTimeMillis(); addChant()
                    }
                    scheduleRestart()
                }
                override fun onError(error: Int) {
                    if (!restartVoice) return
                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS || error == SpeechRecognizer.ERROR_CLIENT) {
                        stopVoice()
                        voiceStatus = if (hindi) "आवाज़ पहचान बंद हुई" else "Voice recognition stopped"
                    } else if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        scheduleRestart()
                    } else {
                        voiceErrors++
                        if (voiceErrors >= 3) {
                            stopVoice()
                            voiceStatus = if (hindi) "आवाज़ पहचान में समस्या हुई। फिर कोशिश करें।" else "Speech recognition had a problem. Try again."
                        } else scheduleRestart(1500)
                    }
                }
            })
        }
        listenOnce()
    }
    private fun listenOnce() {
        if (!restartVoice) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (hindi) "hi-IN" else "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        runCatching { recognizer?.startListening(intent) }.onFailure {
            stopVoice(); voiceStatus = if (hindi) "सुनने में समस्या हुई" else "Could not start listening"
        }
    }
    private fun scheduleRestart(delay: Long = 650) { if (restartVoice) handler.postDelayed({ listenOnce() }, delay) }
    fun stopVoice() { restartVoice = false; isListening = false; voiceStatus = ""; recognizer?.cancel() }

    fun setReminder(hour: Int, minute: Int) {
        reminderHour = hour; reminderMinute = minute
        prefs.edit().putInt("reminder_hour", hour).putInt("reminder_minute", minute).apply()
        val manager = getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(this, 100, Intent(this, ReminderReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.cancel(pending)
        if (hour >= 0) {
            val next = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            manager.setInexactRepeating(AlarmManager.RTC_WAKEUP, next.timeInMillis, AlarmManager.INTERVAL_DAY, pending)
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    override fun onDestroy() { stopVoice(); recognizer?.destroy(); super.onDestroy() }
}

internal fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(System.currentTimeMillis())

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val hindi = context.getSharedPreferences("sumiran", Context.MODE_PRIVATE).getBoolean("hindi", false)
        val open = PendingIntent.getActivity(context, 101, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, "daily_chant").setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (hindi) "सुमिरन का समय" else "A moment for Sumiran")
            .setContentText(if (hindi) "आज का जप शुरू करें" else "Begin your daily chanting")
            .setContentIntent(open).setAutoCancel(true).build()
        context.getSystemService(NotificationManager::class.java).notify(100, notification)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("sumiran", Context.MODE_PRIVATE)
        val hour = prefs.getInt("reminder_hour", -1)
        if (hour < 0) return
        val minute = prefs.getInt("reminder_minute", 0)
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val pending = PendingIntent.getBroadcast(context, 100, Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        context.getSystemService(AlarmManager::class.java).setInexactRepeating(AlarmManager.RTC_WAKEUP, next.timeInMillis, AlarmManager.INTERVAL_DAY, pending)
    }
}
