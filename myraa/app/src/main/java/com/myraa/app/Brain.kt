package com.myraa.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.concurrent.thread

/** A risky action (sending a message) waiting for the user's 'haan' / 'cancel'. */
class Pending(val summary: String, val run: () -> String) {
    val at: Long = System.currentTimeMillis()
}

/** Shared in-process state between the UI and the background wake service. */
object Bus {
    val lines = ArrayList<String>()
    @Volatile var status = "IDLE"
    @Volatile var wakeOn = false
    @Volatile var uiVisible = false
    @Volatile var pending: Pending? = null
    @Volatile var onLine: ((String) -> Unit)? = null
    @Volatile var onState: (() -> Unit)? = null
    @Volatile var onStop: (() -> Unit)? = null
    fun post(s: String) {
        synchronized(lines) { lines.add(s); while (lines.size > 200) lines.removeAt(0) }
        try { onLine?.invoke(s) } catch (e: Exception) { }
    }
    fun state(s: String) { status = s; try { onState?.invoke() } catch (e: Exception) { } }
}

/** Secrets are encrypted with an Android Keystore AES-GCM key (falls back to private-prefs obfuscation if Keystore is broken). */
object Vault {
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val existing = ks.getKey("myraa", null)
        if (existing is SecretKey) return existing
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder("myraa", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return g.generateKey()
    }

    fun enc(s: String): String {
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key())
            Base64.encodeToString(c.iv + c.doFinal(s.toByteArray()), Base64.NO_WRAP)
        } catch (e: Exception) {
            "p:" + Base64.encodeToString(s.toByteArray(), Base64.NO_WRAP)
        }
    }

    fun dec(s: String): String {
        if (s.isEmpty()) return ""
        if (s.startsWith("p:")) return try { String(Base64.decode(s.substring(2), Base64.NO_WRAP)) } catch (e: Exception) { "" }
        return try {
            val b = Base64.decode(s, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b.copyOfRange(0, 12)))
            String(c.doFinal(b, 12, b.size - 12))
        } catch (e: Exception) { "" }
    }
}

object Cfg {
    private fun p(c: Context) = c.getSharedPreferences("myraa", Context.MODE_PRIVATE)
    fun get(c: Context, k: String, d: String = ""): String = p(c).getString(k, d) ?: d
    fun put(c: Context, k: String, v: String) { p(c).edit().putString(k, v).apply() }
    fun secret(c: Context, k: String): String = Vault.dec(get(c, k))
    fun putSecret(c: Context, k: String, v: String) { put(c, k, Vault.enc(v)) }
}

