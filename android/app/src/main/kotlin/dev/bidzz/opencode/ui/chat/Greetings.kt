package dev.bidzz.opencode.ui.chat

import android.content.Context
import kotlin.random.Random

/**
 * The two playful lines on the empty chat: a pun or a developer joke, picked fresh on every
 * visit, in the app's language. The idea comes from Cl0de's greetings (MIT); the lines are
 * written for this app, not copied from it.
 */
object Greetings {

    internal data class Greeting(val aEn: String, val aId: String, val bEn: String, val bId: String)

    internal val all = listOf(
        Greeting("Hello, world.", "Halo, dunia.", "What are we building today?", "Hari ini kita bikin apa?"),
        Greeting("git status:", "git status:", "clean. Ready for the next idea.", "bersih. Siap untuk idemu berikutnya."),
        Greeting("Compiling thoughts…", "Menyusun pikiran…", "Done. What's the task?", "Selesai. Tugasnya apa?"),
        Greeting("Talk is cheap.", "Omong kosong itu murah.", "Show me the prompt.", "Tunjukkan promptnya."),
        Greeting("Keep calm", "Tenang saja", "and git commit.", "dan git commit."),
        Greeting("It's dangerous to code alone.", "Berbahaya ngoding sendirian.", "Take me with you.", "Ajak aku."),
        Greeting("404:", "404:", "worries not found.", "kekhawatiran tidak ditemukan."),
        Greeting("Loading creativity…", "Memuat kreativitas…", "99%. Your turn.", "99%. Giliranmu."),
        Greeting("There's no place", "Tidak ada tempat", "like 127.0.0.1.", "seperti 127.0.0.1."),
        Greeting("It's not a bug,", "Itu bukan bug,", "it's an undocumented feature.", "itu fitur yang belum ada dokumennya."),
        Greeting("Fuel for the compiler:", "Bahan bakar kompiler:", "one prompt, coming up.", "satu prompt, langsung jalan."),
        Greeting("The floor is lava.", "Lantainya lava.", "The workspace is fine.", "Workspacenya aman-aman saja."),
    )

    /** The greeting for [indonesian]; [seed] makes the pick deterministic for tests. */
    fun pick(indonesian: Boolean, seed: Long = Random.nextLong()): Pair<String, String> {
        val g = all[Random(seed).nextInt(all.size)]
        return if (indonesian) g.aId to g.bId else g.aEn to g.bEn
    }

    /** The greeting in the context's language (the wrapped context already carries it). */
    fun from(context: Context): Pair<String, String> =
        pick(indonesian = context.resources.configuration.locales[0].language == "id")
}
