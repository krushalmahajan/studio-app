package com.idbiintech.studio.attendance

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Colours and small view builders (the app builds its screens in code, no XML layouts). */
object Ui {
    val ORANGE = Color.parseColor("#F26F24")
    val ORANGE_DARK = Color.parseColor("#D95E17")
    val GREEN = Color.parseColor("#00A591")
    val GREEN_LIGHT = Color.parseColor("#E0F5F2")
    val RED = Color.parseColor("#D93025")
    val RED_LIGHT = Color.parseColor("#FCE8E6")
    val AMBER_LIGHT = Color.parseColor("#FFF4E5")
    val TEXT = Color.parseColor("#1F2328")
    val MUTED = Color.parseColor("#6B7280")
    val LINE = Color.parseColor("#E5E7EB")
    val BG = Color.parseColor("#F4F5F7")
    val DISABLED = Color.parseColor("#C7CBD1")

    fun dp(ctx: Context, v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    fun round(ctx: Context, fill: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(ctx, radiusDp).toFloat()
            if (stroke != null) setStroke(dp(ctx, 1), stroke)
        }

    fun text(ctx: Context, s: CharSequence = "", sp: Float = 15f, color: Int = TEXT, bold: Boolean = false): TextView =
        TextView(ctx).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    fun button(ctx: Context, label: String, fill: Int = ORANGE, textColor: Int = Color.WHITE, heightDp: Int = 48): Button =
        Button(ctx).apply {
            text = label
            isAllCaps = false
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            stateListAnimator = null
            minHeight = dp(ctx, heightDp)
            background = RippleDrawable(ColorStateList.valueOf(Color.parseColor("#33000000")), round(ctx, fill, 12), null)
        }

    fun setButtonColor(b: Button, fill: Int) {
        b.background = RippleDrawable(ColorStateList.valueOf(Color.parseColor("#33000000")), round(b.context, fill, 12), null)
    }

    fun linkButton(ctx: Context, label: String, color: Int = Color.WHITE): TextView =
        text(ctx, label, 14f, color, true).apply {
            val p = dp(ctx, 10)
            setPadding(p, p, p, p)
            isClickable = true
            isFocusable = true
            val attrs = intArrayOf(android.R.attr.selectableItemBackground)
            val ta = ctx.obtainStyledAttributes(attrs)
            background = ta.getDrawable(0)
            ta.recycle()
        }

    fun vertical(ctx: Context): LinearLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    fun horizontal(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun card(ctx: Context): LinearLayout = vertical(ctx).apply {
        background = round(ctx, Color.WHITE, 14, LINE)
        val p = dp(ctx, 16)
        setPadding(p, p, p, p)
    }

    fun matchWrap(ctx: Context, topDp: Int = 0, bottomDp: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(ctx, topDp)
            bottomMargin = dp(ctx, bottomDp)
        }

    fun divider(ctx: Context): View = View(ctx).apply {
        setBackgroundColor(LINE)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
            topMargin = dp(ctx, 10)
            bottomMargin = dp(ctx, 10)
        }
    }

    /**
     * Draw behind the status / navigation bars on every Android version (Android 15 forces this anyway)
     * and hand the bar sizes to [onInsets] so the screen can pad itself.
     */
    @Suppress("DEPRECATION")
    fun edgeToEdge(activity: Activity, root: View, onInsets: (top: Int, bottom: Int) -> Unit) {
        val window = activity.window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS or WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            val lightNav = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or lightNav)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) window.navigationBarColor = Color.BLACK
        }
        window.statusBarColor = Color.TRANSPARENT
        root.setOnApplyWindowInsetsListener { _, insets: WindowInsets ->
            val top: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                top = bars.top
                bottom = bars.bottom
            } else {
                top = insets.systemWindowInsetTop
                bottom = insets.systemWindowInsetBottom
            }
            onInsets(top, bottom)
            insets
        }
        root.requestApplyInsets()
    }
}
