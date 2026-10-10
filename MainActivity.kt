package com.myraa.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.ByteArrayOutputStream
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var status: TextView
    private lateinit var chat: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var panel: ScrollView
    private lateinit var wakeBtn: Button
    private lateinit var voiceStopBtn: Button
    private lateinit var urlBox: EditText
    private lateinit var tokenBox: EditText
    private lateinit var modelBox: EditText
    private lateinit var keyBox: EditText
    private lateinit var baseBox: EditText
    private lateinit var tavilyBox: EditText
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var web: WebView? = null

    private val cBg1 = Color.parseColor("#070A18")
    private val cBg2 = Color.parseColor("#17102E")
    private val cGlass = Color.parseColor("#14FFFFFF")
    private val cGlassLine = Color.parseColor("#28FFFFFF")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun glass(radiusDp: Int): GradientDrawable {
        val g = GradientDrawable()
        g.cornerRadius = dp(radiusDp).toFloat(); g.setColor(cGlass); g.setStroke(dp(1), cGlassLine)
        return g
    }

    private fun field(h: String, v: String, secret: Boolean = false): EditText {
        val e = EditText(this)
        e.hint = h; e.setText(v); e.setSingleLine(); e.setTextColor(Color.WHITE); e.setHintTextColor(Color.parseColor("#8A90A8"))
        e.background = glass(14); e.setPadding(dp(14), dp(10), dp(14), dp(10))
        if (secret) e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        return e
    }

    private fun lp(): LinearLayout.LayoutParams {
        val l = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        l.setMargins(dp(3), dp(3), dp(3), dp(3))
        return l
    }

    private fun btn(t: String, color: Int = Color.parseColor("#3B4BFF"), f: () -> Unit): Button {
        val b = Button(this)
        b.text = t
        val g = GradientDrawable()
        g.cornerRadius = dp(22).toFloat(); g.setColor(color)
        b.background = g
        b.setTextColor(Color.WHITE); b.isAllCaps = false; b.textSize = 13f
        b.minHeight = 0; b.minimumHeight = 0; b.minWidth = 0; b.minimumWidth = 0
        b.setPadding(dp(14), dp(8), dp(14), dp(8))
        b.setOnClickListener { try { f() } catch (e: Exception) { Bus.post("MYRAA: Error " + e.javaClass.simpleName) } }
        return b
    }

    private fun round(label: String, sizeDp: Int, c1: Int, c2: Int, f: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        val g = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c1, c2))
        g.shape = GradientDrawable.OVAL
        b.background = g
        b.setTextColor(Color.WHITE); b.isAllCaps = false; b.textSize = 12f
        b.minHeight = 0; b.minimumHeight = 0; b.minWidth = 0; b.minimumWidth = 0
        b.setPadding(0, 0, 0, 0)
        b.layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        b.setOnClickListener { try { f() } catch (e: Exception) { Bus.post("MYRAA: Error " + e.javaClass.simpleName) } }
        return b
    }

    private fun chip(label: String, color: Int, f: () -> Unit): Button {
        val b = btn(label, color, f)
        b.textSize = 12f
        b.setPadding(dp(12), dp(6), dp(12), dp(6))
        return b
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        try { tts = TextToSpeech(this, this) } catch (e: Exception) { tts = null }
        try {
            val f = java.io.File(filesDir, "crash.txt")
            if (f.exists()) { Bus.post("MYRAA: Pichhli baar crash hua tha:\n" + f.readText().take(300)); f.delete() }
        } catch (e: Exception) { }
        if (Bus.lines.isEmpty()) Bus.post("MYRAA: Good to see you, Sir. Bolo: 'Hey Myraa, open YouTube'.")

        // 3D avatar (WebView + CSS 3D layers). If WebView is missing/broken the app still works without it.
        try {
            val w = WebView(this)
            w.setBackgroundColor(Color.TRANSPARENT)
            w.settings.javaScriptEnabled = true
            w.webViewClient = object : WebViewClient() {
                override fun onPageFinished(v: WebView?, u: String?) { pushAvatar() }
            }
            w.loadUrl("file:///android_asset/avatar.html")
            web = w
        } catch (e: Throwable) { web = null }

        // header: title + wake pill + settings
        val title = TextView(this)
        title.text = "MYRAA"; title.textSize = 22f; title.setTextColor(Color.WHITE); title.letterSpacing = 0.25f
        title.typeface = android.graphics.Typeface.DEFAULT_BOLD
        wakeBtn = btn("WAKE: OFF", Color.parseColor("#4C1D95")) { toggleWake() }
        val header = LinearLayout(this)
        header.gravity = Gravity.CENTER_VERTICAL
        header.addView(title, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        header.addView(wakeBtn, lp())
        header.addView(btn("SETUP", Color.parseColor("#0F766E")) { setupStep() }, lp())
        header.addView(btn("MORE", Color.parseColor("#334155")) { panel.visibility = if (panel.visibility == View.GONE) View.VISIBLE else View.GONE }, lp())

        status = TextView(this)
        status.textSize = 13f; status.gravity = Gravity.CENTER; status.setPadding(dp(14), dp(5), dp(14), dp(5))
        status.background = glass(16)

        // one-tap quick commands (run locally, instantly)
        val chips = LinearLayout(this)
        chips.addView(chip("YouTube", Color.parseColor("#B91C1C")) { send("open youtube") }, lp())
        chips.addView(chip("Chrome", Color.parseColor("#1D4ED8")) { send("open chrome") }, lp())
        chips.addView(chip("Flipkart", Color.parseColor("#B45309")) { send("open flipkart") }, lp())
        chips.addView(chip("Maps", Color.parseColor("#047857")) { send("open maps") }, lp())
        chips.addView(chip("Torch", Color.parseColor("#A16207")) { send("torch on") }, lp())
        chips.addView(chip("Time", Color.parseColor("#6D28D9")) { send("time") }, lp())
        chips.addView(chip("Battery", Color.parseColor("#0E7490")) { send("battery") }, lp())
        chips.addView(chip("ATTACH", Color.parseColor("#334155")) { attach() }, lp())
        chips.addView(chip("STOP", Color.parseColor("#DC2626")) { tts?.stop(); Bus.onStop?.invoke() }, lp())
        val chipScroll = HorizontalScrollView(this)
        chipScroll.isHorizontalScrollBarEnabled = false
        chipScroll.addView(chips)

        // settings panel
        urlBox = field("Backend URL (khali = standalone)", Cfg.get(this, "url"))
        tokenBox = field(if (Cfg.secret(this, "token").isEmpty()) "Backend token" else "Backend token (saved)", "", true)
        modelBox = field("Model  provider:model", Cfg.get(this, "model", "gemini:gemini-flash-latest"))
        keyBox = field(if (Cfg.secret(this, "apikey").isEmpty()) "API key" else "API key (saved)", "", true)
        baseBox = field("Custom base URL (optional)", Cfg.get(this, "base"))
        tavilyBox = field(if (Cfg.secret(this, "tavily").isEmpty()) "Tavily key (optional, better web search)" else "Tavily key (saved)", "", true)
        val inner = LinearLayout(this)
        inner.orientation = LinearLayout.VERTICAL
        inner.setPadding(dp(10), dp(10), dp(10), dp(10))
        for (v in listOf(modelBox, keyBox, baseBox, tavilyBox, urlBox, tokenBox)) {
            val l = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT); l.setMargins(0, dp(3), 0, dp(3))
            inner.addView(v, l)
        }
        voiceStopBtn = btn(voiceStopLabel(), Color.parseColor("#334155")) { toggleVoiceStop() }
        val r1 = LinearLayout(this)
        r1.addView(btn("SAVE", Color.parseColor("#16A34A")) { saveSettings() }, lp())
        r1.addView(voiceStopBtn, lp())
        inner.addView(r1)
        panel = ScrollView(this)
        panel.addView(inner); panel.visibility = View.GONE; panel.background = glass(16)

        // chat bubbles
        chat = LinearLayout(this)
        chat.orientation = LinearLayout.VERTICAL
        chat.setPadding(dp(4), dp(6), dp(4), dp(6))
        scroll = ScrollView(this)
        scroll.addView(chat)

        // bottom bar: input pill + round mic + round send
        input = field("Kuch bolo ya likho...", "")
        input.background = glass(26)
        val bar = LinearLayout(this)
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.addView(input, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val mic = round("MIC", 58, Color.parseColor("#F43F5E"), Color.parseColor("#9333EA")) { micPressed() }
        val sendB = round("GO", 46, Color.parseColor("#3B82F6"), Color.parseColor("#6366F1")) { send(input.text.toString()) }
        val ml = LinearLayout.LayoutParams(dp(58), dp(58)); ml.setMargins(dp(8), 0, dp(4), 0)
        val sl = LinearLayout.LayoutParams(dp(46), dp(46)); sl.setMargins(dp(4), 0, 0, 0)
        bar.addView(mic, ml); bar.addView(sendB, sl)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(14), dp(28), dp(14), dp(12))
        root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(cBg1, cBg2))
        root.addView(header, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val wv = web
        if (wv != null) root.addView(wv, LinearLayout.LayoutParams(MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.27).toInt()))
        val sl2 = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT); sl2.gravity = Gravity.CENTER_HORIZONTAL; sl2.setMargins(0, dp(2), 0, dp(6))
        root.addView(status, sl2)
        root.addView(chipScroll, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(panel, LinearLayout.LayoutParams(MATCH_PARENT, dp(230)))
        root.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        root.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        setContentView(root)

        if (intent?.getBooleanExtra("autowake", false) == true && !Bus.wakeOn) toggleWake()
    }

    override fun onResume() {
        super.onResume()
        Bus.uiVisible = true
        try { web?.onResume() } catch (e: Exception) { }
        Bus.onLine = { l -> append(l) }
        Bus.onState = { refresh() }
        chat.removeAllViews()
        synchronized(Bus.lines) { for (l in Bus.lines) addBubble(l) }
        refresh()
    }

    override fun onPause() {
        Bus.uiVisible = false
        Bus.onLine = null; Bus.onState = null
        try { web?.onPause() } catch (e: Exception) { }
        super.onPause()
    }

    override fun onInit(s: Int) {
        try {
            if (s == TextToSpeech.SUCCESS) {
                tts?.language = Locale("en", "IN")
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) { Bus.state("SPEAKING") }
                    override fun onDone(id: String?) { Bus.state(if (Bus.wakeOn) "WAKE: bolo 'Hey Myraa'" else "IDLE") }
                    override fun onError(id: String?) { Bus.state("IDLE") }
                })
            }
        } catch (e: Exception) { }
    }

    private fun avatarState(): String {
        val s = Bus.status
        return if (s.contains("LISTEN")) "listening" else if (s.contains("THINK")) "thinking" else if (s.contains("SPEAK")) "speaking"
        else if (s.contains("ERR")) "error" else "idle"
    }

    private fun stateColor(): Int = when (avatarState()) {
        "listening" -> Color.parseColor("#3DFF9A")
        "thinking" -> Color.parseColor("#FFD24A")
        "speaking" -> Color.parseColor("#FF7AD9")
        "error" -> Color.parseColor("#FF4D4D")
        else -> Color.parseColor("#7DF9FF")
    }

    private fun pushAvatar() {
        try { web?.evaluateJavascript("if(window.MYRAA)MYRAA.state('" + avatarState() + "')", null) } catch (e: Exception) { }
    }

    private fun refresh() = runOnUiThread {
        val c = stateColor()
        status.text = "\u25CF  " + Bus.status
        status.setTextColor(c)
        val sg = GradientDrawable()
        sg.cornerRadius = dp(16).toFloat(); sg.setColor(cGlass); sg.setStroke(dp(1), c)
        status.background = sg
        wakeBtn.text = if (Bus.wakeOn) "WAKE: ON" else "WAKE: OFF"
        val bg = wakeBtn.background
        if (bg is GradientDrawable) bg.setColor(if (Bus.wakeOn) Color.parseColor("#059669") else Color.parseColor("#4C1D95"))
        pushAvatar()
    }

    private fun addBubble(line: String) {
        val mine = line.startsWith("Aap: ")
        val tv = TextView(this)
        tv.text = line.removePrefix("Aap: ").removePrefix("MYRAA: ")
        tv.textSize = 15f
        tv.setTextColor(Color.WHITE)
        tv.setPadding(dp(14), dp(10), dp(14), dp(10))
        tv.maxWidth = (resources.displayMetrics.widthPixels * 0.78).toInt()
        val g = GradientDrawable()
        g.cornerRadius = dp(18).toFloat()
        if (mine) {
            g.colors = intArrayOf(Color.parseColor("#6D5BFF"), Color.parseColor("#C04BFF"))
            g.orientation = GradientDrawable.Orientation.LEFT_RIGHT
        } else {
            g.setColor(Color.parseColor("#1FFFFFFF")); g.setStroke(dp(1), cGlassLine)
        }
        tv.background = g
        val l = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        l.gravity = if (mine) Gravity.END else Gravity.START
        l.setMargins(0, dp(4), 0, dp(4))
        chat.addView(tv, l)
        if (chat.childCount > 150) chat.removeViewAt(0)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun append(s: String) = runOnUiThread { addBubble(s) }

    private fun voiceStopLabel() = if (Cfg.get(this, "voicestop") == "1") "VOICE-STOP: ON" else "VOICE-STOP: OFF"

    private fun toggleVoiceStop() {
        Cfg.put(this, "voicestop", if (Cfg.get(this, "voicestop") == "1") "0" else "1")
        voiceStopBtn.text = voiceStopLabel()
        Bus.post("MYRAA: Voice-stop beta " + (if (Cfg.get(this, "voicestop") == "1") "ON" else "OFF") + ". Agar bolte waqt awaaz kat-kat kare to OFF kar do.")
    }

    private fun saveSettings() {
        Cfg.put(this, "url", urlBox.text.toString().trim())
        Cfg.put(this, "model", modelBox.text.toString().trim().ifEmpty { "gemini:gemini-flash-latest" })
        Cfg.put(this, "base", baseBox.text.toString().trim())
        if (tokenBox.text.isNotEmpty()) Cfg.putSecret(this, "token", tokenBox.text.toString().trim())
        if (keyBox.text.isNotEmpty()) Cfg.putSecret(this, "apikey", keyBox.text.toString().trim())
        if (tavilyBox.text.isNotEmpty()) Cfg.putSecret(this, "tavily", tavilyBox.text.toString().trim())
        tokenBox.setText(""); keyBox.setText(""); tavilyBox.setText(""); panel.visibility = View.GONE
        Bus.post("MYRAA: Settings save ho gayi.")
    }

    /** Guided one-step-at-a-time setup: overlay (open apps from background), battery (keep listening), contacts (call by name). */
    private fun setupStep() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Bus.post("MYRAA: Step 1/3: 'Display over other apps' ON karo, taaki background mein bhi YouTube/Chrome seedha khule.")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
            return
        }
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= 23 && !pm.isIgnoringBatteryOptimizations(packageName)) {
            Bus.post("MYRAA: Step 2/3: Battery ko 'Allow' / 'Unrestricted' karo, taaki background mein band na ho.")
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + packageName)))
            return
        }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Bus.post("MYRAA: Step 3/3: Contacts allow karo, taaki 'mom ko call karo' chale.")
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 3)
            return
        }
        Bus.post("MYRAA: Sab set hai. WAKE ON karke bolo: 'Hey Myraa, open YouTube'.")
    }

    // ---- file attach (backend mode): PDF / DOCX / text are uploaded so the server agents can read them
    private fun attach() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        startActivityForResult(i, 5)
    }

    override fun onActivityResult(rc: Int, res: Int, d: Intent?) {
        super.onActivityResult(rc, res, d)
        if (rc != 5 || res != RESULT_OK) return
        val uri = d?.data ?: return
        Bus.post("MYRAA: File upload ho rahi hai...")
        Thread {
            val msg = try {
                var name = "file"
                contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val ix = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (ix >= 0 && c.moveToFirst()) name = c.getString(ix) ?: name
                }
                val bos = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                contentResolver.openInputStream(uri)?.use { s ->
                    while (true) {
                        val n = s.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > 12000000) throw IllegalStateException("File 12MB se badi hai")
                        bos.write(buf, 0, n)
                    }
                }
                Brain.upload(this, name, bos.toByteArray())
            } catch (e: Exception) { "Upload fail: " + (e.message ?: e.javaClass.simpleName).take(80) }
            Bus.post("MYRAA: " + msg)
        }.start()
    }

    // ---- wake word / background service
    private fun toggleWake() {
        if (Bus.wakeOn) {
            Cfg.put(this, "wake_on", "0")
            stopService(Intent(this, WakeService::class.java)); return
        }
        val need = ArrayList<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.POST_NOTIFICATIONS)
        if (need.isNotEmpty()) { requestPermissions(need.toTypedArray(), 2); return }
        startWake()
        if (Cfg.get(this, "setup_done") != "1") { Cfg.put(this, "setup_done", "1"); setupStep() }
    }

    private fun startWake() {
        try {
            val i = Intent(this, WakeService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        } catch (e: Exception) { Bus.post("MYRAA: Wake start nahi ho paya: " + e.javaClass.simpleName) }
    }

    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) {
        val micOk = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (c == 2) {
            if (micOk) {
                startWake()
                if (Cfg.get(this, "setup_done") != "1") { Cfg.put(this, "setup_done", "1"); setupStep() }
            } else Bus.post("MYRAA: Mic permission ke bina wake nahi chalega.")
        }
        if (c == 1 && micOk) listenOnce()
        if (c == 3) Bus.post("MYRAA: " + (if (r.firstOrNull() == PackageManager.PERMISSION_GRANTED) "Contacts permission mil gayi." else "Contacts permission nahi mili."))
    }

    // ---- one-shot mic (only when wake mode is off)
    private fun micPressed() {
        if (Bus.wakeOn) { Bus.post("MYRAA: Wake mode ON hai. 'Hey Myraa' bolo, ya pehle WAKE OFF karo."); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1); return
        }
        listenOnce()
    }

    private fun listenOnce() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { append("MYRAA: Speech recognition available nahi hai."); return }
        try {
            tts?.stop(); recognizer?.destroy()
            val r = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) { Bus.state("LISTENING") }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(e: Int) { Bus.state("IDLE"); append("MYRAA: Mic error code " + e) }
                override fun onResults(res: Bundle?) {
                    val t = res?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (t != null) send(t) else Bus.state("IDLE")
                }
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(t: Int, p: Bundle?) {}
            })
            r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN"))
        } catch (e: Exception) { Bus.state("IDLE"); append("MYRAA: Mic start nahi hua: " + e.javaClass.simpleName) }
    }

    private fun send(text: String) {
        val t = text.trim(); if (t.isEmpty()) return
        runOnUiThread { input.setText("") }
        Bus.post("Aap: " + t); Bus.state("THINKING")
        Brain.ask(this, t) { reply ->
            Bus.post("MYRAA: " + reply)
            val engine = tts
            if (engine == null) Bus.state(if (Bus.wakeOn) "WAKE: bolo 'Hey Myraa'" else "IDLE")
            else runOnUiThread {
                try { engine.speak(reply.take(300), TextToSpeech.QUEUE_FLUSH, null, "ui") }  // never read long output aloud
                catch (e: Exception) { Bus.state("IDLE") }
            }
        }
    }

    override fun onDestroy() {
        try { tts?.shutdown(); recognizer?.destroy(); web?.destroy() } catch (e: Exception) { }
        super.onDestroy()
    }
}
