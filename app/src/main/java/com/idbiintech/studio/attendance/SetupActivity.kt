package com.idbiintech.studio.attendance

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

/** First screen: Studio address + 8-character activation code from the Studio "Mobile App" page. */
class SetupActivity : Activity() {

    private lateinit var store: Store
    private lateinit var api: Api
    private lateinit var serverInput: EditText
    private lateinit var codeInput: EditText
    private lateinit var errorText: TextView
    private lateinit var activateButton: Button
    private lateinit var progress: ProgressBar
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        api = Api(store)
        buildUi()
        intent.getStringExtra(EXTRA_MESSAGE)?.let { showError(it) }
    }

    private fun buildUi() {
        val root = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Ui.BG)
        }
        val column = Ui.vertical(this)
        root.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Orange header
        val header = Ui.vertical(this).apply {
            setBackgroundColor(Ui.ORANGE)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val logo = TextView(this).apply {
            text = "S"
            setTextColor(Ui.ORANGE)
            textSize = 30f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = Ui.round(this@SetupActivity, Color.WHITE, 36)
        }
        header.addView(logo, LinearLayout.LayoutParams(Ui.dp(this, 72), Ui.dp(this, 72)).apply {
            topMargin = Ui.dp(this@SetupActivity, 16)
        })
        header.addView(Ui.text(this, getString(R.string.app_name), 22f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
        }, Ui.matchWrap(this, 12))
        header.addView(Ui.text(this, "Punch attendance inside your office area", 14f, Color.parseColor("#FFE7DA")).apply {
            gravity = Gravity.CENTER
        }, Ui.matchWrap(this, 4, 24))
        column.addView(header, Ui.matchWrap(this))

        val card = Ui.card(this)
        val lp = Ui.matchWrap(this, 16).apply {
            leftMargin = Ui.dp(this@SetupActivity, 16)
            rightMargin = Ui.dp(this@SetupActivity, 16)
        }
        column.addView(card, lp)

        card.addView(Ui.text(this, "Activate this phone", 18f, Ui.TEXT, true))
        card.addView(Ui.text(this,
            "1. On a computer, open Studio → Mobile App.\n2. Tap “Get activation code”.\n3. Type the code below.",
            14f, Ui.MUTED).apply { setLineSpacing(0f, 1.25f) }, Ui.matchWrap(this, 6, 8))

        card.addView(label("Studio address"), Ui.matchWrap(this, 8))
        serverInput = input().apply {
            setText(store.serverUrl)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_NEXT
        }
        card.addView(serverInput, Ui.matchWrap(this, 4))

        card.addView(label("Activation code"), Ui.matchWrap(this, 14))
        codeInput = input().apply {
            hint = "XXXX-XXXX"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            filters = arrayOf(InputFilter.AllCaps(), InputFilter.LengthFilter(9))
            textSize = 22f
            letterSpacing = 0.15f
            gravity = Gravity.CENTER
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    activate(); true
                } else false
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    errorText.visibility = android.view.View.GONE
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        card.addView(codeInput, Ui.matchWrap(this, 4))

        errorText = Ui.text(this, "", 14f, Ui.RED).apply {
            visibility = android.view.View.GONE
            background = Ui.round(this@SetupActivity, Ui.RED_LIGHT, 8)
            val p = Ui.dp(this@SetupActivity, 10)
            setPadding(p, p, p, p)
        }
        card.addView(errorText, Ui.matchWrap(this, 12))

        activateButton = Ui.button(this, "Activate")
        activateButton.setOnClickListener { activate() }
        card.addView(activateButton, Ui.matchWrap(this, 16))

        progress = ProgressBar(this).apply { visibility = android.view.View.GONE }
        card.addView(progress, LinearLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 32)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = Ui.dp(this@SetupActivity, 12)
        })

        column.addView(Ui.text(this,
            "One phone per employee. Your location is checked only when you open the app to punch.\nVersion ${BuildConfig.VERSION_NAME}",
            12f, Ui.MUTED).apply { gravity = Gravity.CENTER }, Ui.matchWrap(this, 16, 24).apply {
            leftMargin = Ui.dp(this@SetupActivity, 24)
            rightMargin = Ui.dp(this@SetupActivity, 24)
        })

        setContentView(root)
        Ui.edgeToEdge(this, root) { top, bottom ->
            header.setPadding(0, top, 0, 0)
            column.setPadding(0, 0, 0, bottom)
        }
    }

    private fun label(s: String): TextView = Ui.text(this, s, 13f, Ui.MUTED, true)

    private fun input(): EditText = EditText(this).apply {
        setTextColor(Ui.TEXT)
        setHintTextColor(Ui.DISABLED)
        textSize = 16f
        setSingleLine(true)
        background = Ui.round(this@SetupActivity, Color.WHITE, 10, Ui.LINE)
        val p = Ui.dp(this@SetupActivity, 12)
        setPadding(p, p, p, p)
    }

    private fun showError(msg: String) {
        errorText.text = msg
        errorText.visibility = android.view.View.VISIBLE
    }

    private fun setBusy(b: Boolean) {
        busy = b
        activateButton.isEnabled = !b
        activateButton.text = if (b) "Activating…" else "Activate"
        progress.visibility = if (b) android.view.View.VISIBLE else android.view.View.GONE
        serverInput.isEnabled = !b
        codeInput.isEnabled = !b
    }

    private fun activate() {
        if (busy) return
        errorText.visibility = android.view.View.GONE
        val base = Api.normaliseBaseUrl(serverInput.text.toString())
        if (base == null) {
            showError("Enter the Studio address, for example studio.yourcompany.com")
            serverInput.requestFocus()
            return
        }
        val code = codeInput.text.toString().uppercase().filter { it.isLetterOrDigit() }
        if (code.length != 8) {
            showError("The activation code has 8 letters and numbers (for example AB2C-9XYZ).")
            codeInput.requestFocus()
            return
        }
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(codeInput.windowToken, 0)
        setBusy(true)
        val params = mapOf(
            "code" to code,
            "device_name" to deviceName(),
            "os_version" to Build.VERSION.RELEASE
        )
        Bg.run({ api.call("activate", params, base) }) { r ->
            if (isFinishing || isDestroyed) return@run
            setBusy(false)
            val token = r.data.optString("token", "")
            if (!r.ok || token.isEmpty()) {
                showError(r.message.ifBlank { "Activation failed. Please try again." })
                return@run
            }
            store.serverUrl = base
            store.token = token
            if (store.token != token) {
                showError("This phone could not store the login securely. Please restart the phone and try again.")
                store.clearSession()
                return@run
            }
            val emp = r.data.optJSONObject("employee")
            store.employeeName = emp?.optString("name", "") ?: ""
            store.employeeCode = emp?.optString("display_code", "") ?: ""
            startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_WELCOME, r.message))
            finish()
        }
    }

    private fun deviceName(): String {
        val maker = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model"
    }

    companion object {
        const val EXTRA_MESSAGE = "message"
    }
}
