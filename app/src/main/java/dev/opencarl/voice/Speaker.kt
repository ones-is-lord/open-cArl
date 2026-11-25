package dev.opencarl.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Reads replies out loud. Output only: there is no microphone path anywhere in
 * this app, by design. Markdown, code fences and URLs are stripped before
 * speaking because nobody wants a TTS voice reading backticks.
 */
class Speaker(private val context: Context) {

    private var engine: TextToSpeech? = null
    private var ready = false

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    fun init() {
        if (engine != null) return
        engine = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                engine?.language = Locale.getDefault()
                engine?.setSpeechRate(1.02f)
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _speaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _speaking.value = false
                    }

                    @Deprecated("deprecated in the platform")
                    override fun onError(utteranceId: String?) {
                        _speaking.value = false
                    }
                })
            } else {
                Log.w("Speaker", "text to speech unavailable")
            }
        }
    }

    fun speak(text: String) {
        init()
        if (!ready) return
        val clean = clean(text)
        if (clean.isBlank()) return
        engine?.stop()
        clean.chunked(3_500).forEach { chunk ->
            engine?.speak(chunk, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
        }
    }

    fun stop() {
        engine?.stop()
        _speaking.value = false
    }

    fun shutdown() {
        runCatching { engine?.stop() }
        runCatching { engine?.shutdown() }
        engine = null
        ready = false
    }

    fun clean(text: String): String {
        var result = text
        result = result.replace(Regex("```[\\s\\S]*?```"), " code block omitted. ")
        result = result.replace(Regex("`([^`]*)`"), "$1")
        result = result.replace(Regex("!\\[[^\\]]*\\]\\([^)]*\\)"), " ")
        result = result.replace(Regex("\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")
        result = result.replace(Regex("https?://\\S+"), " link ")
        result = result.replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
        result = result.replace(Regex("[*_>#|]"), " ")
        result = result.replace(Regex("\\n{2,}"), ". ")
        return result.replace(Regex("\\s+"), " ").trim()
    }
}
