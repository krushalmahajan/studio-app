package com.idbiintech.studio.attendance

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Punch screen: today's punches, live geofence status and the Punch In / Punch Out button. */
class MainActivity : Activity() {

    private lateinit var store: Store
    private lateinit var api: Api
    private lateinit var tracker: LocationTracker
    private val handler = Handler(Looper.getMainLooper())

    // views
    private lateinit var dateText: TextView
    private lateinit var banner: LinearLayout
    private lateinit var bannerText: TextView
    private lateinit var bannerButton: Button
    private lateinit var todayList: LinearLayout
    private lateinit var todaySummary: TextView
    private lateinit var fenceText: TextView
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var statusDetail: TextView
    private lateinit var punchButton: Button
    private lateinit var punchHint: TextView
    private lateinit var progress: ProgressBar

    // state
    private var homeLoaded = false
    private var loadingHome = false
    private var punching = false
    private var fence: Fence? = null
    private var fenceError = ""
    private var nextAction = "in"
    private var maxAccuracy = 100f
    private var maxFixAge = 120L
    private var blockMock = true
    private var askedPermission = false
    private var lastHomeLoad = 0L

    private class Fence(val name: String, val lat: Double, val lng: Double, val radius: Double)

    private val ticker = object : Runnable {
        override fun run() {
            updateLocationUi()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        if (!store.isActivated) {
            openSetup(null)
            return
        }
        api = Api(store)
        tracker = LocationTracker(this) { updateLocationUi() }
        buildUi()
        intent.getStringExtra(EXTRA_WELCOME)?.let { showBanner(it, Ui.GREEN_LIGHT, Ui.GREEN, null, "msg", null) }
    }

    override fun onResume() {
        super.onResume()
        if (!::tracker.isInitialized) return
        dateText.text = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.ENGLISH).format(Date())
        startLocation()
        if (!homeLoaded || System.currentTimeMillis() - lastHomeLoad > 60_000) loadHome()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        if (!::tracker.isInitialized) return
        tracker.stop()
        handler.removeCallbacks(ticker)
    }

    // ------------------------------------------------------------------ UI

