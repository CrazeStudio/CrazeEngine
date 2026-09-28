package com.crazestudio.crazeengine

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import android.util.Base64

data class Message(val id: String = UUID.randomUUID().toString(), val role: String, val text: String, val imageBase64: String? = null, val mimeType: String? = null)
data class Conversation(val id: String, val title: String, val messages: List<Message>)

data class CrazeSettings(val apiKey: String, val model: String, val systemPrompt: String, val dark: Boolean, val amoled: Boolean)

private val Orange = Color(0xFFFF7A00)
private val Dark = Color(0xFF09090B)
private val Surface = Color(0xFF141416)
private val Surface2 = Color(0xFF1C1C20)
private const val PREFS = "craze_ai"
private const val KEY_API = "gemini_api_key"
private const val KEY_MODEL = "gemini_model"
private const val KEY_PROMPT = "system_prompt"
private const val KEY_DARK = "dark_mode"
private const val KEY_AMOLED = "amoled"
private const val KEY_HISTORY = "history_json"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CrazeAiApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrazeAiApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var screen by remember { mutableStateOf("chat") }
    var conversations by remember { mutableStateOf(loadConversations(prefs)) }
    var currentId by remember { mutableStateOf(conversations.firstOrNull()?.id ?: "") }
    var input by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf(prefs.getString(KEY_API, "") ?: "") }
    var model by remember { mutableStateOf(prefs.getString(KEY_MODEL, "gemini-2.5-flash") ?: "gemini-2.5-flash") }
    var systemPrompt by remember { mutableStateOf(prefs.getString(KEY_PROMPT, "You are CrazeAi, a helpful, accurate and concise AI assistant.") ?: "") }
    var dark by remember { mutableStateOf(prefs.getBoolean(KEY_DARK, true)) }
    var amoled by remember { mutableStateOf(prefs.getBoolean(KEY_AMOLED, false)) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingImage by remember { mutableStateOf<Pair<String,String>?>(null) }
    var pendingFileName by remember { mutableStateOf<String?>(null) }
    var showNewChatMenu by remember { mutableStateOf(false) }
    var requestJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val current = conversations.firstOrNull { it.id == currentId } ?: Conversation("", "New chat", emptyList())

    fun persist() { saveConversations(prefs, conversations) }
    fun newChat() {
        val c = Conversation(UUID.randomUUID().toString(), "New chat", emptyList())
        conversations = listOf(c) + conversations
        currentId = c.id
        screen = "chat"
        input = ""
        pendingImage = null
        pendingFileName = null
        persist()
    }
    fun updateMessages(messages: List<Message>) {
        val title = messages.firstOrNull { it.role == "user" }?.text?.take(36)?.ifBlank { "New chat" } ?: current.title
        conversations = conversations.map { if (it.id == currentId) it.copy(title = title, messages = messages) else it }
        persist()
    }
    fun sendMessage() {
        if (loading) return
        if (apiKey.isBlank()) { screen = "settings"; error = "Add your Gemini API key in Settings first."; return }
        if (input.isBlank() && pendingImage == null) return
        val text = input.trim()
        val user = Message(role = "user", text = text, imageBase64 = pendingImage?.first, mimeType = pendingImage?.second)
        val next = current.messages + user
        updateMessages(next)
        input = ""; pendingImage = null; pendingFileName = null; error = null; loading = true
        requestJob = scope.launch {
            runCatching { Gemini.chat(apiKey, model, systemPrompt, next) }
                .onSuccess { reply -> updateMessages(next + Message(role = "model", text = reply)) }
                .onFailure { error = it.message ?: "Request failed" }
            loading = false
            requestJob = null
        }
    }
    fun regenerate(index: Int) {
        if (loading || index <= 0) return
        val previous = current.messages.take(index).filter { it.role == "user" || it.role == "model" }
        val withoutTarget = current.messages.take(index)
        loading = true; error = null
        requestJob = scope.launch {
            runCatching { Gemini.chat(apiKey, model, systemPrompt, previous) }
                .onSuccess { reply -> updateMessages(withoutTarget + Message(role = "model", text = reply)) }
                .onFailure { error = it.message ?: "Regeneration failed" }
            loading = false; requestJob = null
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { readImage(context, uri) }.onSuccess { pendingImage = it }.onFailure { error = "Could not read image: ${it.message}" }
            }
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { readTextFile(context, uri) }.onSuccess { pair -> input = if (input.isBlank()) pair.second else input + "\n\n" + pair.second; pendingFileName = pair.first }.onFailure { error = "Could not read file: ${it.message}" }
            }
        }
    }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!text.isNullOrBlank()) input = if (input.isBlank()) text else "$input $text"
    }

    val bg = if (amoled && dark) Color.Black else if (dark) Dark else Color(0xFFF7F7F8)
    val fg = if (dark) Color.White else Color(0xFF151515)
    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Orange, background = bg, surface = Surface) else lightColorScheme(primary = Color(0xFFCC5E00), background = bg, surface = Color.White)) {
        Scaffold(
            containerColor = bg,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(if (screen == "chat") current.title else screenTitle(screen), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = { IconButton(onClick = { if (screen != "chat") screen = "chat" else showNewChatMenu = true }) { Text(if (screen == "chat") "⚡" else "‹", color = Orange, fontSize = 24.sp) } },
                    actions = { IconButton(onClick = { newChat() }) { Text("＋", fontSize = 24.sp) } },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = bg)
                )
            },
            bottomBar = { if (screen == "chat" || screen == "history") NavigationBar(containerColor = if (dark) Surface else Color.White) {
                NavigationBarItem(screen == "chat", { screen = "chat" }, icon = { Text("⌂") }, label = { Text("Chat") })
                NavigationBarItem(screen == "history", { screen = "history" }, icon = { Text("◷") }, label = { Text("Chats") })
                NavigationBarItem(screen == "settings", { screen = "settings" }, icon = { Text("⚙") }, label = { Text("Settings") })
            } else Unit }
        ) { pad ->
            when (screen) {
                "settings" -> SettingsScreen(apiKey, { apiKey = it; prefs.edit().putString(KEY_API, it).apply() }, model, { model = it; prefs.edit().putString(KEY_MODEL, it).apply() }, systemPrompt, { systemPrompt = it; prefs.edit().putString(KEY_PROMPT, it).apply() }, dark, { dark = it; prefs.edit().putBoolean(KEY_DARK, it).apply() }, amoled, { amoled = it; prefs.edit().putBoolean(KEY_AMOLED, it).apply() })
                "history" -> HistoryScreen(conversations, currentId, { currentId = it; screen = "chat" }, { id -> conversations = conversations.filterNot { it.id == id }; if (currentId == id) currentId = conversations.firstOrNull()?.id ?: ""; persist() }, { newChat() })
                else -> ChatScreen(current.messages, input, { input = it }, loading, error, pendingImage != null, pendingFileName, { imagePicker.launch("image/*") }, { filePicker.launch("text/*") }, { speechLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM) }) }, { sendMessage() }, { requestJob?.cancel(); requestJob = null; loading = false }, { text -> clipboard.setText(AnnotatedString(text)) }, { index -> regenerate(index) }, listState, bg, fg, pad)
            }
        }
        if (showNewChatMenu) AlertDialog(onDismissRequest = { showNewChatMenu = false }, title = { Text("New conversation?") }, text = { Text("Start a fresh CrazeAi conversation. Your previous chats remain in history.") }, confirmButton = { TextButton(onClick = { showNewChatMenu = false; newChat() }) { Text("New chat") } }, dismissButton = { TextButton(onClick = { showNewChatMenu = false }) { Text("Cancel") } })
    }
}

