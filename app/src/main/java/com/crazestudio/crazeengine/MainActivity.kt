package com.crazestudio.crazeengine

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
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
import java.util.UUID

data class ChatMessage(val id: String = UUID.randomUUID().toString(), val role: String, val text: String)
data class ChatConversation(val id: String, val title: String, val messages: List<ChatMessage>)

private const val PREFS = "craze_ai"
private const val API_KEY = "api_key"
private const val MODEL = "model"
private const val SYSTEM = "system"
private const val HISTORY = "history"
private val CrazeOrange = Color(0xFFFF7A00)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CrazeAiApp() }
    }
}

@Composable
fun CrazeAiApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var chats by remember { mutableStateOf(loadChats(prefs).ifEmpty { listOf(ChatConversation(UUID.randomUUID().toString(), "New chat", emptyList())) }) }
    var currentId by remember { mutableStateOf(chats.first().id) }
    var page by remember { mutableStateOf("chat") }
    var input by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf(prefs.getString(API_KEY, "") ?: "") }
    var model by remember { mutableStateOf(prefs.getString(MODEL, "gemini-2.5-flash") ?: "gemini-2.5-flash") }
    var systemPrompt by remember { mutableStateOf(prefs.getString(SYSTEM, "You are CrazeAi, a helpful and accurate AI assistant.") ?: "") }
    var dark by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val current = chats.firstOrNull { it.id == currentId } ?: chats.first()

    fun save() = saveChats(prefs, chats)
    fun newChat() {
        val chat = ChatConversation(UUID.randomUUID().toString(), "New chat", emptyList())
        chats = listOf(chat) + chats
        currentId = chat.id
        input = ""
        error = null
        page = "chat"
        save()
    }
    fun replaceMessages(messages: List<ChatMessage>) {
        val title = messages.firstOrNull { it.role == "user" }?.text?.trim()?.take(36)?.ifBlank { "New chat" } ?: current.title
        chats = chats.map { if (it.id == currentId) it.copy(title = title, messages = messages) else it }
        save()
    }
    fun send() {
        if (loading || input.isBlank()) return
        if (apiKey.isBlank()) {
            error = "Add your Gemini API key in Settings."
            page = "settings"
            return
        }
        val user = ChatMessage(role = "user", text = input.trim())
        val history = current.messages + user
        replaceMessages(history)
        input = ""
        error = null
        loading = true
        scope.launch {
            try {
                val answer = GeminiClient.generate(apiKey, model, systemPrompt, history)
                replaceMessages(history + ChatMessage(role = "model", text = answer))
            } catch (t: Throwable) {
                error = t.message ?: "Gemini request failed"
            } finally {
                loading = false
            }
        }
    }

    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = CrazeOrange) else lightColorScheme(primary = Color(0xFFB95700))) {
        Scaffold(
            topBar = { TopAppBar(title = { Text(if (page == "chat") current.title else if (page == "history") "Chats" else "Settings") }) },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(page == "chat", { page = "chat" }, { Text("⌂") }, { Text("Chat") })
                    NavigationBarItem(page == "history", { page = "history" }, { Text("◷") }, { Text("Chats") })
                    NavigationBarItem(page == "settings", { page = "settings" }, { Text("⚙") }, { Text("Settings") })
                }
            }
        ) { padding ->
            when (page) {
                "settings" -> SettingsScreen(apiKey, { apiKey = it; prefs.edit().putString(API_KEY, it).apply() }, model, { model = it; prefs.edit().putString(MODEL, it).apply() }, systemPrompt, { systemPrompt = it; prefs.edit().putString(SYSTEM, it).apply() }, dark, { dark = it })
                "history" -> HistoryScreen(chats, currentId, { currentId = it; page = "chat" }, { id -> chats = chats.filterNot { it.id == id }; if (chats.isEmpty()) chats = listOf(ChatConversation(UUID.randomUUID().toString(), "New chat", emptyList())); currentId = chats.first().id; save() }, ::newChat)
                else -> ChatScreen(current.messages, input, { input = it }, loading, error, ::send, clipboard)
            }
        }
    }
}

