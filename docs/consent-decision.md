# Decizie: model de consent pentru Analytics/Crashlytics

Pas 0.2 din planul de observabilitate (closed testing). Doar decizie — fără cod.

> **Actualizare:** decizia de mai jos (opt-in global) a fost **înlocuită** de o decizie ulterioară
> de **opt-out global** — vezi secțiunea „Decizie actuală" mai jos. Secțiunea „Decizie inițială
> (opt-in), înlocuită" este păstrată doar ca istoric al deciziei anterioare și al motivației ei;
> nu mai descrie comportamentul implementat.

## Decizie actuală

**Opt-out global**, pe toate piețele, fără excepție regională.

- Colectarea Analytics și Crashlytics pornește **activă implicit** la instalare nouă (meta-data
  `firebase_analytics_collection_enabled` / `firebase_crashlytics_collection_enabled` = `true` în
  `app/src/main/AndroidManifest.xml`, plus fallback `true` la citirea DataStore în
  `UserPreferences.analyticsConsentGranted` când nu există nicio alegere persistată).
- Switch-ul „Help improve Revio" din Settings (`features/settings/SettingsScreen.kt`) pornește
  **ON** și permite dezactivarea oricând; ambele SDK-uri comută împreună, printr-un singur punct
  (`applyAnalyticsConsent()` în `RevioApp.kt`, reutilizat de `SettingsViewModel`).
- Un `false` deja persistat de un utilizator (inclusiv sub regimul opt-in anterior) **nu este
  niciodată suprascris automat** — fallback-ul `true` se aplică strict când cheia lipsește din
  DataStore, nu când valoarea existentă e `false`.
- Dezactivarea din Settings devine complet efectivă pentru Analytics imediat, iar pentru
  Crashlytics complet efectivă abia la următoarea lansare a aplicației (comportament documentat al
  SDK-ului); rapoartele Crashlytics nesemise deja capturate **nu** sunt șterse (nu se apelează
  `deleteUnsentReports()`).

### Rămas deschis din decizia de opt-out

- **Bază legală / GDPR:** trecerea la opt-out global schimbă baza legală tipică din consimțământ
  în interes legitim + drept de opoziție. `PrivacyPolicyScreen.kt` și `PRIVACY_POLICY.md` trebuie
  aliniate la formularea corectă, iar decizia finală necesită confirmare juridică — motivul pentru
  care decizia inițială de mai jos alesese opt-in a fost tocmai riscul GDPR/UK GDPR pentru piețe de
  testare neconfirmate; acel risc nu a dispărut, doar a fost asumat explicit odată cu trecerea la
  opt-out.
- **Piețele de testare** rămân neconfirmate ca fiind în afara UE/UK/California; opt-out global nu
  rezolvă acest lucru, doar îl asumă.
- **Onboarding:** nu există în prezent niciun ecran/notificare la prima rulare care să anunțe
  colectarea implicită înainte ca switch-ul din Settings să fie descoperit de utilizator.

---

## Decizie inițială (opt-in), înlocuită

## Stare actuală (verificat) — la momentul deciziei inițiale

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

## Decizie inițială

**Opt-in global**, pe toate piețele, fără excepție regională.

Motiv: cu piețele de testare neconfirmate, orice model care presupune "opt-out în afara UE"
ar necesita fie geolocalizare a utilizatorului (cost tehnic + suprafață suplimentară de date
colectate, chiar înainte de consimțământ), fie o presupunere nesigură despre cine se
înscrie în closed testing. Opt-in global elimină ambele riscuri: e conform GDPR/UK GDPR
indiferent unde se dovedesc a fi testerii, și reutilizează ecranul de consent deja redactat
în `REVIO_LAUNCH_COPY.md:276-284` fără adaptare per regiune.

## Ce implica decizia inițială (la momentul respectiv)

- **Colectarea pornea oprită implicit** pe toate build-urile de release, până la o alegere
  explicită a utilizatorului.
- Necesar: ecran de consent funcțional (pasul **1.5c**, condiționat explicit de această decizie
  fiind opt-in), persistență a alegerii, și un toggle ulterior în Settings pentru schimbarea ei
  ("You can change this choice later in Settings" — deja promis în copy).
- Consent gate-ul (`setAnalyticsCollectionEnabled` / `setCrashlyticsCollectionEnabled`, pasul
  **1.5b**) trebuia să pornească din starea **oprit**, nu **pornit cu opțiune de refuz**.
- Politica de confidențialitate (`PrivacyPolicyScreen.kt`) trebuia să rămână consistentă cu
  implementarea — nu necesita rescriere suplimentară față de ce prevedea deja pasul **0b.1/0b.3**
  din plan.
- Fără segmentare pe piață — nu se implementa detectare de regiune pentru acest gate.

## Ce a rămas deschis din decizia inițială

- Piețele exacte de closed testing nu erau confirmate. Dacă ulterior se stabilea ferm că
  testarea rămâne 100% în afara UE/UK/California, decizia putea fi revizuită — dar opt-in global
  rămânea opțiunea implicit sigură până atunci.
- Confirmare finală din partea unui jurist înainte de lansarea publică (dincolo de closed
  testing) — decizia inițială acoperea doar faza de closed testing descrisă în plan.