fun screenTitle(screen: String) = when (screen) { "history" -> "Conversations"; "settings" -> "Settings"; else -> "CrazeAi" }

@Composable
fun ChatScreen(messages: List<Message>, input: String, onInput: (String) -> Unit, loading: Boolean, error: String?, hasImage: Boolean, fileName: String?, pickImage: () -> Unit, pickFile: () -> Unit, voice: () -> Unit, send: () -> Unit, stop: () -> Unit, copy: (String) -> Unit, regenerate: (Int) -> Unit, listState: androidx.compose.foundation.lazy.LazyListState, bg: Color, fg: Color, pad: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(pad)) {
        if (messages.isEmpty()) WelcomeHero()
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(messages, key = { _, m -> m.id }) { index, msg -> MessageBubble(msg, { copy(msg.text) }, if (msg.role == "model") ({ regenerate(index) }) else null) }
            if (loading) item { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Orange); Spacer(Modifier.width(10.dp)); Text("CrazeAi is thinking…", color = fg) } }
        }
        error?.let { Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), shape = RoundedCornerShape(12.dp)) { Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp) } }
        if (hasImage || fileName != null) Row(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { AssistChip(onClick = {}, label = { Text(if (hasImage) "Image attached" else fileName ?: "File attached") }) }
        Composer(input, onInput, loading, pickImage, pickFile, voice, send, stop)
    }
}

