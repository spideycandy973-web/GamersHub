package com.gamershub.core.stream.chat

import com.gamershub.core.settings.SettingsRepository
import com.gamershub.core.stream.StreamEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** A single chat line from any platform. */
data class ChatMessage(
    val platform: String,
    val author: String,
    val text: String,
    val ts: Long = System.currentTimeMillis()
)

interface ChatClient {
    val platformId: String
    val messages: SharedFlow<ChatMessage>
    fun start()
    fun stop()
}

/**
 * YouTube Live Chat poller. Needs a YouTube Data API key + the broadcast's
 * liveChatId (both configurable in Settings → Streaming).
 */
class YouTubeLiveChatClient(
    private val apiKey: String,
    private val liveChatId: String
) : ChatClient {

    override val platformId = "youtube"
    override val messages = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 128)

    private val scope = com.gamershub.core.util.SafeScopes.io("chat")
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    @Volatile private var running = false

    override fun start() {
        if (apiKey.isBlank() || liveChatId.isBlank()) return
        running = true
        scope.launch {
            var pageToken: String? = null
            while (isActive && running) {
                var nextDelay = 5000L
                try {
                    var url = "https://www.googleapis.com/youtube/v3/liveChat/messages" +
                            "?liveChatId=$liveChatId&part=snippet,authorDetails&key=$apiKey"
                    if (pageToken != null) url += "&pageToken=$pageToken"
                    val req = Request.Builder().url(url).get().build()
                    client.newCall(req).execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        if (resp.isSuccessful) {
                            val root = JSONObject(body)
                            pageToken = root.optString("nextPageToken", null)
                                .ifBlank { null }
                            nextDelay = root.optLong("pollingIntervalMillis", 5000L)
                                .coerceIn(2000L, 15000L)
                            val items = root.optJSONArray("items")
                            if (items != null) {
                                for (i in 0 until items.length()) {
                                    val item = items.getJSONObject(i)
                                    val snippet = item.optJSONObject("snippet") ?: continue
                                    val author = item.optJSONObject("authorDetails")
                                    messages.emit(
                                        ChatMessage(
                                            platform = platformId,
                                            author = author?.optString("displayName") ?: "?",
                                            text = snippet.optString("displayMessage", "")
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {
                    nextDelay = 8000L
                }
                delay(nextDelay)
            }
        }
    }

    override fun stop() {
        running = false
    }
}

/**
 * Placeholder for platforms whose chat APIs require OAuth tokens with special
 * review scopes (Meta Graph API). Emits one informative line and stays quiet.
 */
class InfoChatClient(
    override val platformId: String,
    private val note: String
) : ChatClient {
    override val messages = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 4)
    override fun start() {
        messages.tryEmit(ChatMessage(platformId, "GamersHub", note))
    }

    override fun stop() = Unit
}

/** Aggregates chat from all platforms into one ordered flow for the overlay. */
class ChatHub(private val settings: SettingsRepository) {

    private val scope = com.gamershub.core.util.SafeScopes.default("chat-poll")
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private var clients: List<ChatClient> = emptyList()

    suspend fun startForStream() {
        stop()
        val list = mutableListOf<ChatClient>()

        val ytEnabled = settings.streamEnabled("youtube").first() &&
                settings.streamChatEnabled("youtube").first()
        if (ytEnabled) {
            val key = settings.ytApiKey.first()
            val chatId = settings.ytLiveChatId.first()
            list += if (key.isNotBlank() && chatId.isNotBlank()) {
                YouTubeLiveChatClient(key, chatId)
            } else {
                InfoChatClient(
                    "youtube",
                    "Add YouTube API key + liveChatId in Settings to see viewer chat here"
                )
            }
        }
        if (settings.streamEnabled("facebook").first() &&
            settings.streamChatEnabled("facebook").first()
        ) {
            list += InfoChatClient(
                "facebook", "Facebook chat needs a Meta Graph API token — configure in Settings"
            )
        }
        if (settings.streamEnabled("instagram").first() &&
            settings.streamChatEnabled("instagram").first()
        ) {
            list += InfoChatClient(
                "instagram", "Instagram chat needs a Meta Graph API token — configure in Settings"
            )
        }

        clients = list
        list.forEach { client ->
            client.start()
            scope.launch {
                client.messages.collect { msg ->
                    val updated = (_messages.value + msg)
                        .sortedBy { it.ts }
                        .takeLast(200)
                    _messages.value = updated
                }
            }
        }
        _active.value = true
    }

    fun stop() {
        clients.forEach { it.stop() }
        clients = emptyList()
        _active.value = false
    }

    fun clear() {
        _messages.value = emptyList()
    }
}
