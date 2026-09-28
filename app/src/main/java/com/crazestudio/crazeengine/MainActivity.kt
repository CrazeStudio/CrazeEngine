package com.crazestudio.crazeengine

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class Message(val role: String, val text: String)

private val Orange = Color(0xFFFF7A00)
private val Dark = Color(0xFF0B0B0F)
private val Card = Color(0xFF17171D)
private const val PREFS = "craze_ai"
private const val KEY_API = "gemini_api_key"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CrazeAiApp() }
    }
}

@Composable
fun CrazeAiApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var screen by remember { mutableStateOf("chat") }
    var messages by remember { mutableStateOf(emptyList<Message>()) }
    var input by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf(prefs.getString(KEY_API, "") ?: "") }
    var model by remember { mutableStateOf("gemini-2.5-flash") }
    var systemPrompt by remember { mutableStateOf("You are CrazeAi, a helpful, accurate and concise AI assistant.") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()

    MaterialTheme(colorScheme = darkColorScheme(primary = Orange, background = Dark, surface = Card)) {
        Scaffold(
            containerColor = Dark,
            topBar = { TopBar(screen) { screen = it } },
            bottomBar = { BottomBar(screen) { screen = it } }
        ) { pad ->
            when (screen) {
                "settings" -> SettingsScreen(apiKey, { value -> apiKey = value; prefs.edit().putString(KEY_API, value).apply() }, model, { model = it }, systemPrompt, { systemPrompt = it })
                "history" -> HistoryScreen(messages) { screen = "chat" }
                else -> Column(Modifier.fillMaxSize().padding(pad)) {
                    if (messages.isEmpty()) Welcome()
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                        state = listState,
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        itemsIndexed(messages) { index, msg ->
                            MessageBubble(msg,
                                onCopy = { clipboard.setText(AnnotatedString(msg.text)) },
                                onRegenerate = if (msg.role == "model") ({
                                    val previous = messages.take(index).takeLastWhile { it.role != "model" }
                                    scope.launch {
                                        loading = true; error = null
                                        runCatching { Gemini.chat(apiKey, model, systemPrompt, previous) }
                                            .onSuccess { reply -> messages = messages.take(index) + Message("model", reply) }
                                            .onFailure { error = it.message ?: "Request failed" }
                                        loading = false
                                    }
                                }) else null
                            )
                        }
                        if (loading) item { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp), color = Orange); Spacer(Modifier.width(10.dp)); Text("CrazeAi is thinking…") } }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                    Composer(input, { input = it }, loading) {
                        if (input.isBlank() || apiKey.isBlank() || loading) {
                            if (apiKey.isBlank()) { error = "Add your Gemini API key in Settings first."; screen = "settings" }
                            return@Composer
                        }
                        val text = input.trim(); input = ""; messages = messages + Message("user", text)
                        scope.launch {
                            loading = true; error = null
                            runCatching { Gemini.chat(apiKey, model, systemPrompt, messages) }
                                .onSuccess { messages = messages + Message("model", it) }
                                .onFailure { error = it.message ?: "Request failed" }
                            loading = false
                            if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(screen: String, navigate: (String) -> Unit) {
    CenterAlignedTopAppBar(
        title = { Text(if (screen == "chat") "CrazeAi" else screen.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold) },
        navigationIcon = { TextButton(onClick = { navigate("chat") }) { Text("⚡", fontSize = 22.sp, color = Orange) } },
        actions = { TextButton(onClick = { navigate("settings") }) { Text("⚙") } },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Dark)
    )
}

@Composable
fun BottomBar(screen: String, navigate: (String) -> Unit) {
    NavigationBar(containerColor = Card) {
        NavigationBarItem(screen == "chat", { navigate("chat") }, icon = { Text("💬") }, label = { Text("Chat") })
        NavigationBarItem(screen == "history", { navigate("history") }, icon = { Text("◷") }, label = { Text("History") })
        NavigationBarItem(screen == "settings", { navigate("settings") }, icon = { Text("⚙") }, label = { Text("Settings") })
    }
}

@Composable
fun Welcome() {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(shape = RoundedCornerShape(28.dp), color = Card) { Text("⚡", fontSize = 54.sp, color = Orange, modifier = Modifier.padding(22.dp)) }
        Text("CrazeAi", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("Your Gemini-powered assistant", color = Color.LightGray, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(20.dp)); Text("Add your Gemini API key in Settings to start chatting.", color = Color.Gray, fontSize = 13.sp)
    }
}

@Composable
fun MessageBubble(msg: Message, onCopy: () -> Unit, onRegenerate: (() -> Unit)?) {
    val user = msg.role == "user"
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = if (user) Alignment.End else Alignment.Start) {
        Surface(color = if (user) Orange else Card, shape = RoundedCornerShape(18.dp), modifier = Modifier.widthIn(max = 360.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text(msg.text, color = if (user) Color.Black else Color.White, lineHeight = 21.sp)
                if (!user) Row(Modifier.padding(top = 6.dp)) {
                    TextButton(onClick = onCopy) { Text("Copy") }
                    onRegenerate?.let { TextButton(onClick = it) { Text("Regenerate") } }
                }
            }
        }
    }
}

@Composable
fun Composer(value: String, onValue: (String) -> Unit, loading: Boolean, send: () -> Unit) {
    Surface(color = Card, tonalElevation = 4.dp) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(value, onValue, Modifier.weight(1f), placeholder = { Text("Message CrazeAi…") }, maxLines = 5, shape = RoundedCornerShape(22.dp))
            Spacer(Modifier.width(8.dp))
            Button(onClick = send, enabled = value.isNotBlank() && !loading, shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) { Text(if (loading) "…" else "➤") }
        }
    }
}

