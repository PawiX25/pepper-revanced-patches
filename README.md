# Pepper ReVanced Patches

Unofficial [ReVanced](https://revanced.app/) patch bundle for the entire
**Pepper.com Group / TippingCanoe** family of regional deal-aggregator apps.

Tested on **v8.12.00** — verified that all fingerprint strings match identically
across every sister app, so the same 6 patches apply to all of them.

| Region | Package | App name |
|---|---|---|
| 🇵🇱 Poland | `com.tippingcanoe.pepperpl` | Pepper PL |
| 🇳🇱 Netherlands | `com.tippingcanoe.peppernl` | Pepper NL |
| 🇩🇪 Germany | `com.tippingcanoe.mydealz` | Mydealz |
| 🇬🇧 UK | `com.tippingcanoe.hukd` | HotUKDeals |
| 🇫🇷 France | `com.dealabs.apps.android` | Dealabs |
| 🇲🇽 Mexico | `com.tippingcanoe.promodescuentos` | PromoDescuentos |
| 🇪🇸 Spain | `com.chollometro` | Chollometros |
| 🇦🇹 Austria | `com.preisjaeger` | Preisjäger |
| 🇸🇪 Sweden | `se.pepperdeals` | Pepper SE |
| 🇺🇸 US / global | `com.pepperdeals` | Pepper.com |

---

## What's in the bundle

| # | Patch | Effect |
|---|---|---|
| 1 | **Enable debug menu** | Re-enables the hidden 3-dot debug menu in MainActivity (45 dev actions: ad inspector, exploration screens, navigation, consent reset, event theming, monetization, etc.). R8 strips the inflate call from the release build — this patch puts it back. |
| 2 | **Unlock tier-locked icons** | Makes all 12 membership-tier icons (Silver / Gold / Platinum + DealMinded / DealDetective / Chilling / Awesome / Tight / Cool / Mascots) available in the icon picker without farming loyalty points. Rewrites every `sget-object Lvsc;->{d,e,f}:Lvsc;` to the DEFAULT-tier `Lvsc;->c:Lvsc;`. |
| 3 | **Hide banner ads in feed** | Disables the Pubmatic OpenWrap / DFPBanner renderer in the deal feed. Ads never load and the empty gray ad-cell container is collapsed to zero (visibility=GONE + height=0 + all 4 margins=0). Lists look as if ad slots were never inserted. |
| 4 | **Always show event-theming icons** | Always shows all 4 event-theming icons (Black Friday, Summer Sales, Autumn Sales, El Buen Fin) in the picker, regardless of whether the corresponding event is currently active. Vanilla shows only the icon for the active event (if any) and hides the other 3. Depends on patch #2. |
| 5 | **Keep event icon after restart** | Disables the use-case that auto-restores the default icon on app start when an event has ended by date. Without this patch, picking SummerSales as your launcher reverts to default on next launch with the *"Our event mode is over!"* dialog. With it, your chosen event icon stays permanently. |
| 6 | **Fix spacing around hidden ad cells** | Companion to patch #3. Without this, after ad cells collapse to zero height, the deal-detail RecyclerView's ItemDecoration (`m07`) still allocates a 16dp section gap under each invisible ad AND paints a `shadow_divider` on top of the next section's header — producing a doubled 32dp gap and a visually clipped *"You may also like"* subheader. This patch inserts guards in `m07.a()` (`getItemOffsets`) and `m07.b()` (`onDraw`) so collapsed ad cells contribute no offset and no draw. Depends on patch #3. |

---

## Telemetry blocking patches

A second, **opt-in** family of patches that strip every piece of analytics
and ad-tracking the app emits — verified against tcpdump capture on
`com.tippingcanoe.pepperpl` v8.13.00.

* No TLS ClientHello to any known tracker host (Iterable, Pepper Ocular,
  Usercentrics 1px+UCT, Adjust, Vungle, Pubmatic, Moloco, Recombee,
  LiveRamp, Confiant, Crashlytics, Firebase Logging, Firebase Analytics,
  Firebase Remote Config, FB Audience Network, AppsFlyer, GMA / DoubleClick
  / 2mdn).
* All attempted tracker URLs are redirected to `127.0.0.1:1` and refused at
  the socket layer.
* No crashes, no Adjust threadpool activity, no GMA SDK init.
* Pepper API (`pepper.pl`), Facebook Login (`graph.facebook.com`), FCM token
  registration (`firebaseinstallations.googleapis.com`) and the Usercentrics
  consent SDK (`aggregator.service.usercentrics.eu`,
  `consent-api.service.consent.usercentrics.eu`) keep working — these are
  load-bearing for the app and intentionally preserved.

Tested on Pepper PL **v8.13.00**. The same fingerprints exist across the
sister apps; the patch family declares the same `pepperFamilyPackages`
compatibility set as the rest of this repo.

| # | Patch | Effect |
|---|---|---|
| T1 | **Redirect tracker URLs to localhost** | dex string-pool rewrite. Every `const-string` and static-field initial value matching a known tracker host gets rewritten to `http://127.0.0.1:1`. Covers Iterable, Pepper Ocular, Usercentrics, Adjust, Vungle, Pubmatic, Firebase Analytics / Remote Config / Crashlytics-settings, LiveRamp, Confiant, Moloco, FB Audience, GMA, IMA, fundingchoicesmessages, mediation.goog. SDKs that survive end up calling `connect()` on port 1 → `ECONNREFUSED`. Foundation patch — every other telemetry patch depends on this one. |
| T2 | **Neuter tracker auto-init ContentProviders** | Replaces `onCreate()Z` body of `com.vungle.ads.VungleProvider`, `com.adjust.sdk.SystemLifecycleContentProvider`, and `com.facebook.ads.AudienceNetworkContentProvider` with `return false`. Android marks each provider as failed-init and never invokes any of its query/insert/update/delete methods, killing the SDK auto-init code that ran inside `onCreate`. Bytecode equivalent of removing `<provider>` from AndroidManifest.xml — done via bytecode because resourcePatches break Pepper boot on this version. |
| T3 | **Kill Pepper first-party pixel tracking** | Two method NOPs against Pepper's own first-party tracking: (a) `AnalyticsEventTransmissionWorker.a(Continuation)` (Pepper Ocular pixel-firing CoroutineWorker) → `return Result.Success()`, so pushed pixel URLs are stored in Room DB but never POSTed; (b) `Lw05;->intercept` (Pepper-Hardware-Id OkHttp interceptor) → real hardware ID is replaced with a per-install random UUID persisted to `SharedPreferences("revanced_pepper", "mock_hwid")`. Same UUID feeds both `POST /device` registration and the request header so Daily Picks (`/dealbot/enable`) keeps working. Standalone — no dependencies. |
| T4 | **Kill Datatransport upload pipeline** | NOPs `JobInfoSchedulerService.onStartJob/onStopJob` and `AlarmManagerSchedulerBroadcastReceiver.onReceive` from the Google Datatransport runtime, killing the JobScheduler + AlarmManager paths that drive Crashlytics report uploads (`crashlyticsreports-pa.googleapis.com`) and Firebase logging uploads (`firebaselogging-pa.googleapis.com`). These two hostnames live as Base64-encoded `byte[]` inside `CctTransportBackend` and decode to strings that never appear as `const-string` in the dex — patch T1 cannot reach them. T4 cuts off the upload entry points instead. |
| T5 | **Disable Google Mobile Ads SDK init** | Replaces both overloads of `com.google.android.gms.ads.MobileAds.initialize(...)` with `return-void`. `MobileAdsInitProvider` is already a no-op placeholder in 8.13.00, but Pepper itself manually calls `MobileAds.initialize(context)` from `nh4.smali` during ad-render path setup. Without this patch the GMA SDK still spins up and pre-fetches script/HTML/appcache resources from `googleads.g.doubleclick.net` plus `gampad/ads` ad requests to `pubads.g.doubleclick.net`. |
| T6 | **Disable GMA ad-load entry points** | Sits on top of T5. Replaces every public `load*` method on `AdLoader`, `AdManagerAdView`, `AdManagerInterstitialAd`, `RewardedAd`, and `RewardedInterstitialAd` with `return-void`. Pepper's native-ad path in `kfb.smali` (`callGoogleAdManager() loadAd()`) calls `AdLoader$Builder.forCustomFormatAd(...).build().loadAd(AdManagerAdRequest)` independent of `MobileAds.initialize`; Pubmatic's DFP bridge calls `AdManagerAdView.loadAd` directly. Both share these GMS load methods as the runtime URL-emitter. With them NOPped, all GMA-related TLS ClientHello disappear. |
| T7 | **Disable Adjust SDK** | Replaces every state-mutating public static method on `com.adjust.sdk.Adjust` (`initSdk`, `onResume`, `onPause`, `trackEvent`, `trackAdRevenue`, `verifyAndTrackPlayStorePurchase`, `enable`, `disable`, `addGlobalCallbackParameter`, `addGlobalPartnerParameter`) with `return-void`. T2 already cuts the ContentProvider auto-init route; T7 cuts the manual route from `PepperApplication.onCreate → Adjust.initSdk(...)`. Without T7 the Adjust threadpool spins up either way; with T7 it's silent. URLs are still redirected by T1 as a safety net; this patch is purely about not burning the threadpool. `getDefaultInstance()` is intentionally left intact so Pepper's caller code doesn't NPE. |
| T8 | **Disable PAIRIP license check** *(`com.pepperdeals` only)* | Removes Google Play's PAIRIP (Play App Install Referrer Integrity Protection) anti-tampering kit that ships **only** in the global/US `com.pepperdeals` build — string-pool scan confirms zero `com/pairip/licensecheck` hits in the other nine regional sister apps. At launch PAIRIP calls `getInstallerPackageName(...)`; if the answer is anything other than `com.android.vending` it throws a non-dismissable *"Something went wrong — Check that Google Play is enabled"* dialog and refuses to open. Reproducible with the stock APKMirror APK + `adb install` — not patch-introduced. Replaces `LicenseContentProvider.onCreate()Z` with `return false` (Android marks the provider failed-init and skips the static-init chain into `LicenseClient`) and `LicenseClient.initializeLicenseCheck()V` with `return-void` (covers reflective / alternate entry paths that bypass the provider). `compatibleWith` restricted to `com.pepperdeals` so T8 doesn't surface in ReVanced Manager for the nine regional builds that don't bundle PAIRIP. Standalone — no dependencies; PAIRIP is a Play Store gating mechanism orthogonal to telemetry. |

### Dependency graph

```
T1 (Redirect URLs) ────── root, no deps
T3 (KillFirstPartyPixel)  root, no deps (Pepper-only, doesn't touch any third-party SDK)
T8 (NeuterPairipLicense)  root, no deps (com.pepperdeals only — Play Store DRM, not telemetry)

T2 (NeuterProviders)      ──→ T1
T4 (KillDatatransport)    ──→ T1, T2
T5 (NopGmaInit)           ──→ T1, T2
T7 (NopAdjustSdk)         ──→ T1, T2

T6 (NopGmaAdLoader)       ──→ T1, T2, T5      (the deepest)
```

These are encoded as `dependsOn(...)` in each patch file (same mechanism as
`CollapseAdSpacingPatch.dependsOn(hideBannerAdsPatch)` in this repo). When you
enable a leaf patch in ReVanced Manager, the bundle auto-pulls its transitive
dependencies — enabling **only** T6 in `revanced-cli` auto-applies T1, T2
and T5 in addition.

### Why these dependencies

| rule | rationale |
|---|---|
| **T2 → T1** | Without T1, the SDKs whose providers T2 cut off still resolve their tracker URLs at request time through other code paths, and the partial-init state explodes at boot. T2 alone reproduced a hard crash in earlier testing. |
| **T4, T5 → T1, T2** | Both patches alone (and T4+T5 together) provoked a `SIGSEGV` in `nterp_helper` at `com.google.android.gms.internal.ads.zzcll.zzau` on app start. The crashing Runnable was queued on the main Looper via the GMA mediation Runnable that gets injected through the ContentProvider path (closed by T2) and the URL fetch path (closed by T1). Both must be closed before T4 or T5 is safe. |
| **T6 → T1, T2, T5** | T6 sits one layer above T5: it cuts the actual `loadAd` entry points GMA exposes, but if T5 hasn't already silenced `MobileAds.initialize`, the SDK still bootstraps mediation discovery (which goes through T2's providers) and the `zzcll.zzau` Runnable returns. The full chain is required. |
| **T7 → T1, T2** | T2 cuts Adjust auto-init via `SystemLifecycleContentProvider`; T7 cuts the manual init via `PepperApplication`. Both must be closed or the threadpool spawns. T1 is a final safety net for any reflective URL fetch that survives — no observed leak in testing, but the redirect costs nothing and the dependency keeps the safety promise transitive. |
| **T3 standalone** | Touches only Pepper's own first-party endpoints (Pepper Ocular pixel worker + `Pepper-Hardware-Id` header). No third-party SDK, no mediation, no Datatransport — fully orthogonal to the T1–T7 GMA/Adjust/Firebase chain. |
| **T8 standalone** | PAIRIP is Google Play's install-source DRM, not a tracker — orthogonal to the T1–T7 telemetry chain. Only present in `com.pepperdeals`, where it blocks the app from opening when sideloaded. Can be applied alone for users who only want the global/US build to install cleanly without enabling any telemetry blocking. |
| **T1 root** | Pure dex string-pool rewriting. Non-destructive: doesn't alter SDK semantics or provider lifecycle, just swaps URL constants. Safe foundation everything else stacks on. |

