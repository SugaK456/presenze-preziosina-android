package it.preziosina.presenze

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

/** Dati salvati sul telefono: collegamento (codice pulsante) e ultimo invio. */
object Prefs {
    private const val FILE = "presenze"

    fun sp(c: Context): SharedPreferences = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun codice(c: Context): String? = sp(c).getString("codice", null)
    fun progetto(c: Context): String? = sp(c).getString("progetto", null)
    fun chiave(c: Context): String? = sp(c).getString("chiave", null)
    fun collegato(c: Context): Boolean = codice(c) != null && progetto(c) != null

    fun salvaCollegamento(c: Context, l: Collegamento) {
        sp(c).edit()
            .putString("codice", l.codice)
            .putString("progetto", l.progetto)
            .putString("chiave", l.chiave)
            .remove("stato").remove("esito").remove("ora")
            .apply()
    }

    fun scollega(c: Context) = sp(c).edit().clear().apply()

    /** esito: "invio" | "coda" | "ok" | "codice" (codice non valido) */
    fun setUltimo(c: Context, stato: String, esito: String) {
        sp(c).edit()
            .putString("stato", stato)
            .putString("esito", esito)
            .putLong("ora", System.currentTimeMillis())
            .apply()
    }

    fun stato(c: Context): String? = sp(c).getString("stato", null)
    fun esito(c: Context): String? = sp(c).getString("esito", null)
    fun ora(c: Context): Long = sp(c).getLong("ora", 0L)
}

data class Collegamento(val codice: String, val progetto: String, val chiave: String?) {
    companion object {
        private val CODICE_OK = Regex("^[A-Za-z0-9]{20,64}$")
        private val PROGETTO_OK = Regex("^[a-z0-9-]{4,40}$")
        private val CHIAVE_OK = Regex("^[A-Za-z0-9_-]{10,80}$")

        /** Accetta il link preziosina://collega?c=...&p=...&k=... (anche incollato con spazi attorno). */
        fun daTesto(testo: String?): Collegamento? {
            if (testo.isNullOrBlank()) return null
            val inizio = testo.indexOf("preziosina://")
            if (inizio < 0) return null
            val link = testo.substring(inizio).trim().split(Regex("\\s"))[0]
            return daUri(Uri.parse(link))
        }

        fun daUri(u: Uri?): Collegamento? {
            if (u == null || u.scheme != "preziosina" || u.host != "collega") return null
            val c = u.getQueryParameter("c") ?: return null
            val p = u.getQueryParameter("p") ?: return null
            val k = u.getQueryParameter("k")
            if (!CODICE_OK.matches(c) || !PROGETTO_OK.matches(p)) return null
            if (k != null && !CHIAVE_OK.matches(k)) return null
            return Collegamento(c, p, k)
        }
    }
}