@Composable
fun WelcomeHero() {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(86.dp).clip(RoundedCornerShape(26.dp)).background(Orange), contentAlignment = Alignment.Center) { Text("⚡", fontSize = 46.sp, color = Color.Black) }
        Spacer(Modifier.height(18.dp)); Text("CrazeAi", fontSize = 34.sp, fontWeight = FontWeight.Bold); Text("Think faster. Build more.", color = Color.Gray, fontSize = 15.sp)
        Spacer(Modifier.height(24.dp)); Text("Ask questions, write code, analyze ideas, or attach an image to get started.", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.widthIn(max = 330.dp))
    }
}

@Composable
fun MessageBubble(msg: Message, onCopy: () -> Unit, onRegenerate: (() -> Unit)?) {
    val user = msg.role == "user"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (user) Alignment.End else Alignment.Start) {
        Surface(color = if (user) Orange else Surface2, shape = RoundedCornerShape(20.dp), modifier = Modifier.widthIn(max = 390.dp)) {
            Column(Modifier.padding(14.dp)) {
                if (!msg.imageBase64.isNullOrBlank()) Text("🖼 Image attached", color = if (user) Color.Black else Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                MarkdownText(msg.text, if (user) Color.Black else Color.White)
                if (!user) Row { TextButton(onClick = onCopy) { Text("Copy") }; onRegenerate?.let { TextButton(onClick = it) { Text("Regenerate") } }; TextButton(onClick = { shareText(LocalContext.current, msg.text) }) { Text("Share") } }
            }
        }
    }
}

@Composable
fun MarkdownText(text: String, color: Color) {
    val blocks = text.split("```")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEachIndexed { i, block ->
            if (i % 2 == 1) Surface(color = Color(0xFF0D0D0F), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) { Text(block.removePrefix("kotlin").removePrefix("java").removePrefix("python").trim(), Modifier.padding(12.dp), color = Color(0xFFE8E8E8), fontSize = 13.sp) }
            else if (block.isNotBlank()) Text(block.trim(), color = color, lineHeight = 21.sp)
        }
    }
}

@Composable
fun Composer(value: String, onValue: (String) -> Unit, loading: Boolean, pickImage: () -> Unit, pickFile: () -> Unit, voice: () -> Unit, send: () -> Unit, stop: () -> Unit) {
    Surface(color = Surface, tonalElevation = 5.dp) {
        Column(Modifier.fillMaxWidth().padding(9.dp)) {
            OutlinedTextField(value, onValue, Modifier.fillMaxWidth(), placeholder = { Text("Message CrazeAi…") }, maxLines = 6, shape = RoundedCornerShape(20.dp), trailingIcon = { if (loading) IconButton(onClick = stop) { Text("■") } else IconButton(onClick = send) { Text("➤", color = Orange, fontSize = 22.sp) } })
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = pickImage) { Text("＋", fontSize = 20.sp) }
                TextButton(onClick = pickFile) { Text("File") }
                TextButton(onClick = voice) { Text("Voice") }
                Spacer(Modifier.weight(1f)); Text("Gemini", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SettingsScreen(key: String, setKey: (String) -> Unit, model: String, setModel: (String) -> Unit, prompt: String, setPrompt: (String) -> Unit, dark: Boolean, setDark: (Boolean) -> Unit, amoled: Boolean, setAmoled: (Boolean) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("CrazeAi", fontSize = 30.sp, fontWeight = FontWeight.Bold); Text("AI assistant settings", color = Color.Gray) }
        item { SettingCard("Gemini API", "Your key is stored only on this device.") { OutlinedTextField(key, setKey, Modifier.fillMaxWidth(), label = { Text("Gemini API key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true) } }
        item { SettingCard("Model", "Use a Gemini model available to your API key.") { OutlinedTextField(model, setModel, Modifier.fillMaxWidth(), label = { Text("Model name") }, singleLine = true) } }
        item { SettingCard("Assistant") { OutlinedTextField(prompt, setPrompt, Modifier.fillMaxWidth(), label = { Text("System prompt") }, minLines = 4) } }
        item { SettingCard("Appearance") { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Dark mode", Modifier.weight(1f)); Switch(dark, setDark) }; Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("AMOLED black", Modifier.weight(1f)); Switch(amoled && dark, setAmoled, enabled = dark) } } }
        item { Text("CrazeAi • Powered by Gemini", color = Color.Gray, fontSize = 13.sp) }
    }
}

@Composable
fun SettingCard(title: String, subtitle: String = "", content: @Composable () -> Unit) { Surface(color = Surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp); if (subtitle.isNotBlank()) Text(subtitle, color = Color.Gray, fontSize = 12.sp); content() } } }

@Composable
fun HistoryScreen(conversations: List<Conversation>, currentId: String, open: (String) -> Unit, delete: (String) -> Unit, newChat: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Conversations", fontSize = 28.sp, fontWeight = FontWeight.Bold, Modifier.weight(1f)); Button(onClick = newChat) { Text("New") } }
        Spacer(Modifier.height(12.dp))
        if (conversations.isEmpty()) Text("No conversations yet.", color = Color.Gray)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(conversations, key = { it.id }) { c -> Surface(color = if (c.id == currentId) Surface2 else Surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clickable { open(c.id) }) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium); Text("${c.messages.size} messages", color = Color.Gray, fontSize = 12.sp) }; TextButton(onClick = { delete(c.id) }) { Text("Delete") } } } } }
    }
}

