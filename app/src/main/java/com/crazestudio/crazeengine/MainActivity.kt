package com.crazestudio.crazeengine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
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

private val Orange = Color(0xFFFF7A00)
private val Dark = Color(0xFF0B0B0F)
private val Card = Color(0xFF17171D)

data class Message(val role: String, val text: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CrazeEngineApp() }
    }
}

@Composable
fun CrazeEngineApp() {
    var screen by remember { mutableStateOf("chat") }
    var messages by remember { mutableStateOf(listOf<Message>()) }
    var input by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
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
                "settings" -> SettingsScreen(apiKey, { apiKey = it }, model, { model = it }, systemPrompt, { systemPrompt = it })
                "history" -> HistoryScreen(messages) { screen = "chat" }
                else -> Column(Modifier.fillMaxSize().padding(pad)) {
                    if (messages.isEmpty()) Welcome(onNew = { messages = emptyList() })
                    else {
                        LazyColumn(
                            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                            state = listState,
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) {
                            itemsIndexed(messages) { index, msg ->
                                MessageBubble(
                                    msg,
                                    onCopy = { clipboard.setText(AnnotatedString(msg.text)) },
                                    onRegenerate = if (msg.role == "model" && index > 0) {
                                        {
                                            scope.launch {
                                                loading = true
                                                error = null
                                                runCatching { Gemini.chat(apiKey, model, systemPrompt, messages.dropLast(1)) }
                                                    .onSuccess { reply -> messages = messages.dropLast(1) + Message("model", reply) }
                                                    .onFailure { error = it.message ?: "Request failed" }
                                                loading = false
                                            }
                                        }
                                    } else null
                                )
                            }
                            if (loading) {
                                item {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(Modifier.size(20.dp), color = Orange)
                                        Spacer(Modifier.width(10.dp))
                                        Text("CrazeAi is thinking…")
                                    }
                                }
                            }
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                    Composer(input, { input = it }, loading) {
                        if (input.isBlank() || apiKey.isBlank() || loading) return@Composer
                        val text = input.trim()
                        input = ""
                        messages = messages + Message("user", text)
                        scope.launch {
                            loading = true
                            error = null
                            runCatching { Gemini.chat(apiKey, model, systemPrompt, messages) }
                                .onSuccess { messages = messages + Message("model", it) }
                                .onFailure { error = it.message ?: "Request failed" }
                            loading = false
                            listState.animateScrollToItem(messages.lastIndex.coerceAtLeast(0))
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
fun Welcome(onNew: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("⚡", fontSize = 54.sp, color = Orange)
        Text("CrazeAi", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("Your Gemini-powered assistant", color = Color.LightGray, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Suggestion("Explain something")
            Suggestion("Write code")
        }
        Spacer(Modifier.height(18.dp))
        Button(onClick = onNew) { Text("Start chatting") }
    }
}

@Composable
fun Suggestion(text: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = Card) {
        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), fontSize = 13.sp)
    }
}

@Composable
fun MessageBubble(msg: Message, onCopy: () -> Unit, onRegenerate: (() -> Unit)?) {
    val user = msg.role == "user"
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = if (user) Alignment.End else Alignment.Start) {
        Surface(color = if (user) Orange else Card, shape = RoundedCornerShape(18.dp), modifier = Modifier.widthIn(max = 340.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text(msg.text, color = if (user) Color.Black else Color.White, lineHeight = 21.sp)
                if (!user) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
            Button(onClick = send, enabled = value.isNotBlank() && !loading, shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) { Text("➤") }
        }
    }
}

@Composable
fun SettingsScreen(key: String, setKey: (String) -> Unit, model: String, setModel: (String) -> Unit, prompt: String, setPrompt: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("AI Settings", fontSize = 25.sp, fontWeight = FontWeight.Bold) }
        item { OutlinedTextField(key, setKey, Modifier.fillMaxWidth(), label = { Text("Gemini API key") }, supportingText = { Text("Stored locally. Never commit it to GitHub.") }, singleLine = true) }
        item { OutlinedTextField(model, setModel, Modifier.fillMaxWidth(), label = { Text("Gemini model") }, singleLine = true) }
        item { OutlinedTextField(prompt, setPrompt, Modifier.fillMaxWidth(), label = { Text("System prompt") }, minLines = 4) }
        item { Text("Suggested model: gemini-2.5-flash", color = Color.LightGray) }
        item { Text("CrazeEngine Android • CrazeAi powered", color = Color.Gray, fontSize = 13.sp) }
    }
}

@Composable
fun HistoryScreen(messages: List<Message>, openChat: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Conversation", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (messages.isEmpty()) Text("No conversations yet.", color = Color.Gray)
        else LazyColumn {
            items(messages.filter { it.role == "user" }) { message ->
                Text(message.text, Modifier.fillMaxWidth().padding(vertical = 12.dp))
                HorizontalDivider()
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = openChat) { Text("Back to chat") }
    }
}

object Gemini {
    private val client = OkHttpClient()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun chat(apiKey: String, model: String, system: String, messages: List<Message>): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Add your Gemini API key in Settings." }
        val contents = JSONArray()
        messages.forEach { m -> contents.put(JSONObject().put("role", if (m.role == "model") "model" else "user").put("parts", JSONArray().put(JSONObject().put("text", m.text)))) }
        val body = JSONObject().put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system)))).put("contents", contents)
        val request = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey").post(body.toString().toRequestBody(jsonType)).build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Gemini ${response.code}: ${JSONObject(raw).optJSONObject("error")?.optString("message") ?: "Request failed"}")
            val candidates = JSONObject(raw).optJSONArray("candidates") ?: error("No response from Gemini")
            val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts") ?: error("Empty response")
            buildString { for (i in 0 until parts.length()) append(parts.getJSONObject(i).optString("text")) }.ifBlank { error("Empty response") }
        }
    }
}