### Verification commands

After installing the patched APK, you can prove each patch is live without
running any analytics flow:

```bash
# T1 — count localhost-redirected connection attempts during a session
adb shell su 0 sh -c "iptables -I OUTPUT -m owner --uid-owner $(adb shell pm list packages -U com.tippingcanoe.pepperpl | cut -d: -f3 | tr -d '\r') -j NFLOG --nflog-group 30"
adb shell su 0 sh -c 'tcpdump -i nflog:30 -w /sdcard/pepper.pcap -U -s 0 &'
# … use the app for a while …
adb pull /sdcard/pepper.pcap   # any TCP destination 127.0.0.1:1 is a T1 hit

# T2 — bytecode evidence: onCreate body returns false
apktool d -f patched.apk -o /tmp/p
grep -A4 'onCreate()Z' /tmp/p/smali_classes*/com/vungle/ads/VungleProvider.smali
# Expected:
#   .method public onCreate()Z
#       .locals 1
#       const/4 p0, 0x0
#       return p0

# T3 — runtime evidence: per-install UUID was generated and persisted
adb shell su 0 cat /data/data/com.tippingcanoe.pepperpl/shared_prefs/revanced_pepper.xml
# Expected:  <string name="mock_hwid">…UUID…</string>

# T7 — runtime evidence: no Adjust threadpool activity
adb logcat -d | grep 'Adjust-pool'   # expected: no output
```

