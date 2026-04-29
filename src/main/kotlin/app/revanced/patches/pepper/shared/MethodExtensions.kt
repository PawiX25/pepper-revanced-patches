package app.revanced.patches.pepper.shared

import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation

/**
 * `MutableMethodImplementation.registerCount` is a `final int`. Growing it is
 * required when injecting smali code that needs scratch registers beyond the
 * method's existing locals.
 *
 * This helper bumps the field via reflection so subsequent `addInstructions`
 * calls compile against the new register count (the inline-smali compiler reads
 * `method.implementation.registerCount` to set `.registers N` in its template).
 *
 * Note: smali parameter notation `p0..pN` is RELATIVE to register count — it
 * always refers to the LAST `parameterCount + 1` registers. Growing register
 * count automatically remaps `p0` to a higher v-register, so existing parameter
 * references stay correct.
 */
fun MutableMethod.ensureRegisters(needed: Int) {
    val impl = implementation ?: return
    if (impl.registerCount >= needed) return

    val field = MutableMethodImplementation::class.java.getDeclaredField("registerCount")
    field.isAccessible = true
    field.setInt(impl, needed)
}