@Composable
fun SettingsScreen(key: String, setKey: (String) -> Unit, model: String, setModel: (String) -> Unit, prompt: String, setPrompt: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("CrazeAi Settings", fontSize = 25.sp, fontWeight = FontWeight.Bold) }
        item { OutlinedTextField(key, setKey, Modifier.fillMaxWidth(), label = { Text("Gemini API key") }, supportingText = { Text("Stored locally on this device.") }, visualTransformation = PasswordVisualTransformation(), singleLine = true) }
        item { OutlinedTextField(model, setModel, Modifier.fillMaxWidth(), label = { Text("Gemini model") }, singleLine = true) }
        item { OutlinedTextField(prompt, setPrompt, Modifier.fillMaxWidth(), label = { Text("System prompt") }, minLines = 4) }
        item { Text("Default model: gemini-2.5-flash", color = Color.LightGray) }
        item { Text("CrazeAi • Gemini-powered", color = Color.Gray, fontSize = 13.sp) }
    }
}

@Composable
fun HistoryScreen(messages: List<Message>, openChat: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Conversation", fontSize = 25.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp))
        if (messages.isEmpty()) Text("No messages yet.", color = Color.Gray)
        else messages.filter { it.role == "user" }.forEach { Text(it.text, Modifier.fillMaxWidth().padding(vertical = 12.dp)); HorizontalDivider() }
        Spacer(Modifier.height(12.dp)); Button(onClick = openChat) { Text("Back to chat") }
    }
}

object Gemini {
    private val client = OkHttpClient.Builder().build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun chat(apiKey: String, model: String, system: String, messages: List<Message>): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Add your Gemini API key in Settings." }
        require(model.isNotBlank()) { "Gemini model cannot be empty." }
        val contents = JSONArray()
        messages.forEach { m ->
            contents.put(JSONObject().put("role", if (m.role == "model") "model" else "user").put("parts", JSONArray().put(JSONObject().put("text", m.text))))
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", 0.7))
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/${model.trim()}:generateContent?key=${apiKey.trim()}")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonType))
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(raw).optJSONObject("error")?.optString("message") }.getOrNull()
                error("Gemini ${response.code}: ${message ?: raw.take(250).ifBlank { "Request failed" }}")
            }
            val root = JSONObject(raw)
            val candidates = root.optJSONArray("candidates") ?: error("Gemini returned no candidates.")
            if (candidates.length() == 0) error("Gemini returned an empty response.")
            val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts") ?: error("Gemini returned no text.")
            buildString { for (i in 0 until parts.length()) append(parts.getJSONObject(i).optString("text")) }.ifBlank { error("Gemini returned an empty response.") }
        }
    }
}
