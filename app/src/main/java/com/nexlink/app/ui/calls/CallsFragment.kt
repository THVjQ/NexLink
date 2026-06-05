package com.nexlink.app.ui.calls

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nexlink.app.R
import com.nexlink.app.databinding.FragmentCallsBinding
import com.nexlink.app.db.CallEntry
import com.nexlink.app.db.CallLogHelper
import com.nexlink.app.util.AvatarColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ── Row model ─────────────────────────────────────────────────────────────────
private sealed class CallRow {
    data class DayHeader(val label: String) : CallRow()
    data class CallGroup(
        val name: String,
        val number: String,
        val latestType: Int,
        val timestamp: Long,
        val count: Int              // calls from same number on same day
    ) : CallRow()
}

class CallsFragment : Fragment() {

    private var _b: FragmentCallsBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentCallsBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.btnDialpad.setOnClickListener {
            startActivity(Intent(requireContext(), DialerActivity::class.java))
        }
        loadCallLog()
    }

    override fun onResume() { super.onResume(); loadCallLog() }

    private fun loadCallLog() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED) return
        val ctx = context ?: return
        Thread {
            val calls = CallLogHelper.getCalls(ctx)
            if (!isAdded) return@Thread
            activity?.runOnUiThread { bindCalls(calls) }
        }.start()
    }

    // ── Build grouped rows ───────────────────────────────────────────────────

    private fun buildRows(calls: List<CallEntry>): List<CallRow> {
        val result  = mutableListOf<CallRow>()
        // calls are DATE DESC; label each entry once
        val labeled = calls.map { it to dayLabel(it.timestamp) }
        val days    = labeled.map { it.second }.distinct()

        for (day in days) {
            result += CallRow.DayHeader(day)
            val dayCalls = labeled.filter { it.second == day }.map { it.first }

            // Group by number preserving newest-first order
            val groups = LinkedHashMap<String, MutableList<CallEntry>>()
            for (c in dayCalls) groups.getOrPut(c.number) { mutableListOf() } += c

            for ((num, group) in groups) {
                val latest = group.first()
                result += CallRow.CallGroup(latest.name, num, latest.type, latest.timestamp, group.size)
            }
        }
        return result
    }

    // ── Bind adapter ─────────────────────────────────────────────────────────

    private fun bindCalls(calls: List<CallEntry>) {
        val rows = buildRows(calls)
        b.recyclerCalls.layoutManager = LinearLayoutManager(requireContext())
        b.recyclerCalls.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

            inner class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
                val tvDate: TextView = v.findViewById(R.id.tvDate)
            }
            inner class CallVH(v: View) : RecyclerView.ViewHolder(v) {
                val avatar:  TextView    = v.findViewById(R.id.tvAvatar)
                val name:    TextView    = v.findViewById(R.id.tvName)
                val detail:  TextView    = v.findViewById(R.id.tvDetail)
                val btnCall: ImageButton = v.findViewById(R.id.btnCall)
            }

            override fun getItemViewType(pos: Int) = when (rows[pos]) {
                is CallRow.DayHeader -> 0
                is CallRow.CallGroup -> 1
            }

            override fun onCreateViewHolder(p: ViewGroup, t: Int): RecyclerView.ViewHolder {
                val inf = LayoutInflater.from(p.context)
                return if (t == 0) HeaderVH(inf.inflate(R.layout.item_date_header, p, false))
                else CallVH(inf.inflate(R.layout.item_call_log, p, false))
            }

            override fun getItemCount() = rows.size

            override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
                when (val row = rows[pos]) {
                    is CallRow.DayHeader -> (h as HeaderVH).tvDate.text = row.label
                    is CallRow.CallGroup -> {
                        h as CallVH
                        val initials = row.name.split(" ").take(2)
                            .joinToString("") { it.take(1).uppercase() }.ifBlank { "?" }
                        h.avatar.background.mutate().setTint(AvatarColors.colorFor(initials))
                        h.avatar.text = initials
                        h.name.text   = row.name

                        val typeStr = when (row.latestType) {
                            CallLog.Calls.INCOMING_TYPE -> "↙ Incoming"
                            CallLog.Calls.OUTGOING_TYPE -> "↗ Outgoing"
                            CallLog.Calls.MISSED_TYPE   -> "↙ Missed"
                            else -> "Call"
                        }
                        val color = when (row.latestType) {
                            CallLog.Calls.MISSED_TYPE   -> 0xFFff5555.toInt()
                            CallLog.Calls.OUTGOING_TYPE -> 0xFFa29bfe.toInt()
                            else                        -> 0xFF25d366.toInt()
                        }
                        val countSuffix = if (row.count > 1) " · ×${row.count}" else ""
                        h.detail.text = "$typeStr · ${formatTime(row.timestamp)}$countSuffix"
                        h.detail.setTextColor(color)

                        h.btnCall.setOnClickListener { placeCall(row.number) }
                        h.itemView.setOnClickListener { placeCall(row.number) }
                        h.btnCall.setOnLongClickListener { showSimCallPicker(row.number); true }
                        h.itemView.setOnLongClickListener { showSimCallPicker(row.number); true }
                    }
                }
            }
        }
    }

    // ── Date / time helpers ────────────────────────────────────────────────────

    private fun dayLabel(ms: Long): String {
        val cal   = Calendar.getInstance().apply { timeInMillis = ms }
        val today = Calendar.getInstance()
        val yes   = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        fun same(a: Calendar, b: Calendar) =
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        return when {
            same(cal, today) -> "Today"
            same(cal, yes)   -> "Yesterday"
            cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) ->
                SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(ms))
            else ->
                SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(ms))
        }
    }

    /** Time within a day — shown on each call row */
    private fun formatTime(ms: Long): String =
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ms))

    // ── SIM-aware call ────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun showSimCallPicker(number: String) {
        val ctx = requireContext()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) return
        try {
            val tm = ctx.getSystemService(TelecomManager::class.java)
            val sm = ctx.getSystemService(SubscriptionManager::class.java)
            val accounts = tm.callCapablePhoneAccounts ?: emptyList()
            if (accounts.size <= 1) { placeCall(number); return }

            val hasState = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED
            val labels = accounts.mapIndexed { idx, handle ->
                try {
                    val name = tm.getPhoneAccount(handle)?.label?.toString() ?: "SIM ${idx + 1}"
                    if (!hasState) return@mapIndexed name
                    val num = sm.activeSubscriptionInfoList?.getOrNull(idx)
                        ?.number?.takeIf { it.isNotBlank() }?.let { "\n$it" } ?: ""
                    "$name$num"
                } catch (_: Exception) { "SIM ${idx + 1}" }
            }.toTypedArray()

            AlertDialog.Builder(ctx)
                .setTitle("Call $number via…")
                .setItems(labels) { _, i ->
                    val bundle = android.os.Bundle()
                    bundle.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accounts[i])
                    try { tm.placeCall(Uri.parse("tel:$number"), bundle) }
                    catch (_: Exception) { placeCall(number) }
                }
                .show()
        } catch (_: Exception) { placeCall(number) }
    }

    private fun placeCall(number: String) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) return
        startActivity(Intent(requireContext(), DialerActivity::class.java).apply {
            data = Uri.parse("tel:$number")
        })
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
