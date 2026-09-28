package com.crazestudio.crazeengine

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val text: String
)

data class ChatConversation(
    val id: String,
    val title: String,
    val messages: List<ChatMessage>
)

private const val PREFS = "craze_ai"
private const val KEY_API = "api_key"
private const val KEY_MODEL = "model"
private const val KEY_SYSTEM = "system_prompt"
private const val KEY_HISTORY = "history"
private const val DEFAULT_MODEL = "gemini-2.5-flash"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CrazeAiApp()
        }
    }
}

@Composable
private fun CrazeAiApp() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var chats by remember {
        mutableStateOf(
            loadChats(prefs).ifEmpty {
                listOf(newConversation())
            }
        )
    }
    var currentId by remember { mutableStateOf(chats.first().id) }
    var screen by remember { mutableStateOf("chat") }
    var input by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf(prefs.getString(KEY_API, "") ?: "") }
    var model by remember {
        mutableStateOf(prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL)
    }
    var systemPrompt by remember {
        mutableStateOf(
            prefs.getString(
                KEY_SYSTEM,
                "You are CrazeAi, a helpful and accurate AI assistant."
            ) ?: ""
        )
    }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    val current = chats.firstOrNull { it.id == currentId } ?: chats.first()

    fun persist() {
        saveChats(prefs, chats)
    }

    fun createChat() {
        val chat = newConversation()
        chats = listOf(chat) + chats
        currentId = chat.id
        input = ""
        error = ""
        screen = "chat"
        persist()
    }

    fun replaceMessages(messages: List<ChatMessage>) {
        val firstUser = messages.firstOrNull { it.role == "user" }
        val title = firstUser?.text?.trim()?.take(36)?.ifBlank { "New chat" } ?: current.title
        chats = chats.map { chat ->
            if (chat.id == currentId) {
                chat.copy(title = title, messages = messages)
            } else {
                chat
            }
        }
        persist()
    }

    fun send() {
        if (loading || input.isBlank()) return
        if (apiKey.isBlank()) {
            error = "Add your Gemini API key in Settings first."
            screen = "settings"
            return
        }

        val userMessage = ChatMessage(role = "user", text = input.trim())
        val conversation = current.messages + userMessage
        replaceMessages(conversation)
        input = ""
        error = ""
        loading = true

        scope.launch {
            try {
                val answer = GeminiClient.generate(
                    apiKey,
                    model,
                    systemPrompt,
                    conversation
                )
                replaceMessages(conversation + ChatMessage(role = "model", text = answer))
            } catch (throwable: Throwable) {
                error = throwable.message ?: "Gemini request failed."
            } finally {
                loading = false
            }
        }
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (screen) {
                                "history" -> "Chats"
                                "settings" -> "Settings"
                                else -> current.title
                            }
                        )
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = screen == "chat",
                        onClick = { screen = "chat" },
                        icon = { Text("Chat") },
                        label = { Text("Chat") }
                    )
                    NavigationBarItem(
                        selected = screen == "history",
                        onClick = { screen = "history" },
                        icon = { Text("Chats") },
                        label = { Text("History") }
                    )
                    NavigationBarItem(
                        selected = screen == "settings",
                        onClick = { screen = "settings" },
                        icon = { Text("⚙") },
                        label = { Text("Settings") }
                    )
                }
            }
        ) { padding ->
            when (screen) {
                "history" -> HistoryScreen(
                    modifier = Modifier.padding(padding),
                    chats = chats,
                    onOpen = { id ->
                        currentId = id
                        screen = "chat"
                    },
                    onDelete = { id ->
                        chats = chats.filterNot { it.id == id }
                        if (chats.isEmpty()) chats = listOf(newConversation())
                        if (chats.none { it.id == currentId }) currentId = chats.first().id
                        persist()
                    },
                    onNew = ::createChat
                )
                "settings" -> SettingsScreen(
                    modifier = Modifier.padding(padding),
                    apiKey = apiKey,
                    onApiKeyChange = {
                        apiKey = it
                        prefs.edit().putString(KEY_API, it).apply()
                    },
                    model = model,
                    onModelChange = {
                        model = it
                        prefs.edit().putString(KEY_MODEL, it).apply()
                    },
                    systemPrompt = systemPrompt,
                    onSystemPromptChange = {
                        systemPrompt = it
                        prefs.edit().putString(KEY_SYSTEM, it).apply()
                    }
                )
                else -> ChatScreen(
                    modifier = Modifier.padding(padding),
                    messages = current.messages,
                    input = input,
                    onInputChange = { input = it },
                    loading = loading,
                    error = error,
                    onSend = ::send,
                    clipboard = clipboard
                )
            }
        }
    }
}

private fun newConversation(): ChatConversation {
    return ChatConversation(
        id = UUID.randomUUID().toString(),
        title = "New chat",
        messages = emptyList()
    )
}

