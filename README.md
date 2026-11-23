# বাংলা টাইপ (Bangla Type) — Vartex Android Voice IME

**বাংলা টাইপ (Bangla Type)** is a lightweight, native Android Input Method Editor (IME) written in Kotlin under the **Vartex** namespace (`com.vartex.banglatype.ime`). It lets you speak in Bangla and type **directly inside Messages (SMS), WhatsApp, Facebook**, and every other Android app **without any copy-paste**.

## Zero Copy-Paste: How It Works

Unlike a web page (which requires copying or sharing text into WhatsApp/Facebook), an Android `InputMethodService` is a system-level keyboard. Once enabled in Android Settings, **বাংলা টাইপ** pops up whenever you tap any text field in WhatsApp, Facebook, or Messages and injects Bangla text directly via `InputConnection.commitText()`.

## Architecture Highlights

1. **System-Level IME Integration**: Uses Android's native `android.inputmethodservice.InputMethodService`.
2. **Native Speech-to-Text Engine**: Uses Android's built-in `android.speech.SpeechRecognizer` backed by Google Speech Services on the device (supports `bn-BD` and `bn-IN` online and with downloadable offline Bangla voice packs).
3. **Automated Cloud Builds**: Includes `.github/workflows/build-apk.yml` so you can compile `app-debug.apk` and `app-release.aab` cleanly via GitHub Actions without a heavy local IDE.

## Build & Mobile Installation Steps

### Step 1: Compile APK via GitHub Actions
1. Extract this ZIP and upload its contents (making sure `.github/workflows/build-apk.yml`, `gradle.properties`, `settings.gradle.kts`, `build.gradle.kts`, and `app/` are directly at your GitHub repository root).
2. GitHub Actions automatically runs `.github/workflows/build-apk.yml` (~2 minutes).
3. Download `bangla-type-apk` (`app-debug.apk`) from **Actions > Artifacts** directly onto your Android phone.

### Step 2: Install & Activate on Your Phone
1. Tap the downloaded `app-debug.apk` and allow *Install from this source* if prompted.
2. Open **বাংলা টাইপ** on your phone and tap the 3 setup buttons:
   - **Grant Microphone Permission** (`RECORD_AUDIO`)
   - **Enable বাংলা টাইপ** in Android *On-screen Keyboard* settings
   - **Select বাংলা টাইপ** as your active keyboard
3. Open **WhatsApp**, **Messages**, or **Facebook**, tap any chat/comment box, switch to **বাংলা টাইপ**, tap the mic, and speak Bangla—text appears directly inside the app with zero copy-paste.
