package com.myraa.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

/** One automation step: kinds are wait, sleep, click_text, type, enter, click_result, send. */
class Step(val kind: String, val arg: String = "", val arg2: String = "", val optional: Boolean = false)

class Task(val pkg: String, val label: String, val steps: List<Step>, val doneMsg: String)

/**
 * Accessibility engine. It does NOTHING until a spoken command starts a Task, and it only runs that task's steps
 * (open search, type, tap a result, tap Send). Screen content is never stored or uploaded.
 */
class MyraaAccess : AccessibilityService(), TextToSpeech.OnInitListener {
    companion object {
        @Volatile var instance: MyraaAccess? = null
        fun enabled(): Boolean = instance != null
    }

    private val h = Handler(Looper.getMainLooper())
    private var task: Task? = null
    private var idx = 0
    private var stepStart = 0L
    private var taskStart = 0L
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        try { tts = TextToSpeech(this, this) } catch (e: Exception) { tts = null }
    }

    override fun onInit(s: Int) {
        if (s == TextToSpeech.SUCCESS) {
            ttsReady = true
            try { tts?.language = Locale("en", "IN") } catch (e: Exception) { }
        }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(i: Intent?): Boolean {
        instance = null
        h.removeCallbacksAndMessages(null)
        try { tts?.shutdown() } catch (e: Exception) { }
        return super.onUnbind(i)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun start(t: Task) {
        h.post {
            task = t
            idx = 0
            taskStart = System.currentTimeMillis()
            stepStart = taskStart
            h.removeCallbacks(tick)
            h.postDelayed(tick, 700L)
        }
    }

    private val tick: Runnable = object : Runnable {
        override fun run() {
            val t = task ?: return
            val now = System.currentTimeMillis()
            if (now - taskStart > 45000L) { finish("Time out ho gaya, kaam poora nahi hua."); return }
            if (idx >= t.steps.size) { finish(t.doneMsg); return }
            val s = t.steps[idx]
            val ok = try { exec(t, s) } catch (e: Exception) { false }
            if (ok) {
                idx++
                stepStart = now
                h.postDelayed(this, 600L)
                return
            }
            if (now - stepStart > 7000L) {
                if (s.optional) { idx++; stepStart = now; h.postDelayed(this, 200L); return }
                finish("Ye step nahi ho paya (" + s.kind + " " + s.arg.take(20) + "). App ka screen alag ho sakta hai.")
                return
            }
            h.postDelayed(this, 450L)
        }
    }

    private fun finish(msg: String) {
        task = null
        h.removeCallbacks(tick)
        Bus.post("MYRAA: " + msg)
        Notifier.show(this, "MYRAA", msg, 300)
        if (ttsReady) {
            try { tts?.speak(msg.take(200), TextToSpeech.QUEUE_FLUSH, null, "acc") } catch (e: Exception) { }
        }
    }

    private fun exec(t: Task, s: Step): Boolean {
        if (s.kind == "sleep") return System.currentTimeMillis() - stepStart >= (s.arg.toLongOrNull() ?: 500L)
        val root = rootInActiveWindow ?: return false
        if (t.pkg.isNotEmpty() && root.packageName?.toString() != t.pkg) return false
        val nodes = ArrayList<AccessibilityNodeInfo>()
        collect(root, nodes, 0)
        return when (s.kind) {
            "wait" -> true
            "click_text" -> clickMatching(nodes, Regex(s.arg, RegexOption.IGNORE_CASE), 24)
            "type" -> typeText(nodes, s.arg, s.arg2)
            "enter" -> pressEnter(nodes)
            "click_result" -> clickResult(nodes, s.arg)
            "send" -> clickMatching(nodes, Regex("\\bsend\\b|भेज", RegexOption.IGNORE_CASE), 14)
            else -> false
        }
    }

    private fun collect(n: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>, depth: Int) {
        if (out.size > 1500 || depth > 40) return
        out.add(n)
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            collect(c, out, depth + 1)
        }
    }

    private fun label(n: AccessibilityNodeInfo): String =
        ((n.text?.toString() ?: "") + " " + (n.contentDescription?.toString() ?: "")).trim()

    private fun meta(n: AccessibilityNodeInfo): String {
        val hint = if (Build.VERSION.SDK_INT >= 26) (n.hintText?.toString() ?: "") else ""
        return label(n) + " " + (n.viewIdResourceName ?: "") + " " + hint
    }

    private fun click(n: AccessibilityNodeInfo): Boolean {
        var c: AccessibilityNodeInfo? = n
        var d = 0
        while (c != null && d < 7) {
            if (c.isClickable && c.isEnabled) return c.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            c = c.parent
            d++
        }
        return false
    }

    private fun clickMatching(nodes: List<AccessibilityNodeInfo>, re: Regex, maxLen: Int): Boolean {
        for (n in nodes) {
            if (!n.isVisibleToUser || n.isEditable) continue
            val l = label(n)
            val id = n.viewIdResourceName ?: ""
            val byLabel = l.isNotEmpty() && l.length <= maxLen && re.containsMatchIn(l)
            val byId = id.isNotEmpty() && re.containsMatchIn(id)
            if ((byLabel || byId) && click(n)) return true
        }
        return false
    }

    private fun typeText(nodes: List<AccessibilityNodeInfo>, text: String, hintRe: String): Boolean {
        val eds = nodes.filter { it.isEditable && it.isVisibleToUser }
        if (eds.isEmpty()) return false
        var pick: AccessibilityNodeInfo? = null
        if (hintRe.isNotEmpty()) {
            val re = Regex(hintRe, RegexOption.IGNORE_CASE)
            for (e in eds) if (pick == null && re.containsMatchIn(meta(e))) pick = e
        }
        val target = pick ?: eds[0]
        val b = Bundle()
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }

    private fun pressEnter(nodes: List<AccessibilityNodeInfo>): Boolean {
        val ed = nodes.firstOrNull { it.isEditable && it.isVisibleToUser }
        if (ed != null && Build.VERSION.SDK_INT >= 30) {
            if (ed.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)) return true
        }
        return clickMatching(nodes, Regex("search|go|खोज", RegexOption.IGNORE_CASE), 14)
    }

    private fun clickResult(nodes: List<AccessibilityNodeInfo>, name: String): Boolean {
        val q = name.lowercase().trim()
        for (n in nodes) {
            if (!n.isVisibleToUser || n.isEditable) continue
            val l = label(n)
            if (l.isEmpty() || l.length > 60) continue
            val hit = l.lowercase().contains(q) || l.split(" ").any { Fuzzy.score(name, it) >= 90 }
            if (hit && click(n)) return true
        }
        return false
    }
}

