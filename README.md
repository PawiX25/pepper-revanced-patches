# Pepper ReVanced Patches

Unofficial [ReVanced](https://revanced.app/) patch bundle for the entire
**Pepper.com Group / TippingCanoe** family of regional deal-aggregator apps.

Tested on **v8.12.00** — verified that all fingerprint strings match identically
across every sister app, so the same 5 patches apply to all of them.

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
