package com.replysaathi.app

import android.accessibilityservice.AccessibilityService
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.ImageView
import android.widget.Toast

/** Reads X's visible accessibility text only after an explicit tap; never posts or clicks X. */
class XReaderService : AccessibilityService() {
    private val xPackages = setOf("com.twitter.android", "com.twitter.android.lite")
    private val manager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var bubble: View? = null
    private var dialog: AlertDialog? = null
    private var overlayEnabled = true
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onServiceConnected() { syncBubble() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Only window metadata is inspected here. No event text or node text is read.
        if (dialog?.isShowing == true) return
        syncBubble()
    }
    override fun onInterrupt() { hideBubble(); dialog?.dismiss() }
    override fun onDestroy() { hideBubble(); dialog?.dismiss(); super.onDestroy() }
    private fun xRoot(): AccessibilityNodeInfo? {
        val appWindow = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && (it.isActive || it.isFocused) }
        val root = appWindow?.root ?: rootInActiveWindow ?: return null
        return if (root.packageName?.toString() in xPackages) root else null
    }
    private fun syncBubble() {
        if (!overlayEnabled || xRoot() == null) hideBubble() else showBubble()
    }
    private fun hideBubble() { bubble?.let { try { manager.removeView(it) } catch (_: Exception) {} }; bubble = null }
    private fun showBubble() {
        if (bubble != null) return
        val params = WindowManager.LayoutParams(dp(58), dp(58), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.LEFT; x = resources.displayMetrics.widthPixels - dp(74); y = dp(190) }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_bot); contentDescription = "Reply Bot: read this X post"; isClickable = true; isFocusable = true
            background = GradientDrawable().apply { setColor(Color.rgb(181,244,119)); shape = GradientDrawable.OVAL; setStroke(dp(2), Color.rgb(181,244,119)) }
            setPadding(dp(3),dp(3),dp(3),dp(3)); elevation = dp(8).toFloat()
            setOnClickListener { readPost() }
            setOnLongClickListener {
                overlayEnabled = false; hideBubble()
                Toast.makeText(this@XReaderService, "Icon pause ho gaya. Accessibility service off/on karke wapas chalao.", Toast.LENGTH_LONG).show(); true
            }
        }
        var startX = 0; var startY = 0; var downX = 0f; var downY = 0f; var moved = false; var downAt = 0L
        icon.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startX = params.x; startY = params.y; downX = e.rawX; downY = e.rawY; moved = false; downAt = android.os.SystemClock.uptimeMillis(); true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt(); val dy = (e.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(8)) moved = true
                    if (moved) {
                        params.x = (startX + dx).coerceIn(0, (resources.displayMetrics.widthPixels - dp(58)).coerceAtLeast(0))
                        params.y = (startY + dy).coerceIn(dp(28), (resources.displayMetrics.heightPixels - dp(100)).coerceAtLeast(dp(28)))
                        try { manager.updateViewLayout(view, params) } catch (_: Exception) {}
                    }; true
                }
                MotionEvent.ACTION_UP -> { if (!moved) { if (android.os.SystemClock.uptimeMillis() - downAt > 650) view.performLongClick() else view.performClick() }; true }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
        try { manager.addView(icon, params); bubble = icon } catch (_: Exception) { bubble = null }
    }
    private fun readPost() {
        // Must still be X at click time. Never traverse another app's text hierarchy.
        val root = xRoot() ?: return
        val texts = linkedSetOf<String>()
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>(); queue.add(root)
        val screen = Rect(0, 0, resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        var visited = 0
        while (queue.isNotEmpty() && visited++ < 700) {
            val node = queue.removeFirst()
            val bounds = Rect(); node.getBoundsInScreen(bounds)
            if (node.isVisibleToUser && Rect.intersects(bounds, screen) && !node.isEditable) {
                val text = node.text?.toString()?.trim().orEmpty()
                if (text.length >= 35 && !text.startsWith("http") && texts.size < 30) texts.add(text.take(6000))
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        val candidates = texts.toList().filter { t -> texts.none { other -> other != t && other.contains(t) } }.take(12)
        if (candidates.isEmpty()) {
            val d = AlertDialog.Builder(this).setTitle("Post ka text nahi mila")
                .setMessage("X ne readable text share nahi kiya. Post ko poora kholo aur dobara icon dabao. Phir bhi na ho to post ka text copy/share karke Reply Bot mein paste karo. Ye photo/video scan nahi karta.")
                .setPositiveButton("Theek hai", null).create()
            d.window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY); dialog = d; d.setOnDismissListener { dialog = null; syncBubble() }; d.show(); return
        }
        val selected = BooleanArray(candidates.size)
        if (selected.size == 1) selected[0] = true
        val labels = candidates.map { if (it.length > 220) it.take(220) + "…" else it }.toTypedArray()
        val d = AlertDialog.Builder(this).setTitle("Kis text ka reply chahiye?")
            .setMultiChoiceItems(labels, selected) { _, which, checked -> selected[which] = checked }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Reply banao", null).create()
        d.window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        d.setOnDismissListener { dialog = null; syncBubble() }; dialog = d; d.show()
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val text = candidates.filterIndexed { index, _ -> selected[index] }.joinToString("\n\n").take(6000)
            if (text.isBlank()) { Toast.makeText(this, "Post ka text select karo", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            // The user chooses text before handing it to AI generation in our Activity.
            d.dismiss(); hideBubble()
            try {
                startActivity(Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_SEND; type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
                    putExtra("from_x_reader", true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
            } catch (_: Exception) { Toast.makeText(this, "Reply Bot app kholo aur post paste karo", Toast.LENGTH_LONG).show() }
        }
    }
}
