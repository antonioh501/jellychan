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

Keystore: `jellychan-release.jks` sul PC di Antonio, fuori dal repo (percorso in `CLAUDE.local.md`); alias e
password sono nel file `jellychan-release-key.txt` nella stessa cartella. **Non stampare né copiare i valori
nel repo o in chat**: carica le variabili d'ambiente da quel file con uno script senza mostrarne il contenuto. Se
`KEYSTORE_FILE` nel file è un segnaposto, usa il percorso di `CLAUDE.local.md`. Se la chiave cambia, l'APK non si
installa sopra la versione precedente (serve disinstallare).

## Test su Fire TV Stick (adb)

La stick sta in rete locale con ADB via rete abilitato (Opzioni sviluppatore → Debug ADB). IP della stick e del
server: vedi `CLAUDE.local.md` (non versionato). Sequenza tipica:

```sh
adb connect <IP_STICK>:5555
adb -s <IP_STICK>:5555 install -r app/build/outputs/apk/release/jellychan-v<versione>-release.apk
adb -s <IP_STICK>:5555 shell monkey -p io.github.antonioh501.jellychan -c android.intent.category.LEANBACK_LAUNCHER 1
```

Telecomando via adb: `shell input keyevent` 19 su, 20 giù, 21 sinistra, 22 destra, 23 OK, 4 indietro, 3 home,
224 sveglia. Screenshot: `adb -s <IP_STICK>:5555 exec-out screencap -p > s.png` (in Git Bash evita
`shell screencap` + `pull`: la conversione dei path rompe `/sdcard/...`; in alternativa premetti `MSYS_NO_PATHCONV=1`).
**Antonio non è davanti alla TV: dopo ogni modifica visiva installa, naviga e mostragli gli screenshot.**
Il daemon adb può morire tra un comando e l'altro: riconnettiti ogni volta.

## Note UI (Compose)

- Seerr (Scopri) sta in `ui/seerr/`, pagine in Compose dentro Fragment. Le stringhe `jc_seerr_*` vanno in entrambe le lingue;
  i contenuti TMDB seguono la lingua di sistema (`Locale.getDefault().language`), con fallback inglese.
- Su una card focalizzabile non applicare `scale` al composable che prende il focus: i genitori con scroll
  (`verticalScroll`, `LazyColumn`) vedono i bounds ingranditi e si spostano (righe che "ballano", titolo tagliato).
  Zoom sul box interno, focus sul box esterno (vedi `SeerrCard.kt`).
- Evita `verticalScroll` dove il focus può finire su elementi di altezza diversa: il bring-into-view automatico
  sovrascrive gli scroll manuali. Per due sole posizioni usa un offset animato (vedi `SeerrDetailFragment.kt`).

## Limiti noti (non ritentare)

- Fire OS 8 ignora `android:banner` per le app sideloaded: il banner nel launcher non si può ottenere. Accettato.
- Quick Connect: l'approvazione dal server può arrivare con ritardo; se "non funziona" controlla prima se la sessione
  è già autenticata.

## Firma di commit e tag

Ambito: firma **Git** di commit e tag annotati. Non è la firma Android dell'APK (sezione "Firma", chiave invariata) né
un'attestazione di build/release: sono quattro cose distinte e nessuna implica le altre. Una release GitHub non è un
oggetto Git firmabile.

Stato (ricognizione 2026-11-09): nessun commit del repo è firmato; i 5 tag `v0.1.0-alpha.1…5` sono tutti
lightweight; i workflow in `.github/workflows/` (build, lint, test, validate) non creano commit, tag né release,
quindi oggi commit e tag nascono solo da chi opera in locale/remoto. Nell'ambiente cloud effimero di Claude la
configurazione globale punta all'identità `Claude <noreply@anthropic.com>` e a un signer dell'ambiente: **non è
l'identità di Antonio e non va usata per firmare al suo posto**. In locale e in ambiente remoto la configurazione
di firma non è condivisa: verificala ogni volta.

