package it.preziosina.presenze

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import java.util.Calendar

/**
 * Promemoria agli orari stabiliti: notifica "Sei ancora DENTRO/FUORI?"
 * e, per un'ora, l'omina sul widget chiede di controllare lo stato.
 */
object Promemoria {
    /** Orari (ora, minuti). Per cambiarli basta modificare questa lista. */
    val ORARI = listOf(9 to 0, 13 to 0, 18 to 30, 22 to 0)
    const val DURATA_MS = 60L * 60L * 1000L

    const val CANALE = "promemoria"
    const val NOTIFICA_ID = 42

    const val AZ_SCATTA = "it.preziosina.presenze.PROMEMORIA"
    const val AZ_FINE = "it.preziosina.presenze.FINE_PROMEMORIA"
    const val AZ_CONFERMA = "it.preziosina.presenze.CONFERMA"
    const val AZ_CAMBIA = "it.preziosina.presenze.CAMBIA"

    fun orariTesto(): String = ORARI.joinToString("  ·  ") { (h, m) -> "%02d:%02d".format(h, m) }

    fun attivi(c: Context): Boolean = Prefs.sp(c).getBoolean("promemoria", true)

    fun setAttivi(c: Context, attivi: Boolean) {
        Prefs.sp(c).edit().putBoolean("promemoria", attivi).apply()
        if (attivi) programma(c) else { annulla(c); chiudi(c) }
    }

    /** true durante l'ora successiva a un promemoria */
    fun inCorso(c: Context): Boolean = System.currentTimeMillis() < Prefs.sp(c).getLong("promemoria_fino", 0L)

    /** Prossimo orario della lista (oggi o domani). */
    fun prossimo(adesso: Long = System.currentTimeMillis()): Long {
        var migliore = Long.MAX_VALUE
        for ((h, m) in ORARI) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = adesso
                set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= adesso + 30_000L) cal.add(Calendar.DAY_OF_YEAR, 1)
            if (cal.timeInMillis < migliore) migliore = cal.timeInMillis
        }
        return migliore
    }

    private fun intento(c: Context, azione: String, req: Int, stato: String? = null): PendingIntent {
        val i = Intent(c, PromemoriaReceiver::class.java).setAction(azione)
        if (stato != null) i.putExtra("stato", stato)
        return PendingIntent.getBroadcast(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Programma la prossima sveglia (senza permessi speciali: può arrivare con qualche minuto di ritardo). */
    fun programma(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val pi = intento(c, AZ_SCATTA, 100)
        if (!attivi(c) || !Prefs.collegato(c)) { am.cancel(pi); return }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, prossimo(), pi)
    }

    fun annulla(c: Context) {
        c.getSystemService(AlarmManager::class.java)?.cancel(intento(c, AZ_SCATTA, 100))
    }

    /** Arriva l'orario: notifica + omina "attenta" per un'ora. */
    fun scatta(c: Context) {
        programma(c)   // prima di tutto il prossimo
        if (!attivi(c) || !Prefs.collegato(c)) return
        val fino = System.currentTimeMillis() + DURATA_MS
        Prefs.sp(c).edit().putLong("promemoria_fino", fino).apply()
        c.getSystemService(AlarmManager::class.java)
            ?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fino + 1000L, intento(c, AZ_FINE, 101))
        PresenzeWidget.aggiornaTutti(c)
        mostraNotifica(c)
    }

    /** Fine anticipata: l'utente ha confermato o ha premuto un pulsante. */
    fun chiudi(c: Context) {
        val eraInCorso = inCorso(c)
        Prefs.sp(c).edit().putLong("promemoria_fino", 0L).apply()
        c.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICA_ID)
        c.getSystemService(AlarmManager::class.java)?.cancel(intento(c, AZ_FINE, 101))
        if (eraInCorso) PresenzeWidget.aggiornaTutti(c)
    }

    fun creaCanale(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        val ch = NotificationChannel(CANALE, c.getString(R.string.canale_nome), NotificationManager.IMPORTANCE_HIGH)
        ch.description = c.getString(R.string.canale_desc)
        nm.createNotificationChannel(ch)
    }

    private fun azione(c: Context, testo: String, pi: PendingIntent): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(c, R.drawable.ic_notifica), testo, pi).build()

    fun mostraNotifica(c: Context) {
        creaCanale(c)
        val apri = PendingIntent.getActivity(
            c, 102, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = Notification.Builder(c, CANALE)
            .setSmallIcon(R.drawable.ic_notifica)
            .setColor(0xFFFF7F96.toInt())
            .setContentIntent(apri)
            .setAutoCancel(true)
            .setTimeoutAfter(DURATA_MS)
            .setCategory(Notification.CATEGORY_REMINDER)
        when (Prefs.stato(c)) {
            "fuori" -> b.setContentTitle("Sei ancora FUORI? 🚪")
                .setContentText("Se nel frattempo il tuo stato è cambiato, aggiornalo con un tocco.")
                .addAction(azione(c, "Sì, sono fuori", intento(c, AZ_CONFERMA, 103)))
                .addAction(azione(c, "Ora sono dentro", intento(c, AZ_CAMBIA, 104, "dentro")))
            "dentro" -> b.setContentTitle("Sei ancora DENTRO? 🏠")
                .setContentText("Se nel frattempo il tuo stato è cambiato, aggiornalo con un tocco.")
                .addAction(azione(c, "Sì, sono dentro", intento(c, AZ_CONFERMA, 103)))
                .addAction(azione(c, "Ora sono fuori", intento(c, AZ_CAMBIA, 105, "fuori")))
            else -> b.setContentTitle("Dove sei adesso?")
                .setContentText("Segna se sei dentro o fuori dalla residenza.")
                .addAction(azione(c, "Sono dentro", intento(c, AZ_CAMBIA, 104, "dentro")))
                .addAction(azione(c, "Sono fuori", intento(c, AZ_CAMBIA, 105, "fuori")))
        }
        try {
            c.getSystemService(NotificationManager::class.java)?.notify(NOTIFICA_ID, b.build())
        } catch (_: SecurityException) { /* notifiche non consentite: resta comunque l'omina sul widget */ }
    }
}

/** Azioni interne (sveglie e pulsanti della notifica). */
class PromemoriaReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            Promemoria.AZ_SCATTA -> Promemoria.scatta(c)
            Promemoria.AZ_FINE -> PresenzeWidget.aggiornaTutti(c)
            Promemoria.AZ_CONFERMA -> {
                Promemoria.chiudi(c)
                Azioni.avviso(c, "Perfetto, grazie! ✓")
            }
            Promemoria.AZ_CAMBIA -> {
                val stato = i.getStringExtra("stato") ?: return
                Promemoria.chiudi(c)
                val pr = goAsync()
                Thread { try { Azioni.inviaOraOInCoda(c, stato) } finally { pr.finish() } }.start()
            }
        }
    }
}

/** Dopo riavvio, aggiornamento dell'app o cambio d'ora: riprogramma i promemoria. */
class AvvioReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Promemoria.programma(c)
        PresenzeWidget.aggiornaTutti(c)
    }
}
