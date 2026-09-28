package com.crazestudio.crazeengine

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
private fun CrazeAiApp() {
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
    var error by remember { mutableStateOf("") }
    val current = chats.firstOrNull { it.id == currentId } ?: chats.first()

    fun save() { saveChats(prefs, chats) }
    fun newChat() {
        val chat = ChatConversation(UUID.randomUUID().toString(), "New chat", emptyList())
        chats = listOf(chat) + chats
        currentId = chat.id
        input = ""
        error = ""
        page = "chat"
        save()
    }
    fun updateCurrent(messages: List<ChatMessage>) {
        val firstUser = messages.firstOrNull { it.role == "user" }
        val title = firstUser?.text?.trim()?.take(36)?.ifBlank { "New chat" } ?: current.title
        chats = chats.map { chat -> if (chat.id == currentId) chat.copy(title = title, messages = messages) else chat }
        save()
    }
    fun sendMessage() {
        if (loading || input.isBlank()) return
        if (apiKey.isBlank()) { error = "Add your Gemini API key in Settings."; page = "settings"; return }
        val user = ChatMessage(role = "user", text = input.trim())
        val history = current.messages + user
        updateCurrent(history)
        input = ""
        error = ""
        loading = true
        scope.launch {
            try {
                val answer = GeminiClient.generate(apiKey, model, systemPrompt, history)
                updateCurrent(history + ChatMessage(role = "model", text = answer))
            } catch (t: Throwable) { error = t.message ?: "Gemini request failed" }
            finally { loading = false }
        }
    }

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text(if (page == "chat") current.title else if (page == "history") "Chats" else "Settings") }) },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = page == "chat", onClick = { page = "chat" }, icon = { Text("⌂") }, label = { Text("Chat") })
                    NavigationBarItem(selected = page == "history", onClick = { page = "history" }, icon = { Text("◷") }, label = { Text("Chats") })
                    NavigationBarItem(selected = page == "settings", onClick = { page = "settings" }, icon = { Text("⚙") }, label = { Text("Settings") })
                }
            }
        ) { paddingValues ->
            when (page) {
                "settings" -> SettingsScreen(Modifier.padding(paddingValues), apiKey, { apiKey = it; prefs.edit().putString(API_KEY, it).apply() }, model, { model = it; prefs.edit().putString(MODEL, it).apply() }, systemPrompt, { systemPrompt = it; prefs.edit().putString(SYSTEM, it).apply() }, dark, { dark = it })
                "history" -> HistoryScreen(Modifier.padding(paddingValues), chats, { currentId = it; page = "chat" }, { id ->
                    chats = chats.filterNot { it.id == id }
                    if (chats.isEmpty()) chats = listOf(ChatConversation(UUID.randomUUID().toString(), "New chat", emptyList()))
                    currentId = chats.first().id
                    save()
                }, ::newChat)
                else -> ChatScreen(Modifier.padding(paddingValues), current.messages, input, { input = it }, loading, error, ::sendMessage, clipboard)
            }
        }
    }
}

@Composable
private fun ChatScreen(modifier: Modifier, messages: List<ChatMessage>, input: String, onInputChange: (String) -> Unit, loading: Boolean, error: String, onSend: () -> Unit, clipboard: androidx.compose.ui.platform.ClipboardManager) {
    Column(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (messages.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⚡", fontSize = 54.sp, color = CrazeOrange)
                Spacer(Modifier.height(8.dp))
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
                        if (!user) TextButton(onClick = { clipboard.setText(AnnotatedString(message.text)) }) { Text("Copy") }
                    }
                }
            }
            if (loading) item { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp)); Spacer(Modifier.size(8.dp)); Text("CrazeAi is thinking…") } }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(value = input, onValueChange = onInputChange, modifier = Modifier.weight(1f), placeholder = { Text("Message CrazeAi…") }, maxLines = 5)
            Spacer(Modifier.size(6.dp))
            IconButton(onClick = onSend, enabled = !loading) { Text("➤", color = CrazeOrange, fontSize = 22.sp) }
        }
    }
}

