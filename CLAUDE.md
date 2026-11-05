# JellyChan — istruzioni per Claude Code

Client Android TV / Fire TV per server Jellyfin. Fork non ufficiale di
[jellyfin-androidtv](https://github.com/jellyfin/jellyfin-androidtv) v0.19.10 con interfaccia ridisegnata
(palette navy, Inter, card arrotondate, hero a carosello). Autore: Antonio (Roma). Parla italiano, tono diretto e
tecnico; se un'impostazione è debole dillo e proponi l'alternativa.

Target principale: **Fire TV Stick 4K Max (Fire OS 8, Android 11)**. Funziona anche su Android TV.

## Regole non negoziabili

1. **Branding**: nel codice, nelle stringhe, nelle risorse e nella UI non devono comparire il nome/logo Jellyfin
   né il nome ElegantFin. Eccezioni consentite: la frase "Jellyfin server" (compatibilità) e la sezione Credits del
   README (basato su Jellyfin for Android TV; design ispirato a ElegantFin di lscambo13, nessun codice copiato).
   Il client si presenta al server come "JellyChan". Policy: https://jellyfin.org/docs/project/branding
2. **Licenza**: GPL-2.0, come upstream. Non copiare codice da ElegantFin o da altri progetti con licenza incompatibile.
3. **Package Kotlin**: resta `org.jellyfin.androidtv` (scelta voluta, facilita il merge dall'upstream).
   `applicationId` = `io.github.antonioh501.jellychan`. Non rinominare i package.
4. **Segreti**: mai committare keystore, password o IP/credenziali del server. Il repo è pubblico.
5. **Passi rischiosi** (push --force, cancellazioni, cambio applicationId o chiave di firma, reset di versione):
   chiedi conferma prima.

## Convenzioni

- Prefisso risorse nuove: `jc_` (drawable, layout, id). Id Kotlin `jcXxx`.
- Stringhe: ogni nuova stringa in `values/strings.xml` **e** `values-it/strings.xml`.
- Nei vector drawable usa colori `#AARRGGBB` espliciti, non `@android:color/white` (rompe `mergeReleaseResources`).
- Versione: `jellychan.version` in `gradle.properties` (SemVer, es. `0.1.0-alpha.2`). `versionCode` è calcolato da
  `getVersionCode` (0.1.0-alpha.1 → 10001). Non toccare `versionCode` a mano.
- Changelog: `CHANGELOG.md` (Keep a Changelog). Ogni modifica visibile all'utente va sotto `[Unreleased]`.
- Commit piccoli e descrittivi, in inglese. Branch di lavoro `feature/...`, merge su `main`.
- Il logo (medusa kawaii lilla, gradiente #C4B5FD→#7C6CF0, sfondo #111827) ha i sorgenti SVG in `branding/`.
  Colori tema in `app/src/main/res/values/logo.xml` e `colors.xml`.

## Ambiente di build (PC Windows di Antonio)

Servono JDK 21, Android SDK (platform-tools incluso per `adb`) e Git. Controlla cosa manca e installalo/guida
Antonio. Imposta `ANDROID_HOME` (o `sdk.dir` in `local.properties`, già ignorato da git).

Build:

```sh
./gradlew assembleDebug           # APK debug, applicationId con suffisso .debug, installabile accanto alla release
./gradlew assembleRelease -x lint # APK release firmato (serve la chiave, vedi sotto)
```

L'APK esce in `app/build/outputs/apk/release/` come `jellychan-v<versione>-release.apk`.

### Firma

`app/build.gradle.kts` legge quattro proprietà (da `-P`, `gradle.properties` utente o variabili d'ambiente):

| Proprietà | Variabile d'ambiente |
|---|---|
| `keystore.file` | `KEYSTORE_FILE` |
| `keystore.password` | `KEYSTORE_PASSWORD` |
| `signing.key.alias` | `SIGNING_KEY_ALIAS` |
| `signing.key.password` | `SIGNING_KEY_PASSWORD` |

Keystore: `jellychan-release.jks` sul PC di Antonio, in `C:\Users\anton\Downloads\jellyfin-firetv\`; alias e
password sono nel file `jellychan-release-key.txt` nella stessa cartella. **Leggi quel file, non copiare i valori
nel repo.** Se la chiave cambia, l'APK non si installa sopra la versione precedente (serve disinstallare).

## Test su Fire TV Stick (adb)

La stick sta in rete locale con ADB via rete abilitato (Opzioni sviluppatore → Debug ADB). IP della stick e del
server: vedi `CLAUDE.local.md` (non versionato). Sequenza tipica:

```sh
adb connect <IP_STICK>:5555
adb -s <IP_STICK>:5555 install -r app/build/outputs/apk/release/jellychan-v<versione>-release.apk
adb -s <IP_STICK>:5555 shell monkey -p io.github.antonioh501.jellychan -c android.intent.category.LEANBACK_LAUNCHER 1
```

Telecomando via adb: `shell input keyevent` 19 su, 20 giù, 21 sinistra, 22 destra, 23 OK, 4 indietro, 3 home,
224 sveglia. Screenshot: `shell screencap -p /sdcard/s.png` poi `adb pull /sdcard/s.png`.
**Antonio non è davanti alla TV: dopo ogni modifica visiva installa, naviga e mostragli gli screenshot.**
Il daemon adb può morire tra un comando e l'altro: riconnettiti ogni volta.

## Limiti noti (non ritentare)

- Fire OS 8 ignora `android:banner` per le app sideloaded: il banner nel launcher non si può ottenere. Accettato.
- Quick Connect: l'approvazione dal server può arrivare con ritardo; se "non funziona" controlla prima se la sessione
  è già autenticata.

## Rilascio (pre-release su GitHub)

1. Aggiorna `jellychan.version` in `gradle.properties` e sposta le voci da `[Unreleased]` alla nuova sezione del
   `CHANGELOG.md` (con data e link di confronto in fondo).
2. `./gradlew assembleRelease -x lint`, installa sulla stick e verifica.
3. Commit su `main`, tag `vX.Y.Z-alpha.N` (o `-beta.N`) e push dei tag.
4. GitHub → Releases → nuova release dal tag, **segna come pre-release**, note dal CHANGELOG, allega l'APK.
   Con `gh` autenticato si può fare da terminale: `gh release create vX.Y.Z-alpha.N <apk> --prerelease --notes-file ...`.

Stato: `v0.1.0-alpha.1` è sul branch `main`; la release GitHub va creata a mano se il tag non esiste ancora sul remoto.

## Idee aperte (solo se richieste)

- Tab "Preferiti" nella toolbar della home.
- Ramo storico `elegantfin-theme` (non pubblicato) con la cronologia passo-passo.
