# Fix DEX build error: Space characters in SimpleName

The project is failing to build the instrumentation tests because some test function names contain spaces. DEX versions prior to 040 (API 34+) do not support spaces in simple names. Since the project's `minSdk` is 26, these names must be changed to avoid spaces.

## Proposed Changes

### [Component] Instrumentation Tests

I will rename the test functions in `FeedGatedCardTest.kt` to use underscores instead of spaces. This follows the pattern already established in other instrumentation tests in the project (e.g., `RevioNavigationBottomNavTest.kt`).

#### [MODIFY] [FeedGatedCardTest.kt](file:///Users/andrei/Proiecte/revio-workspace/revio-android/app/src/androidTest/java/com/revio/app/features/feed/FeedGatedCardTest.kt)

- Rename `` `cardul se randeaza intotdeauna cu un slot de imagine, atomic cu restul continutului` `` to `` `cardul_se_randeaza_intotdeauna_cu_un_slot_de_imagine_atomic_cu_restul_continutului` ``.
- Rename `` `textul Image unavailable offline nu mai exista in card` `` to `` `textul_Image_unavailable_offline_nu_mai_exista_in_card` ``.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:dexBuilderDebugAndroidTest` to verify that the dexing process now succeeds.
- Run the instrumentation tests if a device/emulator is available: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.revio.app.features.feed.FeedGatedCardTest`

### Manual Verification
- Verify that the file `FeedGatedCardTest.kt` still compiles and the test names are readable.
