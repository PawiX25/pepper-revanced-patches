package app.revanced.patches.pepper.debug

import app.revanced.patcher.fingerprint

/**
 * MainActivity.onCreateOptionsMenu(Menu)Z — fixed name (referenced from manifest
 * <activity> as the launcher), so it isn't obfuscated by R8. Pin by class type
 * + method signature.
 */
internal val mainActivityOnCreateOptionsMenuFingerprint = fingerprint {
    returns("Z")
    parameters("Landroid/view/Menu;")
    custom { method, classDef ->
        classDef.type == "Lcom/pepper/apps/android/presentation/MainActivity;" &&
            method.name == "onCreateOptionsMenu"
    }
}
