package dev.bidzz.opencode.core.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type scale for a developer tool.
 *
 * Two families, each with a job: a UI sans for chrome and prose, and a monospace
 * for anything the user might copy — code, paths, ports, identifiers. Mixing them
 * is meaningful rather than decorative, which is why inline code inside a message
 * is monospace too.
 *
 * Sizes are small by default. A coding tool shows a lot of text; oversized headings
 * waste vertical space on a phone, and `plan.md` §12 explicitly forbids them.
 */
// FontVariation (the weight axis of the variable font) is still experimental in this Compose version.
@OptIn(ExperimentalTextApi::class)
object OpenCodeTypography {

    /**
     * Prose and UI chrome: Inter, one variable font file used at four weights.
     * Licence and source: `THIRD_PARTY_NOTICES.md`.
     */
    val ui: FontFamily = FontFamily(
        Font(R.font.inter, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.inter, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.inter, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.inter, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )

    /** Code, paths, and anything copyable. */
    val mono: FontFamily = FontFamily.Monospace

    /** Base body text for chat messages. */
    val body: TextStyle = TextStyle(
        fontFamily = ui,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.1.sp,
    )

    /** Slightly denser, for secondary labels and metadata. */
    val bodySmall: TextStyle = body.copy(
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )

    /** Message author, section labels. */
    val label: TextStyle = TextStyle(
        fontFamily = ui,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
    )

    /** Screen titles. Deliberately modest: the top bar is chrome, not a poster. */
    val title: TextStyle = TextStyle(
        fontFamily = ui,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    )

    /** Code inside a fenced block. */
    val code: TextStyle = TextStyle(
        fontFamily = mono,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Normal,
    )

    /** Inline code inside prose: same family, slightly smaller to sit on the baseline. */
    val codeInline: TextStyle = TextStyle(
        fontFamily = mono,
        fontSize = 13.5.sp,
        lineHeight = 20.sp,
    )

    /** Composer input. Matches body so the text does not jump on send. */
    val input: TextStyle = TextStyle(
        fontFamily = ui,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    )

    /** Material3 typography, mapped so Material components match the scale above. */
    fun material(): Typography = Typography(
        titleLarge = title,
        titleMedium = title.copy(fontSize = 15.sp),
        bodyLarge = body,
        bodyMedium = body,
        bodySmall = bodySmall,
        labelLarge = label.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        labelMedium = label,
        labelSmall = label.copy(fontSize = 11.sp),
    )
}
