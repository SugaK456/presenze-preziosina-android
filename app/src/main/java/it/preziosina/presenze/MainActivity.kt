package it.preziosina.presenze

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var tCollegamento: TextView
    private lateinit var tStato: TextView
    private lateinit var bEsco: Button
    private lateinit var bRientro: Button
    private lateinit var bWidget: Button
    private lateinit var sezioneCollega: View
    private lateinit var campoLink: EditText
    private lateinit var bScollega: Button

    private val ascolta = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> runOnUiThread { aggiornaUi() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tCollegamento = findViewById(R.id.t_collegamento)
        tStato = findViewById(R.id.t_stato)
        bEsco = findViewById(R.id.b_esco)
        bRientro = findViewById(R.id.b_rientro)
        bWidget = findViewById(R.id.b_widget)
        sezioneCollega = findViewById(R.id.sezione_collega)
        campoLink = findViewById(R.id.campo_link)
        bScollega = findViewById(R.id.b_scollega)

        bEsco.setOnClickListener { premi("fuori") }
        bRientro.setOnClickListener { premi("dentro") }
        bWidget.setOnClickListener { aggiungiWidget() }
        findViewById<Button>(R.id.b_incolla).setOnClickListener { incolla() }
        findViewById<Button>(R.id.b_collega).setOnClickListener { collega(campoLink.text.toString()) }
        findViewById<Button>(R.id.b_web).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.url_web))))
        }
        bScollega.setOnClickListener {
            Prefs.scollega(this)
            PresenzeWidget.aggiornaTutti(this)
            Toast.makeText(this, "App scollegata", Toast.LENGTH_SHORT).show()
        }
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
            Toast.makeText(this, "Non è un link di collegamento valido (deve iniziare con preziosina://collega)", Toast.LENGTH_LONG).show()
            return
        }
        salva(l)
    }

    private fun salva(l: Collegamento) {
        Prefs.salvaCollegamento(this, l)
        PresenzeWidget.aggiornaTutti(this)
        campoLink.setText("")
        Toast.makeText(this, "App collegata ✓ Ora aggiungi il widget alla Home", Toast.LENGTH_LONG).show()
        aggiornaUi()
    }

    private fun premi(stato: String) {
        if (!Prefs.collegato(this)) return
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

    private fun aggiornaUi() {
        val collegato = Prefs.collegato(this)
        tCollegamento.text = if (collegato) "✓ App collegata al tuo profilo" else "App non ancora collegata"
        tCollegamento.setTextColor(getColor(if (collegato) R.color.verde else R.color.rosso))
        tStato.text = PresenzeWidget.testoStato(this)
        val stato = Prefs.stato(this)
        bEsco.isEnabled = collegato
        bRientro.isEnabled = collegato
        bEsco.setBackgroundResource(if (stato == "fuori") R.drawable.btn_esco_on else R.drawable.btn_esco_off)
        bRientro.setBackgroundResource(if (stato == "dentro") R.drawable.btn_rientro_on else R.drawable.btn_rientro_off)
        bWidget.visibility = if (collegato) View.VISIBLE else View.GONE
        sezioneCollega.visibility = if (collegato) View.GONE else View.VISIBLE
        bScollega.visibility = if (collegato) View.VISIBLE else View.GONE
    }
}
