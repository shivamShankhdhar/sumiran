package sumiran.app

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

private val Ink = Color(0xFF2B1029)
private val Subtle = Color(0xFF696079)
private val Orange = Color(0xFFEE742E)
private val OrangeLight = Color(0xFFFFC99D)
private val Cream = Color(0xFFFFFCF8)
private val Lavender = Color(0xFFF4F1FA)
private val Line = Color(0xFFEAE5EB)
private val Dark = Color(0xFF291927)
private val Dark2 = Color(0xFF3B2739)
private val MantrasEnglish = listOf("Om Namah Shivaya", "Ram Ram", "Hare Krishna")
private val MantrasHindi = listOf("ॐ नमः शिवाय", "राम राम", "हरे कृष्ण")

@Composable
fun SumiranApp(activity: MainActivity) {
    var page by remember { mutableStateOf("counter") }
    var voiceMode by remember { mutableStateOf(false) }
    var focus by remember { mutableStateOf(false) }
    var showMantra by remember { mutableStateOf(false) }
    var showGoal by remember { mutableStateOf(false) }
    var goalText by remember { mutableStateOf("") }
    var selectedSession by remember { mutableStateOf<ChantSession?>(null) }
    val hi = activity.hindi
    val context = LocalContext.current
    LaunchedEffect(activity.onboarded) { if (activity.onboarded) activity.refreshDay() }
    BackHandler(enabled = focus || page == "settings" || page == "progress") {
        if (focus) focus = false else page = "counter"
    }
    LaunchedEffect(page, voiceMode, focus) {
        if (page != "counter" || !voiceMode) activity.stopVoice()
    }

    Box(Modifier.fillMaxSize().background(if (focus) Dark else Cream)) {
        if (!activity.onboarded) {
            WelcomeScreen(hi, { activity.setLanguage(it) }, { activity.finishOnboarding() })
        } else if (focus) {
            FocusScreen(activity, onExit = { focus = false }, onUndo = { activity.undoChant() })
        } else {
            Column(Modifier.fillMaxSize().padding(WindowInsets.statusBars.asPaddingValues())) {
                when (page) {
                    "progress" -> ProgressScreen(activity, onSession = { selectedSession = it }, modifier = Modifier.weight(1f))
                    "settings" -> SettingsScreen(
                        activity,
                        onBack = { page = "counter" },
                        onMantra = { showMantra = true },
                        onGoal = { goalText = activity.goal.toString(); showGoal = true },
                        onReminder = {
                            TimePickerDialog(context, { _, h, m -> activity.setReminder(h, m) },
                                if (activity.reminderHour >= 0) activity.reminderHour else 7,
                                activity.reminderMinute, false).show()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    else -> CounterScreen(
                        activity, voiceMode,
                        onVoiceMode = { new -> voiceMode = new; if (new && !activity.isListening) activity.toggleVoice() else if (!new) activity.stopVoice() },
                        onMantra = { showMantra = true },
                        onSettings = { page = "settings"; activity.stopVoice() },
                        onFocus = { focus = true },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (page != "settings") BottomNav(hi, page, { page = it; if (it != "counter") activity.stopVoice() })
            }
        }
    }

    if (showMantra) AlertDialog(
        onDismissRequest = { showMantra = false },
        title = { Text(if (hi) "मंत्र चुनें" else "Choose a mantra", color = Ink, fontWeight = FontWeight.Bold) },
        text = { Column { MantrasEnglish.indices.forEach { index ->
            TextButton(onClick = { activity.setMantra(index); showMantra = false }, modifier = Modifier.fillMaxWidth()) {
                Text(if (hi) MantrasHindi[index] else MantrasEnglish[index], modifier = Modifier.fillMaxWidth(), color = Ink)
            }
        } } },
        confirmButton = { TextButton(onClick = { showMantra = false }) { Text(if (hi) "बंद करें" else "Close", color = Orange) } },
        containerColor = Cream
    )
    if (showGoal) AlertDialog(
        onDismissRequest = { showGoal = false },
        title = { Text(if (hi) "दैनिक लक्ष्य" else "Daily goal", color = Ink, fontWeight = FontWeight.Bold) },
        text = { Column {
            Text(if (hi) "हर दिन कितने जप करना चाहते हैं?" else "How many chants each day?", color = Subtle)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = goalText, onValueChange = { goalText = it.filter(Char::isDigit).take(6) }, label = { Text(if (hi) "जप" else "Chants") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(108, 216, 324).forEach { n -> TextButton(onClick = { goalText = n.toString() }) { Text(n.toString(), color = Orange) } }
            }
        } },
        confirmButton = { TextButton(onClick = { goalText.toIntOrNull()?.takeIf { it > 0 }?.let(activity::updateGoal); showGoal = false }) { Text(if (hi) "सहेजें" else "Save", color = Orange) } },
        dismissButton = { TextButton(onClick = { showGoal = false }) { Text(if (hi) "रद्द करें" else "Cancel", color = Subtle) } },
        containerColor = Cream
    )
    selectedSession?.let { session -> AlertDialog(
        onDismissRequest = { selectedSession = null },
        title = { Text(formatDate(session.date, hi), color = Ink, fontWeight = FontWeight.Bold) },
        text = { Text(if (hi) "${session.count} जप • ${session.count / 108} पूरी मालाएँ" else "${session.count} chants • ${session.count / 108} completed malas", color = Subtle) },
        confirmButton = { TextButton(onClick = { selectedSession = null }) { Text(if (hi) "बंद करें" else "Close", color = Orange) } },
        containerColor = Cream
    ) }
}

@Composable
private fun WelcomeScreen(hi: Boolean, onLanguage: (Boolean) -> Unit, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(WindowInsets.statusBars.asPaddingValues()).padding(WindowInsets.navigationBars.asPaddingValues()).padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1.1f))
        Lotus(Modifier.size(134.dp))
        Spacer(Modifier.height(10.dp))
        Text(if (hi) "सुमिरन" else "Sumiran", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(if (hi) "हर जप के साथ, मन को शांति" else "One chant. One moment.", fontSize = 17.sp, color = Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(34.dp))
        SegmentedLanguage(hi, onLanguage)
        Spacer(Modifier.height(20.dp))
        OrangeButton(if (hi) "शुरू करें  →" else "Get started  →", onStart, Modifier.fillMaxWidth())
        Spacer(Modifier.weight(1f))
        Lotus(Modifier.size(180.dp), pale = true)
    }
}

@Composable
private fun CounterScreen(activity: MainActivity, voiceMode: Boolean, onVoiceMode: (Boolean) -> Unit, onMantra: () -> Unit, onSettings: () -> Unit, onFocus: () -> Unit, modifier: Modifier = Modifier) {
    val hi = activity.hindi
    val cycle = activity.todayCount % 108
    val remaining = if (cycle == 0 && activity.todayCount > 0) 0 else 108 - cycle
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (hi) "सुमिरन" else "Sumiran", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
            Text("⚙", fontSize = 25.sp, color = Ink, modifier = Modifier.clip(CircleShape).combinedClickable(onClick = onSettings).padding(8.dp))
        }
        Row(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(20.dp)).combinedClickable(onClick = onMantra).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("ॐ", color = Orange, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(15.dp))
            Text(if (hi) MantrasHindi[activity.mantraIndex] else MantrasEnglish[activity.mantraIndex], fontSize = 15.sp, color = Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("⌄", color = Ink, fontSize = 22.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(42.dp).clip(CircleShape).background(Lavender), verticalAlignment = Alignment.CenterVertically) {
            ModeSegment(if (hi) "♧   टैप" else "♧   Tap", !voiceMode, Modifier.weight(1f)) { onVoiceMode(false) }
            ModeSegment(if (hi) "♩   आवाज़" else "♩   Voice", voiceMode, Modifier.weight(1f)) { onVoiceMode(true) }
        }
        Spacer(Modifier.weight(0.25f))
        CountRing(cycle, activity.todayCount, hi, Modifier.size(198.dp))
        Spacer(Modifier.height(6.dp))
        Text(if (hi) "$remaining जप शेष" else "$remaining chants remaining", color = Color(0xFFE95419), fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Spacer(Modifier.weight(0.2f))
        if (voiceMode) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF2EEFF)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("♩  ▏▃▆▃▏  ${activity.voiceStatus.ifBlank { if (hi) "सुनने के लिए तैयार" else "Ready to listen" }}", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                Text(if (hi) "हर पूरा जप बोलने पर गिनती बढ़ेगी" else "Count increases after each complete chant", color = Subtle, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(8.dp))
        }
        OrangeButton(if (voiceMode) { if (activity.isListening) { if (hi) "Ⅱ   सुनना रोकें" else "Ⅱ   Pause listening" } else { if (hi) "♩   सुनना शुरू करें" else "♩   Start listening" } } else { if (hi) "+   जप जोड़ें" else "+   Tap to count" },
            onClick = { if (voiceMode) activity.toggleVoice() else activity.addChant() }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallAction(if (hi) "↶   वापस" else "↶   Undo", Modifier.weight(1f)) { activity.undoChant() }
            SmallAction(if (hi) "◐   ध्यान मोड" else "◐   Focus mode", Modifier.weight(1f), onFocus)
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(14.dp)).padding(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(if (hi) "आज का लक्ष्य" else "Today's goal", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${activity.todayCount} / ${activity.goal}", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(7.dp))
            ProgressBar(activity.todayCount.toFloat() / activity.goal)
        }
        Spacer(Modifier.weight(0.25f))
    }
}

@Composable
private fun FocusScreen(activity: MainActivity, onExit: () -> Unit, onUndo: () -> Unit) {
    val hi = activity.hindi
    val cycle = activity.todayCount % 108
    val remaining = if (cycle == 0 && activity.todayCount > 0) 0 else 108 - cycle
    Column(Modifier.fillMaxSize().padding(WindowInsets.statusBars.asPaddingValues()).padding(WindowInsets.navigationBars.asPaddingValues()).padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(0.45f))
        Text("ॐ", color = Orange, fontSize = 48.sp, fontWeight = FontWeight.Bold)
        Text(if (hi) MantrasHindi[activity.mantraIndex] else MantrasEnglish[activity.mantraIndex], color = Color.White, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))
        Text(if (activity.isListening) { if (hi) "♩  सुन रहा है...   ▏▃▆▃▏" else "♩  Listening...   ▏▃▆▃▏" } else { if (hi) "♩  शांत क्षण" else "♩  Quiet moment" }, color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.weight(0.35f))
        CountRing(cycle, activity.todayCount, hi, Modifier.size(220.dp), dark = true)
        Spacer(Modifier.height(14.dp))
        Text(if (hi) "$remaining जप शेष" else "$remaining chants remaining", color = Orange, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(0.45f))
        OrangeButton(if (activity.isListening) { if (hi) "Ⅱ   सुनना रोकें" else "Ⅱ   Pause listening" } else { if (hi) "♩   सुनना शुरू करें" else "♩   Start listening" }, { activity.toggleVoice() }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Box(Modifier.clip(CircleShape).border(1.dp, Color.White.copy(alpha = .55f), CircleShape).combinedClickable(onClick = onUndo).padding(horizontal = 28.dp, vertical = 9.dp)) {
            Text(if (hi) "↶   वापस" else "↶   Undo", color = Color.White)
        }
        Spacer(Modifier.weight(0.45f))
        Box(Modifier.fillMaxWidth().clip(CircleShape).background(Dark2).border(1.dp, Color.White.copy(alpha = .4f), CircleShape)
            .combinedClickable(onClick = {}, onLongClick = onExit).padding(16.dp), contentAlignment = Alignment.Center) {
            Text(if (hi) "◉   बाहर जाने के लिए दबाकर रखें" else "◉   Hold to exit", color = Color.White, fontSize = 14.sp)
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ProgressScreen(activity: MainActivity, onSession: (ChantSession) -> Unit, modifier: Modifier = Modifier) {
    val hi = activity.hindi
    val week = (6 downTo 0).map { offset -> Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -offset) } }
    val dateCounts = activity.sessions.associate { it.date to it.count }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Text(if (hi) "प्रगति" else "Progress", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(top = 15.dp, bottom = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard(if (hi) "आज" else "Today", activity.todayCount.toString(), if (hi) "जप" else "chants", if (hi) "लक्ष्य ${activity.goal}" else "Goal ${activity.goal}", activity.todayCount.toFloat() / activity.goal, Modifier.weight(1f))
            StatCard(if (hi) "कुल जप" else "Total chants", activity.totalCount.toString(), "", null, null, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard(if (hi) "पूरी मालाएँ" else "Completed malas", (activity.totalCount / 108).toString(), "", null, null, Modifier.weight(1f))
            StatCard(if (hi) "दैनिक लक्ष्य" else "Daily goal", activity.goal.toString(), if (hi) "जप" else "chants", null, null, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(18.dp)).padding(16.dp)) {
            Text(if (hi) "इस सप्ताह" else "This week", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                week.forEach { day ->
                    val count = dateCounts[SimpleDateFormat("yyyy-MM-dd", Locale.US).format(day.time)] ?: 0
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.height(95.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.width(23.dp).height((8 + 87 * (count.toFloat() / activity.goal).coerceIn(0f, 1f)).dp)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(Brush.verticalGradient(listOf(Color(0xFFFFAD84), OrangeLight))))
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(SimpleDateFormat("EEE", if (hi) Locale.forLanguageTag("hi") else Locale.ENGLISH).format(day.time), fontSize = 10.sp, color = Subtle, maxLines = 1)
                        Text(count.toString(), fontSize = 10.sp, color = Ink)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(if (hi) "पिछले सत्र" else "Recent sessions", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
        Spacer(Modifier.height(8.dp))
        if (activity.sessions.isEmpty()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(16.dp)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Lotus(Modifier.size(45.dp))
                Text(if (hi) "आपका पहला जप यहाँ दिखेगा" else "Your first chant will appear here", color = Subtle, textAlign = TextAlign.Center)
            }
        } else activity.sessions.take(14).forEach { session ->
            Row(Modifier.fillMaxWidth().padding(bottom = 7.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(14.dp))
                .combinedClickable(onClick = { onSession(session) }).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("◴", fontSize = 24.sp, color = Ink)
                Spacer(Modifier.width(15.dp))
                Column(Modifier.weight(1f)) {
                    Text(formatDate(session.date, hi), color = Ink, fontWeight = FontWeight.SemiBold)
                    Text(if (hi) "${session.count} जप  •  ${session.count / 108} माला" else "${session.count} chants  •  ${session.count / 108} malas", color = Subtle, fontSize = 12.sp)
                }
                Text("›", color = Subtle, fontSize = 24.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SettingsScreen(activity: MainActivity, onBack: () -> Unit, onMantra: () -> Unit, onGoal: () -> Unit, onReminder: () -> Unit, modifier: Modifier = Modifier) {
    val hi = activity.hindi
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", fontSize = 34.sp, color = Ink, modifier = Modifier.clip(CircleShape).combinedClickable(onClick = onBack).padding(end = 15.dp))
            Text(if (hi) "सेटिंग्स" else "Settings", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(18.dp))) {
            SettingRow("ॐ", if (hi) "मंत्र" else "Mantra", if (hi) MantrasHindi[activity.mantraIndex] else MantrasEnglish[activity.mantraIndex], onMantra)
            SettingRow("◎", if (hi) "दैनिक लक्ष्य" else "Daily goal", if (hi) "${activity.goal} जप" else "${activity.goal} chants", onGoal)
            SettingRow("♧", if (hi) "रिमाइंडर" else "Reminder", if (activity.reminderHour < 0) { if (hi) "बंद" else "Off" } else formatTime(activity.reminderHour, activity.reminderMinute), onReminder)
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("◎", color = Ink, fontSize = 25.sp, modifier = Modifier.width(45.dp))
                Text(if (hi) "भाषा" else "Language", color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                SegmentedLanguage(hi, { activity.setLanguage(it) }, compact = true)
            }
        }
        if (activity.reminderHour >= 0) {
            TextButton(onClick = { activity.setReminder(-1, 0) }) { Text(if (hi) "रिमाइंडर बंद करें" else "Turn off reminder", color = Orange) }
        }
    }
}

@Composable
private fun SettingRow(icon: String, title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(67.dp).combinedClickable(onClick = onClick).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, color = Ink, fontSize = 25.sp, modifier = Modifier.width(45.dp))
        Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = Subtle, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(7.dp)); Text("›", color = Subtle, fontSize = 24.sp)
    }
    Box(Modifier.fillMaxWidth().padding(start = 58.dp).height(1.dp).background(Line))
}

@Composable
private fun BottomNav(hi: Boolean, page: String, onPage: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Cream).border(1.dp, Line).padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 4.dp, top = 4.dp, start = 10.dp, end = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf("counter", "progress").forEach { item ->
            val active = page == item
            Column(Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(18.dp)).background(if (active) Color(0xFFFFF0E2) else Cream).combinedClickable(onClick = { onPage(item) }), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (item == "counter") Lotus(Modifier.size(27.dp), muted = !active) else Text("▥", color = if (active) Orange else Subtle, fontSize = 25.sp, lineHeight = 25.sp)
                Text(if (item == "counter") { if (hi) "जप" else "Counter" } else { if (hi) "प्रगति" else "Progress" }, color = if (active) Color(0xFFD95018) else Subtle, fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun ModeSegment(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.fillMaxHeight().clip(CircleShape).background(if (selected) Brush.horizontalGradient(listOf(OrangeLight, Color(0xFFFFD7B7))) else Brush.horizontalGradient(listOf(Lavender, Lavender))).combinedClickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = Ink, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, fontSize = 14.sp)
    }
}

@Composable
private fun SegmentedLanguage(hi: Boolean, onLanguage: (Boolean) -> Unit, compact: Boolean = false) {
    Row(Modifier.width(if (compact) 144.dp else 260.dp).height(if (compact) 39.dp else 48.dp).clip(CircleShape).background(Lavender).border(1.dp, Line, CircleShape)) {
        listOf(true, false).forEach { value ->
            Box(Modifier.weight(1f).fillMaxHeight().clip(CircleShape).background(if (hi == value) Color(0xFFFFD1A9) else Color.Transparent).combinedClickable(onClick = { onLanguage(value) }), contentAlignment = Alignment.Center) {
                Text(if (value) "हिन्दी" else "English", color = Ink, fontSize = if (compact) 12.sp else 14.sp, fontWeight = if (hi == value) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun OrangeButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.height(54.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(Color(0xFFEA7930), Color(0xFFF58939), Color(0xFFEA712B)))).combinedClickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SmallAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.height(38.dp).clip(CircleShape).background(Lavender).combinedClickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = Ink, fontSize = 13.sp)
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Line)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(Brush.horizontalGradient(listOf(Orange, Color(0xFFF98D4D)))))
    }
}

@Composable
private fun CountRing(cycle: Int, count: Int, hi: Boolean, modifier: Modifier, dark: Boolean = false) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val diameter = size.minDimension - stroke - 4.dp.toPx()
            val top = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            drawArc(if (dark) Color(0xFF6B5969) else Color(0xFFD7D1DB), -90f, 360f, false, top, Size(diameter, diameter), style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(brush = Brush.sweepGradient(listOf(Orange, Color(0xFFFFA353), Orange)), startAngle = -90f,
                sweepAngle = 360f * ((if (cycle == 0 && count > 0) 108 else cycle).toFloat() / 108f), useCenter = false,
                topLeft = top, size = Size(diameter, diameter), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text((if (cycle == 0 && count > 0) 108 else cycle).toString(), fontSize = if (dark) 52.sp else 50.sp, lineHeight = 56.sp, fontWeight = FontWeight.Bold, color = if (dark) Color.White else Ink)
            Text(if (hi) "108 में से ${if (cycle == 0 && count > 0) 108 else cycle} जप" else "${if (cycle == 0 && count > 0) 108 else cycle} of 108 chants", fontSize = 12.sp, color = if (dark) Color.White else Ink)
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, suffix: String, goal: String?, progress: Float?, modifier: Modifier = Modifier) {
    Column(modifier.height(112.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, Line, RoundedCornerShape(16.dp)).padding(12.dp)) {
        Text(label, color = Subtle, fontSize = 12.sp, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = Ink, fontWeight = FontWeight.Bold, fontSize = 24.sp, maxLines = 1)
            if (suffix.isNotEmpty()) Text(" $suffix", color = Ink, fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
        if (goal != null && progress != null) {
            Text(goal, color = Subtle, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            ProgressBar(progress)
        }
    }
}

@Composable
private fun Lotus(modifier: Modifier = Modifier, pale: Boolean = false, muted: Boolean = false) {
    Canvas(modifier) {
        val scale = size.minDimension / 100f
        withTransform({ scale(scale, scale, Offset.Zero) }) {
            val orangeBrush = if (pale) Brush.verticalGradient(listOf(Color(0x15F47D35), Color(0x12F47D35))) else if (muted) Brush.verticalGradient(listOf(Color(0xFF8A8193), Color(0xFF8A8193))) else Brush.verticalGradient(listOf(Color(0xFFF58235), Color(0xFFFFA553)))
            val plumBrush = if (pale) orangeBrush else if (muted) orangeBrush else Brush.verticalGradient(listOf(Color(0xFFDF634B), Color(0xFF743352)))
            fun petal(x: Float, y: Float, cx: Float, cy: Float, ex: Float, ey: Float, brush: Brush) {
                val path = Path().apply { moveTo(50f, 85f); quadraticTo(cx, cy, x, y); quadraticTo(ex, ey, 50f, 85f); close() }
                drawPath(path, brush)
            }
            petal(50f, 8f, 18f, 43f, 83f, 40f, orangeBrush)
            petal(23f, 28f, 13f, 71f, 34f, 78f, orangeBrush)
            petal(77f, 28f, 87f, 71f, 66f, 78f, orangeBrush)
            petal(4f, 53f, 9f, 91f, 35f, 88f, plumBrush)
            petal(96f, 53f, 91f, 91f, 65f, 88f, plumBrush)
            drawLine(if (pale) Color(0x12F47D35) else Color.White, Offset(50f, 84f), Offset(50f, 18f), 1.6f)
        }
    }
}

private fun formatDate(date: String, hi: Boolean): String = runCatching {
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val yesterdayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(yesterday.time)
    when (date) {
        todayKey() -> if (hi) "आज" else "Today"
        yesterdayKey -> if (hi) "कल" else "Yesterday"
        else -> {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)!!
            SimpleDateFormat("d MMM yyyy", if (hi) Locale.forLanguageTag("hi") else Locale.ENGLISH).format(parsed)
        }
    }
}.getOrDefault(date)

private fun formatTime(hour: Int, minute: Int): String = "%d:%02d %s".format(if (hour % 12 == 0) 12 else hour % 12, minute, if (hour < 12) "AM" else "PM")
