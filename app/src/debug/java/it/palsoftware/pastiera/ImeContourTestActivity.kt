package it.palsoftware.pastiera

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.getSystemService
import androidx.core.view.WindowCompat

/** High-contrast host used to inspect the IME contour on physical devices. */
class ImeContourTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val testBackground = Color.rgb(225, 29, 72)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = testBackground
        window.navigationBarColor = testBackground
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val root = FrameLayout(this).apply { setBackgroundColor(testBackground) }
        root.addView(
            TextView(this).apply {
                text = "RED = transparent gap"
                setTextColor(Color.WHITE)
                textSize = 20f
                gravity = Gravity.CENTER
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(72)
            ).apply {
                gravity = Gravity.TOP
                topMargin = dp(80)
                marginStart = dp(24)
                marginEnd = dp(24)
            }
        )
        val input = EditText(this).apply {
            hint = "Focus keeps the Pastiera status bar open"
            setTextColor(Color.rgb(65, 0, 17))
            setHintTextColor(Color.rgb(125, 18, 43))
            setBackgroundColor(Color.argb(210, 255, 255, 255))
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        root.addView(
            input,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(64)
            ).apply {
                gravity = Gravity.TOP
                topMargin = dp(168)
                marginStart = dp(24)
                marginEnd = dp(24)
            }
        )
        setContentView(root)
        input.requestFocus()
        input.post {
            getSystemService<InputMethodManager>()?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
