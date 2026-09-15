package com.nexlink.social

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nexlink.social.core.session.UserSummary
import com.nexlink.social.ui.R as UiR
import com.nexlink.social.ui.chrome.Chrome
import kotlinx.coroutines.launch

/**
 * Start a conversation — §6.6, §14.8.
 *
 * §6.6 is the chapter that matters here, and it is a deliberately narrow
 * feature: there is **no phone number to match on and no public directory to
 * browse** (§1.3). Discovery is a lookup by the username someone gave you, on
 * this homeserver only, because federation is off (§2.6).
 *
 * That is a worse discovery story than a phone-number messenger, and it is the
 * price of §6.1's stronger privacy position. The copy on this screen says so
 * rather than pretending the box is a search engine.
 */
class NewChatActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private var results: List<UserSummary> = emptyList()
    private var status: String? = null
    private var query: String = ""
    private var selected: List<UserSummary> = emptyList()
    private var groupName: String = ""

    private fun createGroup() {
        status = "Creating group…"; render()
        lifecycleScope.launch {
            val s = SessionProvider.manager(this@NewChatActivity).current() ?: return@launch
            s.createGroup(groupName, selected.map { it.id })
                .onSuccess { rid ->
                    startActivity(ConversationActivity.intent(this@NewChatActivity, rid.value, groupName))
                    finish()
                }
                .onFailure { status = it.message ?: "Couldn't create the group."; render() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // §14 — the shared title bar, which also takes the system-bar insets
        // this screen used to handle with its own listener.
        val page = Chrome(this).page("New conversation", onBack = { finish() },
            horizontalPaddingDp = 20)
        root = page.content
        setContentView(page.root)
        render()
    }

    private fun render() {
        root.removeAllViews()
        root.addView(text(
            "Enter the username someone gave you. There's no directory to browse " +
            "and no way to look people up by phone number — that's deliberate.",
            14f, UiR.color.social_muted))
        root.addView(gap(12))

        val field = EditText(this).apply {
            hint = "username"
            setText(query)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        root.addView(field)

        // §14.8 — a group needs a name, because unlike a DM it has no other
        // identity to fall back on.
        if (selected.isNotEmpty()) {
            root.addView(gap(12))
            root.addView(text("Group members", 14f, UiR.color.social_accent, bold = true))
            selected.forEach { u ->
                root.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(text(u.displayName ?: u.id.value, 15f, UiR.color.social_text).apply {
                        layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                    })
                    addView(Button(this@NewChatActivity).apply {
                        text = "Remove"; isAllCaps = false
                        setOnClickListener { selected = selected - u; render() }
                    })
                })
            }
            val nameField = EditText(this).apply {
                hint = "Group name"; setText(groupName)
            }
            root.addView(nameField)
            root.addView(Button(this).apply {
                text = "Create group"
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) }
                setOnClickListener {
                    groupName = nameField.text.toString().trim()
                    if (groupName.isEmpty()) { status = "Give the group a name."; render(); return@setOnClickListener }
                    createGroup()
                }
            })
            root.addView(gap(8)); root.addView(divider())
        }

        root.addView(Button(this).apply {
            text = "Search"
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) }
            setOnClickListener {
                query = field.text.toString().trim()
                if (query.isEmpty()) return@setOnClickListener
                status = "Searching…"; render()
                lifecycleScope.launch {
                    val s = SessionProvider.manager(this@NewChatActivity).current()
                        ?: return@launch
                    s.findUsers(query)
                        .onSuccess {
                            results = it
                            status = if (it.isEmpty())
                                "Nobody found. Usernames are exact — check the spelling."
                            else null
                            render()
                        }
                        .onFailure { status = it.message ?: "Search failed."; render() }
                }
            }
        })

        status?.let { root.addView(gap(8)); root.addView(text(it, 14f, UiR.color.social_muted)) }

        if (results.isNotEmpty()) {
            root.addView(gap(12)); root.addView(divider()); root.addView(gap(8))
            results.forEach { root.addView(resultRow(it)) }
        }
    }

    private fun resultRow(u: UserSummary): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(12), 0, dp(12))
        isClickable = true
        addView(text(u.displayName ?: u.id.value, 17f, UiR.color.social_text, bold = true))
        // §6.7 — the full MXID is always shown. A display name is chosen by its
        // owner and is not unique; showing only that is how impersonation works.
        addView(text(u.id.value, 13f, UiR.color.social_muted))
        // §14.8 — tap opens a one-to-one; "Add" builds a group instead. Two
        // actions on one row, because the user does not know which they want
        // until they have found the first person.
        addView(Button(this@NewChatActivity).apply {
            text = if (selected.contains(u)) "Added" else "Add to group"
            isAllCaps = false
            isEnabled = !selected.contains(u)
            setOnClickListener { selected = selected + u; render() }
        })
        setOnClickListener {
            status = "Opening…"; render()
            lifecycleScope.launch {
                val s = SessionProvider.manager(this@NewChatActivity).current() ?: return@launch
                s.startDirectMessage(u.id)
                    .onSuccess { roomId ->
                        startActivity(ConversationActivity.intent(
                            this@NewChatActivity, roomId.value, u.displayName ?: u.id.value))
                        finish()
                    }
                    .onFailure { status = it.message ?: "Couldn't start the conversation."; render() }
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun gap(h: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, dp(h))
    }
    private fun divider() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH, 1)
        setBackgroundColor(ContextCompat.getColor(this@NewChatActivity, UiR.color.social_divider))
    }
    private fun text(v: CharSequence, size: Float, c: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = v
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@NewChatActivity, c))
            setLineSpacing(dp(3).toFloat(), 1f)
        }

    companion object {
        private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        fun intent(c: Context) = Intent(c, NewChatActivity::class.java)
    }
}
