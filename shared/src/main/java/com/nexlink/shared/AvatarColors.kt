package com.nexlink.shared

object AvatarColors {

    private val ALPHA_COLORS = intArrayOf(
        0xFF1565C0.toInt(), // A - deep blue
        0xFF1976D2.toInt(), // B - blue
        0xFF0288D1.toInt(), // C - light blue
        0xFF0097A7.toInt(), // D - cyan
        0xFF00838F.toInt(), // E - dark cyan
        0xFF00796B.toInt(), // F - teal
        0xFF2E7D32.toInt(), // G - dark green
        0xFF388E3C.toInt(), // H - green
        0xFF558B2F.toInt(), // I - light green
        0xFF827717.toInt(), // J - yellow-green
        0xFFF9A825.toInt(), // K - amber
        0xFFF57F17.toInt(), // L - dark amber
        0xFFE65100.toInt(), // M - deep orange
        0xFFBF360C.toInt(), // N - deep orange dark
        0xFFB71C1C.toInt(), // O - dark red
        0xFFC62828.toInt(), // P - red
        0xFFD32F2F.toInt(), // Q - red
        0xFFAD1457.toInt(), // R - pink dark
        0xFFC2185B.toInt(), // S - pink
        0xFFE91E63.toInt(), // T - light pink
        0xFF880E4F.toInt(), // U - deep pink
        0xFF6A1B9A.toInt(), // V - purple dark
        0xFF7B1FA2.toInt(), // W - purple
        0xFF8E24AA.toInt(), // X - medium purple
        0xFF6200EA.toInt(), // Y - deep purple
        0xFF311B92.toInt()  // Z - indigo
    )

    private val DIGIT_COLORS = intArrayOf(
        0xFF0D47A1.toInt(), // 0 - darkest blue
        0xFF006064.toInt(), // 1 - dark cyan
        0xFF004D40.toInt(), // 2 - dark teal
        0xFF1B5E20.toInt(), // 3 - dark green
        0xFF33691E.toInt(), // 4 - dark light-green
        0xFFE65100.toInt(), // 5 - deep orange
        0xFF8D1F1F.toInt(), // 6 - dark red
        0xFF880E4F.toInt(), // 7 - dark pink
        0xFF4A148C.toInt(), // 8 - dark deep-purple
        0xFF1A237E.toInt()  // 9 - dark indigo
    )

    fun colorFor(key: String): Int {
        val ch = key.firstOrNull() ?: return ALPHA_COLORS[0]
        return when {
            ch.isLetter() -> ALPHA_COLORS[(ch.uppercaseChar() - 'A').coerceIn(0, 25)]
            ch.isDigit()  -> DIGIT_COLORS[ch - '0']
            else          -> ALPHA_COLORS[0]
        }
    }
}
