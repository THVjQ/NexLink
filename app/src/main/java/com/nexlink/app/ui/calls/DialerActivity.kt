package com.nexlink.app.ui.calls

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nexlink.app.R
import com.nexlink.app.databinding.ActivityDialerBinding
import com.nexlink.app.db.SmsHelper
import com.nexlink.app.ui.sms.ConversationActivity

class DialerActivity : AppCompatActivity() {

    private lateinit var b: ActivityDialerBinding
    private var number = ""
    private lateinit var suggestAdapter: SuggestionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDialerBinding.inflate(layoutInflater)
        setContentView(b.root)

        suggestAdapter = SuggestionAdapter(
            onCall = { num -> number = num; placeCall() },
            onSms  = { num, name ->
                startActivity(Intent(this, ConversationActivity::class.java).apply {
                    putExtra("address", num)
                    putExtra("contact_name", name)
                })
            }
        )
        b.rvSuggestions.layoutManager = LinearLayoutManager(this)
        b.rvSuggestions.adapter = suggestAdapter

        val uri = intent.data
        if (uri?.scheme == "tel") { number = uri.schemeSpecificPart ?: ""; updateDisplay() }

        bindKeys()
        b.btnClose.setOnClickListener { it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); finish() }
        b.btnCall.setOnClickListener  { it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); placeCall() }
        b.btnDel.setOnClickListener   {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (number.isNotEmpty()) { number = number.dropLast(1); updateDisplay() }
        }
    }

    private fun bindKeys() {
        val keys = mapOf(
            b.k0 to "0", b.k1 to "1", b.k2 to "2", b.k3 to "3",
            b.k4 to "4", b.k5 to "5", b.k6 to "6", b.k7 to "7",
            b.k8 to "8", b.k9 to "9", b.kStar to "*", b.kHash to "#"
        )
        keys.forEach { (view, digit) ->
            view.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                number += digit
                updateDisplay()
            }
        }
    }

    private fun updateDisplay() {
        b.tvNumber.text = formatNumber(number)
        updateSuggestions()
    }

    private fun updateSuggestions() {
        if (number.length < 2) { suggestAdapter.setData(emptyList()); return }
        Thread {
            val results = queryContacts(number)
            runOnUiThread { suggestAdapter.setData(results) }
        }.start()
    }

    private fun queryContacts(query: String): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val proj = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val cleanQuery = query.replace("[^\\d]".toRegex(), "")
        val sel = "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            proj, sel, arrayOf("%$cleanQuery%"),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC LIMIT 3"
        )?.use { c ->
            val seen = mutableSetOf<String>()
            while (c.moveToNext() && list.size < 3) {
                val name = c.getString(0) ?: continue
                val num  = c.getString(1) ?: continue
                if (num in seen) continue
                seen += num
                list += Pair(name, num)
            }
        }
        return list
    }

    private fun formatNumber(n: String) = when {
        n.length <= 4  -> n
        n.length <= 7  -> "${n.take(4)} ${n.drop(4)}"
        else           -> "${n.take(4)} ${n.drop(4).take(3)} ${n.drop(7)}"
    }

    private fun placeCall() {
        if (number.isEmpty()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) return
        val uri = Uri.fromParts("tel", number, null)
        try {
            getSystemService(TelecomManager::class.java).placeCall(uri, null)
        } catch (_: Exception) {
            startActivity(Intent(Intent.ACTION_CALL, uri))
        }
    }
}

class SuggestionAdapter(
    private val onCall: (String) -> Unit,
    private val onSms:  (String, String) -> Unit
) : RecyclerView.Adapter<SuggestionAdapter.VH>() {

    private var items = listOf<Pair<String, String>>()
    fun setData(d: List<Pair<String, String>>) { items = d; notifyDataSetChanged() }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val avatar: TextView = v.findViewById(R.id.tvAvatar)
        val name:   TextView = v.findViewById(R.id.tvName)
        val number: TextView = v.findViewById(R.id.tvNumber)
        val btnCall: View    = v.findViewById(R.id.btnSuggestCall)
        val btnSms:  View    = v.findViewById(R.id.btnSuggestSms)
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_dialer_suggestion, p, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val (name, num) = items[pos]
        val initials = name.split(" ").take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { "?" }
        h.avatar.text  = initials
        h.name.text    = name
        h.number.text  = num
        h.btnCall.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onCall(num.replace("[^+\\d]".toRegex(), ""))
        }
        h.btnSms.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onSms(num.replace("[^+\\d]".toRegex(), ""), name)
        }
    }
}