object Brain {
    private val history = ArrayList<String>()
    private val memRe = Regex("^\\W*(?:hey\\s+myr+a+[,\\s]*)?(?:please\\s+)?(?:yaad\\s*rakh(?:na|o)?|remember(?:\\s+that)?)\\s*(?:ki|that)?\\s*(.+)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val sensitive = Regex("password|passcode|\\botp\\b|token|api[\\s_-]?key|secret|\\bpin\\b|cvv", RegexOption.IGNORE_CASE)
    private val unit = "(seconds?|sec|minutes?|min|hours?|ghante|ghanta)"
    private val remindRe = Regex("remind|yaad\\s*dila|reminder", RegexOption.IGNORE_CASE)
    private val delayRe = Regex("(\\d+)\\s*" + unit + "\\b(\\s*(baad|mein|me|later))?", RegexOption.IGNORE_CASE)
    private val everyRe = Regex("(?:every|har)\\s*(\\d+)\\s*" + unit + "\\b", RegexOption.IGNORE_CASE)
    private val actionRe = Regex("\\b(open|kholo|khol|launch|call|dial|phone|sms|text|message|whatsapp|mail|email|navigate|directions|route|rasta|play|chalao|bajao|gaana|song|video|timer|alarm|torch|flashlight|flash|volume|awaaz|battery|camera|photo|search|dhundo|google|settings|wifi|bluetooth|copy|clipboard|share|time|date|samay|baje|news|weather|mausam|calculate|calculator|lookup)\\b", RegexOption.IGNORE_CASE)
    private val deepRe = Regex("\\b(research|compare|comparison|analy[sz]e|analysis)\\b", RegexOption.IGNORE_CASE)
    private val known = mapOf(
        "gemini" to "https://generativelanguage.googleapis.com/v1beta", "anthropic" to "https://api.anthropic.com/v1",
        "openai" to "https://api.openai.com/v1", "groq" to "https://api.groq.com/openai/v1",
        "openrouter" to "https://openrouter.ai/api/v1", "ollama" to "http://127.0.0.1:11434/v1")

    fun ask(ctx: Context, text: String, done: (String) -> Unit) {
        val app = ctx.applicationContext
        thread {
            val reply = try { answer(app, text) } catch (e: Throwable) {
                var m = e.message ?: e.javaClass.simpleName
                for (k in listOf(Cfg.secret(app, "apikey"), Cfg.secret(app, "token"))) if (k.isNotEmpty()) m = m.replace(k, "***")
                when {
                    e is IllegalStateException -> m
                    m.contains("401") || m.contains("403") -> "Key ya token galat hai ($m)."
                    m.contains("429") -> "Rate limit lag gayi, thodi der baad try karo."
                    e is java.net.UnknownHostException || e is java.net.ConnectException || e is java.net.SocketTimeoutException ->
                        "Internet ya server nahi mil raha. Offline mode: sirf reminders aur memory kaam karti hain."
                    else -> "Error: " + m.take(100)
                }
            }
            try { done(reply) } catch (e: Exception) { }
        }
    }

    fun get(url: String, token: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 20000
        if (token.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer " + token)
        val code = c.responseCode
        if (code >= 400) throw RuntimeException("HTTP " + code)
        return c.inputStream.bufferedReader().readText()
    }

    fun postJson(url: String, headers: Map<String, String>, body: JSONObject): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 10000; c.readTimeout = 120000
        c.setRequestProperty("Content-Type", "application/json")
        for ((k, v) in headers) c.setRequestProperty(k, v)
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val s = if (code < 400) c.inputStream else (c.errorStream ?: c.inputStream)
        val t = s.bufferedReader().readText()
        if (code >= 400) throw RuntimeException("HTTP " + code)
        return t
    }

    private fun human(s: Long): String =
        if (s >= 3600L && s % 3600L == 0L) (s / 3600L).toString() + " ghante"
        else if (s >= 60L && s % 60L == 0L) (s / 60L).toString() + " minute"
        else s.toString() + " second"

    private fun remind(ctx: Context, text: String): String? {
        if (!remindRe.containsMatchIn(text)) return null
        val ev = everyRe.find(text)
        val mt = ev ?: delayRe.find(text) ?: return null
        var secs = mt.groupValues[1].toLongOrNull() ?: return null
        val u = mt.groupValues[2].lowercase()
        secs *= if (u.startsWith("sec")) 1L else if (u.startsWith("min")) 60L else 3600L
        if (ev != null) secs = maxOf(secs, 60L)
        val msg = remindRe.replace(everyRe.replace(delayRe.replace(text, ""), ""), "").trim(' ', ',', '.').ifEmpty { text }
        Reminders.add(ctx, msg, secs * 1000L, if (ev != null) secs * 1000L else 0L)
        return if (ev != null) "Theek hai, har " + human(secs) + ": " + msg else "Theek hai, " + human(secs) + " baad: " + msg
    }

    fun saveMemory(ctx: Context, factRaw: String): String {
        val fact = factRaw.trim().replace("\n", " ")
        if (fact.isEmpty()) return "error: kya yaad rakhna hai?"
        if (sensitive.containsMatchIn(fact)) return "Ye sensitive lag raha hai, main ise yaad nahi rakhungi."
        Cfg.put(ctx, "mem", (Cfg.get(ctx, "mem") + "\n" + fact).trim())
        return "Yaad rakh liya."
    }

    /** One file picked in the app -> uploaded to the MYRAA server so its agents can read it (PDF, DOCX, text...). */
    fun upload(ctx: Context, name: String, data: ByteArray): String {
        val url = Cfg.get(ctx, "url").trim().trimEnd('/')
        if (url.isEmpty()) return "Files backend mode mein padhi jaati hain: Settings mein Backend URL aur token daalo."
        val body = JSONObject().put("name", name).put("data_b64", Base64.encodeToString(data, Base64.NO_WRAP))
        val r = JSONObject(postJson(url + "/api/v1/files", mapOf("Authorization" to "Bearer " + Cfg.secret(ctx, "token")), body))
        return "File upload ho gayi: " + r.optString("path") + ". Ab bolo: 'is file ko padh ke important points batao'."
    }

    private fun answer(ctx: Context, text: String): String {
        val m = memRe.find(text)
        if (m != null) return saveMemory(ctx, m.groupValues[1])
        val r = remind(ctx, text)
        if (r != null) return r
        val direct = LocalCommands.handle(ctx, text)  // instant on-device commands: no model, no key, no internet
        if (direct != null) return direct
        val url = Cfg.get(ctx, "url").trim().trimEnd('/')
        val haveModel = Cfg.secret(ctx, "apikey").isNotEmpty() || Cfg.get(ctx, "base").isNotEmpty()
        if (haveModel && actionRe.containsMatchIn(text)) {  // on-device agent: phone control, web, parallel tools, reviewer
            val a = PhoneAgent.run(ctx, text) { callModel(ctx, it) }
            if (a != null) return a
        }
        if (url.isNotEmpty()) {  // full multi-agent backend (approvals: just say "approve" / "cancel")
            val t = postJson(url + "/api/v1/chat", mapOf("Authorization" to "Bearer " + Cfg.secret(ctx, "token")), JSONObject().put("text", text))
            return JSONObject(t).optString("reply", t)
        }
        if (deepRe.containsMatchIn(text) && text.trim().split(" ").size > 4) {
            val d = deep(ctx, text)
            if (d != null) return d
        }
        return direct(ctx, text)
    }

    private fun remember(text: String, reply: String) {
        synchronized(history) {
            history.add("User: " + text); history.add("MYRAA: " + reply)
            while (history.size > 12) history.removeAt(0)
        }
    }

    private fun direct(ctx: Context, text: String): String {
        val sb = StringBuilder("You are MYRAA, a concise personal voice assistant. Reply in the user's language (Hinglish if they use it). Keep answers short enough to be spoken.\n")
        val mem = Cfg.get(ctx, "mem").split("\n").filter { it.isNotBlank() }.takeLast(10)
        if (mem.isNotEmpty()) sb.append("Known about the user: ").append(mem.joinToString("; ")).append("\n")
        synchronized(history) { for (h in history) sb.append(h).append("\n") }
        sb.append("User: ").append(text)
        val reply = callModel(ctx, sb.toString())
        remember(text, reply)
        return reply
    }

    /** Standalone multi-agent mode: split into sub-questions, answer them in parallel, then synthesize. */
    private fun deep(ctx: Context, text: String): String? {
        val raw = callModel(ctx, "Split this request into 2 or 3 independent sub-questions. Return ONLY a JSON array of strings. Request: " + text)
        val clean = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val arr = try { JSONArray(clean) } catch (e: Exception) { return null }
        val subs = ArrayList<String>()
        for (k in 0 until minOf(arr.length(), 3)) subs.add(arr.optString(k))
        if (subs.size < 2) return null
        val out = arrayOfNulls<String>(subs.size)
        val ts = ArrayList<Thread>()
        for (i in subs.indices) ts.add(thread { out[i] = try { callModel(ctx, subs[i]) } catch (e: Exception) { null } })
        for (t in ts) t.join()
        val parts = StringBuilder()
        for (i in subs.indices) if (out[i] != null) parts.append("Q: ").append(subs[i]).append("\nA: ").append(out[i]).append("\n")
        if (parts.isEmpty()) return null
        val reply = callModel(ctx, "Combine these findings into one short spoken answer in the user's language. Request: " + text + "\n" + parts)
        remember(text, reply)
        return reply
    }

    private fun callModel(ctx: Context, prompt: String): String {
        val spec = Cfg.get(ctx, "model", "gemini:gemini-flash-latest").trim()
        val base0 = Cfg.get(ctx, "base").trim().trimEnd('/')
        val key = Cfg.secret(ctx, "apikey")
        val i = spec.indexOf(':')
        val head = if (i > 0) spec.substring(0, i).lowercase() else ""
        val prov = if (known.containsKey(head)) head else if (base0.isNotEmpty()) "custom" else "gemini"
        val model = if (known.containsKey(head)) spec.substring(i + 1) else spec
        val base = if (base0.isNotEmpty()) base0 else (known[prov] ?: "")
        if (key.isEmpty() && prov != "ollama" && prov != "custom") throw IllegalStateException("Settings mein API key daalo.")
        val out: String = when (prov) {
            "gemini" -> {
                val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
                val r = JSONObject(postJson(base + "/models/" + model + ":generateContent", mapOf("x-goog-api-key" to key), body))
                r.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
            }
            "anthropic" -> {
                val body = JSONObject().put("model", model).put("max_tokens", 1024)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                val r = JSONObject(postJson(base + "/messages", mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"), body))
                val arr = r.getJSONArray("content")
                val sb = StringBuilder()
                for (k in 0 until arr.length()) sb.append(arr.getJSONObject(k).optString("text"))
                sb.toString()
            }
            else -> {
                val body = JSONObject().put("model", model)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                val h: Map<String, String> = if (key.isNotEmpty()) mapOf("Authorization" to "Bearer " + key) else emptyMap<String, String>()
                JSONObject(postJson(base + "/chat/completions", h, body)).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            }
        }
        val t = out.trim()
        if (t.isEmpty()) throw IllegalStateException("Model ne khali jawab diya.")
        return t
    }
}
