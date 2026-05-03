package app.revanced.patches.pepper.telemetry

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstructions
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.pepper.shared.pepperFamilyPackages

/**
 * Bytecode equivalent of removing tracker `<provider>` entries from
 * AndroidManifest.xml — but achieved by neutering each provider's `onCreate`
 * method instead of touching the manifest.
 *
 * Why bytecode and not manifest:
 *   ReVanced 22.0.1-SNAPSHOT's `resourcePatch` machinery runs the bundled
 *   AAPT2 against Pepper's resources and produces a re-encoded
 *   `resources.arsc` + binary `AndroidManifest.xml.bin` that Pepper PL
 *   8.13.00 fails to boot (white-screened on AppStartProcessActivity, no
 *   logcat error, splash never advances). A diagnostic no-op `resourcePatch`
 *   that opens the manifest and writes nothing reproduces the hang exactly,
 *   so the fault is in the resource-recompile pipeline, not in any specific
 *   manifest edit. Pure `bytecodePatch` work is unaffected.
 *
 * What this patch does:
 *   For each tracker `ContentProvider`, replace its `onCreate()Z` body with:
 *       .method public onCreate()Z
 *           .locals 0
 *           const/4 p0, 0x0
 *           return p0
 *       .end method
 *   When `onCreate` returns false, Android marks the provider as failed-init
 *   in `ContentProviderHolder`, removes it from the package's published
 *   provider table, and never invokes any of its query/insert/update/delete
 *   methods. The SDK's auto-init code that lived inside `onCreate` (e.g.
 *   `VungleProvider.onCreate` calls `k6.Companion` SDK
 *   init; `SystemLifecycleContentProvider.onCreate` instantiates the Adjust
 *   SystemLifecycle singleton) never runs.
 *
 *   This is the SAME effective behaviour as removing `<provider>` from the
 *   manifest (which patchinfo.txt §1 documented, but which we cannot apply
 *   via resourcePatch — see above). The difference is purely the layer at
 *   which we cut the SDK off: manifest = "this provider does not exist",
 *   bytecode = "this provider exists but immediately reports failure".
 *
 * Providers neutered:
 *   - com.vungle.ads.VungleProvider                     (Vungle ad SDK auto-
 *                                                        init — also kills
 *                                                        Liftoff backend
 *                                                        calls to
 *                                                        us-event.app-install.bid
 *                                                        and cdn/impression-
 *                                                        east.liftoff.io,
 *                                                        because Liftoff is
 *                                                        Vungle's parent and
 *                                                        the SDK relays via
 *                                                        it on init)
 *   - com.adjust.sdk.SystemLifecycleContentProvider     (Adjust attribution
 *                                                        SDK singleton init)
 *   - com.facebook.ads.AudienceNetworkContentProvider   (FB Audience Network)
 *
 *   MobileAdsInitProvider is NOT in this list because in 8.13.00 its
 *   onCreate already returns false and does no work (verified in stock
 *   smali; it's a placeholder for future GMA versions). Pepper's GMA
 *   initialisation runs from its own code path (nh4.smali manually calls
 *   MobileAds.initialize()), which the URL-redirect patch handles.
 *
 *   IterableFirebaseMessagingService is a `<service>`, not a `<provider>` —
 *   different mechanism, would need a different bytecode neuter strategy
 *   (e.g. neuter `IterableFirebaseMessagingService.onMessageReceived`).
 *   Left as future work; the URL-redirect already prevents Iterable from
 *   reaching api.iterable.com at all.
 */
@Suppress("unused")
val neuterTrackerProvidersPatch = bytecodePatch(
    name = "Neuter tracker auto-init ContentProviders",
    description = "Stops the Vungle, Adjust, and Facebook Audience Network SDKs " +
        "from auto-initialising at app start.",
) {
    pepperFamilyPackages.forEach { compatibleWith(it) }

    // 01 must run first: redirecting URL constants in the dex string-pool
    // is non-destructive on its own, but with provider auto-init cut off
    // here, the SDKs that survive (e.g. Adjust via PepperApplication.onCreate
    // manual init, FB Audience via lazy attach) still resolve their
    // tracker URLs at request time. Without 01 they reach live tracker
    // hosts; with 01 they hit 127.0.0.1:1 and ECONNREFUSED. Applying 02
    // alone made the partial-init path explode at boot in testing.
    dependsOn(redirectTrackerUrlsPatch)

    val targets = setOf(
        "Lcom/vungle/ads/VungleProvider;",
        "Lcom/adjust/sdk/SystemLifecycleContentProvider;",
        "Lcom/facebook/ads/AudienceNetworkContentProvider;",
    )

    execute {
        var neutered = 0
        classDefs.toList().forEach { classDef ->
            if (classDef.type !in targets) return@forEach

            val mutableClass = classDefs.getOrReplaceMutable(classDef)
            val onCreate = mutableClass.methods.firstOrNull {
                it.name == "onCreate" && it.parameterTypes.isEmpty() && it.returnType == "Z"
            } ?: return@forEach

            val originalCount = onCreate.implementation!!.instructions.toList().size
            onCreate.removeInstructions(0, originalCount)
            // Two-instruction stub returning false. `p0` aliases to the last
            // register; with .locals 0 and a single `this` param, p0 == v0.
            // We could also write `const/4 v0, 0x0; return v0` but matching
            // the canonical style of the no-op MobileAdsInitProvider in stock
            // 8.13.00 makes the diff easy to inspect.
            onCreate.addInstructions(
                0,
                """
                const/4 p0, 0x0
                return p0
                """.trimIndent(),
            )
            neutered++
        }
        System.err.println("[neuterTrackerProvidersPatch] neutered $neutered ContentProvider onCreate methods")
    }
}