@Composable
private fun ChatScreen(messages: List<ChatMessage>, input: String, setInput: (String) -> Unit, loading: Boolean, error: String?, send: () -> Unit, clipboard: androidx.compose.ui.platform.ClipboardManager) {
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (messages.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(Modifier.size(82.dp), shape = RoundedCornerShape(24.dp), color = CrazeOrange) { BoxLogo() }
                Spacer(Modifier.height(14.dp))
                Text("CrazeAi", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("Think faster. Build more.", color = Color.Gray)
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages, key = { it.id }) { message ->
                val user = message.role == "user"
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(if (user) "You" else "CrazeAi", fontWeight = FontWeight.SemiBold, color = if (user) CrazeOrange else MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(5.dp))
                        Text(message.text)
                        if (!user) TextButton({ clipboard.setText(AnnotatedString(message.text)) }) { Text("Copy") }
                    }
                }
            }
            if (loading) item { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("CrazeAi is thinking…") } }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        Row(verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(input, setInput, Modifier.weight(1f), placeholder = { Text("Message CrazeAi…") }, maxLines = 5)
            Spacer(Modifier.width(6.dp))
            IconButton(send) { Text("➤", color = CrazeOrange, fontSize = 22.sp) }
        }
    }
}

@Composable
private fun BoxLogo() { androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("⚡", fontSize = 44.sp, color = Color.Black) } }

@Composable
private fun HistoryScreen(chats: List<ChatConversation>, currentId: String, open: (String) -> Unit, delete: (String) -> Unit, newChat: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Conversations", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(newChat) { Text("New") }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(chats, key = { it.id }) { chat ->
                Card(Modifier.fillMaxWidth().clickable { open(chat.id) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(chat.title); Text("${chat.messages.size} messages", color = Color.Gray, fontSize = 12.sp) }
                        TextButton({ delete(chat.id) }) { Text("Delete") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(apiKey: String, setApiKey: (String) -> Unit, model: String, setModel: (String) -> Unit, prompt: String, setPrompt: (String) -> Unit, dark: Boolean, setDark: (Boolean) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("CrazeAi", fontSize = 30.sp, fontWeight = FontWeight.Bold); Text("Gemini-powered AI assistant", color = Color.Gray) }
        item {
            Text("Gemini API key", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(apiKey, setApiKey, Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), singleLine = true, placeholder = { Text("Paste your API key") })
        }
        item { Text("Model", fontWeight = FontWeight.SemiBold); OutlinedTextField(model, setModel, Modifier.fillMaxWidth(), singleLine = true) }
        item { Text("System prompt", fontWeight = FontWeight.SemiBold); OutlinedTextField(prompt, setPrompt, Modifier.fillMaxWidth(), minLines = 4) }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Dark mode", Modifier.weight(1f)); androidx.compose.material3.Switch(dark, setDark) } }
        item { Text("Your API key stays in local app preferences. Do not commit it to GitHub.", color = Color.Gray, fontSize = 12.sp) }
    }
}

private fun saveChats(prefs: android.content.SharedPreferences, chats: List<ChatConversation>) {
    val root = JSONArray()
    chats.take(50).forEach { chat ->
        val obj = JSONObject().put("id", chat.id).put("title", chat.title)
        val messages = JSONArray()
        chat.messages.takeLast(100).forEach { messages.put(JSONObject().put("id", it.id).put("role", it.role).put("text", it.text)) }
        obj.put("messages", messages)
        root.put(obj)
    }
    prefs.edit().putString(HISTORY, root.toString()).apply()
}

private fun loadChats(prefs: android.content.SharedPreferences): List<ChatConversation> = runCatching {
    val root = JSONArray(prefs.getString(HISTORY, "[]"))
    (0 until root.length()).map { i ->
        val obj = root.getJSONObject(i)
        val arr = obj.optJSONArray("messages") ?: JSONArray()
        ChatConversation(obj.optString("id", UUID.randomUUID().toString()), obj.optString("title", "New chat"), (0 until arr.length()).map { j ->
            val m = arr.getJSONObject(j)
            ChatMessage(m.optString("id", UUID.randomUUID().toString()), m.optString("role", "user"), m.optString("text", ""))
        })
    }
}.getOrDefault(emptyList())

private object GeminiClient {
    private val client = OkHttpClient()
    suspend fun generate(key: String, model: String, system: String, messages: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        val contents = JSONArray()
        messages.forEach { m ->
            contents.put(JSONObject().put("role", if (m.role == "model") "model" else "user").put("parts", JSONArray().put(JSONObject().put("text", m.text))))
        }
        val body = JSONObject().put("contents", contents).apply {
            if (system.isNotBlank()) put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        }
        val request = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key").post(body.toString().toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("Gemini ${response.code}: ${runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }.getOrNull() ?: text.take(240)}")
            val json = JSONObject(text)
            val candidates = json.optJSONArray("candidates") ?: throw IllegalStateException("Gemini returned no candidates")
            val parts = candidates.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: throw IllegalStateException("Gemini returned no text")
            buildString { for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text").orEmpty()) }.trim().ifBlank { throw IllegalStateException("Gemini returned an empty response") }
        }
    }
}
