# Android vs iOS: parity check

Each iOS screen and service, the Kotlin file that ports it, and where the two differ. Screens were
ported line by line from the Swift file, keeping the same copy, sizes (points → dp), colors, fonts
and order. Design codes (2a, 3b…) are the ones in the Swift comments.

## Screens

| iOS (Swift) | Android (Kotlin) | Same | Differences |
|---|---|---|---|
| Onboarding 4a–4g `OnboardingFlow.swift` | `ui/onboarding/OnboardingFlow.kt`, `CameraStep.kt`, `DogDraft.kt` | Steps, copy, progress bars, field sheets, track grid, meal times, household, permissions, paywall then one-time offer | Breed detection is weaker (ML Kit has no breed classifier). Date and time use Material pickers instead of iOS wheels. "I already have an account" can't restore App Store purchases. |
| Home 2a, add a pet 2h `HomeView.swift` | `ui/home/HomeScreen.kt`, `AvatarView.kt` | Pager with dots, living photo with mood motion and tier sound, chips, quote, stats, family chip, share, settings | Generated motion clips download but don't play yet; the living photo shows instead. |
| Avatar studio `AvatarView.swift` | `ui/home/AvatarStudio.kt` | Mood preview, generate, poll, regenerate | Clip playback (see above). |
| Family tree 2i `FamilyTreeView.swift` | `ui/home/FamilyTree.kt` | Tree layout on the 390-point grid, relatives sheet, at home, invite, pet limit | |
| Today 2b `TodayView.swift` | `ui/care/CareDetails.kt` | Week strip, six tiles, log with points, hold to delete, offline chip, pull to refresh | |
| Care 3a, poop log 3b `CareView.swift` | `ui/care/CareScreen.kt`, `CareSheets.kt` | Streak card, circles with badges and due alerts, who's caring, repeat warnings, custom tracks (done, count, amount) | Long-press menu replaces the iOS context menu. |
| Schedule 3c `ScheduleView.swift` | `ui/care/CareDetails.kt` | Timeline, due in red with Log, coming up, add reminder | |
| History `HistoryView.swift` | `ui/care/CareDetails.kt` | Ranges, summary, weight chart, poop strip, vet summary share | The weight chart is drawn directly instead of Swift Charts; same line, dots and labels. |
| Edit tracks 3h, new track 3i `Tracks.swift` | `ui/care/CareSheets.kt` | Toggles, drag to reorder, icons, log styles, daily reminder, delete | |
| Activity 3f, live walk 2c, summary 3e `WalkViews.swift` | `ui/walks/WalkScreens.kt`, `OpawMap.kt` | Goal ring, bars, records, start with activity type, live stats, pins, pet marker, hold to finish, summary, walk detail | Maps use free OpenFreeMap tiles (MapLibre) instead of Apple Maps, so the map style differs. |
| Live Activity 3g `OpawWidgets.swift` | `services/WalkRecorder.kt` (`WalkService`) | Distance, timer, pee and poop counts, pause and resume | Shown as an ongoing notification; Android has no Dynamic Island. |
| Memories 2d `MemoriesView.swift` | `ui/memories/MemoriesScreens.kt` | Filters, walk cards with route, photo cards, pairs, milestones, favourites, detail, add memory | |
| Challenges 2f `ChallengesView.swift` | `ui/profile/ProfileScreens.kt` | Month ring, week streak, badge medals with NEW | |
| Settings 5e, Units, Edit pet `ProfileView.swift` | `ui/profile/ProfileScreens.kt` | Rows, Premium banner and tags, sounds and haptics switches, reset data | "iCloud backup" is "Backup" (coming soon on both). |
| Household 4f `Household.swift` | `ui/profile/Household.kt` | Members, roles, invite | Android can't tell whether the share sheet sent the invite, so the member is added when it opens. |
| Pack 2e `PackView.swift`, `PackService.swift` | `ui/pack/PackScreen.kt`, `services/PackService.kt` | Join with rules, nearby map, filters, card, profile, report, block, wave, delete account | Same Supabase backend over its REST APIs. Needs `SUPABASE_HOST` and `SUPABASE_ANON_KEY` (not set on iOS either yet). |
| Paywall 5a/5b, offer 5c, pet limit 5d, subscription 5f `Premium/*.swift` | `ui/premium/PremiumScreens.kt`, `services/PremiumStore.kt` | Layout, plans, saving, trial timeline, offer sheet, subscription card | Google Play Billing instead of StoreKit. Play doesn't give apps the renewal date, so it's shown only for trials. |
| Toasts 7b `Toasts.swift`, `CareLogging.swift` | `services/ToastCenter.kt`, `ui/components/ToastHost.kt` | Logged, deleted and undone toasts with Undo, family, due, offline, failed with Retry, milestone | Family toasts need household sync, which neither app has yet. |
| Sounds and haptics 7c `Sounds.swift`, `Theme.swift` | `services/Sounds.kt`, `ui/components/DesignKit.kt` | Same synthesized tones and envelopes for every cue | Android haptic patterns are the closest system ones, not identical to iOS Taptic. |

## Not on the phone layout yet

- **Wide screens (the open "Duo" display, 6a–6p).** iOS has two-pane layouts for wide screens. On
  Android, foldables and tablets currently get the phone layout, stretched.
- **Home Screen widgets.** Quick log and Care score are ported (`widget/QuickLogWidget.kt`). The iOS
  Lock Screen variants have no Android equivalent.
- **Siri.** Replaced by launcher shortcuts (long-press the icon: Log poop, Log pee, Log meal).

## iOS code that isn't used anywhere

`PawTrail`, `WalkingPaws`, `PawConfetti`, `CareHistoryView`, `RemindersView` and `BadgeGrid` aren't
shown on any iOS screen, so they weren't ported.
