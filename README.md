# Presenze Preziosina – app Android

App Android con **widget sulla Home** (pulsanti **ESCO** e **RIENTRO**) collegata alla web app https://presenze-preziosina.web.app.

- Nessun SDK Firebase e nessun login nell'app: si collega con il **codice pulsante** personale creato nella web app (link `preziosina://collega?c=…&p=…&k=…`).
- Un tocco sul widget invia lo stato a Firestore (`pulsanti/{codice}`), senza aprire l'app.
- Senza rete l'invio viene messo in coda (WorkManager) e parte appena torna la connessione; conta l'ultimo pulsante premuto.
- Se il codice viene rigenerato/eliminato, il widget mostra "Codice non valido: apri l'app".

## Compilazione
Ogni push su `main` avvia GitHub Actions (`.github/workflows/build.yml`) che produce l'APK firmato:
Actions → ultima esecuzione → Artifacts → `presenze-preziosina-apk`.
Si può anche avviare a mano: Actions → "Compila APK" → Run workflow.

## Distribuzione
Caricare `presenze-preziosina.apk` nella cartella della web app e pubblicarlo con
`firebase deploy --only hosting --project presenze-preziosina`: il link di download sarà
https://presenze-preziosina.web.app/presenze-preziosina.apk

## Firma
La chiave di firma NON è nel repository. È salvata nei secret del repository (Settings → Secrets and variables → Actions):
- `KEYSTORE_BASE64`: il file keystore codificato in base64
- `KEYSTORE_PASSWORD`: la sua password

Deve restare sempre la stessa, altrimenti gli aggiornamenti non si installano sopra la versione precedente. Conservarne una copia al sicuro.

## Requisiti
Android 8.0 o superiore.
