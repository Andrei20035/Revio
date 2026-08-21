# Rollout gradual și criterii de rollback

Pas 6.8 din planul de observabilitate (closed testing). Doar criteriile — construirea
efectivă a etapelor de rollout (Play Console) și acțiunea de rollback rămân manuale, executate
de cine monitorizează releasul, nu automatizate de acest document.

Mirror-uit identic în `revio-android/docs/` și `revio-server/docs/`.

## Etapele de rollout

Closed testing pe Google Play, staged prin procentul de utilizatori eligibili din track:

| Etapă | % utilizatori | Durată minimă înainte de a trece mai departe | Condiție de avansare |
|---|---|---|---|
| 1 | Intern (echipă) | 1 zi | Fără crash nou, fără regresie vizibilă manual |
| 2 | 10% | 2 zile | Toate criteriile de rollback de mai jos respectate |
| 3 | 50% | 2 zile | Toate criteriile de rollback de mai jos respectate |
| 4 | 100% | — | Toate criteriile de rollback de mai jos respectate |

Avansarea la etapa următoare e manuală (Play Console), nu automată — cere confirmarea
explicită a cui monitorizează dashboard-urile din pas 6.6.

## Criterii de rollback

Identice cu pragurile deja stabilite în plan (secțiunea Faza 6 și
`docs/dashboards-and-success-criteria.md`, metrica 1 și 7) — **nu se introduce un prag
divergent aici**.

| # | Criteriu | Prag | Sursă |
|---|---|---|---|
| 1 | Crash-free users rate | **< 98%** pe orice etapă activă | Crashlytics |
| 2 | Volum de trafic | **> 3× estimarea** pentru etapa curentă | Analytics / server |
| 3 | Rata de erori 5xx pe rutele P0 | **≥ 1%** | `/metrics` (pas 3.6) |
| 4 | PII în telemetrie | **orice apariție confirmată** | audit manual / raport din DebugView |

Criteriul 4 (PII) e necondiționat de etapă — o singură apariție confirmată oprește imediat
rollout-ul, indiferent de procentul curent.

## Acțiune de rollback

1. **Oprește avansarea** — nu trece la procentul următor din Play Console; dacă e deja în
   desfășurare, Play Console permite doar oprirea rollout-ului la procentul curent (nu o
   coborâre automată a lui), deci pasul 2 e obligatoriu pentru orice criteriu care cere
   retragere efectivă, nu doar plafonare.
2. **Retrage releasul** din Play Console (halt sau revert la releasul anterior cunoscut bun).
3. **Criteriul 4 (PII) — acțiune suplimentară imediată**: `setAnalyticsCollectionEnabled(false)`
   (mecanismul de consent din pas 1.5b/1.5c), pentru a opri colectarea înainte ca retragerea
   din Play Console să ajungă la toți utilizatorii activi.
4. Investighează folosind dashboard-urile din pas 6.6 (Stabilitate / Funnel P0 / Sănătate
   server) și `X-Request-Id` (pas 6.5) pentru a corela crash-urile cu request-urile server.

## Ce NU decide acest document

- Nu construiește rollout-ul efectiv în Play Console — e o acțiune manuală, executată separat.
- Nu automatizează verificarea criteriilor — cine monitorizează dashboard-urile din 6.6 decide
  manual dacă un criteriu e încălcat.
- Nu introduce praguri noi față de cele deja scrise în plan — vezi
  `docs/dashboards-and-success-criteria.md`.
