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

/**
 * Canvas-drawn keyboard. One View, no child views, no allocations while typing:
 * keys fire on ACTION_DOWN (zero-lag), only the touched key's rect is invalidated.
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onKey(key: KeyDef)
    }

    private class KeyRect(val key: KeyDef, val rect: RectF)

    var listener: Listener? = null
    var hapticEnabled = true

    private var layout: KeyboardLayout? = null
    private var shift = ShiftState.OFF
    private var keyRects: List<KeyRect> = emptyList()
    private val pressed = SparseArray<KeyRect>()

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private fun dp(v: Float) = v * density
    private fun sp(v: Float) = v * scaledDensity

    private val padH = dp(3f)
    private val padV = dp(6f)
    private val gapH = dp(5f)
    private val gapV = dp(8f)
    private val radius = dp(8f)

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val fm = Paint.FontMetrics()

    private var cBackground = 0
    private var cKey = 0
    private var cSpecial = 0
    private var cPressed = 0
    private var cText = 0

    private val handler = Handler(Looper.getMainLooper())
    private var repeatPointer = -1
    private var repeatKey: KeyDef? = null
    private val repeatRunnable = object : Runnable {
        override fun run() {
            val k = repeatKey ?: return
            listener?.onKey(k)
            handler.postDelayed(this, REPEAT_INTERVAL_MS)
        }
    }

    init {
        updateColors()
    }

    fun setKeyboard(newLayout: KeyboardLayout, newShift: ShiftState) {
        val layoutChanged = newLayout !== layout
        if (layoutChanged) {
            stopRepeat()
            pressed.clear()
            layout = newLayout
            computeRects()
        }
        shift = newShift
        invalidate()
    }

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
            cBackground = 0xFF1B1B1F.toInt(); cKey = 0xFF3A3A3F.toInt(); cSpecial = 0xFF2A2A2E.toInt()
            cPressed = 0xFF5A5A62.toInt(); cText = 0xFFFFFFFF.toInt()
        } else {
            cBackground = 0xFFD1D4DB.toInt(); cKey = 0xFFFFFFFF.toInt(); cSpecial = 0xFFAEB3BE.toInt()
            cPressed = 0xFFB4B8C2.toInt(); cText = 0xFF000000.toInt()
        }
    }

    private fun rowHeight(rows: Int): Float {
        val base = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) dp(44f) else dp(54f)
        val maxTotal = resources.displayMetrics.heightPixels * 0.5f
        return minOf(base, (maxTotal - 2 * padV) / rows)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val rows = layout?.rows?.size ?: 5
        val h = (rows * rowHeight(rows) + 2 * padV).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        computeRects()
    }

    private fun computeRects() {
        val l = layout ?: return
        val w = width
        if (w == 0) return
        val rowH = rowHeight(l.rows.size)
        val list = ArrayList<KeyRect>()
        l.rows.forEachIndexed { r, row ->
            val unit = (w - 2 * padH) / row.totalUnits
            var x = padH + row.sidePadding * unit
            val y = padV + r * rowH
            for (key in row.keys) {
                val kw = unit * key.weight
                list.add(KeyRect(key, RectF(x + gapH / 2, y + gapV / 2, x + kw - gapH / 2, y + rowH - gapV / 2)))
                x += kw
            }
        }
        keyRects = list
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(cBackground)
        for (kr in keyRects) {
            val special = kr.key.type != KeyType.CHAR && kr.key.type != KeyType.SPACE
            keyPaint.color = when {
                isPressed(kr) -> cPressed
                special -> cSpecial
                else -> cKey
            }
            canvas.drawRoundRect(kr.rect, radius, radius, keyPaint)
            drawLabel(canvas, kr)
        }
    }

    private fun isPressed(kr: KeyRect) = pressed.indexOfValue(kr) >= 0

    private fun drawLabel(canvas: Canvas, kr: KeyRect) {
        val key = kr.key
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
        textPaint.color = cText
        textPaint.alpha = if (key.type == KeyType.SPACE) 150 else 255
        textPaint.textSize = when (key.type) {
            KeyType.CHAR -> sp(22f)
            KeyType.SPACE -> sp(13f)
            KeyType.SYMBOLS, KeyType.ALPHA, KeyType.EXTRAS -> sp(15f)
            else -> sp(20f)
        }
        textPaint.getFontMetrics(fm)
        val y = kr.rect.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText(label, kr.rect.centerX(), y, textPaint)
    }

    private fun findKey(x: Float, y: Float): KeyRect? {
        for (kr in keyRects) if (kr.rect.contains(x, y)) return kr
        // Tap landed in a gap: pick the nearest key in the same row band.
        var best: KeyRect? = null
        var bestD = Float.MAX_VALUE
        for (kr in keyRects) {
            if (y < kr.rect.top - gapV || y > kr.rect.bottom + gapV) continue
            val dx = if (x < kr.rect.left) kr.rect.left - x else if (x > kr.rect.right) x - kr.rect.right else 0f
            if (dx < bestD) { bestD = dx; best = kr }
        }
        return if (bestD <= gapH) best else null
    }

    private fun invalidateKey(kr: KeyRect) {
        val r = kr.rect
        invalidate(r.left.toInt() - 1, r.top.toInt() - 1, r.right.toInt() + 1, r.bottom.toInt() + 1)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = e.actionIndex
                val id = e.getPointerId(idx)
                val kr = findKey(e.getX(idx), e.getY(idx)) ?: return true
                pressed.put(id, kr)
                invalidateKey(kr)
                if (hapticEnabled) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                listener?.onKey(kr.key)
                if (kr.key.type == KeyType.BACKSPACE) startRepeat(id, kr.key)
            }
            MotionEvent.ACTION_MOVE -> {
                if (repeatPointer != -1) {
                    val idx = e.findPointerIndex(repeatPointer)
                    val kr = pressed.get(repeatPointer)
                    if (idx < 0 || kr == null || !kr.rect.contains(e.getX(idx), e.getY(idx))) stopRepeat()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> release(e.getPointerId(e.actionIndex))
            MotionEvent.ACTION_CANCEL -> {
                stopRepeat()
                for (i in 0 until pressed.size()) invalidateKey(pressed.valueAt(i))
                pressed.clear()
            }
        }
        return true
    }

    private fun release(pointerId: Int) {
        val kr = pressed.get(pointerId) ?: return
        pressed.remove(pointerId)
        if (pointerId == repeatPointer) stopRepeat()
        invalidateKey(kr)
    }

    private fun startRepeat(pointerId: Int, key: KeyDef) {
        stopRepeat()
        repeatPointer = pointerId
        repeatKey = key
        handler.postDelayed(repeatRunnable, REPEAT_DELAY_MS)
    }

    private fun stopRepeat() {
        handler.removeCallbacks(repeatRunnable)
        repeatPointer = -1
        repeatKey = null
    }

    override fun onDetachedFromWindow() {
        stopRepeat()
        pressed.clear()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val REPEAT_DELAY_MS = 400L
        const val REPEAT_INTERVAL_MS = 45L
    }
}
