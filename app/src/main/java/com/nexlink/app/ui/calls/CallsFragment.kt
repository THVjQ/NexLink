package com.nexlink.app.ui.calls

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.view.*
import android.widget.*
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
                val avatar: TextView = v.findViewById(R.id.tvAvatar)
                val name:   TextView = v.findViewById(R.id.tvName)
                val detail: TextView = v.findViewById(R.id.tvDetail)
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
                h.btnCall.setOnClickListener { placeCall(c.number) }
                h.itemView.setOnClickListener { placeCall(c.number) }
            }
        }
    }

    private fun showDialer() {
        val sheet = BottomSheetDialog(requireContext(), R.style.DialerSheet)
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_dialer, null)
        sheet.setContentView(v)

        val display = v.findViewById<TextView>(R.id.tvDialNumber)
        var num = ""

        fun updateDisplay() { display.text = if (num.isEmpty()) "" else formatNumber(num) }

        val keys = mapOf(
            R.id.k0 to "0", R.id.k1 to "1", R.id.k2 to "2", R.id.k3 to "3",
            R.id.k4 to "4", R.id.k5 to "5", R.id.k6 to "6", R.id.k7 to "7",
            R.id.k8 to "8", R.id.k9 to "9", R.id.kStar to "*", R.id.kHash to "#"
        )
        keys.forEach { (id, digit) ->
            v.findViewById<View>(id).setOnClickListener { num += digit; updateDisplay() }
        }
        v.findViewById<View>(R.id.kDel).setOnClickListener {
            if (num.isNotEmpty()) { num = num.dropLast(1); updateDisplay() }
        }
        v.findViewById<View>(R.id.kCall).setOnClickListener {
            if (num.isNotEmpty()) { sheet.dismiss(); placeCall(num) }
            else Toast.makeText(requireContext(), "Enter a number", Toast.LENGTH_SHORT).show()
        }
        sheet.show()
    }

    private fun placeCall(number: String) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) return
        startActivity(Intent(requireContext(), DialerActivity::class.java).apply {
            data = Uri.parse("tel:$number")
        })
    }

    private fun formatNumber(n: String) = when {
        n.length <= 4  -> n
        n.length <= 7  -> "${n.take(4)} ${n.drop(4)}"
        else           -> "${n.take(4)} ${n.drop(4).take(3)} ${n.drop(7)}"
    }

    private fun formatDate(ms: Long): String {
        val diff = System.currentTimeMillis() - ms
        return when {
            diff < 86_400_000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
            diff < 7 * 86_400_000L -> SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(ms))
            else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ms))
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
