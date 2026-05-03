package app.revanced.patches.pepper.telemetry

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstructions
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation

/**
 * Disables Google Play's PAIRIP (Play App Install Referrer Integrity
 * Protection) license check that ships ONLY with the global / US build
 * `com.pepperdeals`. The other nine regional sister apps don't bundle
 * PAIRIP at all — verified by string-pool scan of every dex in
 * pepperFamilyPackages: zero `com/pairip/licensecheck` hits everywhere
 * except `com.pepperdeals`, where there's exactly one.
 *
 * What PAIRIP does and why it breaks our pipeline:
 *   PAIRIP is Google's app-bundle anti-tampering kit. At runtime it asks
 *   the system "was this APK installed by the Play Store?" via
 *   `Context.getPackageManager().getInstallerPackageName(...)`. If the
 *   answer is anything other than `com.android.vending` (or the
 *   per-device Play Store package), PAIRIP attempts a Play licensing
 *   round-trip and, on failure, throws up a non-dismissable dialog:
 *
 *     Something went wrong
 *     Check that Google Play is enabled on your device and that you're
 *     using an up-to-date version before opening the app. If the problem
 *     persists try reinstalling the app.
 *
 *   For ReVanced-patched APKs, the install path is `adb install` /
 *   `pm install` (or sideload through Manager) — not Play Store — so
 *   PAIRIP always fails. Same dialog appears if you take the original
 *   pepper.com APK from APKMirror and `adb install` it; it's not a
 *   patch-introduced regression. Logcat trace from a fresh patched run:
 *
 *     I LicenseClient: Local install check failed due to wrong installer.
 *     D LicenseClient: Connecting to the licensing service...
 *     E LicenseClient: Error while checking license:
 *         com.pairip.licensecheck.LicenseCheckException: Licensing
 *         service could not process request.
 *
 * What we patch:
 *   PAIRIP wires itself in two places. Both must go:
 *
 *   1. `Lcom/pairip/licensecheck/LicenseContentProvider;->onCreate()Z`
 *      Auto-init via `<provider>` in AndroidManifest.xml. Same trick as
 *      [neuterTrackerProvidersPatch] — return `false`, Android marks the
 *      provider failed-init and never calls into it. This kills the
 *      static-init path that reads `meta-data` from the manifest and
 *      kicks off `LicenseClient`.
 *
 *   2. `Lcom/pairip/licensecheck/LicenseClient;->initializeLicenseCheck()V`
 *      Explicit runtime entry that other code paths can hit even with
 *      the provider neutered (e.g. if Pepper code touches PAIRIP via
 *      reflection during its first-launch flow). Replace the body with
 *      `return-void`. Same approach as patches T5 / T6 / T7 —
 *      build a fresh `MutableMethodImplementation` to dodge dexlib's
 *      read-only `tryBlocks` list, which would otherwise leave the
 *      original exception-entry table pointing at offsets that no
 *      longer exist.
 *
 * What we deliberately DON'T touch:
 *   * `LicenseActivity` — the dialog Activity. Killing the entry points
 *     above means it never gets started, so we don't need to neuter it
 *     and risk crashing manifest validation.
 *   * `LicenseClient.<init>` — pulling out the constructor would NPE
 *     anything that holds a `LicenseClient` reference. Leaving the
 *     constructor alive but the methods empty is cleaner.
 *   * Any of the nested $Lambda / $1 / $2 helpers — they're called
 *     only from the methods we already neutered.
 *
 * Compatibility note:
 *   Patch is declared compatible with `com.pepperdeals` only. PAIRIP
 *   ships exclusively in the global/US build — verified by string-pool
 *   scan of every dex in pepperFamilyPackages: zero
 *   `com/pairip/licensecheck` hits in the other nine regional sister
 *   apps. Listing them as compatible would still produce a no-op
 *   (`classDefs` walk skips over absent target classes), but it would
 *   also surface T8 in ReVanced Manager's per-app patch list for nine
 *   apps that have nothing to gain from it — confusing UX. Restricting
 *   `compatibleWith` to `com.pepperdeals` hides T8 from the other nine.
 *
 * No `dependsOn` on the T1–T7 chain because PAIRIP is orthogonal: it's
 * a Play Store gating mechanism, not a tracker. This patch can be used
 * alone (e.g. someone who only wants `com.pepperdeals` to install
 * cleanly without telemetry blocking).
 */
@Suppress("unused")
val neuterPairipLicenseCheckPatch = bytecodePatch(
    name = "Disable PAIRIP license check",
    description = "Removes Google Play's install-source DRM check from the " +
        "global / US Pepper.com build, allowing sideloaded APKs to open.",
) {
    compatibleWith("com.pepperdeals")

    val providerType = "Lcom/pairip/licensecheck/LicenseContentProvider;"
    val clientType = "Lcom/pairip/licensecheck/LicenseClient;"

    execute {
        var providerNeutered = 0
        var clientNeutered = 0

        classDefs.toList().forEach { classDef ->
            when (classDef.type) {
                providerType -> {
                    val mutableClass = classDefs.getOrReplaceMutable(classDef)
                    mutableClass.methods.forEach { method ->
                        if (method.name == "onCreate" &&
                            method.parameterTypes.isEmpty() &&
                            method.returnType == "Z"
                        ) {
                            val n = method.implementation!!.instructions.toList().size
                            method.removeInstructions(0, n)
                            method.addInstructions(
                                0,
                                """
                                const/4 p0, 0x0
                                return p0
                                """.trimIndent(),
                            )
                            providerNeutered++
                        }
                    }
                }

                clientType -> {
                    val mutableClass = classDefs.getOrReplaceMutable(classDef)
                    mutableClass.methods.forEach { method ->
                        if (method.name == "initializeLicenseCheck" &&
                            method.parameterTypes.isEmpty() &&
                            method.returnType == "V"
                        ) {
                            val origRegisters = method.implementation!!.registerCount
                            val fresh = MutableMethodImplementation(origRegisters)
                            method.implementation = fresh
                            method.addInstructions(0, "return-void")
                            clientNeutered++
                        }
                    }
                }
            }
        }

        System.err.println(
            "[neuterPairipLicenseCheckPatch] " +
                "LicenseContentProvider.onCreate neutered: $providerNeutered, " +
                "LicenseClient.initializeLicenseCheck neutered: $clientNeutered"
        )
    }
}