    private fun buildUi() {
        val root = Ui.vertical(this).apply { setBackgroundColor(Ui.BG) }

        // Header
        val header = Ui.vertical(this).apply { setBackgroundColor(Ui.ORANGE) }
        val headerRow = Ui.horizontal(this)
        val who = Ui.vertical(this)
        who.addView(Ui.text(this, store.employeeName.ifBlank { "Studio Attendance" }, 19f, Color.WHITE, true))
        who.addView(Ui.text(this, store.employeeCode, 13f, Color.parseColor("#FFE7DA")))
        headerRow.addView(who, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val history = Ui.linkButton(this, "History").apply {
            setOnClickListener { startActivity(Intent(this@MainActivity, HistoryActivity::class.java)) }
        }
        val signOut = Ui.linkButton(this, "Sign out").apply { setOnClickListener { confirmSignOut() } }
        headerRow.addView(history)
        headerRow.addView(signOut)
        header.addView(headerRow, Ui.matchWrap(this))
        dateText = Ui.text(this, "", 13f, Color.WHITE)
        header.addView(dateText, Ui.matchWrap(this, 2))
        val hp = Ui.dp(this, 16)
        root.addView(header, Ui.matchWrap(this))

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val content = Ui.vertical(this)
        val cp = Ui.dp(this, 16)
        content.setPadding(cp, Ui.dp(this, 12), cp, cp)
        scroll.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Banner (messages / permission actions)
        banner = Ui.vertical(this).apply {
            visibility = View.GONE
            val p = Ui.dp(this@MainActivity, 12)
            setPadding(p, p, p, p)
        }
        bannerText = Ui.text(this, "", 14f)
        bannerButton = Ui.button(this, "", Ui.ORANGE, Color.WHITE, 40).apply { visibility = View.GONE }
        banner.addView(bannerText)
        banner.addView(bannerButton, Ui.matchWrap(this, 10))
        content.addView(banner, Ui.matchWrap(this, 0, 12))

        // Location card
        val locCard = Ui.card(this)
        locCard.addView(Ui.text(this, "OFFICE AREA", 12f, Ui.MUTED, true))
        fenceText = Ui.text(this, "Loading…", 16f, Ui.TEXT, true)
        locCard.addView(fenceText, Ui.matchWrap(this, 4))
        locCard.addView(Ui.divider(this))
        val statusRow = Ui.horizontal(this)
        statusDot = View(this).apply { background = Ui.round(this@MainActivity, Ui.DISABLED, 6) }
        statusRow.addView(statusDot, LinearLayout.LayoutParams(Ui.dp(this, 12), Ui.dp(this, 12)).apply {
            rightMargin = Ui.dp(this@MainActivity, 10)
        })
        statusText = Ui.text(this, "Finding your location…", 16f, Ui.TEXT, true)
        statusRow.addView(statusText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        locCard.addView(statusRow)
        statusDetail = Ui.text(this, "", 13f, Ui.MUTED)
        locCard.addView(statusDetail, Ui.matchWrap(this, 4).apply { leftMargin = Ui.dp(this@MainActivity, 22) })
        content.addView(locCard, Ui.matchWrap(this))

        // Punch button
        punchButton = Ui.button(this, "Punch In", Ui.GREEN, Color.WHITE, 64).apply {
            textSize = 20f
            setOnClickListener { punch() }
        }
        content.addView(punchButton, Ui.matchWrap(this, 16))
        punchHint = Ui.text(this, "", 13f, Ui.MUTED).apply { gravity = Gravity.CENTER }
        content.addView(punchHint, Ui.matchWrap(this, 8))
        progress = ProgressBar(this).apply { visibility = View.GONE }
        content.addView(progress, LinearLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 32)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = Ui.dp(this@MainActivity, 8)
        })

        // Today card
        val todayCard = Ui.card(this)
        val todayHead = Ui.horizontal(this)
        todayHead.addView(Ui.text(this, "TODAY'S PUNCHES", 12f, Ui.MUTED, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        todayHead.addView(Ui.linkButton(this, "Refresh", Ui.ORANGE).apply {
            setOnClickListener { loadHome() }
        })
        todayCard.addView(todayHead)
        todaySummary = Ui.text(this, "", 14f, Ui.MUTED)
        todayCard.addView(todaySummary)
        todayList = Ui.vertical(this)
        todayCard.addView(todayList, Ui.matchWrap(this, 6))
        content.addView(todayCard, Ui.matchWrap(this, 16))

        content.addView(Ui.text(this, "App punches appear in Studio → My Attendance with your biometric punches.\nVersion ${BuildConfig.VERSION_NAME}",
            12f, Ui.MUTED).apply { gravity = Gravity.CENTER }, Ui.matchWrap(this, 16))

        setContentView(root)
        Ui.edgeToEdge(this, root) { top, bottom ->
            header.setPadding(hp, top + Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 14))
            root.setPadding(0, 0, 0, bottom)
        }
    }

    private fun showBanner(msg: String, bg: Int, fg: Int, action: String?, kind: String = "msg", onAction: (() -> Unit)?) {
        bannerKind = kind
        banner.background = Ui.round(this, bg, 12)
        bannerText.text = msg
        bannerText.setTextColor(fg)
        if (action != null && onAction != null) {
            bannerButton.text = action
            bannerButton.visibility = View.VISIBLE
            bannerButton.setOnClickListener { onAction() }
        } else {
            bannerButton.visibility = View.GONE
        }
        banner.visibility = View.VISIBLE
    }

    private fun hideBanner() {
        banner.visibility = View.GONE
    }

    // ------------------------------------------------------------------ location

    private fun startLocation() {
        if (!tracker.hasPermission()) {
            tracker.stop()
            return
        }
        if (tracker.isLocationOn()) tracker.start()
    }

    private fun requestLocationPermission() {
        val canAsk = !askedPermission ||
                shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
        if (canAsk && !tracker.hasCoarseOnly()) {
            askedPermission = true
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
        } else {
            openAppSettings()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION) {
            startLocation()
            updateLocationUi()
        }
    }

    private fun openAppSettings() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun openLocationSettings() {
        try {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun setStatus(dot: Int, title: String, detail: String, titleColor: Int = Ui.TEXT) {
        statusDot.background = Ui.round(this, dot, 6)
        statusText.text = title
        statusText.setTextColor(titleColor)
        statusDetail.text = detail
    }

    /** Re-evaluates everything shown about location and whether the button may be pressed. */
    private fun updateLocationUi() {
        if (!::punchButton.isInitialized) return
        var canPunch = false
        var hint = ""
        val loc: Location? = tracker.best
        val f = fence

        when {
            !tracker.hasPermission() -> {
                val coarse = tracker.hasCoarseOnly()
                setStatus(Ui.RED, "Location permission needed", if (coarse) "Turn on “Precise location” for this app." else "Allow location so the app can check you are in the office.")
                showBanner(
                    if (coarse) "Precise location is off for this app. Open settings → Permissions → Location and turn on “Use precise location”."
                    else "Studio Attendance needs your location only while this screen is open, to check that you are inside the office area.",
                    Ui.AMBER_LIGHT, Ui.TEXT, if (coarse) "Open settings" else "Allow location", "perm"
                ) { requestLocationPermission() }
                hint = "Location permission is required to punch."
            }
            !tracker.isLocationOn() -> {
                setStatus(Ui.RED, "Location is turned off", "Turn on location (GPS) on your phone.")
                showBanner("Location is turned off on this phone.", Ui.AMBER_LIGHT, Ui.TEXT, "Turn on location", "perm") { openLocationSettings() }
                hint = "Turn on location to punch."
            }
            !homeLoaded -> {
                if (bannerIsPermission()) hideBanner()
                tracker.start()
                setStatus(Ui.DISABLED, "Connecting to Studio…", "")
                hint = ""
            }
            f == null -> {
                if (bannerIsPermission()) hideBanner()
                setStatus(Ui.RED, "No office area assigned", fenceError.ifBlank { "Please contact the Attendance Admin." })
                hint = "You cannot punch until an office area is assigned."
            }
            loc == null -> {
                if (bannerIsPermission()) hideBanner()
                tracker.start()
                setStatus(Ui.DISABLED, "Finding your location…", "Stay near a window or step outside if this takes long.")
                hint = "Waiting for location…"
            }
            else -> {
                if (bannerIsPermission()) hideBanner()
                val age = LocationTracker.ageSeconds(loc)
                val acc = loc.accuracy
                val dist = LocationTracker.distanceM(loc.latitude, loc.longitude, f.lat, f.lng)
                val accText = "Accuracy ±${Math.round(acc)} m"
                when {
                    blockMock && LocationTracker.isMock(loc) -> {
                        setStatus(Ui.RED, "Fake location detected", "Turn off any fake-GPS / mock-location app.", Ui.RED)
                        hint = "Punch is blocked while a fake-location app is active."
                    }
                    age > maxFixAge -> {
                        setStatus(Ui.DISABLED, "Updating your location…", "Last location is ${age / 60} min old.")
                        hint = "Waiting for a fresh location…"
                    }
                    acc > maxAccuracy -> {
                        setStatus(Ui.DISABLED, "Improving accuracy…", "$accText — needs ±${Math.round(maxAccuracy)} m or better.")
                        hint = "Move near a window or outside for a better signal."
                    }
                    dist > f.radius -> {
                        setStatus(Ui.RED, "Outside office area", "${LocationTracker.formatDistance(dist - f.radius)} away from ${f.name} · $accText", Ui.RED)
                        hint = "You can punch only inside the office area."
                    }
                    else -> {
                        setStatus(Ui.GREEN, "Inside office area", "${LocationTracker.formatDistance(dist)} from centre · $accText", Ui.GREEN)
                        canPunch = true
                        hint = if (nextAction == "in") "Tap to record your arrival." else "Tap to record your departure (your last punch counts as out time)."
                    }
                }
            }
        }

        val enabled = canPunch && !punching && !loadingHome
        punchButton.isEnabled = enabled
        punchButton.text = when {
            punching -> "Saving…"
            nextAction == "in" -> "Punch In"
            else -> "Punch Out"
        }
        Ui.setButtonColor(punchButton, if (enabled) (if (nextAction == "in") Ui.GREEN else Ui.ORANGE) else Ui.DISABLED)
        punchHint.text = hint
    }

    private var bannerKind = ""
    private fun bannerIsPermission(): Boolean = banner.visibility == View.VISIBLE && bannerKind == "perm"

    // ------------------------------------------------------------------ server

    private fun loadHome() {
        if (loadingHome) return
        loadingHome = true
        progress.visibility = View.VISIBLE
        Bg.run({ api.call("home") }) { r ->
            if (isFinishing || isDestroyed) return@run
            loadingHome = false
            progress.visibility = View.GONE
            if (!r.ok) {
                handleFailure(r, inline = true)
                updateLocationUi()
                return@run
            }
            lastHomeLoad = System.currentTimeMillis()
            homeLoaded = true
            val d = r.data
            d.optJSONObject("employee")?.let {
                store.employeeName = it.optString("name", store.employeeName)
                store.employeeCode = it.optString("display_code", store.employeeCode)
            }
            val f = d.optJSONObject("fence")
            fence = if (f != null) Fence(
                f.optString("name", "Office"), f.optDouble("lat"), f.optDouble("lng"), f.optDouble("radius_m", 200.0)
            ) else null
            fenceError = d.optString("fence_error", "")
            fenceText.text = fence?.let { "${it.name} · within ${Math.round(it.radius)} m" } ?: "Not assigned"
            d.optJSONObject("rules")?.let {
                maxAccuracy = it.optDouble("max_accuracy_m", 100.0).toFloat()
                maxFixAge = it.optLong("max_fix_age_s", 120L)
                blockMock = it.optBoolean("block_mock", true)
            }
            nextAction = d.optString("next_action", "in")
            renderToday(d.optJSONArray("today"))
            if (banner.visibility == View.VISIBLE && bannerKind == "error") hideBanner()
            updateLocationUi()
        }
    }

    private fun renderToday(arr: JSONArray?) {
        todayList.removeAllViews()
        val n = arr?.length() ?: 0
        if (n == 0) {
            todaySummary.text = "No punches yet today."
            return
        }
        val first = arr!!.optJSONObject(0)
        val last = arr.optJSONObject(n - 1)
        todaySummary.text = if (n == 1) "In at ${first.optString("label")}" else
            "In ${first.optString("label")} · Last ${last.optString("label")} · $n punches"
        for (i in n - 1 downTo 0) {
            val p = arr.optJSONObject(i) ?: continue
            val row = Ui.horizontal(this).apply {
                val v = Ui.dp(this@MainActivity, 8)
                setPadding(0, v, 0, v)
            }
            row.addView(Ui.text(this, p.optString("label"), 16f, Ui.TEXT, true),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val isApp = p.optString("source") == "app"
            row.addView(Ui.text(this, if (isApp) "App" else "Device", 12f, if (isApp) Ui.GREEN else Ui.MUTED, true).apply {
                background = Ui.round(this@MainActivity, if (isApp) Ui.GREEN_LIGHT else Ui.BG, 10)
                val ph = Ui.dp(this@MainActivity, 10)
                val pv = Ui.dp(this@MainActivity, 3)
                setPadding(ph, pv, ph, pv)
            })
            todayList.addView(row)
            if (i > 0) todayList.addView(View(this).apply { setBackgroundColor(Ui.LINE) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
        }
    }

    private fun punch() {
        val loc = tracker.best ?: return
        if (punching) return
        punching = true
        updateLocationUi()
        val params = mapOf(
            "lat" to loc.latitude.toString(),
            "lng" to loc.longitude.toString(),
            "accuracy" to loc.accuracy.toString(),
            "fix_age_s" to LocationTracker.ageSeconds(loc).toString(),
            "is_mock" to if (LocationTracker.isMock(loc)) "1" else "0",
            "provider" to (loc.provider ?: "")
        )
        Bg.run({ api.call("punch", params) }) { r ->
            if (isFinishing || isDestroyed) return@run
            punching = false
            if (r.ok) {
                vibrate()
                nextAction = r.data.optString("next_action", "out")
                renderToday(r.data.optJSONArray("today"))
                lastHomeLoad = System.currentTimeMillis()
                updateLocationUi()
                dialog("Done", r.message)
            } else {
                updateLocationUi()
                handleFailure(r, inline = false)
            }
        }
    }

    private fun handleFailure(r: ApiResult, inline: Boolean) {
        when {
            r.needsReactivation -> {
                store.clearSession()
                openSetup(r.message)
            }
            r.updateRequired -> dialog("Update needed", r.message)
            inline -> showBanner(r.message, Ui.RED_LIGHT, Ui.RED, "Try again", "error") { hideBanner(); loadHome() }
            else -> dialog("Punch not recorded", r.message)
        }
    }

    private fun dialog(title: String, msg: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun vibrate() {
        try {
            @Suppress("DEPRECATION")
            val v = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(80)
            }
        } catch (e: Exception) {
        }
    }

    private fun confirmSignOut() {
        AlertDialog.Builder(this)
            .setTitle("Sign out?")
            .setMessage("You will need a new activation code from Studio to use the app again on this phone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Sign out") { _, _ ->
                progress.visibility = View.VISIBLE
                Bg.run({ api.call("logout") }) { _ ->
                    if (isFinishing || isDestroyed) return@run
                    store.clearSession()
                    openSetup(null)
                }
            }
            .show()
    }

    private fun openSetup(message: String?) {
        val i = Intent(this, SetupActivity::class.java)
        if (message != null) i.putExtra(SetupActivity.EXTRA_MESSAGE, message)
        startActivity(i)
        finish()
    }

    companion object {
        const val EXTRA_WELCOME = "welcome"
        private const val REQ_LOCATION = 41
    }
}
