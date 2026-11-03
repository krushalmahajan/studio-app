package com.idbiintech.studio.attendance

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

/** Last 31 days: first and last punch per day (all sources). */
class HistoryActivity : Activity() {

    private lateinit var store: Store
    private lateinit var api: Api
    private lateinit var list: LinearLayout
    private lateinit var info: TextView
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        api = Api(store)
        buildUi()
        load()
    }

    private fun buildUi() {
        val root = Ui.vertical(this).apply { setBackgroundColor(Ui.BG) }
        val header = Ui.horizontal(this).apply { setBackgroundColor(Ui.ORANGE) }
        header.addView(Ui.linkButton(this, "‹ Back").apply { setOnClickListener { finish() } })
        header.addView(Ui.text(this, "Punch history", 19f, Color.WHITE, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(Ui.linkButton(this, "Refresh").apply { setOnClickListener { load() } })
        root.addView(header, Ui.matchWrap(this))

        val scroll = ScrollView(this)
        val content = Ui.vertical(this)
        val p = Ui.dp(this, 16)
        content.setPadding(p, Ui.dp(this, 12), p, p)
        scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        info = Ui.text(this, "", 13f, Ui.MUTED)
        content.addView(info, Ui.matchWrap(this, 0, 8))
        progress = ProgressBar(this)
        content.addView(progress, LinearLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 32)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = Ui.dp(this@HistoryActivity, 24)
        })
        list = Ui.card(this).apply { visibility = View.GONE }
        content.addView(list, Ui.matchWrap(this))

        setContentView(root)
        Ui.edgeToEdge(this, root) { top, bottom ->
            header.setPadding(Ui.dp(this, 4), top + Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 8))
            root.setPadding(0, 0, 0, bottom)
        }
    }

    private fun load() {
        progress.visibility = View.VISIBLE
        info.text = "Loading…"
        Bg.run({ api.call("history") }) { r ->
            if (isFinishing || isDestroyed) return@run
            progress.visibility = View.GONE
            if (!r.ok) {
                if (r.needsReactivation) {
                    store.clearSession()
                    startActivity(Intent(this, SetupActivity::class.java).putExtra(SetupActivity.EXTRA_MESSAGE, r.message))
                    finishAffinity()
                    return@run
                }
                info.text = r.message
                return@run
            }
            val days = r.data.optJSONArray("days")
            list.removeAllViews()
            val n = days?.length() ?: 0
            if (n == 0) {
                info.text = "No punches in the last 31 days."
                list.visibility = View.GONE
                return@run
            }
            info.text = "First and last punch of each day (app and biometric device). Final attendance status is in Studio → My Attendance."
            list.visibility = View.VISIBLE
            for (i in 0 until n) {
                val d = days!!.optJSONObject(i) ?: continue
                val row = Ui.horizontal(this).apply {
                    val v = Ui.dp(this@HistoryActivity, 10)
                    setPadding(0, v, 0, v)
                }
                val left = Ui.vertical(this)
                left.addView(Ui.text(this, d.optString("date_label"), 15f, Ui.TEXT, true))
                val count = d.optInt("count")
                val appCount = d.optInt("app_count")
                left.addView(Ui.text(this, "$count punch" + (if (count == 1) "" else "es") +
                        (if (appCount > 0) " · $appCount from app" else ""), 12f, Ui.MUTED))
                row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val right = Ui.vertical(this).apply { gravity = Gravity.END }
                val last = d.optString("last")
                right.addView(Ui.text(this, d.optString("first") + if (last.isNotEmpty()) " – $last" else "", 15f, Ui.TEXT).apply {
                    gravity = Gravity.END
                })
                val dur = d.optString("duration")
                right.addView(Ui.text(this, if (dur.isNotEmpty()) dur else "Single punch", 12f,
                    if (dur.isNotEmpty()) Ui.GREEN else Ui.ORANGE).apply { gravity = Gravity.END })
                row.addView(right)
                list.addView(row)
                if (i < n - 1) list.addView(View(this).apply { setBackgroundColor(Ui.LINE) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
            }
        }
    }
}
