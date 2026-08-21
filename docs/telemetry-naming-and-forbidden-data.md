# Convenția de naming pentru telemetrie + lista de date interzise

Pas 0.3 din planul de observabilitate (closed testing). Document normativ — orice PR care
adaugă sau modifică un eveniment de analytics, un breadcrumb, o custom key Crashlytics, sau un
log structurat pe server trebuie să respecte regulile de mai jos. Depinde de decizia de consent
din pasul 0.2 (opt-in global) — colectarea descrisă aici pornește doar după consimțământ.

Acest fișier e mirror-uit identic în `revio-android/docs/` și `revio-server/docs/`, fiindcă cele
două repo-uri sunt independente (fiecare poate fi clonat separat) — nu există o locație comună
de unde ambele să poată referi un singur fișier.

## Convenția de naming pentru evenimente Analytics

- **Format:** `snake_case`, structură `<domeniu>_<obiect>_<acțiune>`.
- **Prefixe de domeniu în uz:** `auth_`, `onb_` (onboarding), `post_`, `feed_`, `session_`,
  `app_`. Prefixele existente `fp_feedback_` și `sf_` (din `core/feedback/FeedbackAnalytics.kt`)
  rămân neschimbate — nu se redenumesc evenimentele deja livrate.
- **Succes/eșec ca un singur eveniment:** parametru `outcome ∈ {success, failure}`, **nu**
  evenimente separate de tip `_success`/`_failed`. Motiv: funnelul rămâne o singură serie de
  evenimente; rata de eșec devine un filtru pe `outcome`, nu o interogare separată.
- **`failure_code`:** obligatoriu când `outcome=failure`. Valoare **doar** dintr-o enumerare deja
  existentă în cod (ex. `AuthErrorCode`, `ERROR_CODE_NETWORK`) sau dintr-un set fix documentat
  explicit la evenimentul respectiv (ex. `http_5xx`, `compression_failed`, `timeout`).
  **Niciodată `exception.message` sau text liber.**
- **Bucket-uri obligatorii** pentru orice valoare continuă (evită cardinalitate mare și scurgere
  indirectă de date):
  - `duration_bucket`: `lt_1s, 1_3s, 3_10s, 10_30s, gte_30s`
  - `size_bucket`: `lt_500kb, 500kb_1mb, 1_2mb, gte_2mb`
  - `retry_bucket`: `0, 1, 2, gte_3`
- **Deduplicare:** un `attempt_id` — UUID generat local, **efemer, nepersistat**, per încercare
  (nu per utilizator, nu per device) — perechează evenimentele `_start`/`_result` ale aceleiași
  acțiuni.
- **Limite Firebase de respectat prin construcție** (documentate oficial): nume de eveniment
  ≤40 caractere; ≤25 parametri per eveniment; nume de parametru ≤40 caractere; valoare de
  parametru ≤100 caractere; ≤25 user properties; ≤500 nume de evenimente distincte per aplicație.
- **Cardinalitate:** niciun parametru nu depășește ~20 valori distincte, cu excepția
  `failure_code` (≤30, limitat de mărimea enumerării sursă). Zero identificatori ca parametru.

## Convenția pentru breadcrumbs și custom keys (Crashlytics)

- Breadcrumbs (`Crashlytics.log()`) — o linie per tranziție de flow, folosite ca **context
  pentru un crash viitor**, nu ca metrică agregată. Buget total: **64 kB** per raport
  (limită documentată Firebase pentru custom logging).
- Custom keys — starea curentă la momentul unui eventual crash: `flow`, `stage`,
  `last_api_code`, `is_offline`, `screen`, `build_type`, `last_request_id`.
  **Niciodată un identificator de utilizator sau de cont ca valoare de custom key.**

## Convenția pentru logging structurat pe server

- Fiecare linie de log pe o cale de request poartă `callId` (din `X-Request-Id`, pas 3.1-3.2 —
  neimplementat încă la data acestui document).
- Nivele: `INFO` pentru rezultate de business (succes/eșec așteptat, cod cunoscut), `WARN` pentru
  eșecuri best-effort (challenge evaluation, cleanup storage), `ERROR` pentru excepții
  neașteptate cu stack trace.
- Identificatorii (userId, postId, credentialId) apar în clar în logurile de server — asta e deja
  practica existentă și corectă (`PostService.kt:152`, `UserRoutes.kt:83-88` etc.), spre deosebire
  de Analytics/Crashlytics unde sunt interzise. Regula de mai jos pentru date interzise se aplică
  identic pe server pentru categoriile explicit enumerate (email, parolă, token etc.), nu pentru
  UUID-uri interne.

## Lista de date interzise (Analytics, breadcrumbs, custom keys — fără excepție)

Niciodată, sub nicio formă, ca parametru de eveniment, breadcrumb, custom key sau user property:

- email
- telefon
- username
- nume complet
- data nașterii
- parolă
- access token / refresh token
- caption (text de postare)
- text de comentariu
- orice text liber introdus de utilizator
- request body / response body
- coordonate exacte (latitudine/longitudine)
- URL-uri sau path-uri de imagini (`objectKey`)
- mesaje brute de excepție (`exception.message`, stack trace ca string)

**Interzis și ca identificator de analytics/breadcrumb/custom key** (chiar dacă nu e "PII" în
sens clasic): `userId`, `postId`, `credentialId`, `deviceId`. Pe server, unde corelarea e
necesară pentru diagnostic, se folosesc hash-uri, niciodată valori brute — vezi practica
existentă `hashEmailForLogging()` din `WaitlistLookupService.kt`/`AuthService.kt` ca precedent
de urmat (cu observația că versiunea actuală e nesărată — remediere separată, în afara acestui
document).

## Ce NU acoperă acest document

- Nu decide *dacă* colectarea pornește (asta e pasul 0.2 — decis: opt-in global).
- Nu instalează Crashlytics, nu scrie cod de sanitizare, nu creează evenimente noi.
- Nu creează un fișier de PR template — niciunul nu există azi în `revio-android/.github/` sau
  `revio-server/.github/`. Când unul va exista, ar trebui să refere acest document; până atunci,
  respectarea lui e o convenție de revizuire manuală a codului.
