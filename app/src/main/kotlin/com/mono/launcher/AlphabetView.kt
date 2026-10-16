package com.mono.launcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.exp
import kotlin.math.min

/**
 * The A-Z scrubber: drag a finger along the edge and the letters bulge out in a wave
 * under it while the list jumps to that section (with a haptic tick per letter).
 */
class AlphabetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val letters: List<String> = listOf("#") + ('A'..'Z').map { it.toString() }

    var onLetterSelected: ((String) -> Unit)? = null

    private var active: Set<String> = emptySet()
    private var accent = DEFAULT_ACCENT
    private var inactive = 0x80FFFFFF.toInt()
    private var onRight = true
    private var strength = 1f
    private var haptics = true

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val baseTextPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
    private val bulgePx = dp(50).toFloat()
    private val edgePadPx = dp(18).toFloat()
    private val touchZonePx = dp(60).toFloat()

    private var touchY = -1f
    private var wave = 0f
    private var lastIndex = -1
    private var animator: ValueAnimator? = null

    fun configure(accent: Int, inactive: Int, onRight: Boolean, wave: Float, haptics: Boolean) {
        this.accent = accent
        this.inactive = inactive
        this.onRight = onRight
        this.strength = wave
        this.haptics = haptics
        invalidate()
    }

    fun setActiveLetters(set: Set<String>) {
        active = set
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        if (h <= 0f) return
        val cell = h / letters.size
        val baseSize = min(baseTextPx, cell * 0.62f)
        val x0 = if (onRight) width - edgePadPx else edgePadPx
        val direction = if (onRight) -1f else 1f
        val sigma = cell * 3.4f

        for (i in letters.indices) {
            val cy = cell * (i + 0.5f)
            var dx = 0f
            var scale = 1f
            if (wave > 0f) {
                val d = (cy - touchY) / sigma
                val g = exp(-d * d)
                dx = direction * bulgePx * strength * g * wave
                scale = 1f + 0.85f * strength * g * wave
            }
            val isActive = letters[i] in active
            paint.color = if (isActive) accent else inactive
            paint.isFakeBoldText = wave > 0f && i == lastIndex
            paint.textSize = baseSize * scale
            val y = cy - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(letters[i], x0 + dx, y, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val inZone = if (onRight) event.x >= width - touchZonePx else event.x <= touchZonePx
                if (!inZone) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                animateWave(1f)
                select(event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                select(event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                animateWave(0f)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun select(y: Float) {
        touchY = y.coerceIn(0f, height.toFloat())
        val cell = height.toFloat() / letters.size
        val idx = (touchY / cell).toInt().coerceIn(0, letters.lastIndex)
        if (idx != lastIndex) {
            lastIndex = idx
            if (haptics) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onLetterSelected?.invoke(letters[idx])
        }
        invalidate()
    }

    private fun animateWave(target: Float) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(wave, target).apply {
            duration = if (target > 0f) 110L else 240L
            addUpdateListener {
                wave = it.animatedValue as Float
                invalidate()
            }
            if (target == 0f) {
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (wave == 0f) {
                            touchY = -1f
                            lastIndex = -1
                        }
                    }
                })
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
