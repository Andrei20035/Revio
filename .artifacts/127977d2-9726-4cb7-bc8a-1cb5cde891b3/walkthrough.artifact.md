# Walkthrough: Fixed DEX build error

I have fixed the issue where the instrumentation tests failed to build due to space characters in test function names.

## Changes

### Instrumentation Tests

I updated `FeedGatedCardTest.kt` to replace spaces with underscores in test function names. This ensures compatibility with older DEX versions (prior to 040) which are used because the project's `minSdk` is 26.

#### [FeedGatedCardTest.kt](file:///Users/andrei/Proiecte/revio-workspace/revio-android/app/src/androidTest/java/com/revio/app/features/feed/FeedGatedCardTest.kt)

```diff
-    @Test
-    fun `cardul se randeaza intotdeauna cu un slot de imagine, atomic cu restul continutului`() {
+    @Test
+    fun cardul_se_randeaza_intotdeauna_cu_un_slot_de_imagine_atomic_cu_restul_continutului() {
```

```diff
-    @Test
-    fun `textul Image unavailable offline nu mai exista in card`() {
+    @Test
+    fun textul_Image_unavailable_offline_nu_mai_exista_in_card() {
```

## Verification Results

### Automated Tests

I verified the fix by running the specific Gradle task that was previously failing:
- Executed: `gradle_build(":app:dexBuilderDebugAndroidTest")`
- Result: **Build finished successfully.**

The project can now successfully generate DEX files for the instrumentation tests.
