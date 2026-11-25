package it.preziosina.presenze

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.widget.EditText
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var tChip: TextView
    private lateinit var tGrande: TextView
    private lateinit var tStato: TextView
    private lateinit var tFumetto: TextView
    private lateinit var iOmina: ImageView
    private lateinit var bEsco: TextView
    private lateinit var bRientro: TextView
    private lateinit var sezioneCollega: View
    private lateinit var sezionePromemoria: View
    private lateinit var sezioneWidget: View
    private lateinit var campoLink: EditText
    private lateinit var sPromemoria: Switch
    private lateinit var tOrari: TextView
    private lateinit var bPermesso: TextView
    private lateinit var bScollega: TextView

    private val ascolta = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> runOnUiThread { aggiornaUi() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        barreChiare()

        tChip = findViewById(R.id.t_chip)
        tGrande = findViewById(R.id.t_grande)
        tStato = findViewById(R.id.t_stato)
        tFumetto = findViewById(R.id.t_fumetto)
        iOmina = findViewById(R.id.i_omina)
        bEsco = findViewById(R.id.b_esco)
        bRientro = findViewById(R.id.b_rientro)
        sezioneCollega = findViewById(R.id.sezione_collega)
        sezionePromemoria = findViewById(R.id.sezione_promemoria)
        sezioneWidget = findViewById(R.id.sezione_widget)
        campoLink = findViewById(R.id.campo_link)
        sPromemoria = findViewById(R.id.s_promemoria)
        tOrari = findViewById(R.id.t_orari)
        bPermesso = findViewById(R.id.b_permesso)
        bScollega = findViewById(R.id.b_scollega)

        bEsco.setOnClickListener { premi("fuori") }
        bRientro.setOnClickListener { premi("dentro") }
        findViewById<View>(R.id.b_widget).setOnClickListener { aggiungiWidget() }
        findViewById<View>(R.id.b_incolla).setOnClickListener { incolla() }
        findViewById<View>(R.id.b_collega).setOnClickListener { collega(campoLink.text.toString()) }
        findViewById<View>(R.id.b_web).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.url_web))))
        }
        bPermesso.setOnClickListener { chiediPermessoNotifiche(forza = true) }
        sPromemoria.setOnCheckedChangeListener { _, attivo ->
            if (attivo != Promemoria.attivi(this)) {
                Promemoria.setAttivi(this, attivo)
                if (attivo) chiediPermessoNotifiche(forza = false)
            }
        }
        bScollega.setOnClickListener {
            Promemoria.annulla(this)
            Promemoria.chiudi(this)
            Prefs.scollega(this)
            PresenzeWidget.aggiornaTutti(this)
            Toast.makeText(this, "App scollegata", Toast.LENGTH_SHORT).show()
        }

        Promemoria.creaCanale(this)
        Promemoria.programma(this)
        gestisciLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        gestisciLink(intent)
    }

    override fun onResume() {
        super.onResume()
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(ascolta)
        aggiornaUi()
    }

    override fun onPause() {
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(ascolta)
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        aggiornaUi()
    }

    // ---------------- collegamento ----------------

    private fun gestisciLink(i: Intent?) {
        if (i?.action != Intent.ACTION_VIEW) return
        val l = Collegamento.daUri(i.data)
        if (l == null) {
            Toast.makeText(this, "Link di collegamento non valido", Toast.LENGTH_LONG).show()
            return
        }
        salva(l)
    }

    private fun incolla() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val testo = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        if (testo.isNullOrBlank()) {
            Toast.makeText(this, "Gli appunti sono vuoti: copia prima il link dalla web app", Toast.LENGTH_LONG).show()
            return
        }
        campoLink.setText(testo)
        collega(testo)
    }

    private fun collega(testo: String) {
        val l = Collegamento.daTesto(testo)
        if (l == null) {
            Toast.makeText(this, "Non è un link valido (deve iniziare con preziosina://collega)", Toast.LENGTH_LONG).show()
            return
        }
        salva(l)
    }

    private fun salva(l: Collegamento) {
        Prefs.salvaCollegamento(this, l)
        PresenzeWidget.aggiornaTutti(this)
        Promemoria.programma(this)
        campoLink.setText("")
        Toast.makeText(this, "App collegata ✓ Ora aggiungi il widget alla Home", Toast.LENGTH_LONG).show()
        aggiornaUi()
        chiediPermessoNotifiche(forza = false)
    }

    // ---------------- azioni ----------------

    private fun premi(stato: String) {
        if (!Prefs.collegato(this)) {
            Toast.makeText(this, "Prima collega l'app", Toast.LENGTH_SHORT).show()
            return
        }
        Thread { Azioni.inviaOraOInCoda(applicationContext, stato) }.start()
    }

    private fun aggiungiWidget() {
        val m = getSystemService(AppWidgetManager::class.java)
        if (m != null && m.isRequestPinAppWidgetSupported) {
            m.requestPinAppWidget(ComponentName(this, PresenzeWidget::class.java), null, null)
        } else {
            Toast.makeText(this, "Tieni premuto su uno spazio vuoto della Home → Widget → Presenze Preziosina", Toast.LENGTH_LONG).show()
        }
    }

    private fun notificheConsentite(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() ?: true
    }

    private fun chiediPermessoNotifiche(forza: Boolean) {
        if (notificheConsentite()) return
        val giaChiesto = Prefs.sp(this).getBoolean("notifiche_chieste", false)
        val puoChiedere = Build.VERSION.SDK_INT >= 33 &&
            (!giaChiesto || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))
        when {
            puoChiedere && (forza || !giaChiesto) -> {
                Prefs.sp(this).edit().putBoolean("notifiche_chieste", true).apply()
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
            }
            forza -> {
                // permesso già negato in modo definitivo (o notifiche spente): apro le impostazioni dell'app
                startActivity(Intent("android.settings.APP_NOTIFICATION_SETTINGS")
                    .putExtra("android.provider.extra.APP_PACKAGE", packageName))
            }
        }
    }

    // ---------------- interfaccia ----------------

    private fun barreChiare() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        @Suppress("DEPRECATION")
        window.statusBarColor = getColor(R.color.sfondo)
    }

    private fun aggiornaUi() {
        val collegato = Prefs.collegato(this)
        val stato = Prefs.stato(this)
        val allerta = collegato && Promemoria.inCorso(this)

        // chip
        tChip.text = if (collegato) "✓ Collegata" else "Non collegata"
        tChip.setBackgroundResource(if (collegato) R.drawable.chip_ok else R.drawable.chip_ko)
        tChip.setTextColor(getColor(if (collegato) R.color.verde else R.color.rosso))

        // stato grande
        when {
            !collegato -> { tGrande.text = "Ciao! 👋"; tGrande.setTextColor(getColor(R.color.scuro)) }
            stato == "fuori" -> { tGrande.text = "Sei FUORI"; tGrande.setTextColor(getColor(R.color.rosso)) }
            stato == "dentro" -> { tGrande.text = "Sei DENTRO"; tGrande.setTextColor(getColor(R.color.verde)) }
            else -> { tGrande.text = "Dove sei?"; tGrande.setTextColor(getColor(R.color.scuro)) }
        }
        tStato.text = if (collegato) PresenzeWidget.testoStato(this) else "Collega l'app per iniziare"

        // tessera-mascotte e fumetto
        iOmina.setImageResource(PresenzeWidget.immagine(this))
        tFumetto.visibility = if (allerta) View.VISIBLE else View.GONE

        // pulsanti
        bEsco.setBackgroundResource(if (stato == "fuori") R.drawable.btn_esco_on else R.drawable.btn_esco_off)
        bRientro.setBackgroundResource(if (stato == "dentro") R.drawable.btn_rientro_on else R.drawable.btn_rientro_off)
        bEsco.alpha = if (collegato) 1f else 0.5f
        bRientro.alpha = if (collegato) 1f else 0.5f

        // sezioni
        sezioneCollega.visibility = if (collegato) View.GONE else View.VISIBLE
        sezionePromemoria.visibility = if (collegato) View.VISIBLE else View.GONE
        sezioneWidget.visibility = if (collegato) View.VISIBLE else View.GONE
        bScollega.visibility = if (collegato) View.VISIBLE else View.GONE

        // promemoria
        val attivi = Promemoria.attivi(this)
        if (sPromemoria.isChecked != attivi) sPromemoria.isChecked = attivi
        tOrari.text = if (attivi)
            "Ti chiedo se sei ancora dentro o fuori alle\n${Promemoria.orariTesto()}"
        else
            "Promemoria spenti."
        bPermesso.visibility = if (attivi && !notificheConsentite()) View.VISIBLE else View.GONE
    }
}
