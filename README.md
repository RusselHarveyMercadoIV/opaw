# Opaw

Opaw turns your dog into a living avatar and helps you take care of them: one-tap care logs, walks
tracked like Strava, a memories journal, badges, and a "Pack" of dogs nearby.

This is the iOS MVP, built natively in SwiftUI for iOS 17+, with layouts that adapt to wide screens
such as the unfolded iPhone Duo.

## What's in the MVP

| Area | What it does |
|---|---|
| **Avatar** | Take or pick a photo; Vision cuts the dog out on device and it comes alive as a breathing, mood-reacting "living photo". Optionally, the avatar worker generates realistic motion clips (wag, sleepy, celebrate…) with the open-source Wan 2.2 model. |
| **Care** | One-tap logging for meals, water, pee and poop, plus bath, brushing, nails, meds, vet visits, weight and play. Poop consistency hints, history with filters, daily reminders. |
| **Widget and Siri** | Home Screen quick-log widget with interactive buttons; "Log poop in Opaw" via Siri and Shortcuts. |
| **Walks** | GPS recording with live map, distance, moving time and pace, pee/poop pins, pause/resume, and a Live Activity on the Lock Screen and Dynamic Island. Weekly goal based on size, age and energy. |
| **Mood** | The avatar's mood follows real care: hungry when a meal is overdue, restless without a walk, fresh after a bath, proud after a badge. |
| **Memories** | Photo journal with "on this day" throwbacks and birthday milestones. |
| **Badges** | 16 badges with progress rings: streaks, distance, early walks, baths, memories, friends. |
| **Pack (social)** | Anonymous sign-in, community rules, nearby dogs on a map at ~1 km precision, follow, a feed of shared walks, memories and badges, likes, report and block. Shared routes hide their start and end. |

## Repository layout

```
Opaw/                 iOS app (SwiftUI, SwiftData)
OpawWidgets/          Widget extension: quick-log widget and walk Live Activity
Shared/               Code compiled into both: data models, App Intents, Live Activity attributes
Packages/OpawKit/     Pure Swift logic with unit tests: streaks, badges, mood, walk maths, privacy
supabase/migrations/  Database schema, row level security and storage for the Pack and avatar jobs
avatar-worker/        Python worker that turns a cutout into stacked-alpha motion clips
docs/                 Setup guide
project.yml           XcodeGen project definition (the .xcodeproj is generated, not committed)
```

## Quick start

```bash
brew install xcodegen
xcodegen generate
open Opaw.xcodeproj
```

Pick an iPhone simulator and run. Everything except the Pack and motion clips works offline with no
setup. To turn those on, follow [docs/SETUP.md](docs/SETUP.md).

## Tests

```bash
swift test --package-path Packages/OpawKit         # Swift logic
pip install -r avatar-worker/requirements-dev.txt
python -m pytest avatar-worker/tests                 # worker
```

CI runs both, and builds the app for the iOS Simulator, on every pull request.
