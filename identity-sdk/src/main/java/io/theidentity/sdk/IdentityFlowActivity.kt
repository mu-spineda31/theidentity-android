/*
 * IdentityFlowActivity.kt
 *
 * Activity host del flujo: una WebView fullscreen + bridge JS.
 */
package io.theidentity.sdk

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class IdentityFlowActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TOKEN = "io.theidentity.sdk.TOKEN"
        const val EXTRA_HOST = "io.theidentity.sdk.HOST"
    }

    private lateinit var webView: WebView
    private lateinit var token: String
    private lateinit var host: String
    private var verification: IdentityVerification? = null

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                loadFlow()
            } else {
                verification?.let {
                    it.onFailed?.invoke(IdentityError.CameraPermissionDenied())
                }
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        token = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
        host = intent.getStringExtra(EXTRA_HOST).orEmpty()
        verification = IdentityCallbacks.get(token)

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = false
                allowContentAccess = false
            }
            // Bridge JS → Kotlin: el flujo llama
            // window.IdentityAndroidBridge.postMessage(JSON.stringify(msg))
            addJavascriptInterface(IdentityJSBridge(), "IdentityAndroidBridge")
            webChromeClient = object : WebChromeClient() {
                // Sin este override, getUserMedia falla con "permission denied".
                override fun onPermissionRequest(request: PermissionRequest) {
                    val needsCamera = request.resources.any {
                        it == PermissionRequest.RESOURCE_VIDEO_CAPTURE ||
                        it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
                    }
                    if (needsCamera) request.grant(request.resources) else request.deny()
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?,
                ) {
                    verification?.handleLoadError(description ?: "WebView error $errorCode")
                    finish()
                }
            }
            // Configuración para que ocupe toda la pantalla.
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        setContentView(webView)

        // Pedimos permiso de cámara antes de cargar (sino el primer getUserMedia falla).
        val cameraOk = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        if (cameraOk) {
            loadFlow()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun loadFlow() {
        val url = "$host/identity/v/$token?embed=1"
        webView.loadUrl(url)
    }

    override fun onBackPressed() {
        // Si el usuario aprieta atrás antes de completar → cancelado.
        verification?.handleUserDismiss()
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    override fun onDestroy() {
        webView.removeJavascriptInterface("IdentityAndroidBridge")
        webView.destroy()
        super.onDestroy()
    }

    /** Bridge expuesto al JS dentro de la WebView. */
    inner class IdentityJSBridge {
        @android.webkit.JavascriptInterface
        fun postMessage(json: String) {
            // Volvemos al main thread para que los callbacks puedan tocar UI.
            webView.post {
                verification?.handleEvent(json)
                // Auto-finish en eventos terminales — ya disparamos los callbacks.
                if (json.contains("\"completed\"") ||
                    json.contains("\"failed\"") ||
                    json.contains("\"cancelled\"")
                ) {
                    finish()
                }
            }
        }
    }

    // Suprimimos un warning sobre el View import — el SDK no lo usa directamente
    @Suppress("unused") private val unused: View? = null
}
