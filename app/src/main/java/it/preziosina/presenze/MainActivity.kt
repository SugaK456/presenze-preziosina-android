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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Schermata principale: stesso stile della web app (barra scura, interruttore grande, card bianche). */
class MainActivity : Activity() {

    private lateinit var sw: View
    private lateinit var tGrande: TextView
    private lateinit var tSub: TextView
    private lateinit var tStato: TextView
    private lateinit var iTessera: ImageView
    private lateinit var banner: View
    private lateinit var tBanner: TextView
    private lateinit var vOnline: View
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
        @Suppress("DEPRECATION")
        window.statusBarColor = getColor(R.color.scuro)

        sw = findViewById(R.id.sw)
        tGrande = findViewById(R.id.t_grande)
        tSub = findViewById(R.id.t_sub)
        tStato = findViewById(R.id.t_stato)
        iTessera = findViewById(R.id.i_omina)
        banner = findViewById(R.id.banner)
        tBanner = findViewById(R.id.t_banner)
        vOnline = findViewById(R.id.v_online)
        sezioneCollega = findViewById(R.id.sezione_collega)
        sezionePromemoria = findViewById(R.id.sezione_promemoria)
        sezioneWidget = findViewById(R.id.sezione_widget)
        campoLink = findViewById(R.id.campo_link)
        sPromemoria = findViewById(R.id.s_promemoria)
        tOrari = findViewById(R.id.t_orari)
        bPermesso = findViewById(R.id.b_permesso)
        bScollega = findViewById(R.id.b_scollega)

        // come nella web app: tocca l'interruttore per passare DENTRO <-> FUORI
        sw.setOnClickListener {
            if (!Prefs.collegato(this)) {
                findViewById<View>(R.id.sezione_collega).requestFocus()
                Toast.makeText(this, "Prima collega l'app al tuo profilo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            premi(if (Prefs.stato(this) == "fuori") "dentro" else "fuori")
        }
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
        Toast.makeText(this, "App collegata. Ora aggiungi il widget alla Home.", Toast.LENGTH_LONG).show()
        aggiornaUi()
        chiediPermessoNotifiche(forza = false)
    }

    // ---------------- azioni ----------------

    private fun premi(stato: String) {
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
            forza -> startActivity(Intent("android.settings.APP_NOTIFICATION_SETTINGS")
                .putExtra("android.provider.extra.APP_PACKAGE", packageName))
        }
    }

    private fun online(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ---------------- interfaccia ----------------

    private fun aggiornaUi() {
        val collegato = Prefs.collegato(this)
        val stato = Prefs.stato(this)
        val fuori = stato == "fuori"

        // interruttore
        when {
            !collegato -> {
                sw.setBackgroundResource(R.drawable.switch_neutro)
                tGrande.text = "CIAO"
                tSub.text = "Collega l'app per iniziare"
            }
            fuori -> {
                sw.setBackgroundResource(R.drawable.switch_fuori)
                tGrande.text = "FUORI"
                tSub.text = "Tocca quando rientri"
            }
            else -> {
                sw.setBackgroundResource(R.drawable.switch_dentro)
                tGrande.text = "DENTRO"
                tSub.text = "Tocca quando esci"
            }
        }
        iTessera.setImageResource(PresenzeWidget.immagine(this))

        // riga di stato
        tStato.text = when {
            !collegato -> "App non collegata"
            stato == null -> "Nessuno stato inviato da questo telefono"
            else -> {
                val t = PresenzeWidget.testoStato(this)
                if (t.startsWith("registrato")) "Aggiornato ${t.removePrefix("registrato ")}" else t
            }
        }

        // avviso promemoria
        val allerta = collegato && Promemoria.inCorso(this)
        banner.visibility = if (allerta) View.VISIBLE else View.GONE
        tBanner.text = if (stato == null) "Segna se sei dentro o fuori." else "Ora risulti ${if (fuori) "FUORI" else "DENTRO"}: se non è così, tocca l'interruttore."

        vOnline.alpha = if (online()) 1f else 0.3f

        // sezioni
        sezioneCollega.visibility = if (collegato) View.GONE else View.VISIBLE
        sezionePromemoria.visibility = if (collegato) View.VISIBLE else View.GONE
        sezioneWidget.visibility = if (collegato) View.VISIBLE else View.GONE
        bScollega.visibility = if (collegato) View.VISIBLE else View.GONE

        // promemoria
        val attivi = Promemoria.attivi(this)
        if (sPromemoria.isChecked != attivi) sPromemoria.isChecked = attivi
        tOrari.text = if (attivi)
            "Ti chiedo se sei ancora dentro o fuori alle ${Promemoria.orariTesto().replace("  ·  ", ", ")}."
        else
            "Promemoria spenti."
        bPermesso.visibility = if (attivi && !notificheConsentite()) View.VISIBLE else View.GONE
    }
}
