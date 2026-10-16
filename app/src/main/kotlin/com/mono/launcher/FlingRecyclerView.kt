package com.mono.launcher

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView

/** RecyclerView with an adjustable fling speed, so the scroll "weight" can be tuned in Customize. */
class FlingRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    var flingMultiplier: Float = 1f

    override fun fling(velocityX: Int, velocityY: Int): Boolean =
        super.fling((velocityX * flingMultiplier).toInt(), (velocityY * flingMultiplier).toInt())
}
