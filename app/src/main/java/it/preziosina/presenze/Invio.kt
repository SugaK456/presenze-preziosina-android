package it.preziosina.presenze

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Richiesta a Firestore: cambia SOLO lo stato del proprio pulsante (pulsanti/{codice}). */
object Rete {
    @Throws(IOException::class)
    fun invia(progetto: String, chiave: String?, codice: String, stato: String): Int {
        val q = if (chiave.isNullOrBlank()) "" else "?key=" + URLEncoder.encode(chiave, "UTF-8")
        val url = URL("https://firestore.googleapis.com/v1/projects/$progetto/databases/(default)/documents:commit$q")
        val nome = "projects/$progetto/databases/(default)/documents/pulsanti/$codice"
        val body = "{\"writes\":[{" +
            "\"update\":{\"name\":\"$nome\",\"fields\":{\"stato\":{\"stringValue\":\"$stato\"}}}," +
            "\"updateMask\":{\"fieldPaths\":[\"stato\"]}," +
            "\"updateTransforms\":[{\"fieldPath\":\"aggiornato\",\"setToServerValue\":\"REQUEST_TIME\"}]," +
            "\"currentDocument\":{\"exists\":true}" +
            "}]}"
        val con = url.openConnection() as HttpURLConnection
        try {
            con.requestMethod = "POST"
            con.connectTimeout = 8000
            con.readTimeout = 8000
            con.doOutput = true
            con.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            con.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = con.responseCode
            try { (if (code < 400) con.inputStream else con.errorStream)?.close() } catch (_: IOException) {}
            return code
        } finally {
            con.disconnect()
        }
    }
}

object Azioni {
    private const val LAVORO = "invio-stato"

    /**
     * Prova a inviare subito. Se manca la rete, mette l'invio in coda (WorkManager)
     * e lo spedisce appena il telefono torna online. Va chiamata FUORI dal thread principale.
     */
    fun inviaOraOInCoda(c: Context, stato: String) {
        val ctx = c.applicationContext
        val codice = Prefs.codice(ctx) ?: return
        val progetto = Prefs.progetto(ctx) ?: return
        Promemoria.chiudi(ctx)   // premere un pulsante = stato controllato: l'omina torna serena
        Prefs.setUltimo(ctx, stato, "invio")
        PresenzeWidget.aggiornaTutti(ctx)
        try {
            val code = Rete.invia(progetto, Prefs.chiave(ctx), codice, stato)
            gestisciRisposta(ctx, stato, code, mostraMessaggi = true)
            if (code in 200..299) WorkManager.getInstance(ctx).cancelUniqueWork(LAVORO)
            else if (code >= 500 || code == 429) accoda(ctx, stato)
        } catch (e: IOException) {
            Prefs.setUltimo(ctx, stato, "coda")
            PresenzeWidget.aggiornaTutti(ctx)
            accoda(ctx, stato)
            avviso(ctx, "Senza rete: lo invio appena torna il segnale")
        }
    }

    /** @return true se l'invio è riuscito */
    fun gestisciRisposta(ctx: Context, stato: String, code: Int, mostraMessaggi: Boolean): Boolean {
        return when {
            code in 200..299 -> {
                Prefs.setUltimo(ctx, stato, "ok")
                PresenzeWidget.aggiornaTutti(ctx)
                if (mostraMessaggi) avviso(ctx, if (stato == "fuori") "Registrato: sei FUORI ✓" else "Registrato: sei DENTRO ✓")
                true
            }
            code == 400 || code == 403 || code == 404 || code == 412 -> {
                // codice rigenerato/eliminato o residente rimosso dall'elenco
                Prefs.setUltimo(ctx, stato, "codice")
                PresenzeWidget.aggiornaTutti(ctx)
                if (mostraMessaggi) avviso(ctx, "Codice non valido: apri l'app e ricollegala")
                false
            }
            else -> {
                Prefs.setUltimo(ctx, stato, "coda")
                PresenzeWidget.aggiornaTutti(ctx)
                false
            }
        }
    }

    fun accoda(c: Context, stato: String) {
        val req = OneTimeWorkRequestBuilder<InvioWorker>()
            .setInputData(workDataOf("stato" to stato))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        // REPLACE: conta solo l'ultimo pulsante premuto
        WorkManager.getInstance(c).enqueueUniqueWork(LAVORO, ExistingWorkPolicy.REPLACE, req)
    }

    fun avviso(c: Context, testo: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(c, testo, Toast.LENGTH_SHORT).show() }
    }
}

/** Invio rimandato: parte quando torna la connessione. */
class InvioWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        val stato = inputData.getString("stato") ?: return Result.failure()
        val codice = Prefs.codice(c) ?: return Result.failure()
        val progetto = Prefs.progetto(c) ?: return Result.failure()
        return try {
            val code = withContext(Dispatchers.IO) { Rete.invia(progetto, Prefs.chiave(c), codice, stato) }
            when {
                Azioni.gestisciRisposta(c, stato, code, mostraMessaggi = false) -> Result.success()
                code >= 500 || code == 429 -> Result.retry()
                else -> Result.failure()
            }
        } catch (e: IOException) {
            Result.retry()
        }
    }
}
