package com.mono.launcher

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mono.launcher.databinding.ItemAppBinding
import com.mono.launcher.databinding.ItemHeaderBinding

sealed interface Row {
    val key: String

    data class Header(val letter: String) : Row {
        override val key: String get() = "h:$letter"
    }

    data class App(val app: AppInfo, val notifCount: Int = 0, val subtitle: String? = null) : Row {
        override val key: String get() = "a:${app.id}"
    }

    data class Web(val query: String) : Row {
        override val key: String get() = "web"
    }
}

/** Everything the rows need to draw themselves; swapping it restyles the list live. */
data class RowStyle(
    val sizeSp: Float,
    val typeface: Typeface,
    val text: Int,
    val sub: Int,
    val accent: Int,
    val padV: Int,
    val gravity: Int
)

private val DIFF = object : DiffUtil.ItemCallback<Row>() {
    override fun areItemsTheSame(a: Row, b: Row): Boolean = a.key == b.key

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(a: Row, b: Row): Boolean = a == b
}

class RowAdapter(
    private val showSubtitle: Boolean,
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (AppInfo, View) -> Unit,
    private val onSubtitleClick: (AppInfo) -> Unit,
    private val onWebSearch: (String) -> Unit
) : ListAdapter<Row, RecyclerView.ViewHolder>(DIFF) {

    var style: RowStyle = RowStyle(22f, Typeface.DEFAULT, 0xFFFFFFFF.toInt(), 0xB3FFFFFF.toInt(), DEFAULT_ACCENT, 20, 0)
        set(value) {
            if (field != value) {
                field = value
                notifyItemRangeChanged(0, itemCount)
            }
        }

    override fun getItemViewType(position: Int): Int =
        if (getItem(position) is Row.Header) TYPE_HEADER else TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(ItemHeaderBinding.inflate(inflater, parent, false))
        } else {
            ItemVH(ItemAppBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is Row.Header -> (holder as HeaderVH).bind(row)
            is Row.App -> (holder as ItemVH).bindApp(row)
            is Row.Web -> (holder as ItemVH).bindWeb(row)
        }
    }

    fun positionOfHeader(letter: String): Int =
        currentList.indexOfFirst { it is Row.Header && it.letter == letter }

    private inner class HeaderVH(private val b: ItemHeaderBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: Row.Header) {
            val s = style
            b.letter.text = row.letter
            b.letter.setTextColor(s.accent)
            b.letter.gravity = s.gravity
        }
    }

    private inner class ItemVH(private val b: ItemAppBinding) : RecyclerView.ViewHolder(b.root) {
        init {
            b.name.compoundDrawablePadding = b.root.dp(10)
        }

        private fun applyStyle(s: RowStyle) {
            b.name.setTextSize(TypedValue.COMPLEX_UNIT_SP, s.sizeSp)
            b.name.typeface = s.typeface
            b.name.setTextColor(s.text)
            b.subtitle.typeface = s.typeface
            b.subtitle.setTextColor(s.sub)
            b.root.gravity = s.gravity
            b.root.setPadding(b.root.paddingLeft, s.padV, b.root.paddingRight, s.padV)
        }

        fun bindApp(row: Row.App) {
            val s = style
            applyStyle(s)
            val app = row.app
            b.name.text = app.label
            b.name.setCompoundDrawablesRelativeWithIntrinsicBounds(
                0, 0, if (row.notifCount > 0) R.drawable.notif_dot else 0, 0
            )
            b.name.compoundDrawableTintList = ColorStateList.valueOf(s.accent)

            val sub = row.subtitle
            if (showSubtitle && !sub.isNullOrEmpty()) {
                b.subtitle.visibility = View.VISIBLE
                b.subtitle.text = sub
                b.subtitle.setOnClickListener { onSubtitleClick(app) }
            } else {
                b.subtitle.visibility = View.GONE
                b.subtitle.setOnClickListener(null)
                b.subtitle.isClickable = false
            }

            b.root.setOnClickListener { onClick(app) }
            b.root.setOnLongClickListener {
                onLongClick(app, b.root)
                true
            }
        }

        fun bindWeb(row: Row.Web) {
            applyStyle(style)
            b.name.text = b.root.context.getString(R.string.search_web, row.query)
            b.name.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
            b.subtitle.visibility = View.GONE
            b.root.setOnClickListener { onWebSearch(row.query) }
            b.root.setOnLongClickListener(null)
            b.root.isLongClickable = false
        }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
    }
}
