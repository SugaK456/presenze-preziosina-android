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

/** Widget sulla Home: due pulsanti ESCO / RIENTRO + riga di stato. */
class PresenzeWidget : AppWidgetProvider() {

    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        val v = vista(c)
        ids.forEach { m.updateAppWidget(it, v) }
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

        fun testoStato(c: Context): String {
            if (!Prefs.collegato(c)) return "Tocca per collegare l'app"
            val stato = Prefs.stato(c) ?: return "Tocca ESCO quando esci, RIENTRO quando torni"
            val nome = if (stato == "fuori") "FUORI" else "DENTRO"
            val ora = SimpleDateFormat("HH:mm", Locale.ITALY).format(Date(Prefs.ora(c)))
            return when (Prefs.esito(c)) {
                "invio" -> "Invio in corso…"
                "coda" -> "$nome · senza rete, lo invio appena torna"
                "codice" -> "Codice non valido: apri l'app"
                else -> "Sei $nome · registrato alle $ora"
            }
        }

        fun vista(c: Context): RemoteViews {
            val v = RemoteViews(c.packageName, R.layout.widget_presenze)
            val collegato = Prefs.collegato(c)
            val stato = Prefs.stato(c)
            // il pulsante dell'ultimo stato è "acceso", l'altro più tenue
            v.setInt(R.id.w_esco, "setBackgroundResource", if (stato == "fuori") R.drawable.btn_esco_on else R.drawable.btn_esco_off)
            v.setInt(R.id.w_rientro, "setBackgroundResource", if (stato == "dentro") R.drawable.btn_rientro_on else R.drawable.btn_rientro_off)
            v.setTextViewText(R.id.w_stato, testoStato(c))
            if (collegato) {
                v.setOnClickPendingIntent(R.id.w_esco, azione(c, AZ_ESCO, 1))
                v.setOnClickPendingIntent(R.id.w_rientro, azione(c, AZ_RIENTRO, 2))
            } else {
                v.setOnClickPendingIntent(R.id.w_esco, apriApp(c))
                v.setOnClickPendingIntent(R.id.w_rientro, apriApp(c))
            }
            v.setOnClickPendingIntent(R.id.w_stato, apriApp(c))
            return v
        }
    }
}
