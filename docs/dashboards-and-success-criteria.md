# Dashboards și success criteria pentru closed testing

Pas 0.5 din planul de observabilitate (closed testing). Doar definirea metricilor și a
pragurilor — **fără construirea efectivă a dashboard-urilor** (asta e pas ulterior, condiționat
de Faza 2/3 fiind implementate). Depinde de pasul 0.3 (naming/schema de evenimente înghețată).

Mirror-uit identic în `revio-android/docs/` și `revio-server/docs/` — cele două repo-uri sunt
independente, iar metricile de mai jos combină date de client și de server.

## Cele 8 metrici, cu praguri

Fiecare metrică e legată de un eveniment/log deja definit în schema din plan (secțiunea
"Schema minimală de evenimente"), nu de un eveniment nou inventat aici.

| # | Metrică | Sursă | Prag / criteriu de succes | De ce contează |
|---|---|---|---|---|
| 1 | **Crash-free users rate** | Crashlytics (client) | **≥ 98%** utilizatori fără crash/ANR per zi | Semnal de bază de stabilitate; identic cu pragul de rollback deja stabilit în Faza 6 a planului — nu se introduce un al doilea prag divergent |
| 2 | **Non-fatal report rate** | Crashlytics (client), din ev. `post_compress_result`, `session_restore_failed` etc. | > 0 rapoarte în primele 48h de la activare | Verifică faptul brut că pipeline-ul de captură (pas 1.7d) chiar funcționează — o rată de zero e la fel de suspectă ca una foarte mare |
| 3 | **Rata de finalizare auth** | `auth_start` → `auth_result{outcome=success}` (client) | **≥ 90%** dintre încercările fără eroare de validare client-side | Primul pas din orice funnel P0; sub prag indică probleme de UX sau server, nu doar erori de business așteptate |
| 4 | **Rata de finalizare onboarding** | `onb_step_view` → `onb_completed` (client) | **≥ 80%**; `onb_abandoned_after_commit` **= 0 cazuri tolerate fără investigare** | Risc cunoscut (H8/G12): cont creat, onboarding incomplet, nereparabil. Orice apariție a `onb_abandoned_after_commit` trebuie investigată individual, nu doar agregată |
| 5 | **Rata de succes la creare postare** | `post_create_start` → `post_upload_result{outcome=success}` (client) | **≥ 85%** | Flow P0 cu cea mai mare betweenness în graf (ImageUploadViewModel); cel mai conectat, deci cel mai vizibil la degradare |
| 6 | **Rata de postări duplicate** | server, corelat pe `userId`+fereastră scurtă de timp la `post_create_result` | **0 cazuri tolerate** | Risc identificat și confirmat structural (corecția C5): `PostDAO.insert` comite într-o tranzacție separată de scoring; un eșec de scoring + retry client poate produce duplicate. Această metrică e testul de foc pentru pasul 3.4a/3.4b |
| 7 | **Rata de erori 5xx pe server** | `post_create_result`/`auth_result` server-side + log ERROR (pas 3.3a) | **< 1%** din request-urile pe rutele P0 | Azi orice 5xx e nelogat (`PostRoutes.kt:212-214`); pragul devine măsurabil abia după pasul 3.3a/3.4c |
| 8 | **Timp până la primul conținut din Feed** | `feed_first_content` (client), `duration_bucket` | **≥ 90%** din sesiuni în bucket-ul `lt_1s` sau `1_3s` (cache sau network) | Ultimul pas din funnelul P0 înainte ca utilizatorul să vadă valoare din aplicație |

## Grupare pe dashboard (fără construire — doar organizare logică)

- **Dashboard "Stabilitate"** — metricile 1, 2. Sursă: consola Crashlytics.
- **Dashboard "Funnel P0"** — metricile 3, 4, 5, 8, ca o singură serie orizontală
  (auth → onboarding → post creation → feed), pentru a vedea vizual unde se pierd utilizatorii.
  Sursă: Firebase Analytics (DebugView în fază de testare, apoi consola standard).
- **Dashboard "Sănătate server"** — metricile 6, 7. Sursă: logurile structurate de pe VPS
  (pas 3.2) + endpoint-ul de metrici (pas 3.6), ambele neimplementate încă la data acestui
  document.

## Ce NU decide acest document

- Nu construiește niciun dashboard efectiv (Firebase console, Grafana sau altceva) — asta
  depinde de Faza 2/3 fiind implementate și de decizia din pasul 0.4 (categoria de error
  tracking server).
- Nu adaugă evenimente noi față de cele 23 din schema deja definită.
- Pragurile de mai sus sunt puncte de plecare pentru closed testing, nu ținte contractuale —
  se pot recalibra după primele date reale (pas 6.6 din plan).
