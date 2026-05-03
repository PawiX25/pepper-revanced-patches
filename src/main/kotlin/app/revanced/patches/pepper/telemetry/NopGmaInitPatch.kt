package app.revanced.patches.pepper.telemetry

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstructions
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.pepper.shared.pepperFamilyPackages

/**
 * NOP `com.google.android.gms.ads.MobileAds.initialize(...)` so the Google
 * Mobile Ads SDK is never bootstrapped, no matter who calls it. Replaces
 * both overloads with `return-void`.
 *
 * Why this is necessary on top of [neuterTrackerProvidersPatch]:
 *   `MobileAdsInitProvider` already returns false in stock 8.13.00 (it's
 *   a no-op placeholder), so the ContentProvider auto-init route is
 *   already dead. But Pepper itself manually calls `MobileAds.initialize(
 *   context)` from `nh4.smali` (lines 666 and 742 in 8.13.00 — patchinfo
 *   §3 GMA notes "Pepper's nh4.smali:676,752 manually calls
 *   MobileAds.initialize() — defense in depth"). With provider auto-init
 *   gone but the manual call alive, GMA SDK still spins up — confirmed
 *   leaks during a logged-in browse to:
 *      googleads.g.doubleclick.net/getconfig/pubsetting…
 *      googleads.g.doubleclick.net/mads/static/.../sdk-core-v40-loader.js
 *      googleads.g.doubleclick.net/mads/static/.../sdk-core-v40-impl.js
 *      googleads.g.doubleclick.net/mads/static/.../sdk-core-v40-loader.appcache
 *      googleads.g.doubleclick.net/favicon.ico
 *      pubads.g.doubleclick.net/gampad/ads?…
 *   The first three of these come from the SDK's `WebViewClient` resource
 *   pre-fetch on init; the gampad/ads request is the actual ad-serve
 *   request. None of these URLs live as `const-string` constants in dex —
 *   they're concatenated at runtime from base URLs the SDK keeps in
 *   encoded byte[] form, so `redirectTrackerUrlsPatch` cannot reach them.
 *
 *   Replacing `MobileAds.initialize` body with `return-void` short-circuits
 *   `MobileAdsInitializerInternal.initialize`, the chain that builds the
 *   `RequestConfigurationParcel` and kicks off the `MobileAdsBridge`
 *   thread responsible for those calls. With it gone, the SDK never gets
 *   to the point of issuing any HTTP request. Pepper's calling code
 *   ignores the return (the method is `void`), so there's no observable
 *   effect on Pepper's flow.
 *
 * MobileAds class is fully-qualified (NOT obfuscated by R8 because of
 * @Keep annotations on Google's GMS ads classes) so we match by exact
 * class type and method signature.
 */
@Suppress("unused")
val nopGmaInitPatch = bytecodePatch(
    name = "Disable Google Mobile Ads SDK init",
    description = "Stops the Google Mobile Ads SDK from initialising.",
) {
    pepperFamilyPackages.forEach { compatibleWith(it) }

    // Same pre-condition as 04: alone (or with 04) the GMA mediation Runnable
    // still lands on the main Looper via the ContentProvider/URL paths that
    // 02 and 01 neutralise, and we get the same `zzcll.zzau` SIGSEGV. NOPping
    // `MobileAds.initialize` only closes one of the doors; the other doors
    // need 01 + 02 closed before this patch is safe to apply.
    dependsOn(redirectTrackerUrlsPatch, neuterTrackerProvidersPatch)

    val mobileAdsType = "Lcom/google/android/gms/ads/MobileAds;"

    execute {
        var nopped = 0
        classDefs.toList().forEach { classDef ->
            if (classDef.type != mobileAdsType) return@forEach
            val mutableClass = classDefs.getOrReplaceMutable(classDef)
            mutableClass.methods.forEach { method ->
                if (method.name != "initialize" || method.returnType != "V") return@forEach
                // Match both overloads:
                //   initialize(Context)V
                //   initialize(Context, OnInitializationCompleteListener)V
                val n = method.implementation!!.instructions.toList().size
                method.removeInstructions(0, n)
                method.addInstructions(0, "return-void")
                nopped++
            }
        }
        System.err.println("[nopGmaInitPatch] NOPped $nopped MobileAds.initialize overloads")
    }
}
