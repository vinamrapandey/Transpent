# Transpent Issues And Fix Log

## 2026-05-14

- Issue: Project initially only had a debug APK. User requested a proper exported working app and emulator verification.
  - Status: Fixed.
  - Action: Preparing release build configuration, emulator run, and install verification.

- Issue: Build outputs and local Gradle caches appeared in `git status`.
  - Status: Fixed.
  - Action: Updated `.gitignore` to exclude `.gradle-build-cache/`, APK/AAB outputs, and local build artifacts.

- Issue: Release APK needed signing instead of debug-only packaging.
  - Status: Fixed.
  - Action: Created a local release keystore and configured the `release` build type. Keystore files are intentionally ignored by git.

- Issue: Signed release build needed verification.
  - Status: Fixed.
  - Action: Built signed release APK with Gradle task `assembleRelease`.

- Issue: Emulator boot polling produced a PowerShell null-output warning before sys.boot_completed returned 1.
  - Status: Not app-impacting.
  - Action: Emulator still booted successfully; continued with install/run verification.

- Issue: Emulator could launch the Google account picker, but the current emulator image has no usable signed-in Google account flow to complete login.
  - Status: Environment limitation, not fully app-verified.
  - Action: Verified the app process stays alive with no crash logs after tapping Google sign-in. Full Google login and Drive backup must be validated on a real phone or an emulator signed into Google Play services.

- Issue: Signed release APK needed install/run verification.
  - Status: Fixed.
  - Action: Installed `Transpent-release.apk` on `Medium_Phone_API_36.1` emulator and launched package `com.transpent.app` successfully.


- Issue: Source needed to be committed and pushed to GitHub for later continuation.
  - Status: Fixed.
  - Action: Created local git commit, renamed branch to `main`, added remote `https://github.com/vinamrapandey/Transpent.git`, and pushed to origin.

- Issue: Proper installable release artifact needed outside debug build.
  - Status: Fixed.
  - Action: Exported signed release APK at `C:\Users\Lenovo\Downloads\Transpent App\Transpent-release.apk`.

- Issue: Text in customer, supplier, and product cards was clipped and partly invisible because cards were forced to a fixed 48dp height while containing padded 19-22sp text.
  - Status: Fixed.
  - Action: Changed cards to wrap their content with a safe minimum height and adjusted title text sizing/font padding so tab content remains visible.
