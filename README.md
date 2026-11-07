# ✦ Reply Bot

An Android app + Chrome extension for **three relevant AI reply suggestions**, in Roman Urdu, Urdu, or English. Friendly, funny, professional, and thoughtful tones. Custom instructions, editable replies, and copy buttons.

**No auto-posting, no account monitoring, no X password, and no X API integration.** You review and post every reply yourself.

## Current status

Source implementation, not a deployed service or compiled APK. No tests, build, browser validation, or live AI calls have been run. The APK workflow is manual-only and has not been run. You must configure Gemini access and an HTTPS backend before suggestions work. X's DOM can change; the extension's post detection may then need adjustment.

## How it works

- **Chrome on desktop:** each text post on `x.com`/`twitter.com` gets a `✦ Reply Bot` button. Clicking sends that post's displayed text to your backend, which calls Gemini. Nothing is sent just by scrolling or opening a post.
- **Android:** in X select Share → Reply Bot, or paste post text into the app. If X shares only a URL, paste the actual post text too. The app cannot read the X app's screen or place a button inside it.
- AI sees post text, selected language/tone, and your custom instruction. Images, videos, linked pages, and full threads are not analyzed. Replies can be inaccurate: review them.
- The Gemini key stays exclusively on the server. Clients use a separate pairing token. This is a personal-use service, not a multi-user SaaS authentication system.

## 1. Backend: Gemini connection

Requirements: Node.js 22+, Gemini API key, and a server with persistent disk. No npm packages are needed.

1. Get a Gemini key at https://aistudio.google.com/apikey . Use a project without paid billing for a free-only experiment. Check model availability and quotas in your own account.
2. In `backend/`, copy `.env.example` to `.env`.
3. Set `GEMINI_API_KEY` and `GEMINI_MODEL`. The example uses `gemini-2.5-flash-lite`; choose another available free text model if your account does not offer it.
4. Generate a random pairing token:

   ```sh
   node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
   ```

5. Put this token in server `APP_TOKEN`. Keep it secret. It gives access to your AI quota. Never use your Gemini key as a pairing token.
6. Run from `backend/`:

   ```sh
   npm start
   ```

The server listens on loopback port 8787 by default. Put it behind an **HTTPS reverse proxy** on your own hostname. On a container host, set `HOST=0.0.0.0`, use the host-provided `PORT`, mount `DATA_DIR` to persistent disk, and let the hosting platform terminate TLS. Do not expose the plain HTTP port to the internet.

The app and extension both require an HTTPS origin, such as `https://replies.example.com`, without a path. `/health` reports process health only; it does not verify Gemini credentials.

### Free usage and limits

The app's default budget is **30 AI request attempts per UTC day**, shared across both clients. A UTC day resets at 5 AM Pakistan time. Attempts that fail after quota reservation also count; this prevents unlimited retry spend. Change `DAILY_LIMIT` in the server environment if desired.

The quota is persisted in `DATA_DIR/quota.json`. Use one server process/replica and a persistent volume. This filesystem counter is not a distributed rate limiter. It rejects simultaneous requests and requests less than 2.5 seconds apart. Provider errors do not trigger automated retries or paid model fallbacks.

**An app request limit is not a billing cap.** To avoid provider charges, use an eligible free-tier project without paid billing. Gemini's own request/token quotas may be lower than the app's daily limit. Availability and free terms can change. Hosting and internet are separate expenses; no free hosting service has been provisioned here. You can run the backend on your own computer, but it must stay online and be securely reachable over HTTPS for your phone.

## 2. Chrome extension (desktop)

1. Open `chrome://extensions` in desktop Chrome.
2. Turn on Developer Mode, choose **Load unpacked**, and select the `extension/` directory.
3. Click the extension toolbar icon. Enter your backend HTTPS URL and server `APP_TOKEN`, then save. Chrome asks permission to contact only your chosen server.
4. The popup displays `chrome-extension://YOUR_ID`. Add that exact origin to server `.env` `ALLOWED_ORIGINS`, then restart the backend.
5. Pick your language, tone, and optional instruction. Save.
6. Open or reload X. Click **✦ Reply Bot** beside a post. Edit and copy any suggestion; paste and post it yourself.

