package com.replysaathi.app

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val bg = Color.rgb(16, 21, 30)
    private val card = Color.rgb(27, 36, 51)
    private val green = Color.rgb(181, 244, 119)
    private val muted = Color.rgb(169, 183, 204)
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private lateinit var post: EditText
    private lateinit var instruction: EditText
    private lateinit var server: EditText
    private lateinit var token: EditText
    private lateinit var language: Spinner
    private lateinit var tone: Spinner
    private lateinit var status: TextView
    private lateinit var results: LinearLayout
    private lateinit var generate: Button
    private val languages = arrayOf("Roman Urdu", "Urdu", "English")
    private val tones = arrayOf("Friendly", "Funny", "Professional", "Thoughtful")
    private var requestVersion = 0

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val scroll = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(28), dp(24), dp(32)) }
        scroll.addView(column); setContentView(scroll)
        fun label(s: String, size: Float = 13f) = TextView(this).apply { text = s; textSize = size; setTextColor(muted); setPadding(0, dp(18), 0, dp(8)) }
        column.addView(TextView(this).apply { text = "✦ Reply Bot"; textSize = 30f; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD) })
        column.addView(label("Relevant replies. In your own voice.", 16f))
        column.addView(label("POST TEXT"))
        post = field("Paste the post text here", 6000).apply { minLines = 4; gravity = android.view.Gravity.TOP }
        column.addView(post)
        column.addView(label("Share → Reply Bot se post bhejo. Agar sirf link aaye, post ka text bhi paste karo. Photos aur videos abhi analyze nahi hote."))
        column.addView(label("LANGUAGE")); language = picker(languages, prefs.getString("language", "Roman Urdu")!!); column.addView(language)
        column.addView(label("TONE")); tone = picker(tones, prefs.getString("tone", "Friendly")!!); column.addView(tone)
        column.addView(label("YOUR INSTRUCTION")); instruction = field("Chhota, natural reply likho. Emojis kam rakho.", 500); instruction.setText(prefs.getString("instruction", "")); column.addView(instruction)
        generate = button("Suggest 3 replies"); column.addView(generate)
        status = label("Only the text above is sent to your configured AI when you tap. Review and post replies yourself."); status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE; column.addView(status)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; column.addView(results)
        val settingsToggle = button("Connection settings"); column.addView(settingsToggle)
        val settings = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }; column.addView(settings)
        settings.addView(label("BACKEND HTTPS URL")); server = field("https://your-server.example", 300).apply { setSingleLine(true); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI; setText(prefs.getString("server", "")) }; settings.addView(server)
        settings.addView(label("PAIRING TOKEN — NOT GEMINI API KEY")); token = field("Server APP_TOKEN", 200).apply { setSingleLine(true); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; setText(prefs.getString("token", "")) }; settings.addView(token)
        val save = button("Save connection"); settings.addView(save)
        settingsToggle.setOnClickListener { settings.visibility = if (settings.visibility == View.GONE) View.VISIBLE else View.GONE }
        save.setOnClickListener {
            try { val endpoint = endpoint(); val key = token.text.toString().trim(); require(key.length >= 32) { "Pairing token must have at least 32 characters." }
                prefs.edit().putString("server", endpoint).putString("token", key).apply(); status.text = "Connection saved."; settings.visibility = View.GONE
            } catch (e: Exception) { status.text = e.message }
        }
        generate.setOnClickListener { suggest() }
        if (state != null) {
            post.setText(state.getString("post", "")); instruction.setText(state.getString("instruction", ""))
            language.setSelection(languages.indexOf(state.getString("language")).coerceAtLeast(0)); tone.setSelection(tones.indexOf(state.getString("tone")).coerceAtLeast(0))
        } else receive(intent)
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun shape() = GradientDrawable().apply { setColor(card); cornerRadius = dp(12).toFloat() }
    private fun field(h: String, max: Int) = EditText(this).apply { hint = h; setHintTextColor(muted); setTextColor(Color.WHITE); textSize = 15f; background = shape(); setPadding(dp(14), dp(12), dp(14), dp(12)); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; filters = arrayOf(InputFilter.LengthFilter(max)); layoutParams = LinearLayout.LayoutParams(-1, -2) }
    private fun button(s: String) = Button(this).apply { text = s; isAllCaps = false; setTextColor(bg); backgroundTintList = android.content.res.ColorStateList.valueOf(green); layoutParams = LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(16) } }
    private fun picker(items: Array<String>, selected: String) = Spinner(this).apply {
        adapter = object : ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_spinner_item, items) {
            init { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View = super.getView(position, convertView, parent).apply { (this as TextView).setTextColor(Color.WHITE); setPadding(dp(12), dp(12), dp(12), dp(12)) }
        }
        background = shape(); setSelection(items.indexOf(selected).coerceAtLeast(0))
    }
    private fun endpoint(): String {
        val url = URL(server.text.toString().trim())
        require(url.protocol == "https" && url.userInfo == null && (url.path == "" || url.path == "/") && url.query == null && url.ref == null) { "Use an HTTPS server URL without a path." }
        return "https://${url.authority}"
    }
    private fun receive(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND && i.type == "text/plain") {
            requestVersion++
            generate.isEnabled = true
            post.setText(i.getStringExtra(Intent.EXTRA_TEXT).orEmpty().take(6000))
            results.removeAllViews()
            status.text = "Post received. Agar sirf URL hai, post ka text paste karo, phir Suggest dabao."
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receive(intent) }
    override fun onSaveInstanceState(out: Bundle) { out.putString("post", post.text.toString()); out.putString("instruction", instruction.text.toString()); out.putString("language", language.selectedItem.toString()); out.putString("tone", tone.selectedItem.toString()); super.onSaveInstanceState(out) }
    override fun onDestroy() { requestVersion++; executor.shutdownNow(); super.onDestroy() }
    private fun suggest() {
        val text = post.text.toString().trim()
        if (text.length < 3 || text.matches(Regex("https?://\\S+"))) { status.text = "Post ka text paste karo. Sirf link se post nahi parh sakte."; return }
        val base: String
        try { base = endpoint() } catch (e: Exception) { status.text = "Connection settings kholo aur HTTPS server set karo."; return }
        val key = token.text.toString().trim()
        if (key.length < 32) { status.text = "Connection settings mein pairing token set karo."; return }
        val lang = language.selectedItem.toString(); val voice = tone.selectedItem.toString(); val custom = instruction.text.toString()
        prefs.edit().putString("language", lang).putString("tone", voice).putString("instruction", custom).apply()
        val payload = JSONObject().put("text", text).put("language", lang).put("tone", voice).put("instruction", custom).toString()
        val version = ++requestVersion
        generate.isEnabled = false; results.removeAllViews(); status.text = "Thinking of something relevant…"
        executor.execute {
            var connection: HttpURLConnection? = null
            try {
                connection = URL("$base/suggest").openConnection() as HttpURLConnection
                connection.requestMethod = "POST"; connection.doOutput = true; connection.instanceFollowRedirects = false
                connection.connectTimeout = 15000; connection.readTimeout = 45000
                connection.setRequestProperty("Content-Type", "application/json"); connection.setRequestProperty("Authorization", "Bearer $key")
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() } ?: throw Exception("Server returned no response ($code).")
                val result = JSONObject(body)
                if (code !in 200..299) throw Exception(result.optString("error", "Server error ($code)"))
                val replies = result.getJSONArray("suggestions")
                require(replies.length() == 3) { "Invalid reply format." }
                val suggestions = (0 until replies.length()).map { replies.getString(it) }
                runOnUiThread {
                    if (isDestroyed || version != requestVersion) return@runOnUiThread
                    status.text = "3 ideas · ${result.optInt("remaining")} requests left today"
                    suggestions.forEach { reply ->
                        val edit = field("Edit your reply", 2000).apply { setText(reply); minLines = 2 }; results.addView(edit)
                        val copy = button("Copy reply"); copy.setOnClickListener { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Reply", edit.text.toString())); Toast.makeText(this, "Copied — paste in X", Toast.LENGTH_SHORT).show() }; results.addView(copy)
                    }
                    generate.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread { if (!isDestroyed && version == requestVersion) { status.text = e.message ?: "Internet connection failed."; generate.isEnabled = true } }
            } finally { connection?.disconnect() }
        }
    }
}
