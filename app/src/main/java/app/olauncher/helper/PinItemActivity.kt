package app.olauncher.helper

import android.content.Intent // FORK:widget-screen
import android.content.pm.LauncherApps
import android.os.Build // FORK:widget-screen
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.olauncher.R
import app.olauncher.fork.widgets.ForkWidgetHostManager // FORK:widget-screen
import app.olauncher.fork.widgets.ForkWidgetPinDelegate // FORK:widget-screen

class PinItemActivity : AppCompatActivity() {

    private var forkWidgetPinDelegate: ForkWidgetPinDelegate? = null // FORK:widget-screen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.setBackgroundDrawable(null)

        val launcherApps = getSystemService(LauncherApps::class.java)
        val pinItemRequest = launcherApps.getPinItemRequest(intent)

        val shouldFinish = when (pinItemRequest != null) {
            true -> handleRequestType(pinItemRequest)
            false -> {
                showToast(R.string.invalid_pin_request)
                true
            }
        }

        if (shouldFinish) finish() // FORK:widget-screen
    }

    private fun handleRequestType(pinItemRequest: LauncherApps.PinItemRequest): Boolean {
        return when (pinItemRequest.requestType) {
            LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT -> {
                handleShortcutRequest(pinItemRequest)
                true
            }

            LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // FORK:widget-screen
                    val delegate = ForkWidgetPinDelegate(this, ForkWidgetHostManager.getInstance(this)).also { forkWidgetPinDelegate = it } // FORK:widget-screen
                    delegate.handlePinItemRequest(pinItemRequest) // FORK:widget-screen
                } else { // FORK:widget-screen
                    showToast(R.string.widgets_not_supported)
                    true
                } // FORK:widget-screen
            }

            else -> {
                showToast(R.string.pin_action_not_supported)
                true
            }
        }
    }

    @Deprecated("Deprecated in Java") // FORK:widget-screen
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) { // FORK:widget-screen
        super.onActivityResult(requestCode, resultCode, data) // FORK:widget-screen
        forkWidgetPinDelegate?.onActivityResult(requestCode, resultCode, data) // FORK:widget-screen
    } // FORK:widget-screen

    override fun onDestroy() { // FORK:widget-screen
        forkWidgetPinDelegate?.onDestroy() // FORK:widget-screen
        super.onDestroy() // FORK:widget-screen
    } // FORK:widget-screen

    private fun handleShortcutRequest(pinItemRequest: LauncherApps.PinItemRequest) {
        val shortcutInfo = pinItemRequest.shortcutInfo
        if (shortcutInfo != null) {
            val success = runCatching { pinItemRequest.accept() }.getOrDefault(false)
            val message = when (success) {
                true -> R.string.shortcut_pinned
                false -> R.string.shortcut_pin_failed
            }
            showToast(message)
        } else {
            showToast(R.string.invalid_shortcut)
        }
    }
}