The extension has no X write permissions or account credentials. It reads displayed post text locally and sends it only after your button click. Host permissions are requested for your configured backend, not automatically granted for every HTTPS site. The pairing token lives in Chrome local extension storage, not sync storage. Only install on a device you trust.

## 3. Android app

### Build an APK without running tests

Option A — **GitHub Actions**, after this project is at the root of a GitHub repository:

1. Open repository → **Actions** → **Build Android APK (manual, no tests)**.
2. Select **Run workflow**.
3. When complete, download artifact `reply-bot-android-debug` and unzip it.
4. Install `app-debug.apk` on your own Android device (Android 8+). Android may ask you to allow installation from that source. Device or regional developer-verification requirements may also apply.

The workflow only runs on manual dispatch. It runs `assembleDebug -x test -x lint`, not tests. It produces a debug APK for personal use, not a signed Play Store release. The workflow itself has not been executed here.

Option B — open `android/` in Android Studio with JDK 17, SDK 35, Android Gradle Plugin 8.9.2 and Gradle 8.11.1, then build an APK. No Gradle wrapper binary is bundled; use installed Gradle 8.11.1 or Android Studio's Gradle configuration. CLI from `android/`: `gradle assembleDebug -x test -x lint`.

### Use it

1. Open the app and expand **Connection settings**.
2. Set your HTTPS backend origin and pairing token; save.
3. Paste a post's text, or share a post from X into Reply Bot.
4. Choose language, tone, and optional instruction, then tap **Suggest 3 replies**.
5. Edit a reply, tap **Copy reply**, and paste it in X manually.

No accessibility service, overlay permission, clipboard monitoring, or X login is used. Connection settings are stored privately on the device; app backup is disabled. The pairing token is not hardware-backed encrypted; use a device lock and rotate the server token if your device is lost. Post text and generated replies are not saved as an app history. Android may preserve the current screen temporarily through activity state; the clipboard holds a reply only when you copy it. Device keyboards/clipboard providers have their own privacy behavior.

## GitHub repository

Public source: https://github.com/imdadh01/Reply-Bot

Project source is stored at the repository root. Use the manual Actions workflow above to build a personal-use debug APK without running tests. No actual keys or pairing tokens are committed. Never commit `.env`, quota data, or signing keys.

## API contract

`POST /suggest`, with `Authorization: Bearer APP_TOKEN` and `Content-Type: application/json`:

```json
{"text":"Post text here","language":"Roman Urdu","tone":"Friendly","instruction":"Keep it short"}
```

Success:

```json
{"suggestions":["Reply one","Reply two","Reply three"],"remaining":29}
```

Errors return `{ "error": "Readable explanation" }` and an appropriate HTTP status. Max post length is 6000 JS string units; instructions are limited to 500. The backend validates exactly three different nonempty suggestions, each up to 280 Unicode characters. X's weighted character rules can differ; edit as needed before posting.

## Privacy

Text and style instructions go to your server and Google Gemini for generation. There are no analytics or content logs in the app/backend code. Hosting providers and Google may apply their own logging/data policies; Gemini free-tier inputs may be used for product improvement. Do not send private/sensitive posts unless you accept those policies. Errors never forward provider response bodies or API keys to clients.

## References

- Gemini API keys: https://ai.google.dev/gemini-api/docs/api-key
- Gemini pricing and data handling: https://ai.google.dev/gemini-api/docs/pricing
- Generate content API: https://ai.google.dev/api/generate-content
- Chrome local extension installation: https://developer.chrome.com/docs/extensions/get-started/tutorial/hello-world
- Android receiving shares: https://developer.android.com/develop/ui/compose/sharing/receive

## License

MIT. See LICENSE.
