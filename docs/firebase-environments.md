# Decizie: separarea proiectelor Firebase și strategia de applicationId

Pas 0.1 din planul de observabilitate (closed testing). Doar decizie — fără cod.

## Stare actuală (verificat)

- Un singur proiect Firebase: `carspotter-f2b68` (`app/google-services.json`).
- `applicationId = "com.revio.social"` (`app/build.gradle.kts:23`), fără `applicationIdSuffix`,
  fără product flavors — debug și release folosesc **același** applicationId, deci **același**
  client Firebase.
- `google-services.json` conține 2 intrări de client: `com.revio.app` (vechi, nefolosit) și
  `com.revio.social` (activ).
- Doar două medii de backend există în `gradle.properties`, fără concept de staging:
  - `DEBUG_API_BASE_URL` → IP local/hotspot
  - `RELEASE_API_BASE_URL` → `https://api.joinrevio.app/api/`
- Fără server Firebase — Firebase e folosit doar din Android (Analytics; Crashlytics încă
  neconfigurat funcțional). Această decizie **nu** privește `revio-server`.

## Decizie

**Două proiecte Firebase**, aliniate cu cele două medii de backend care există deja azi.
Nu se introduce un al treilea proiect (staging) — nu există un backend de staging separat de
care să depindă, iar un proiect suplimentar ar adăuga cost de întreținere fără beneficiu imediat.

| Mediu | Proiect Firebase | applicationId | Backend |
|---|---|---|---|
| Debug (dezvoltare locală) | **proiect nou**, ex. `revio-dev` | `com.revio.social.debug` | `DEBUG_API_BASE_URL` |
| Release (closed testing → producție) | **`carspotter-f2b68`** (existent, redenumit ulterior dacă se dorește) | `com.revio.social` | `RELEASE_API_BASE_URL` |

Motiv: astăzi orice build de debug de pe orice laptop scrie evenimente Analytics în
`carspotter-f2b68`, alături de traficul real al testerilor din closed testing. Cele două
proiecte separă strict aceste fluxuri.

## Strategia de `applicationIdSuffix`

- **Debug:** `applicationIdSuffix = ".debug"` → applicationId efectiv `com.revio.social.debug`.
- **Release:** neschimbat, `com.revio.social`.

Implicații de reținut pentru pasul de implementare (Faza 1, pas 1.3a din plan, **nu acum**):
- Necesită un `google-services.json` nou, descărcat din noul proiect `revio-dev`, pentru
  varianta de build debug (fie prin `app/src/debug/google-services.json`, fie printr-un
  product flavor dedicat).
- Google Sign-In (`play-services-auth`) leagă un OAuth client de `applicationId` + amprenta
  SHA-1 a certificatului de semnare → **trebuie înregistrat un OAuth client nou** pentru
  `com.revio.social.debug` în noul proiect, altfel login-ul Google eșuează pe build-urile debug.
- Intrarea `com.revio.app` din `google-services.json`-ul actual e neutilizată; se poate omite
  din configurarea noului proiect.

## Ce rămâne de făcut manual (în afara acestui plan)

- Creare proiect Firebase nou (`revio-dev`) în consola Firebase.
- Activare Analytics + Crashlytics pe ambele proiecte.
- Configurare OAuth client Google Sign-In pentru `com.revio.social.debug` (SHA-1 debug keystore).
- Descărcare `google-services.json` pentru fiecare proiect.

Aceste acțiuni necesită acces la consola Firebase/Google Cloud a proiectului și sunt în afara
scopului acestui pas (decizie + documentare). Implementarea tehnică (fișiere Gradle,
`google-services.json` per build type, binding-ul `NoOpAnalyticsClient` pe debug) este acoperită
de pașii **1.3a** și **1.3b** din planul de observabilitate.