Configurazione (solo con identità e chiave approvate da Antonio, scope `--local`, mai globale salvo richiesta):
`gpg.format=ssh`, `user.signingkey=<percorso reale della .pub, non versionato>`, `commit.gpgsign=true`,
`tag.gpgsign=true`. La chiave privata deve essere nell'agent del firmatario; la pubblica va registrata su GitHub
come *Signing key* (non basta come Authentication key) e l'email del committer deve essere associata all'account.
Se GPG/SSH è già configurato e funzionante, non cambiarlo. Per la verifica locale SSH serve un
`gpg.ssh.allowedSignersFile` con principal e chiave pubblica approvati, fuori dal repo: è fiducia locale, distinta
dalla verifica indipendente di GitHub. Non salvare nel repo percorsi personali, chiavi private, token o passphrase.

Regole permanenti:

1. Ogni nuovo commit di Claude è firmato: `git commit -S`, con `commit.gpgsign=true`. Non disabilitare la firma per
   aggirare errori: se fallisce, fermarsi e diagnosticare.
2. Prima di ogni commit controlla diff, identità e disponibilità del firmatario. Niente commit vuoti di prova sul
   branch di lavoro senza autorizzazione.
3. Ogni tag di versione è annotato e firmato, creato sullo SHA completo approvato:
   `git tag -s <VERSIONE> <SHA_COMPLETO> -m "Release <VERSIONE>"` (placeholder illustrativi). Mai tag lightweight.
4. Crea, verifica e pusha prima il tag; poi pubblica la release su quel tag esistente (`gh release create ... --verify-tag`).
   Né `gh`, né la UI né un workflow devono creare implicitamente un tag non firmato.
5. Verifica in locale: `git verify-commit <SHA>`, `git verify-tag <TAG>`, e che il tag risolva allo SHA atteso
   (`git rev-parse <TAG>^{commit}`).
6. Push e pubblicazione solo secondo le autorizzazioni di Antonio. Dopo il push verifica su GitHub **separatamente**
   commit e oggetto tag (per un tag annotato interroga l'oggetto tag, non solo il commit dereferenziato) e riporta i
   motivi di un'eventuale mancata verifica. Non dichiarare "Verified" sulla base del solo esito locale.
7. Se in futuro commit o tag nascessero in GitHub Actions, la stessa politica va implementata nel workflow con un
   firmatario autorizzato e credenziali gestite in modo sicuro e approvate da Antonio: la config locale non passa al
   runner, nessuna chiave privata in YAML/CLAUDE.md/log/repo né esposta a PR non fidate. Senza infrastruttura sicura:
   fermarsi con un piano.
8. Vietato, salvo approvazione separata e dettagliata: riscrivere commit pubblicati, spostare o sostituire tag
   esistenti, force push, cancellare release. I tag storici restano com'è (lightweight, non firmati).
9. Non cambiare prerelease/latest come effetto collaterale della firma. Non toccare chiave APK, artefatti o note di
   release per ottenere il badge.
10. Non creare né pubblicare una versione solo per provare la firma senza autorizzazione.

## Rilascio (pre-release su GitHub)

1. Aggiorna `jellychan.version` in `gradle.properties` e sposta le voci da `[Unreleased]` alla nuova sezione del
   `CHANGELOG.md` (con data e link di confronto in fondo).
2. `./gradlew assembleRelease -x lint`, installa sulla stick e verifica.
3. Commit firmato su `main`, poi tag **annotato e firmato** `vX.Y.Z-alpha.N` (o `-beta.N`) sullo SHA completo
   approvato (vedi "Firma di commit e tag"), verifica locale e push del solo tag.
4. GitHub → Releases → nuova release **da un tag già esistente** (mai lasciare che la UI o `gh` lo creino),
   **segna come pre-release**, note dal CHANGELOG, allega l'APK.
   Con `gh` autenticato: `gh release create vX.Y.Z-alpha.N <apk> --verify-tag --prerelease --notes-file ...`.

Stato: ultima pre-release pubblicata `v0.1.0-alpha.3`. Esiste solo il branch `main` (locale e remoto): i branch
`feature/...` si cancellano dopo il merge.

## Idee aperte (solo se richieste)

- Tab "Preferiti" nella toolbar della home.