@Composable
private fun ChatScreen(
    modifier: Modifier,
    messages: List<ChatMessage>,
    input: String,
    onInputChange: (String) -> Unit,
    loading: Boolean,
    error: String,
    onSend: () -> Unit,
    clipboard: androidx.compose.ui.platform.ClipboardManager
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (messages.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("⚡", fontSize = 52.sp)
                Text("CrazeAi", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Your Gemini-powered assistant")
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                MessageCard(message, clipboard)
            }
            if (loading) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("CrazeAi is thinking...")
                    }
                }
            }
        }

        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }

        Row(verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message CrazeAi...") },
                maxLines = 5
            )
            Spacer(Modifier.size(6.dp))
            IconButton(onClick = onSend, enabled = !loading && input.isNotBlank()) {
                Text("Send")
            }
        }
    }
}

@Composable
private fun MessageCard(
    message: ChatMessage,
    clipboard: androidx.compose.ui.platform.ClipboardManager
) {
    val isUser = message.role == "user"
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                if (isUser) "You" else "CrazeAi",
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(5.dp))
            Text(message.text)
            if (!isUser) {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(message.text))
                    }
                ) {
                    Text("Copy")
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    modifier: Modifier,
    chats: List<ChatConversation>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNew: () -> Unit
) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Conversations",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onNew) {
                Text("New")
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(chats, key = { it.id }) { chat ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(chat.title, fontWeight = FontWeight.SemiBold)
                            Text("${chat.messages.size} messages", fontSize = 12.sp)
                        }
                        TextButton(onClick = { onOpen(chat.id) }) { Text("Open") }
                        TextButton(onClick = { onDelete(chat.id) }) { Text("Delete") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    model: String,
    onModelChange: (String) -> Unit,
    systemPrompt: String,
    onSystemPromptChange: (String) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("⚡ CrazeAi", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Gemini-powered AI assistant")
        }
        item {
            Text("Gemini API key", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                placeholder = { Text("Paste your Gemini API key") }
            )
        }
        item {
            Text("Model", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = model,
                onValueChange = onModelChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        item {
            Text("System prompt", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = onSystemPromptChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 4
            )
        }
        item {
            Text(
                "The API key is stored locally on this device. Do not commit it to GitHub.",
                fontSize = 12.sp
            )
        }
    }
}

private fun saveChats(
    prefs: android.content.SharedPreferences,
    chats: List<ChatConversation>
) {
    val root = JSONArray()
    chats.take(50).forEach { chat ->
        val messages = JSONArray()
        chat.messages.takeLast(100).forEach { message ->
            messages.put(
                JSONObject()
                    .put("id", message.id)
                    .put("role", message.role)
                    .put("text", message.text)
            )
        }
        root.put(
            JSONObject()
                .put("id", chat.id)
                .put("title", chat.title)
                .put("messages", messages)
        )
    }
    prefs.edit().putString(KEY_HISTORY, root.toString()).apply()
}

private fun loadChats(prefs: android.content.SharedPreferences): List<ChatConversation> {
    return runCatching {
        val root = JSONArray(prefs.getString(KEY_HISTORY, "[]") ?: "[]")
        buildList {
            for (index in 0 until root.length()) {
                val chat = root.getJSONObject(index)
                val messageArray = chat.optJSONArray("messages") ?: JSONArray()
                val messages = buildList {
                    for (messageIndex in 0 until messageArray.length()) {
                        val message = messageArray.getJSONObject(messageIndex)
                        add(
                            ChatMessage(
                                id = message.optString("id", UUID.randomUUID().toString()),
                                role = message.optString("role", "user"),
                                text = message.optString("text", "")
                            )
                        )
                    }
                }
                add(
                    ChatConversation(
                        id = chat.optString("id", UUID.randomUUID().toString()),
                        title = chat.optString("title", "New chat"),
                        messages = messages
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

private object GeminiClient {
    private val client = OkHttpClient()

    suspend fun generate(
        apiKey: String,
        model: String,
        systemPrompt: String,
        messages: List<ChatMessage>
    ): String = withContext(Dispatchers.IO) {
        val contents = JSONArray()
        messages.forEach { message ->
            contents.put(
                JSONObject()
                    .put("role", if (message.role == "model") "model" else "user")
                    .put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", message.text))
                    )
            )
        }

        val body = JSONObject().put("contents", contents)
        if (systemPrompt.isNotBlank()) {
            body.put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemPrompt))
                )
            )
        }

        val request = Request.Builder()
            .url(
                "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "$model:generateContent?key=$apiKey"
            )
            .post(
                body.toString().toRequestBody("application/json".toMediaType())
            )
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(raw)
                        .optJSONObject("error")
                        ?.optString("message")
                }.getOrNull()
                throw IllegalStateException(
                    "Gemini ${response.code}: ${message ?: raw.take(200)}"
                )
            }

            val json = JSONObject(raw)
            val candidates = json.optJSONArray("candidates")
                ?: throw IllegalStateException("Gemini returned no candidates")
            val content = candidates.optJSONObject(0)
                ?.optJSONObject("content")
                ?: throw IllegalStateException("Gemini returned no content")
            val parts = content.optJSONArray("parts")
                ?: throw IllegalStateException("Gemini returned no parts")

            val answer = buildString {
                for (index in 0 until parts.length()) {
                    append(parts.optJSONObject(index)?.optString("text").orEmpty())
                }
            }.trim()

            if (answer.isBlank()) {
                throw IllegalStateException("Gemini returned an empty response")
            }
            answer
        }
    }
}
