package app.revanced.patches.pepper.telemetry

import app.revanced.patcher.fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Pepper's pixel-firing CoroutineWorker. Class name is non-obfuscated;
 * method `a` is the obfuscated suspend `doWork(Continuation)`.
 */
internal val pixelWorkerDoWorkFingerprint = fingerprint {
    returns("Ljava/lang/Object;")
    custom { method, classDef ->
        classDef.type ==
            "Lcom/pepper/analytics/backgroundjob/AnalyticsEventTransmissionWorker;" &&
            method.name == "a" &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0].toString().let {
                it.startsWith("L") && it.endsWith(";")
            }
    }
}

/**
 * Pepper-Hardware-Id OkHttp interceptor. The literal `"Pepper-Hardware-Id"`
 * is unique to this method in the dex; `intercept` survives obfuscation
 * as a kept interface method.
 */
internal val pepperHardwareIdInterceptorFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    strings("Pepper-Hardware-Id")
    custom { method, _ -> method.name == "intercept" }
}

/**
 * The single static converter `(state) → AppStartProcessRequiredSteps` that
 * the AppStartProcess navigator funnels every nav-decision through. Return
 * type is Pepper-package (non-obfuscated) and unique across the entire APK,
 * so matching purely on `returns(...)` + 1 ref parameter is unambiguous.
 */
internal val appStartRequiredStepsConverterFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL)
    returns(
        "Lcom/pepper/presentation/appstartprocess/AppStartProcessRequiredSteps;",
    )
    custom { method, _ -> method.parameterTypes.size == 1 }
}
