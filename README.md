<p align="center">
  <img src="docs/mark.svg" width="88" alt="Seeds mark" />
</p>

<h1 align="center">seeds</h1>
<p align="center"><b>Small seeds, every day.</b><br/>A personal Android app for daily non‑negotiables, honest time tracking and Mobile Money.</p>

---

## Why

At the end of a year, it's easy to *feel* productive and not *be* productive. Seeds shows the gap between the plan and the truth, every day, so you can close it.

- **Seeds** are your non‑negotiables (deep work, exercise, reading, prayer, family…). Each has a time target: **daily**, **weekly** or **monthly**.
- You tell Seeds what you're doing. Seeds checks that it's still true.
- Every night you get a report on what you planned vs. what you actually did, plus your money.

## What it does

| | |
|---|---|
| **Today** | Live “Now growing” card with timer, day score ring, a wake→sleep strip painted with what actually happened, and every seed with its progress. Weekly/monthly seeds tell you how much *today* needs to stay on pace. |
| **Honest check-ins** | Every 45 min (configurable) Seeds asks *“Still on Deep work?”* — *Still on it / Switched / Done*. No answer in 15 min, and Seeds stops counting at the check-in time. One tap (*“I was still on it”*) restores it. |
| **Drift guard** | During work hours, or while a focus seed runs, Seeds notices when TikTok/Instagram/YouTube/… has been in front for 5+ min and asks: *Back to work / On a break / Log as drift*. Logged drift ends automatically when you leave the app, and Seeds offers to resume what you were doing. |
| **Home‑screen widgets** | **Now** (4×2): live timer, progress, *Switch* / *Done*, quick-start chips when idle. **Today** (2×2): the day score ring. |
| **Morning plan & nightly report** | At wake time you get today's seeds and where to start. 15 min before bed you get your score, planned vs. actual per seed, where the hours went (growing, breaks, drift, untracked), money spent, and a short reflection (mood, one win, one lesson). |
| **Garden** | 12 weeks of days drawn as seeds, sprouts and blooms. Planned vs. actual per seed, streaks, average score, % of awake time tracked, and real **scroll time** per distracting app (from Android usage stats). |
| **Money (MoMo)** | Reads MTN MoMo / Airtel Money SMS on the device, pulls out amount, direction, fee, balance and counterparty, and asks *“−5,000 RWF to John. What was this for?”* with one-tap category buttons. You get balance, in/out/net, fees, spending by category, budgets, and cash entries. |
| **Debts & loans** | Label a payment as *Lent to someone* or *Borrowed* and it becomes a debt. *Paid back to me* or *Paid back a debt* reduces it automatically. You always see *owed to you* vs. *you owe*. |
| **Self‑update** | Every push to `main` builds a signed APK and publishes a GitHub Release. Seeds checks a few times a day, notifies you, downloads and installs. |
| **Your data** | Everything stays on the phone (Room DB). Export/restore a JSON backup any time. |

## Brand

| Token | Hex | Use |
|---|---|---|
| **Soil** | `#0E1210` | dark ground (dark theme, app icon) |
| **Paper** | `#F5F3EC` | warm unbleached paper (light theme) |
| **Sprout** | `#B6F36A` | the brand: alive, hopeful (accent in dark, hero fills) |
| **Forest** | `#1E7A4A` | sprout grown up (accent on light) |
| **Ember** | `#FF6B57` | drift & overspending: honest, never alarming |

- **Mark:** a seed splitting open, the moment growth begins. It reads as a seed and a leaf.
- **Type:** Plus Jakarta Sans (OFL, bundled). Geometric and warm, with tabular numerals for timers and money.
- **Shape:** 24dp cards, pill buttons, circular seeds. 20dp gutters on an 8pt grid.
- **Growth language:** each seed is a *Seed* (untouched), a *Sprout* (in progress) or a *Bloom* (target met). A day is the same.
- **Voice:** honest and kind. *“A seed is still a seed.”* *“Fallow ground. Tomorrow is fertile.”*
- **Seed colors:** Sprout, Sky, Sun, Clay, Lilac, Mint, Rose, Sand. Fills are always readable with dark ink on top, in both themes.

## Build locally

Requirements: JDK 17 and the Android SDK (API 36).

```bash
./gradlew :app:assembleDebug        # app.seeds.dev, installs next to the release
./gradlew :app:assembleRelease      # signed with keystore.properties if present
```

## Releases & self-update (one-time setup)

1. Create the GitHub repo and push:
   ```bash
   git remote add origin https://github.com/Bertrand-Ir/Seeds.git
   git push -u origin main
   ```
   The repo must be **public** for the in-app updater to read releases without a token.
2. In **Settings → Secrets and variables → Actions**, add:
   | Secret | Value |
   |---|---|
   | `SEEDS_KEYSTORE_BASE64` | contents of `keystore/seeds-release.jks.base64` |
   | `SEEDS_KEYSTORE_PASSWORD` | `storePassword` from `keystore.properties` |
   | `SEEDS_KEY_ALIAS` | `seeds` |
   | `SEEDS_KEY_PASSWORD` | `keyPassword` from `keystore.properties` |
3. Re-run the **Release** workflow (Actions tab). Each push to `main` now publishes `vX.Y.<run>`, and the app offers it.

> ⚠️ **Back up `keystore/` and `keystore.properties` somewhere safe** (password manager or cloud drive). They are git-ignored on purpose. If you lose the key, future updates can't install over the existing app.

The workflow injects the repo name automatically (`-Pseeds.githubRepo=${{ github.repository }}`). For local builds, set `seeds.githubRepo` in `gradle.properties` or change it in the app under **You → App**.

## Permissions, and why

| Permission | Powers |
|---|---|
| Notifications | check-ins, drift, money questions, morning plan, nightly report |
| Usage access | seeing which app is in front (drift guard, scroll time) |
| SMS (receive/read) | Mobile Money messages only. Parsed on device, never uploaded. |
| Exact alarms | check-ins and rituals on time |
| Foreground service | the live timer while something is tracked, and the guard during work hours |
| Install packages | self-update |

If Android blocks SMS for a sideloaded app: **App info → ⋮ → Allow restricted settings**.

## Architecture

Kotlin · Jetpack Compose (Material 3) · Room · DataStore · WorkManager · AlarmManager · RemoteViews widgets. Single module, manual DI (`AppContainer`).

```
app/src/main/java/app/seeds/
  data/       Room entities & DAOs, settings, repositories, backup
  domain/     DayClock (logical days), Engine (progress, pace, score, streaks), MomoParser, formatting
  tracking/   TrackerService (live timer + drift guard), ActionReceiver (notification/widget/alarms), drift detection
  money/      SmsReceiver
  notify/     Notifier (all notification designs), Alarms, BootReceiver
  update/     GitHub release checker, downloader, installer, worker
  widget/     Now & Today widgets
  ui/         theme, components, Today, Garden, Money, Review, You, Onboarding, Quick switch
```

**Logical days:** your day ends when you sleep, not at midnight. The boundary sits halfway through your sleep window, so work at 00:40 still counts toward "today".
