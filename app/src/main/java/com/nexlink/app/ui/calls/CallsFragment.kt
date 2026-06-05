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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nexlink.app.R
import com.nexlink.app.databinding.FragmentCallsBinding
import com.nexlink.app.db.CallEntry
import com.nexlink.app.db.CallLogHelper
import com.nexlink.app.util.AvatarColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    private fun bindCalls(calls: List<CallEntry>) {
        b.recyclerCalls.layoutManager = LinearLayoutManager(requireContext())
        b.recyclerCalls.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            inner class VH(v: View) : RecyclerView.ViewHolder(v) {
                val avatar:  TextView    = v.findViewById(R.id.tvAvatar)
                val name:    TextView    = v.findViewById(R.id.tvName)
                val detail:  TextView    = v.findViewById(R.id.tvDetail)
                val btnCall: ImageButton = v.findViewById(R.id.btnCall)
            }
            override fun onCreateViewHolder(p: ViewGroup, t: Int) =
                VH(LayoutInflater.from(p.context).inflate(R.layout.item_call_log, p, false))
            override fun getItemCount() = calls.size
            override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
                h as VH
                val c = calls[pos]
                val initials = c.name.split(" ").take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { "?" }
                h.avatar.background.mutate().setTint(AvatarColors.colorFor(initials))
                h.avatar.text = initials
                h.name.text   = c.name
                val typeStr = when (c.type) {
                    CallLog.Calls.INCOMING_TYPE -> "↙ Incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "↗ Outgoing"
                    CallLog.Calls.MISSED_TYPE   -> "↙ Missed"
                    else -> "Call"
                }
                val color = when (c.type) {
                    CallLog.Calls.MISSED_TYPE   -> 0xFFff5555.toInt()
                    CallLog.Calls.OUTGOING_TYPE -> 0xFFa29bfe.toInt()
                    else                        -> 0xFF25d366.toInt()
                }
                h.detail.text = "$typeStr · ${formatDate(c.timestamp)}"
                h.detail.setTextColor(color)

                // Short tap → call with default SIM
                h.btnCall.setOnClickListener { placeCall(c.number) }
                h.itemView.setOnClickListener { placeCall(c.number) }
                // Long-press → SIM picker
                h.btnCall.setOnLongClickListener { showSimCallPicker(c.number); true }
                h.itemView.setOnLongClickListener { showSimCallPicker(c.number); true }
            }
        }
    }

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

            val hasPhoneState = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED

            val labels = accounts.mapIndexed { idx, handle ->
                try {
                    // getPhoneAccount gives us the human-readable label (SIM carrier name)
                    val acct = tm.getPhoneAccount(handle)
                    val name = acct?.label?.toString() ?: "SIM ${idx + 1}"
                    if (!hasPhoneState) return@mapIndexed name
                    // Try to also show the SIM number
                    val subs = sm.activeSubscriptionInfoList ?: emptyList()
                    val num  = subs.getOrNull(idx)?.number?.takeIf { it.isNotBlank() }?.let { "\n$it" } ?: ""
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
        } catch (_: Exception) {
            placeCall(number)
        }
    }

    private fun placeCall(number: String) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) return
        startActivity(Intent(requireContext(), DialerActivity::class.java).apply {
            data = Uri.parse("tel:$number")
        })
    }

    private fun formatDate(ms: Long): String {
        val diff = System.currentTimeMillis() - ms
        return when {
            diff < 86_400_000      -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
            diff < 7*86_400_000L   -> SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(ms))
            else                   -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ms))
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