/** High-level voice actions built on the accessibility engine. */
object Automation {
    private const val NEED = "Accessibility ON nahi hai: SETUP dabao aur MYRAA ko Accessibility mein ON karo."

    fun searchInApp(ctx: Context, app: String, query: String): String {
        val acc = MyraaAccess.instance ?: return NEED
        val found = PhoneAgent.findPackage(ctx, app) ?: return "Nahi ho paya: '" + app + "' naam ka app nahi mila."
        val r = PhoneAgent.callTool(ctx, "open_app", JSONObject().put("name", found.second))
        if (r.startsWith("error:") || r.startsWith("failed:")) return "Nahi ho paya: " + r
        if (r == "tap-needed") return "Notification par tap karke " + found.second + " kholo, phir dobara bolo."
        acc.start(Task(found.first, found.second, listOf(
            Step("wait"), Step("click_text", "search|खोज", "", true), Step("sleep", "700"),
            Step("type", query, "search|find|खोज"), Step("enter")),
            "'" + query + "' ko " + found.second + " mein search kar diya."))
        return found.second + " mein '" + query + "' search kar raha hoon."
    }

    /** Asks for confirmation first (unless AUTO-SEND is on): a mis-heard sentence must never message someone by itself. */
    fun prepareSend(ctx: Context, app0: String, contact: String, text: String): String {
        val appName = if (app0.isBlank()) Cfg.get(ctx, "msgapp", "whatsapp") else app0
        val found = PhoneAgent.findPackage(ctx, appName) ?: return "Nahi ho paya: '" + appName + "' app nahi mila."
        if (Cfg.get(ctx, "autosend") == "1") return doSend(ctx, found, contact, text)
        val summary = contact + " ko " + found.second + " par bhejun: '" + text.take(80) + "'?"
        Bus.pending = Pending(summary) { doSend(ctx, found, contact, text) }
        return summary + " Bhejne ke liye 'haan' bolo, ya 'cancel'."
    }

