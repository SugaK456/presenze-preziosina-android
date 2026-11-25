package it.preziosina.presenze

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Widget: la tessera-mascotte appesa + fumetto + pulsanti ESCO / RIENTRO. */
class PresenzeWidget : AppWidgetProvider() {

    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        val v = vista(c)
        ids.forEach { m.updateAppWidget(it, v) }
        Promemoria.programma(c)
    }

    override fun onReceive(c: Context, intent: Intent) {
        super.onReceive(c, intent)
        val stato = when (intent.action) {
            AZ_ESCO -> "fuori"
            AZ_RIENTRO -> "dentro"
            else -> return
        }
        if (!Prefs.collegato(c)) return
        val pr = goAsync()   // tiene vivo il receiver durante l'invio in rete
        Thread {
            try { Azioni.inviaOraOInCoda(c, stato) } finally { pr.finish() }
        }.start()
    }

    /** Testi del fumetto: titolo + riga sotto */
    data class Fumetto(val titolo: String, val sotto: String, val allerta: Boolean)

    companion object {
        const val AZ_ESCO = "it.preziosina.presenze.ESCO"
        const val AZ_RIENTRO = "it.preziosina.presenze.RIENTRO"

        fun aggiornaTutti(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            val ids = m.getAppWidgetIds(ComponentName(c, PresenzeWidget::class.java))
            if (ids.isEmpty()) return
            val v = vista(c)
            ids.forEach { m.updateAppWidget(it, v) }
        }

        private fun azione(c: Context, az: String, req: Int): PendingIntent {
            val i = Intent(c, PresenzeWidget::class.java).setAction(az)
            return PendingIntent.getBroadcast(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun apriApp(c: Context): PendingIntent {
            val i = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(c, 3, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        fun nomeStato(stato: String?) = if (stato == "fuori") "FUORI" else "DENTRO"

        fun oraUltimo(c: Context): String = SimpleDateFormat("HH:mm", Locale.ITALY).format(Date(Prefs.ora(c)))

        /** Riga di stato (usata anche nell'app) */
        fun testoStato(c: Context): String {
            if (!Prefs.collegato(c)) return "App non collegata"
            val stato = Prefs.stato(c) ?: return "Tocca Esco quando esci, Rientro quando torni"
            return when (Prefs.esito(c)) {
                "invio" -> "Invio in corso…"
                "coda" -> "${nomeStato(stato)} · senza rete, lo invio appena torna"
                "codice" -> "Codice non valido: ricollega l'app"
                else -> "registrato alle ${oraUltimo(c)}"
            }
        }

        fun fumetto(c: Context): Fumetto {
            if (!Prefs.collegato(c)) return Fumetto("Ciao! Collegami all'app 💕", "Tocca qui per aprirla", false)
            val stato = Prefs.stato(c)
            if (Promemoria.inCorso(c)) {
                val ora = if (stato == null) "Segna se sei dentro o fuori" else "Ora risulti ${nomeStato(stato)}"
                return Fumetto("Controlla che il tuo status sia veritiero!", ora, true)
            }
            if (stato == null) return Fumetto("Ciao! Dove sei adesso?", "Tocca Esco o Rientro", false)
            return when (Prefs.esito(c)) {
                "invio" -> Fumetto("Un attimo…", "Sto inviando ${nomeStato(stato)}", false)
                "coda" -> Fumetto("Niente rete 📶", "Invio ${nomeStato(stato)} appena torna", false)
                "codice" -> Fumetto("Ops, codice non valido", "Apri l'app e ricollegala", true)
                else -> if (stato == "fuori")
                    Fumetto("Sei FUORI 🚪 A dopo!", "dalle ${oraUltimo(c)}", false)
                else
                    Fumetto("Sei DENTRO 🏠 Al calduccio!", "dalle ${oraUltimo(c)}", false)
            }
        }

        /** Numero della stanza da stampare sulla tessera ("–" se l'app è stata collegata con un link vecchio) */
        fun stanzaTesto(c: Context): String = Prefs.stanza(c)?.takeIf { it.isNotBlank() } ?: "–"

        /** Tessera-mascotte: verde DENTRO, rossa FUORI, gialla nell'ora del promemoria, grigia se non collegata */
        fun immagine(c: Context): Int = when {
            !Prefs.collegato(c) -> R.drawable.tessera_neutra
            Promemoria.inCorso(c) || Prefs.esito(c) == "codice" -> R.drawable.tessera_attenta
            Prefs.stato(c) == "fuori" -> R.drawable.tessera_fuori
            Prefs.stato(c) == "dentro" -> R.drawable.tessera_dentro
            else -> R.drawable.tessera_neutra
        }

        fun vista(c: Context): RemoteViews {
            val v = RemoteViews(c.packageName, R.layout.widget_presenze)
            val collegato = Prefs.collegato(c)
            val stato = Prefs.stato(c)
            val f = fumetto(c)

            v.setImageViewResource(R.id.w_omina, immagine(c))
            v.setTextViewText(R.id.w_stanza, stanzaTesto(c))
            v.setInt(R.id.w_fumetto, "setBackgroundResource", if (f.allerta) R.drawable.fumetto_allerta else R.drawable.fumetto)
            v.setTextViewText(R.id.w_titolo, f.titolo)
            v.setTextViewText(R.id.w_sotto, f.sotto)

            // il pulsante dello stato attuale è "acceso", l'altro più tenue
            v.setInt(R.id.w_esco, "setBackgroundResource", if (stato == "fuori") R.drawable.btn_esco_on else R.drawable.btn_esco_off)
            v.setInt(R.id.w_rientro, "setBackgroundResource", if (stato == "dentro") R.drawable.btn_rientro_on else R.drawable.btn_rientro_off)

            if (collegato) {
                v.setOnClickPendingIntent(R.id.w_esco, azione(c, AZ_ESCO, 1))
                v.setOnClickPendingIntent(R.id.w_rientro, azione(c, AZ_RIENTRO, 2))
            } else {
                v.setOnClickPendingIntent(R.id.w_esco, apriApp(c))
                v.setOnClickPendingIntent(R.id.w_rientro, apriApp(c))
            }
            v.setOnClickPendingIntent(R.id.w_fumetto, apriApp(c))
            v.setOnClickPendingIntent(R.id.w_omina, apriApp(c))
            return v
        }
    }
}
