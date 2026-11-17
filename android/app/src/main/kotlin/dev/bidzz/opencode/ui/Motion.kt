package dev.bidzz.opencode.ui

import android.content.Context
import android.provider.Settings

/**
 * Android's "remove animations" (developer option, and the accessibility switch behind it):
 * when the animator duration scale is 0 the user asked for no motion, so every custom
 * animation in this app becomes an instant swap. Platform animations (drawer, dialogs)
 * honour this themselves; the ones we hand-roll must check here.
 */
fun animationsOff(context: Context): Boolean =
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
