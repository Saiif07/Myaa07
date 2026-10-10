package com.myraa.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.json.JSONObject
import java.util.Locale

/** Foreground service: always listening for "Hey Myraa", then command -> Brain -> spoken reply. */
class WakeService : Service(), TextToSpeech.OnInitListener {
    companion object {
        const val ACT_STOP = "com.myraa.app.STOP"
        const val ACT_OFF = "com.myraa.app.OFF"
    }

    private val h = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var after: (() -> Unit)? = null
    private var mode = 0      // 0 = waiting for wake word, 1 = listening for command, 2 = busy, 3 = speaking (voice-stop beta)
    @Volatile private var alive = false
    private var handled = false
    private var fails = 0
    private var lock: PowerManager.WakeLock? = null
    private val wake = Regex("\\b(myr+a+|meer+a+|mir+a+|mair+a+|mayr+a+|my\\s*r+a+)\\b", RegexOption.IGNORE_CASE)
    private val idle = "WAKE: bolo 'Hey Myraa'"
    private val stopRe = Regex("\\b(stop|ruko|ruk\\s*jao|bas|chup|cancel)\\b", RegexOption.IGNORE_CASE)

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val act = i?.action
        if (act == ACT_STOP) { h.post { tts?.stop(); runAfter() }; return START_STICKY }
        if (act == ACT_OFF) { Cfg.put(this, "wake_on", "0"); stopSelf(); return START_NOT_STICKY }
        if (alive) return START_STICKY
        try { goForeground() } catch (e: Exception) {
            Bus.post("MYRAA: Wake service start nahi ho payi (" + e.javaClass.simpleName + "). App kholkar WAKE dabao.")
            stopSelf(); return START_NOT_STICKY
        }
        alive = true
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "myraa:wake")
            lock?.setReferenceCounted(false)
            lock?.acquire()
        } catch (e: Exception) { }
        Cfg.put(this, "wake_on", "1")
        Bus.wakeOn = true; Bus.state(idle)
        Bus.onStop = { h.post { tts?.stop(); runAfter() } }
        tts = try { TextToSpeech(this, this) } catch (e: Exception) { null }
        h.post { toWake() }
        Thread {
            var last = -1L
            while (alive) {
                try { last = poll(last) } catch (e: Exception) { }
                try { Thread.sleep(15000L) } catch (e: InterruptedException) { break }
            }
        }.start()
        return START_STICKY
    }

    private fun goForeground() {
        val ch = "myraa_wake"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(ch, "MYRAA", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stopPi = PendingIntent.getService(this, 1, Intent(this, WakeService::class.java).setAction(ACT_STOP), PendingIntent.FLAG_IMMUTABLE)
        val offPi = PendingIntent.getService(this, 2, Intent(this, WakeService::class.java).setAction(ACT_OFF), PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, ch) else Notification.Builder(this)
        b.setContentTitle("MYRAA sun rahi hai").setContentText("'Hey Myraa, open YouTube' jaise bolo")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentIntent(pi).setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "STOP", stopPi)
            .addAction(android.R.drawable.ic_delete, "OFF", offPi)
        val n = b.build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(1, n)
    }

    /** Backend mode only: show server events (reminders, approvals, scheduled results) as notifications. */
    private fun poll(last: Long): Long {
        val url = Cfg.get(this, "url").trim().trimEnd('/')
        if (url.isEmpty()) return last
        val tok = Cfg.secret(this, "token")
        val j = JSONObject(Brain.get(if (last < 0L) url + "/api/v1/events" else url + "/api/v1/events?after=" + last, tok))
        if (last < 0L) return j.optLong("latest", 0L)
        val ev = j.optJSONArray("events") ?: return last
        var l = last
        for (k in 0 until ev.length()) {
            val e = ev.getJSONObject(k)
            l = e.optLong("id", l)
            val t = e.optString("text")
            Bus.post("MYRAA: " + t)
            Notifier.show(this, "MYRAA", t, 200 + (l % 1000L).toInt())
        }
        return l
    }

    override fun onInit(s: Int) {
        if (s != TextToSpeech.SUCCESS) return
        ttsReady = true
        try {
            tts?.language = Locale("en", "IN")
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { h.post { runAfter() } }
                override fun onError(id: String?) { h.post { runAfter() } }
            })
        } catch (e: Exception) { ttsReady = false }
    }

    private fun runAfter() { val a = after; after = null; a?.invoke() }

    private fun speak(t: String, next: () -> Unit) {
        if (!ttsReady) { h.post(next); return }
        after = next
        try { tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "myraa") } catch (e: Exception) { runAfter() }
    }

    private fun toWake() { mode = 0; Bus.state(idle); listen() }

    private fun again(ms: Long) { h.postDelayed({ if (alive && mode == 0) listen() }, ms) }

    private fun again3(ms: Long) { h.postDelayed({ if (alive && mode == 3) listen() }, ms) }

    private fun first(b: Bundle?): String? = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun listen() {
        if (!alive) return
        handled = false
        try { rec?.destroy() } catch (e: Exception) { }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Bus.post("MYRAA: Is phone par speech recognition available nahi hai."); stopSelf(); return
        }
        try {
            val r = SpeechRecognizer.createSpeechRecognizer(this)
            rec = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) { fails = 0 }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(t: Int, p: Bundle?) {}
                override fun onPartialResults(p: Bundle?) {
                    if (mode == 0) first(p)?.let { check(it) }
                    else if (mode == 3) first(p)?.let { checkStop(it) }
                }
                override fun onResults(res: Bundle?) {
                    if (handled) return
                    val t = first(res)
                    if (mode == 3) { if (t == null || !checkStop(t)) again3(300L); return }
                    if (mode == 0) { if (t == null || !check(t)) again(150L) }
                    else if (mode == 1) { if (t.isNullOrBlank()) toWake() else { handled = true; process(t) } }
                }
                override fun onError(e: Int) {
                    if (handled) return
                    if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        Bus.post("MYRAA: Mic permission nahi hai."); Cfg.put(this@WakeService, "wake_on", "0"); stopSelf(); return
                    }
                    if (mode == 3) { again3(600L); return }
                    if (mode == 1) { toWake(); return }
                    again(if (e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1500L else 300L)
                }
            })
            r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true))
        } catch (e: Exception) {
            fails++
            if (fails >= 5) { Bus.post("MYRAA: Mic baar-baar fail ho raha hai (" + e.javaClass.simpleName + "), wake band kar rahi hoon."); stopSelf(); return }
            h.postDelayed({ if (alive) toWake() }, 3000L)
        }
    }

    /** Voice-stop (beta): while MYRAA speaks, a short utterance like "stop" / "ruko" cuts the speech. */
    private fun checkStop(text: String): Boolean {
        if (handled || !stopRe.containsMatchIn(text) || text.trim().split(" ").size > 3) return false
        handled = true
        try { rec?.cancel() } catch (e: Exception) { }
        try { tts?.stop() } catch (e: Exception) { }
        runAfter()
        return true
    }

    /** true if the wake word was found (and handled) in [text]. */
    private fun check(text: String): Boolean {
        val m = wake.find(text) ?: return false
        if (handled) return true
        handled = true
        try { rec?.cancel() } catch (e: Exception) { }
        val rest = text.substring(m.range.last + 1).trim(' ', ',', '.')
        if (rest.length > 2) { process(rest); return true }   // "Hey Myraa, <command>" in one breath
        mode = 2; Bus.state("LISTENING")
        speak("Ji?") { mode = 1; listen() }
        return true
    }

    private fun process(cmd: String) {
        mode = 2
        Bus.post("Aap: " + cmd); Bus.state("THINKING")
        Brain.ask(this, cmd) { reply ->
            h.post {
                Bus.post("MYRAA: " + reply); Bus.state("SPEAKING")
                speak(reply.take(300)) { toWake() }   // never read long output aloud
                if (Cfg.get(this, "voicestop") == "1" && ttsReady) { mode = 3; listen() }
            }
        }
    }

    override fun onDestroy() {
        alive = false
        h.removeCallbacksAndMessages(null)
        try { rec?.destroy() } catch (e: Exception) { }
        try { tts?.shutdown() } catch (e: Exception) { }
        try { lock?.release() } catch (e: Exception) { }
        Bus.wakeOn = false; Bus.onStop = null; Bus.state("IDLE")
        super.onDestroy()
    }
}
