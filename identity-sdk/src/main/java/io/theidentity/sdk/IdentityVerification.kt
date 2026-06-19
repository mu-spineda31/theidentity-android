/*
 * IdentityVerification.kt
 *
 * Identity SDK para Android — embebe el flujo de verificación en una
 * WebView controlada. Mismo modelo que el SDK iOS: thin shell nativo
 * sobre un componente web; el procesamiento (OCR, liveness, anti-spoof)
 * corre en nuestra infraestructura.
 *
 * Uso desde una Activity:
 *
 *   val verification = IdentityVerification(token = tokenFromBackend)
 *   verification.onCompleted = { result ->
 *     Log.d("Identity", "decision: ${result.decision}")
 *     finish()
 *   }
 *   verification.onFailed = { error -> Log.e("Identity", "$error") }
 *   verification.onCancelled = { /* user closed */ }
 *   verification.present(this)
 *
 * Requisitos en el AndroidManifest del cliente:
 *   <uses-permission android:name="android.permission.CAMERA" />
 *   <uses-permission android:name="android.permission.RECORD_AUDIO" />
 *   <uses-permission android:name="android.permission.INTERNET" />
 */
package io.theidentity.sdk

import android.app.Activity
import android.content.Intent
import org.json.JSONObject

enum class IdentityDecision(val raw: String) {
    APPROVED("approved"),
    REJECTED("rejected"),
    MANUAL_REVIEW("manual_review");

    companion object {
        fun fromRaw(raw: String?): IdentityDecision = when (raw) {
            "approved" -> APPROVED
            "rejected" -> REJECTED
            else -> MANUAL_REVIEW
        }
    }
}

data class IdentityCompletedResult(
    val verificationId: String,
    val decision: IdentityDecision,
    val callbackUrl: String?,
)

sealed class IdentityError(message: String) : Exception(message) {
    class LoadFailed(message: String) : IdentityError(message)
    class FlowError(val code: String, message: String) : IdentityError(message)
    class InvalidConfig(message: String) : IdentityError(message)
    class CameraPermissionDenied : IdentityError("Camera permission was denied.")
}

class IdentityVerification(
    private val token: String,
    /** Override del host del flujo. Default: producción. */
    private val host: String = "https://identity-biometrics.web.app",
) {
    /** Llamado cuando el usuario termina el flujo (incluye manual_review). */
    var onCompleted: ((IdentityCompletedResult) -> Unit)? = null

    /** Llamado en errores irrecuperables. */
    var onFailed: ((IdentityError) -> Unit)? = null

    /** Llamado cuando el usuario cierra sin terminar. */
    var onCancelled: (() -> Unit)? = null

    /** Callback opcional de progreso por paso. */
    var onStepChanged: ((String) -> Unit)? = null

    /**
     * Lanza la Activity del flujo. La Activity se autodestruye al
     * recibir un evento `completed`, `failed` o `cancelled`.
     */
    fun present(activity: Activity) {
        // Registramos el callback en un singleton para que la nueva
        // Activity (lanzada vía Intent) pueda llamarnos de vuelta sin
        // serialización de funciones.
        IdentityCallbacks.register(token, this)

        val intent = Intent(activity, IdentityFlowActivity::class.java).apply {
            putExtra(IdentityFlowActivity.EXTRA_TOKEN, token)
            putExtra(IdentityFlowActivity.EXTRA_HOST, host.trimEnd('/'))
        }
        activity.startActivity(intent)
    }

    /**
     * Procesa el JSON recibido del WebView via window.IdentityAndroidBridge.
     * Llamado por IdentityFlowActivity — internal a la librería.
     */
    internal fun handleEvent(json: String) {
        val msg = try {
            JSONObject(json)
        } catch (e: Exception) {
            return
        }
        if (msg.optString("source") != "identity") return
        val event = msg.optJSONObject("event") ?: return
        val type = event.optString("type")
        when (type) {
            "ready", "opened" -> Unit // sin handler nativo

            "step_changed" -> {
                val step = event.optString("step")
                if (step.isNotEmpty()) onStepChanged?.invoke(step)
            }

            "completed" -> {
                val result = IdentityCompletedResult(
                    verificationId = event.optString("verification_id", token),
                    // optString(key, fallback) exige fallback non-null; usamos
                    // la versión de 1 arg que retorna "" si no existe, y
                    // collapsamos vacío → null para que el parser nullable de
                    // fromRaw caiga al default MANUAL_REVIEW.
                    decision = IdentityDecision.fromRaw(
                        event.optString("decision").ifEmpty { null }
                    ),
                    callbackUrl = event.optString("callback_url").ifEmpty { null },
                )
                onCompleted?.invoke(result)
                IdentityCallbacks.unregister(token)
            }

            "failed" -> {
                val code = event.optString("error_code", "unknown")
                val message = event.optString("error_message", "Identity flow failed.")
                onFailed?.invoke(IdentityError.FlowError(code, message))
                IdentityCallbacks.unregister(token)
            }

            "cancelled" -> {
                onCancelled?.invoke()
                IdentityCallbacks.unregister(token)
            }
        }
    }

    internal fun handleUserDismiss() {
        onCancelled?.invoke()
        IdentityCallbacks.unregister(token)
    }

    internal fun handleLoadError(message: String) {
        onFailed?.invoke(IdentityError.LoadFailed(message))
        IdentityCallbacks.unregister(token)
    }
}

/**
 * Singleton interno que mapea token → instancia de IdentityVerification.
 * La Activity del flujo es lanzada por Intent (no podemos serializar
 * lambdas), así que mantiene viva la referencia mientras dura el flujo.
 */
internal object IdentityCallbacks {
    private val map = HashMap<String, IdentityVerification>()

    fun register(token: String, instance: IdentityVerification) {
        synchronized(map) { map[token] = instance }
    }

    fun unregister(token: String) {
        synchronized(map) { map.remove(token) }
    }

    fun get(token: String): IdentityVerification? =
        synchronized(map) { map[token] }
}
