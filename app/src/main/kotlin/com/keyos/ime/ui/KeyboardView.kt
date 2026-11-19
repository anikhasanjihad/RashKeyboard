package com.keyos.ime.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.SparseArray
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.keyos.engine.KeyDef
import com.keyos.engine.KeyType
import com.keyos.engine.KeyboardLayout
import com.keyos.engine.ShiftState
import kotlin.math.abs

/**
 * Gboard-style keyboard drawn on one Canvas: suggestion strip on top, keys below,
 * key-preview bubble, long-press alternates popup, slide on space bar to move the cursor.
 * No child views and no allocations on the touch path.
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onKey(key: KeyDef)
        fun onText(text: String)
        fun onCursorMove(direction: Int)
        fun onSuggestion(index: Int)
    }

    private class KeyRect(val key: KeyDef, val rect: RectF)

    private class Touch {
        var kr: KeyRect? = null
        var stripIndex = -1
        var lastX = 0f
        var spaceAcc = 0f
        var longPressed = false
        var slid = false
        var firedOnDown = false
    }

    var listener: Listener? = null
    var hapticEnabled = true

    private var layout: KeyboardLayout? = null
    private var shift = ShiftState.OFF
    private var keyRects: List<KeyRect> = emptyList()
    private var suggestions: List<String> = emptyList()
    private val touches = SparseArray<Touch>()

    // long-press popup state
    private var altTouch: Touch? = null
    private var altList: List<String> = emptyList()
    private val altRect = RectF()
    private var altCell = 0f
    private var altSelected = 0

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private fun dp(v: Float) = v * density
    private fun sp(v: Float) = v * scaledDensity

    private val stripH = dp(44f)
    private val padH = dp(3f)
    private val padV = dp(6f)
    private val gapH = dp(5f)
    private val gapV = dp(8f)
    private val radius = dp(8f)
    private val cursorStep = dp(14f)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1f) }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val fm = Paint.FontMetrics()
    private val tmp = RectF()

    private var cBg = 0
    private var cKey = 0
    private var cFunc = 0
    private var cPressed = 0
    private var cText = 0
    private var cAccent = 0
    private var cOnAccent = 0
    private var cPopup = 0
    private var cDivider = 0

    private val handler = Handler(Looper.getMainLooper())
    private var repeatTouch: Touch? = null
    private val repeatRunnable = object : Runnable {
        override fun run() {
            val k = repeatTouch?.kr?.key ?: return
            listener?.onKey(k)
            handler.postDelayed(this, REPEAT_INTERVAL_MS)
        }
    }
    private var longPressTouch: Touch? = null
    private val longPressRunnable = Runnable { showAlternates() }

    init {
        updateColors()
    }

    // ---- public API ---------------------------------------------------------------------

    fun setKeyboard(newLayout: KeyboardLayout, newShift: ShiftState) {
        if (newLayout !== layout) {
            cancelAllTouches()
            layout = newLayout
            computeRects()
        }
        shift = newShift
        invalidate()
    }

    fun setSuggestions(list: List<String>) {
        if (list == suggestions) return
        suggestions = list
        invalidate(0, 0, width, stripH.toInt() + 1)
    }

    // ---- sizing / colours ---------------------------------------------------------------

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateColors()
        requestLayout()
        invalidate()
    }

    private fun updateColors() {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        if (night) {
            cBg = 0xFF202124.toInt(); cKey = 0xFF3C4043.toInt(); cFunc = 0xFF2B2D30.toInt()
            cPressed = 0xFF5F6368.toInt(); cText = 0xFFE8EAED.toInt(); cAccent = 0xFF8AB4F8.toInt()
            cOnAccent = 0xFF202124.toInt(); cPopup = 0xFF3C4043.toInt(); cDivider = 0x33FFFFFF
        } else {
            cBg = 0xFFECEFF1.toInt(); cKey = 0xFFFFFFFF.toInt(); cFunc = 0xFFD7DBE0.toInt()
            cPressed = 0xFFC4C8CE.toInt(); cText = 0xFF202124.toInt(); cAccent = 0xFF1A73E8.toInt()
            cOnAccent = 0xFFFFFFFF.toInt(); cPopup = 0xFFFFFFFF.toInt(); cDivider = 0x33000000
        }
    }

    private fun rowHeight(rows: Int): Float {
        val base = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) dp(42f) else dp(52f)
        val maxTotal = resources.displayMetrics.heightPixels * 0.55f
        val units = rows - 1 + NUMBER_ROW_FACTOR
        return minOf(base, (maxTotal - stripH - 2 * padV) / units)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val rows = layout?.rows?.size ?: 5
        val h = stripH + 2 * padV + rowHeight(rows) * (rows - 1 + NUMBER_ROW_FACTOR)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h.toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = computeRects()

    private fun computeRects() {
        val l = layout ?: return
        val w = width
        if (w == 0) return
        val rowH = rowHeight(l.rows.size)
        val list = ArrayList<KeyRect>()
        var y = stripH + padV
        l.rows.forEachIndexed { r, row ->
            val h = if (r == 0) rowH * NUMBER_ROW_FACTOR else rowH
            val unit = (w - 2 * padH) / row.totalUnits
            var x = padH + row.sidePadding * unit
            for (key in row.keys) {
                val kw = unit * key.weight
                list.add(KeyRect(key, RectF(x + gapH / 2, y + gapV / 2, x + kw - gapH / 2, y + h - gapV / 2)))
                x += kw
            }
            y += h
        }
        keyRects = list
    }

    // ---- drawing ------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(cBg)
        drawStrip(canvas)
        for (kr in keyRects) drawKey(canvas, kr)
        for (i in 0 until touches.size()) {
            val t = touches.valueAt(i)
            val kr = t.kr ?: continue
            if (kr.key.type == KeyType.CHAR && t !== altTouch && !t.longPressed) drawPreview(canvas, kr)
        }
        if (altTouch != null) drawAlternates(canvas)
    }

    private fun drawStrip(canvas: Canvas) {
        val n = suggestions.size
        if (n == 0) return
        val cell = width.toFloat() / n
        for (i in 0 until n) {
            val left = i * cell
            if (isStripPressed(i)) {
                fill.color = cPressed
                canvas.drawRect(left, 0f, left + cell, stripH, fill)
            }
            if (i > 0) {
                fill.color = cDivider
                canvas.drawRect(left - dp(0.5f), stripH * 0.25f, left + dp(0.5f), stripH * 0.75f, fill)
            }
            val text = suggestions[i]
            textPaint.color = cText
            textPaint.alpha = 255
            textPaint.textSize = sp(17f)
            val avail = cell - dp(16f)
            val tw = textPaint.measureText(text)
            if (tw > avail) textPaint.textSize = textPaint.textSize * avail / tw
            textPaint.getFontMetrics(fm)
            canvas.drawText(text, left + cell / 2, stripH / 2 - (fm.ascent + fm.descent) / 2, textPaint)
        }
    }

    private fun isStripPressed(i: Int): Boolean {
        for (k in 0 until touches.size()) if (touches.valueAt(k).stripIndex == i) return true
        return false
    }

    private fun isPressed(kr: KeyRect): Boolean {
        for (k in 0 until touches.size()) if (touches.valueAt(k).kr === kr) return true
        return false
    }

    private fun drawKey(canvas: Canvas, kr: KeyRect) {
        val key = kr.key
        val pressed = isPressed(kr)
        val isEnter = key.type == KeyType.ENTER
        val isCaps = key.type == KeyType.SHIFT && shift == ShiftState.CAPS_LOCK
        val special = key.type != KeyType.CHAR && key.type != KeyType.SPACE
        fill.color = when {
            isEnter || isCaps -> cAccent
            pressed -> cPressed
            special -> cFunc
            else -> cKey
        }
        canvas.drawRoundRect(kr.rect, radius, radius, fill)
        if (pressed && (isEnter || isCaps)) {
            fill.color = 0x33000000
            canvas.drawRoundRect(kr.rect, radius, radius, fill)
        }

        val label = when (key.type) {
            KeyType.CHAR -> key.textFor(shift)
            KeyType.SHIFT -> if (shift == ShiftState.CAPS_LOCK) "⇪" else "⇧"
            KeyType.BACKSPACE -> "⌫"
            KeyType.ENTER -> "⏎"
            KeyType.SYMBOLS -> "?123"
            KeyType.ALPHA -> key.label
            KeyType.EXTRAS -> "অআ"
            KeyType.LANGUAGE -> "🌐"
            KeyType.SPACE -> key.label
        }
        if (label.isEmpty()) return
        textPaint.color = when {
            isEnter || isCaps -> cOnAccent
            key.type == KeyType.SHIFT && shift == ShiftState.ONCE -> cAccent
            else -> cText
        }
        textPaint.alpha = if (key.type == KeyType.SPACE) 150 else 255
        textPaint.textSize = when (key.type) {
            KeyType.CHAR -> sp(22f)
            KeyType.SPACE -> sp(13f)
            KeyType.SYMBOLS, KeyType.ALPHA, KeyType.EXTRAS -> sp(15f)
            else -> sp(20f)
        }
        textPaint.getFontMetrics(fm)
        canvas.drawText(label, kr.rect.centerX(), kr.rect.centerY() - (fm.ascent + fm.descent) / 2f, textPaint)
    }

    private fun drawPreview(canvas: Canvas, kr: KeyRect) {
        val text = kr.key.textFor(shift)
        if (text.isBlank()) return
        val r = kr.rect
        val w = maxOf(r.width() + dp(12f), dp(52f))
        val h = dp(58f)
        val left = (r.centerX() - w / 2).coerceIn(dp(2f), width - w - dp(2f))
        val top = maxOf(dp(2f), r.top - h - dp(2f))
        tmp.set(left, top, left + w, top + h)
        fill.color = cPopup
        canvas.drawRoundRect(tmp, radius * 1.5f, radius * 1.5f, fill)
        stroke.color = cDivider
        canvas.drawRoundRect(tmp, radius * 1.5f, radius * 1.5f, stroke)
        textPaint.color = cText
        textPaint.alpha = 255
        textPaint.textSize = sp(30f)
        textPaint.getFontMetrics(fm)
        canvas.drawText(text, tmp.centerX(), tmp.centerY() - (fm.ascent + fm.descent) / 2f, textPaint)
    }

    private fun drawAlternates(canvas: Canvas) {
        fill.color = cPopup
        canvas.drawRoundRect(altRect, radius * 1.5f, radius * 1.5f, fill)
        stroke.color = cDivider
        canvas.drawRoundRect(altRect, radius * 1.5f, radius * 1.5f, stroke)
        textPaint.textSize = sp(22f)
        textPaint.getFontMetrics(fm)
        for (i in altList.indices) {
            val left = altRect.left + i * altCell
            if (i == altSelected) {
                fill.color = cAccent
                tmp.set(left + dp(2f), altRect.top + dp(3f), left + altCell - dp(2f), altRect.bottom - dp(3f))
                canvas.drawRoundRect(tmp, radius, radius, fill)
                textPaint.color = cOnAccent
            } else {
                textPaint.color = cText
            }
            textPaint.alpha = 255
            canvas.drawText(
                altList[i], left + altCell / 2,
                altRect.centerY() - (fm.ascent + fm.descent) / 2f, textPaint,
            )
        }
    }

    // ---- touch --------------------------------------------------------------------------

    private fun findKey(x: Float, y: Float): KeyRect? {
        for (kr in keyRects) if (kr.rect.contains(x, y)) return kr
        var best: KeyRect? = null
        var bestD = Float.MAX_VALUE
        for (kr in keyRects) {
            if (y < kr.rect.top - gapV || y > kr.rect.bottom + gapV) continue
            val dx = if (x < kr.rect.left) kr.rect.left - x else if (x > kr.rect.right) x - kr.rect.right else 0f
            if (dx < bestD) { bestD = dx; best = kr }
        }
        return if (bestD <= gapH) best else null
    }

    private fun stripIndexAt(x: Float): Int {
        val n = suggestions.size
        if (n == 0) return -1
        return (x / (width.toFloat() / n)).toInt().coerceIn(0, n - 1)
    }

    private fun haptic() {
        if (hapticEnabled) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = e.actionIndex
                val x = e.getX(idx)
                val y = e.getY(idx)
                val t = Touch()
                t.lastX = x
                if (y < stripH) {
                    t.stripIndex = stripIndexAt(x)
                    if (t.stripIndex < 0) return true
                    touches.put(e.getPointerId(idx), t)
                } else {
                    val kr = findKey(x, y) ?: return true
                    t.kr = kr
                    touches.put(e.getPointerId(idx), t)
                    onKeyDown(t, kr.key)
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val t = touches.get(e.getPointerId(i)) ?: continue
                    onMove(t, e.getX(i), e.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = e.getPointerId(e.actionIndex)
                val t = touches.get(id)
                if (t != null) {
                    touches.remove(id)
                    onUp(t, e.getX(e.actionIndex))
                    invalidate()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelAllTouches()
                invalidate()
            }
        }
        return true
    }

    private fun onKeyDown(t: Touch, key: KeyDef) {
        haptic()
        when {
            key.type == KeyType.SPACE -> Unit // committed on release unless the finger slides
            key.type == KeyType.CHAR && key.alts.isNotEmpty() -> {
                longPressTouch = t
                handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
            }
            else -> {
                t.firedOnDown = true
                listener?.onKey(key)
                if (key.type == KeyType.BACKSPACE) {
                    stopRepeat()
                    repeatTouch = t
                    handler.postDelayed(repeatRunnable, REPEAT_DELAY_MS)
                }
            }
        }
    }

    private fun onMove(t: Touch, x: Float, y: Float) {
        if (t.stripIndex >= 0) return
        val kr = t.kr ?: return
        if (t === altTouch) {
            val sel = ((x - altRect.left) / altCell).toInt().coerceIn(0, altList.size - 1)
            if (sel != altSelected) { altSelected = sel; invalidate() }
            return
        }
        when (kr.key.type) {
            KeyType.SPACE -> {
                t.spaceAcc += x - t.lastX
                t.lastX = x
                if (abs(t.spaceAcc) >= cursorStep) {
                    val steps = (t.spaceAcc / cursorStep).toInt()
                    val dir = if (steps > 0) 1 else -1
                    repeat(abs(steps)) { listener?.onCursorMove(dir) }
                    t.spaceAcc -= steps * cursorStep
                    t.slid = true
                }
            }
            KeyType.BACKSPACE -> if (t === repeatTouch && !kr.rect.contains(x, y)) stopRepeat()
            else -> if (t === longPressTouch && !kr.rect.contains(x, y)) cancelLongPress()
        }
    }

    private fun onUp(t: Touch, x: Float) {
        if (t.stripIndex >= 0) {
            if (stripIndexAt(x) == t.stripIndex) listener?.onSuggestion(t.stripIndex)
            return
        }
        if (t === longPressTouch) cancelLongPress()
        if (t === repeatTouch) stopRepeat()
        if (t === altTouch) {
            val choice = altList.getOrNull(altSelected)
            altTouch = null
            if (choice != null) listener?.onText(choice)
            return
        }
        val key = t.kr?.key ?: return
        when {
            key.type == KeyType.SPACE -> if (!t.slid) listener?.onKey(key)
            !t.firedOnDown && !t.longPressed -> listener?.onKey(key)
        }
    }

    private fun showAlternates() {
        val t = longPressTouch ?: return
        longPressTouch = null
        val kr = t.kr ?: return
        val alts = kr.key.altsFor(shift)
        if (alts.isEmpty()) return
        var cell = maxOf(dp(42f), kr.rect.width())
        val maxTotal = width - dp(8f)
        if (cell * alts.size > maxTotal) cell = maxTotal / alts.size
        val total = cell * alts.size
        val left = (kr.rect.centerX() - total / 2).coerceIn(dp(4f), width - total - dp(4f))
        val h = dp(52f)
        val top = maxOf(dp(2f), kr.rect.top - h - dp(6f))
        altRect.set(left, top, left + total, top + h)
        altCell = cell
        altList = alts
        altSelected = ((kr.rect.centerX() - left) / cell).toInt().coerceIn(0, alts.size - 1)
        altTouch = t
        t.longPressed = true
        haptic()
        invalidate()
    }

    private fun cancelLongPress() {
        handler.removeCallbacks(longPressRunnable)
        longPressTouch = null
    }

    private fun stopRepeat() {
        handler.removeCallbacks(repeatRunnable)
        repeatTouch = null
    }

    private fun cancelAllTouches() {
        cancelLongPress()
        stopRepeat()
        altTouch = null
        touches.clear()
    }

    override fun onDetachedFromWindow() {
        cancelAllTouches()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val NUMBER_ROW_FACTOR = 0.85f
        const val REPEAT_DELAY_MS = 400L
        const val REPEAT_INTERVAL_MS = 45L
        const val LONG_PRESS_MS = 350L
    }
}