fun saveConversations(prefs: android.content.SharedPreferences, conversations: List<Conversation>) {
    val arr = JSONArray(); conversations.take(50).forEach { c -> val o = JSONObject().put("id", c.id).put("title", c.title); val msgs = JSONArray(); c.messages.takeLast(100).forEach { m -> msgs.put(JSONObject().put("id", m.id).put("role", m.role).put("text", m.text)) }; o.put("messages", msgs); arr.put(o) }; prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
}
fun loadConversations(prefs: android.content.SharedPreferences): List<Conversation> = runCatching { val arr = JSONArray(prefs.getString(KEY_HISTORY, "[]")); (0 until arr.length()).map { i -> val o = arr.getJSONObject(i); val msgs = o.optJSONArray("messages") ?: JSONArray(); Conversation(o.getString("id"), o.optString("title", "New chat"), (0 until msgs.length()).map { j -> val m = msgs.getJSONObject(j); Message(m.optString("id", UUID.randomUUID().toString()), m.getString("role"), m.optString("text")) }) } }.getOrDefault(emptyList())

suspend fun readImage(context: Context, uri: Uri): Pair<String,String> = withContext(Dispatchers.IO) { val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Empty image"); val mime = context.contentResolver.getType(uri) ?: "image/jpeg"; Base64.encodeToString(bytes, Base64.NO_WRAP) to mime }
suspend fun readTextFile(context: Context, uri: Uri): Pair<String,String> = withContext(Dispatchers.IO) { val name = uri.lastPathSegment ?: "file"; val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Empty file"); name to text.take(50000) }
fun shareText(context: Context, text: String) { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Share CrazeAi response")) }

object Gemini {
    private val client = OkHttpClient.Builder().build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    suspend fun chat(apiKey: String, model: String, system: String, messages: List<Message>): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Add your Gemini API key in Settings." }
        val contents = JSONArray()
        messages.forEach { m ->
            val parts = JSONArray().put(JSONObject().put("text", m.text))
            if (!m.imageBase64.isNullOrBlank()) parts.put(JSONObject().put("inlineData", JSONObject().put("mimeType", m.mimeType ?: "image/jpeg").put("data", m.imageBase64)))
            contents.put(JSONObject().put("role", if (m.role == "model") "model" else "user").put("parts", parts))
        }
        val body = JSONObject().put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system)))).put("contents", contents).put("generationConfig", JSONObject().put("temperature", 0.7))
        val request = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/${model.trim()}:generateContent?key=${apiKey.trim()}").header("Content-Type", "application/json").post(body.toString().toRequestBody(jsonType)).build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) { val msg = runCatching { JSONObject(raw).optJSONObject("error")?.optString("message") }.getOrNull(); error("Gemini ${response.code}: ${msg ?: raw.take(300).ifBlank { "Request failed" }}") }
            val candidates = JSONObject(raw).optJSONArray("candidates") ?: error("Gemini returned no candidates.")
            if (candidates.length() == 0) error("Gemini returned an empty response.")
            val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts") ?: error("Gemini returned no text.")
            buildString { for (i in 0 until parts.length()) append(parts.getJSONObject(i).optString("text")) }.ifBlank { error("Gemini returned an empty response.") }
        }
    }
}