@Composable
private fun HistoryScreen(modifier: Modifier, chats: List<ChatConversation>, onOpen: (String) -> Unit, onDelete: (String) -> Unit, onNew: () -> Unit) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Conversations", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(onClick = onNew) { Text("New") }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(chats, key = { it.id }) { chat ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(chat.id) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(chat.title); Text("${chat.messages.size} messages", color = Color.Gray, fontSize = 12.sp) }
                        TextButton(onClick = { onDelete(chat.id) }) { Text("Delete") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier, apiKey: String, onApiKeyChange: (String) -> Unit, model: String, onModelChange: (String) -> Unit, prompt: String, onPromptChange: (String) -> Unit, dark: Boolean, onDarkChange: (Boolean) -> Unit) {
    LazyColumn(modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("CrazeAi", fontSize = 30.sp, fontWeight = FontWeight.Bold); Text("Gemini-powered AI assistant", color = Color.Gray) }
        item {
            Text("Gemini API key", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(value = apiKey, onValueChange = onApiKeyChange, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), singleLine = true, placeholder = { Text("Paste your API key") })
        }
        item { Text("Model", fontWeight = FontWeight.SemiBold); OutlinedTextField(value = model, onValueChange = onModelChange, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { Text("System prompt", fontWeight = FontWeight.SemiBold); OutlinedTextField(value = prompt, onValueChange = onPromptChange, modifier = Modifier.fillMaxWidth(), minLines = 4) }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Dark mode", Modifier.weight(1f)); Switch(checked = dark, onCheckedChange = onDarkChange) } }
        item { Text("Your API key stays in local app preferences. Never commit it to GitHub.", color = Color.Gray, fontSize = 12.sp) }
    }
}

private fun saveChats(prefs: android.content.SharedPreferences, chats: List<ChatConversation>) {
    val root = JSONArray()
    chats.take(50).forEach { chat ->
        val messages = JSONArray()
        chat.messages.takeLast(100).forEach { message -> messages.put(JSONObject().put("id", message.id).put("role", message.role).put("text", message.text)) }
        root.put(JSONObject().put("id", chat.id).put("title", chat.title).put("messages", messages))
    }
    prefs.edit().putString(HISTORY, root.toString()).apply()
}

private fun loadChats(prefs: android.content.SharedPreferences): List<ChatConversation> = runCatching {
    val root = JSONArray(prefs.getString(HISTORY, "[]") ?: "[]")
    (0 until root.length()).map { index ->
        val obj = root.getJSONObject(index)
        val arr = obj.optJSONArray("messages") ?: JSONArray()
        val messages = (0 until arr.length()).map { j ->
            val message = arr.getJSONObject(j)
            ChatMessage(message.optString("id", UUID.randomUUID().toString()), message.optString("role", "user"), message.optString("text", ""))
        }
        ChatConversation(obj.optString("id", UUID.randomUUID().toString()), obj.optString("title", "New chat"), messages)
    }
}.getOrDefault(emptyList())

private object GeminiClient {
    private val client = OkHttpClient()
    suspend fun generate(key: String, model: String, system: String, messages: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        val contents = JSONArray()
        messages.forEach { message ->
            contents.put(JSONObject().put("role", if (message.role == "model") "model" else "user").put("parts", JSONArray().put(JSONObject().put("text", message.text))))
        }
        val body = JSONObject().put("contents", contents)
        if (system.isNotBlank()) body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        val request = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key").post(body.toString().toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(responseText).optJSONObject("error")?.optString("message") }.getOrNull()
                throw IllegalStateException("Gemini ${response.code}: ${message ?: responseText.take(240)}")
            }
            val json = JSONObject(responseText)
            val candidates = json.optJSONArray("candidates") ?: throw IllegalStateException("Gemini returned no candidates")
            val content = candidates.optJSONObject(0)?.optJSONObject("content") ?: throw IllegalStateException("Gemini returned no content")
            val parts = content.optJSONArray("parts") ?: throw IllegalStateException("Gemini returned no text")
            val result = buildString { for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text").orEmpty()) }.trim()
            if (result.isBlank()) throw IllegalStateException("Gemini returned an empty response")
            result
        }
    }
}
