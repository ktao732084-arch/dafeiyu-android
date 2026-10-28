# Android framework and UI checks

These tests execute the app's compiled Java classes against Robolectric 4.13's Android 14 / API 34 implementation. They load the actual `build/unsigned.apk` binary resources and assets. Activity creation, dialogs, native widget click listeners, SharedPreferences, local JSON state, handlers and the app's AgentRuntime execute in the test JVM.

This is **not an Android emulator, a physical device, or proof of APK installation**. Model responses are controlled test doubles. `TestKeyStore` supplies an in-memory AES key instead of Android's real Keystore; the app's AES/GCM encryption, decryption and settings code still execute. No production model credential is used, and these tests make no paid model calls.

Covered scenarios:

1. Main activity creation and homepage widgets.
2. Five personality presets plus one random choice in first-meeting UI; manual selection reaches the prompt; later UI is read-only.
3. Random personality is selected once and survives Activity recreation.
4. Model URL validation, credential storage, readback, same-origin blank-key reuse and rejection of cross-origin key reuse without partial setting writes.
5. Memory creation and forgetting through actual dialog controls.
6. Chat input → controlled model → streamed reply → history, memory and emotion; remembered facts enter subsequent context.
7. Model failure restores the input draft and does not append a failed turn.
8. Destroying an Activity closes its pending chat and releases runtime ownership.
9. Thirty-three isolated personality initialization, locking, migration and error checks.
10. Cancel after a committed reply delivers completion exactly once.
11. Manual chat interrupts an outstanding proactive request.
12. Forgetting explicit memory also removes its source turn from later context.
13. Changing name and nickname changes the actual next prompt without changing frozen personality.
14. Memory/proactive/stream switches persist; disabled memory is excluded from context while stored entries remain; one-hour focus and cancel-focus buttons update runtime settings.

Still requires Android emulator or physical-device verification: APK installation, actual Android Keystore, OEM overlay permission behavior, foreground service lifetime, alpha-video rendering, drag/physics/animation smoothness, system background restrictions, battery use and a real model endpoint.

## Reproduce on Windows

Build the Android app first, and do not rebuild concurrently with this script because it snapshots `build/classes`.

```powershell
./run-tests.ps1 -AndroidRoot <path-to-android-directory> -Sdk <android-sdk-root> -Jdk <jdk-17-root>
```

Use optional `-Maven <path-to-mvn.cmd>` to reuse an installed Maven. Otherwise the script downloads portable Maven. It downloads AndroidX monitor 1.7.1 and espresso-idling-resource 3.6.1 AARs from Google's Maven repository and extracts `classes.jar` to `libs/monitor.jar` and `libs/espresso.jar`. The offline Android runtime is `android-runtime/android-all-instrumented-14-robolectric-10818077-i6.jar` from Maven Central; the bootstrap checks its SHA-512 against Central before use. Maven dependencies and all test results also remain under this directory. Python must be available. Requires the product's `build/classes`, `build/unsigned.apk`, manifest and Android SDK `platforms/android-34/android.jar`. The runner generates resource/manifest paths and Maven settings at execution time; no workstation-specific paths are required in distributed source files.

Review `target/surefire-reports/com.xiaoxiao.pet.UiTest.txt` and `TEST-com.xiaoxiao.pet.UiTest.xml` for executed results. A successful compile alone is not a test pass.
