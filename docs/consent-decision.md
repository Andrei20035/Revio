# Decizie: model de consent pentru Analytics/Crashlytics

Pas 0.2 din planul de observabilitate (closed testing). Doar decizie — fără cod.

## Stare actuală (verificat)

- Nu există niciun mecanism de consent în cod: grep pentru `setAnalyticsCollectionEnabled`,
  `setConsent`, `ConsentType`, `firebase_analytics_collection_enabled` etc. → zero rezultate
  în `app/src/main`. Colectarea Firebase, acolo unde SDK-ul e prezent, pornește automat la
  instalare, necondiționat.
- `REVIO_LAUNCH_COPY.md:276-284` conține deja un draft de ecran de consent, marcat
  `[REQUIRED BEFORE LAUNCH: ... Do not ship this screen until the SDK exists]`:
  > **Help improve Revio** — Allow anonymous app-usage analytics to help us understand
  > performance and improve features. Analytics are not used for advertising. You can change
  > this choice later in Settings.
  > Buttons: **Not now** / **Allow analytics**
- `features/settings/PrivacyPolicyScreen.kt:153-154` (politica in-app) descrie deja consimțământul
  ca bază legală pentru analytics — text existent, indiferent de decizia de mai jos.
- Piețele de testare din closed testing **nu sunt confirmate încă** — nu se poate exclude
  prezența unor testeri din UE/SEE/UK.

## Decizie

**Opt-in global**, pe toate piețele, fără excepție regională.

Motiv: cu piețele de testare neconfirmate, orice model care presupune "opt-out în afara UE"
ar necesita fie geolocalizare a utilizatorului (cost tehnic + suprafață suplimentară de date
colectate, chiar înainte de consimțământ), fie o presupunere nesigură despre cine se
înscrie în closed testing. Opt-in global elimină ambele riscuri: e conform GDPR/UK GDPR
indiferent unde se dovedesc a fi testerii, și reutilizează ecranul de consent deja redactat
în `REVIO_LAUNCH_COPY.md:276-284` fără adaptare per regiune.

## Ce implică decizia (pentru pașii ulteriori din plan, neimplementat aici)

- **Colectarea pornește oprită implicit** pe toate build-urile de release, până la o alegere
  explicită a utilizatorului.
- Necesar: ecran de consent funcțional (pasul **1.5c**, condiționat explicit de această decizie
  fiind opt-in), persistență a alegerii, și un toggle ulterior în Settings pentru schimbarea ei
  ("You can change this choice later in Settings" — deja promis în copy).
- Consent gate-ul (`setAnalyticsCollectionEnabled` / `setCrashlyticsCollectionEnabled`, pasul
  **1.5b**) trebuie să pornească din starea **oprit**, nu **pornit cu opțiune de refuz**.
- Politica de confidențialitate (`PrivacyPolicyScreen.kt`) rămâne consistentă cu implementarea —
  nu necesită rescriere suplimentară față de ce prevede deja pasul **0b.1/0b.3** din plan.
- Fără segmentare pe piață — nu se implementează detectare de regiune pentru acest gate.

## Ce rămâne deschis

- Piețele exacte de closed testing nu sunt confirmate. Dacă ulterior se stabilește ferm că
  testarea rămâne 100% în afara UE/UK/California, această decizie poate fi revizuită — dar
  opt-in global rămâne opțiunea implicit sigură până atunci.
- Confirmare finală din partea unui jurist înainte de lansarea publică (dincolo de closed
  testing) — această decizie acoperă doar faza de closed testing descrisă în plan.
