# Vartex Publishing Guide — বাংলা টাইপ (Bangla Type IME)

This checklist prepares **বাংলা টাইপ** (`com.vartex.banglatype.ime`) for official distribution from **Vartex** (either via Google Play Console or direct APK hosting on your Vartex portal).

## 1. Platform Clarification (Vartex Web Portal vs. Android Distribution)
- **Vartex Web / Cloud Run** hosts web apps and live demos.
- **Android System Keyboards (IME)** that type directly inside WhatsApp, Messages, and Facebook must be packaged as an Android `.apk` (direct install) or `.aab` (Google Play Console).
- This project includes the **Vartex midnight navy & gold adaptive launcher icon** (`ic_launcher`), **ProGuard rules**, and **Release bundle workflow** so both distribution paths are ready.

## 2. Generate a Signing Keystore (One-Time, For Play Store / Signed APK)
Run this once using Java's `keytool`:
```bash
keytool -genkey -v -keystore vartex-bangla-type.jks -keyalg RSA -keysize 2048 -validity 10000 -alias banglatype
```
Add your signing details to GitHub Secrets or local `keystore.properties` before publishing to Google Play Console.

## 3. Google Play Console Data Safety & Permissions Disclosure
- **Declared Permissions**:
  - `RECORD_AUDIO`: Used strictly on-demand when the user taps the keyboard microphone button to convert Bangla speech into text via Android `SpeechRecognizer`.
  - `INTERNET`: Used only by the device's system SpeechRecognizer if the user has not downloaded an offline Bangla voice pack.
- **IME Security Best Practice**: **বাংলা টাইপ** does not log keystrokes, does not store typed messages, and does not run background analytics or external servers.

## 4. High-Resolution Vartex BANGLA Store Icon
A 1:1 **Vartex BANGLA** app icon matching your Vartex Search icon style is included in the web studio for instant download and upload to Google Play Console or your Vartex portal.
