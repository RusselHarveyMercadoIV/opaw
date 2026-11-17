# Releasing Opaw on Google Play

The Android app lives in `android/` and builds in GitHub Actions, so nothing large has to be installed
locally. Most of these steps happen in Play Console and need you.

## 1. One-time setup

1. **Developer account.** Create one at <https://play.google.com/console> (a one-time fee). New personal
   accounts must run a **closed test with at least 12 testers for 14 days** before production.
2. **Upload key.** Make a keystore once and keep it safe; Play App Signing holds the real signing key.
   Using Java's `keytool` (it comes with the JDK already on your Mac):

   ```bash
   keytool -genkeypair -v -keystore opaw-upload.jks -alias opaw -keyalg RSA -keysize 2048 -validity 10000
   base64 -i opaw-upload.jks | pbcopy   # copies it for the next step
   ```

3. **GitHub secrets** (repo Settings > Secrets and variables > Actions):
   `OPAW_KEYSTORE_BASE64` (paste from above), `OPAW_KEYSTORE_PASSWORD`, `OPAW_KEY_ALIAS` (`opaw`),
   `OPAW_KEY_PASSWORD`, and for the Pack, `SUPABASE_HOST` and `SUPABASE_ANON_KEY`.
4. **Create the app** in Play Console with package name `app.opaw`.

## 2. Build and upload

1. GitHub > Actions > **Android release** > Run workflow. Enter a version code (1, then 2, 3… for each
   upload) and a version name (such as `1.0.0`).
2. Download the `opaw-release-aab` artifact and upload the `.aab` to a testing track in Play Console.

## 3. Subscriptions (Monetize > Products > Subscriptions)

Create these so the paywall shows real prices (the IDs must match `PremiumStore.kt`):

| Product ID | Base plan | Offer |
|---|---|---|
| `app.opaw.premium.yearly` | yearly, auto-renewing | 7-day free trial, new customers |
| `app.opaw.premium.monthly` | monthly, auto-renewing | 7-day free trial, new customers |
| `app.opaw.premium.yearly.offer` | yearly, auto-renewing | 50% off the first year |

Add your Google account under **License testing** to buy without being charged.

## 4. Store listing and policy forms

- **Privacy policy URL:** host `docs/PRIVACY.md` (for example with GitHub Pages) and add a contact email.
- **Data safety:**
  - Location (approximate and precise): collected for app functionality. Shared only when the
    person shares a walk or their pet with the Pack. Optional.
  - Photos: collected for app functionality. Shared only when posted to the Pack. Optional.
  - User IDs: an anonymous Pack account ID. Optional.
  - Data is encrypted in transit. Users can request deletion (Leave the pack and delete my account).
- **Foreground service (location):** declare it for walk tracking and attach a short screen recording
  that starts a walk, turns the screen off and shows the ongoing notification.
- **Content rating, target audience, ads (none):** fill in the questionnaires.
- **Screenshots:** at least two phone screenshots, plus the feature graphic (1024×500).

## 5. Testing checklist on a phone

- Onboarding: photo, cutout, details, reveal, tracks, household, permissions, paywall, offer.
- Care: one-tap logs, poop log, hold a log to delete, Undo, Edit tracks, a custom track, reminders.
- Walk: start, lock the phone for a few minutes, unlock, finish with hold-to-finish, summary.
- Widgets: add Quick log and Care score, tap a button, check the count in the app.
- Shortcuts: long-press the app icon, Log poop.
- Premium: buy with a license tester, check Settings > Subscription.
