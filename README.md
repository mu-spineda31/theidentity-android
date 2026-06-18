# Identity SDK for Android

Embed the [Identity](https://dev.theidentity.io) verification flow inside your Android app via a single Gradle module.

- 🛠 **minSdk 24** (Android 7+) · Kotlin 1.9 · Zero third-party deps
- 📷 Camera + microphone access for liveness
- 🎯 Single `IdentityVerification` API with callbacks
- 🪶 Thin native shell over `WebView` — the heavy lifting runs on our servers

## Installation

### JitPack (fastest)

In your project's root `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}
```

In your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.theidentity:identity-sdk:0.1.0")
}
```

### Maven Central (when published)

```kotlin
dependencies {
    implementation("io.theidentity:identity-sdk:0.1.0")
}
```

## Manifest setup

Add to your app's `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />

<uses-feature android:name="android.hardware.camera" android:required="false" />
```

The SDK declares its own `IdentityFlowActivity` via manifest merging — you don't need to add it.

## Usage

```kotlin
import io.theidentity.sdk.IdentityVerification
import io.theidentity.sdk.IdentityDecision

class OnboardingActivity : AppCompatActivity() {
    private fun startVerification() {
        lifecycleScope.launch {
            // 1. Pide a tu backend que cree una verification
            val token = api.createVerification()

            // 2. Lanza el SDK
            val verification = IdentityVerification(token = token)

            verification.onCompleted = { result ->
                Log.d("Identity", "decision: ${result.decision}")
                if (result.decision == IdentityDecision.APPROVED) {
                    markUserAsVerified()
                }
            }

            verification.onFailed = { error ->
                Log.e("Identity", "verification failed", error)
            }

            verification.onCancelled = {
                Log.d("Identity", "user cancelled")
            }

            verification.present(this@OnboardingActivity)
        }
    }
}
```

## API

### `IdentityVerification`

```kotlin
class IdentityVerification(
    token: String,
    host: String = "https://identity-biometrics.web.app",
)
```

| Property | Type | Description |
| --- | --- | --- |
| `onCompleted` | `(IdentityCompletedResult) -> Unit` | Fires when the user finishes the flow (including manual review). |
| `onFailed` | `(IdentityError) -> Unit` | Fires on irrecoverable errors. |
| `onCancelled` | `() -> Unit` | Fires when the user dismisses without completing. |
| `onStepChanged` | `(String) -> Unit` | Optional progress callback per step. |

```kotlin
fun present(activity: Activity)
```

### `IdentityCompletedResult`

```kotlin
data class IdentityCompletedResult(
    val verificationId: String,
    val decision: IdentityDecision,  // APPROVED | REJECTED | MANUAL_REVIEW
    val callbackUrl: String?,
)
```

### `IdentityError`

```kotlin
sealed class IdentityError : Exception {
    class LoadFailed : IdentityError
    class FlowError(val code: String, message: String) : IdentityError
    class InvalidConfig : IdentityError
    class CameraPermissionDenied : IdentityError
}
```

## What about the API key?

The `token` you pass to `IdentityVerification(token = ...)` is a short-lived **verification token** generated on YOUR backend by calling `POST /v1/verifications` with your API key. The SDK never sees your API key, and the token is single-use.

See the [Quickstart](https://dev.theidentity.io/quickstart) for the backend setup.

## License

UNLICENSED · © Identity
