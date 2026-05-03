package app.revanced.patches.pepper.telemetry

import app.revanced.patcher.fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Pepper's CoroutineWorker that fires server-pushed pixel-tracking URLs
 * (Pepper Ocular `/batch_receiver_app`, click/impression/product-clicked
 * pixel URLs) stored in Room DB.
 *
 * Class name: `com.pepper.analytics.backgroundjob.AnalyticsEventTransmissionWorker`
 * (non-obfuscated — Pepper keeps its own package names). Method `a` is the
 * suspend-fn implementation of `CoroutineWorker.doWork(Continuation)`,
 * obfuscated to a single-letter name. Continuation type is also obfuscated
 * but always exactly one reference parameter.
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
 * The Pepper-Hardware-Id OkHttp Interceptor: an `okhttp3.Interceptor`
 * implementation that adds a `Pepper-Hardware-Id: <id>` header to every
 * outgoing request. The literal `"Pepper-Hardware-Id"` String is unique to
 * this interceptor's `intercept(Chain)` method in the entire dex.
 *
 * The class is small (single instance field — the cached hardware ID) and
 * implements one interface (`okhttp3.Interceptor`, obfuscated). The method
 * name `intercept` survives obfuscation because R8 keeps interface method
 * signatures.
 */
internal val pepperHardwareIdInterceptorFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.FINAL)
    strings("Pepper-Hardware-Id")
    custom { method, _ -> method.name == "intercept" }
}