    private fun launched(ctx: Context, acc: MyraaAccess?, launchResult: String, task: Task, contact: String, label: String): String {
        if (launchResult.startsWith("error:") || launchResult.startsWith("failed:")) return "Nahi ho paya: " + launchResult
        if (launchResult == "tap-needed") return "Notification par tap karke " + label + " kholo, phir dobara bolo."
        if (acc == null) return "Message tayar hai: " + label + " mein ab Send dabao. (Auto-send ke liye Accessibility ON karo.)"
        acc.start(task)
        return contact + " ko " + label + " par bhej raha hoon..."
    }

    private fun doSend(ctx: Context, found: Pair<String, String>, contact: String, text: String): String {
        val pkg = found.first
        val label = found.second
        val acc = MyraaAccess.instance
        val digits = PhoneAgent.contactNumber(ctx, contact).filter { it.isDigit() }
        val done = contact + " ko " + label + " par message bhej diya."
        val send = Step("send")
        val pause = Step("sleep", "900")
        val searchBtn = Step("click_text", "search|खोज", "", true)
        // 1) WhatsApp + known number: chat opens with the text already typed, then tap Send
        if (pkg.startsWith("com.whatsapp") && digits.length >= 8) {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + digits + "?text=" + URLEncoder.encode(text, "UTF-8")))
            i.setPackage(pkg)
            return launched(ctx, acc, PhoneAgent.startIntent(ctx, i), Task(pkg, label, listOf(Step("wait"), pause, send), done), contact, label)
        }
        // 2) the SMS app + known number
        val smsPkg = try { Telephony.Sms.getDefaultSmsPackage(ctx) } catch (e: Exception) { null }
        if (smsPkg != null && pkg == smsPkg && digits.length >= 5) {
            val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + digits))
            i.putExtra("sms_body", text)
            i.setPackage(pkg)
            return launched(ctx, acc, PhoneAgent.startIntent(ctx, i), Task(pkg, label, listOf(Step("wait"), pause, send), done), contact, label)
        }
        // 3) ANY app that accepts shared text (Telegram, Instagram, Signal, Messenger, Snapchat, Gmail...): its own contact picker
        val share = Intent(Intent.ACTION_SEND)
        share.type = "text/plain"
        share.putExtra(Intent.EXTRA_TEXT, text)
        share.setPackage(pkg)
        val sr = PhoneAgent.startIntent(ctx, share)
        if (sr == "ok" || sr == "tap-needed") {
            val steps = listOf(Step("wait"), searchBtn, pause, Step("type", contact, "search|find|खोज"), Step("sleep", "1200"),
                Step("click_result", contact), pause, send)
            return launched(ctx, acc, sr, Task(pkg, label, steps, done), contact, label)
        }
        // 4) otherwise open the app and drive its chat screen: search contact, open chat, type message, send
        val r = PhoneAgent.callTool(ctx, "open_app", JSONObject().put("name", label))
        val steps = listOf(Step("wait"), searchBtn, pause, Step("type", contact, "search|find|खोज"), Step("sleep", "1200"),
            Step("click_result", contact), pause, Step("type", text, "message|type|संदेश|write|msg"), Step("send"))
        return launched(ctx, acc, r, Task(pkg, label, steps, done), contact, label)
    }
}