---

## Install

### Option A — ReVanced Manager (recommended)

Tested with [inotia00/revanced-manager](https://github.com/inotia00/revanced-manager/releases) v2.5.x (`app.revanced.manager.flutter` package). The official ReVanced/revanced-manager should also work — they both use patcher v22.

1. Install ReVanced Manager on your Android device.
2. In Manager: **Settings → Sources → Patches**, paste:
   ```
   https://github.com/PawiX25/pepper-revanced-patches/releases/latest/download/manifest.json
   ```
   *(Don't use `api.github.com/.../releases/latest` — Manager parses its
   `created_at` field with timezone-naive `LocalDateTime`, which fails on the `Z`
   suffix GitHub returns. The repo's CI publishes a hand-crafted manifest in the
   format Manager actually accepts.)*
3. Pick any of the 10 listed APKs from your device storage. Manager doesn't
   auto-fetch them — extract from your installed app or download from APKPure.
4. Select all 5 patches → **Patch** → **Install**.

### Option B — revanced-cli (desktop)

```bash
# 1. Download a release .rvp from this repo's releases page
# 2. Download revanced-cli: https://github.com/ReVanced/revanced-cli/releases
# 3. Patch:
java -jar revanced-cli.jar patch \
    --patches pepper-patches-1.0.0.rvp \
    --out pepper-patched.apk \
    pepper-original.apk

# 4. Sign + align (apksigner from Android SDK build-tools):
apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android \
    --ks-key-alias androiddebugkey --key-pass pass:android \
    --out pepper-signed.apk pepper-patched.apk
```

These apps ship as XAPKs with split APKs for ABI / DPI / language. The patch
bundle only modifies the **base** APK — install the patched base alongside the
original `arm64_v8a` and `xxxhdpi` (or your own DPI) splits via
`adb install-multiple`.

---

## Build from source

Requires **JDK 17** and **Android SDK build-tools** (for `d8`, exposed via
`ANDROID_HOME`). The patches are compiled against ReVanced Patcher v22, which
isn't published on any public Maven; clone and install it to mavenLocal first:

```bash
# 1. Build patcher v22 to mavenLocal (one-time)
git clone --depth 1 https://github.com/revanced/revanced-patcher.git /tmp/rv-patcher
cd /tmp/rv-patcher
sed -i 's/^version = .*/version = 22.0.1-SNAPSHOT/' gradle.properties
sed -i '/signAllPublications()/d; /useGpgCmd()/d' patcher/build.gradle.kts
./gradlew :patcher:publishJvmPublicationToMavenLocal \
    -PgithubPackagesUsername=$(gh api user --jq .login) \
    -PgithubPackagesPassword=$(gh auth token)

# 2. Build the bundle
cd path/to/pepper-revanced-patches
./gradlew buildPatchBundle
# Output: build/libs/pepper-patches-1.0.0.rvp
```

The CI workflow does the same thing on every tag push.

---

## Project layout

```
src/main/kotlin/app/revanced/patches/pepper/
├── ads/
│   ├── Fingerprints.kt          # bannerAdRendererFingerprint
│   └── HideAdsPatch.kt
├── debug/
│   ├── Fingerprints.kt          # mainActivityOnCreateOptionsMenuFingerprint
│   └── EnableDebugMenuPatch.kt
├── icons/
│   ├── Fingerprints.kt          # tier / event / restore fingerprints
│   ├── UnlockTierIconsPatch.kt
│   ├── AlwaysShowEventIconsPatch.kt
│   └── KeepEventIconAfterRestartPatch.kt
├── layout/
│   ├── Fingerprints.kt          # itemDecoration fingerprints
│   └── CollapseAdSpacingPatch.kt
├── telemetry/
│   ├── Fingerprints.kt          # pixelWorker / Pepper-Hardware-Id interceptor
│   ├── RedirectTrackerUrlsPatch.kt        # T1
│   ├── NeuterTrackerProvidersPatch.kt     # T2  (dependsOn T1)
│   ├── KillFirstPartyPixelTrackingPatch.kt # T3
│   ├── KillDatatransportPatch.kt          # T4  (dependsOn T1, T2)
│   ├── NopGmaInitPatch.kt                 # T5  (dependsOn T1, T2)
│   ├── NopGmaAdLoaderPatch.kt             # T6  (dependsOn T1, T2, T5)
│   ├── NopAdjustSdkPatch.kt               # T7  (dependsOn T1, T2)
│   └── NeuterPairipLicenseCheckPatch.kt   # T8  (com.pepperdeals only, no deps)
└── shared/
    ├── Compatibility.kt         # full list of supported package names
    └── MethodExtensions.kt      # ensureRegisters() reflection helper
```

Each patch file documents the obfuscated symbol it targets, the heuristic used
to find it under R8 minification, and the smali stub it injects. Read them
before reporting bugs against newer versions — the heuristics are documented
so they can be adjusted without re-reverse-engineering.

---

## Releasing

Tag a commit `vX.Y.Z` and push the tag. The GitHub Actions workflow at
`.github/workflows/release.yml` builds the `.rvp` bundle and creates a release
with it attached. The ReVanced Manager source URL above always tracks the
latest tag.

```bash
git tag v1.0.1
git push origin v1.0.1
```

---

## License

[GPL-3.0](LICENSE) — same license as ReVanced itself.

This project is **not affiliated with** Pepper.com, TippingCanoe, or ReVanced.
Use at your own risk; running modified APKs may violate the app's ToS and could
result in your account being banned.
