package it.palsoftware.pastiera.inputmethod

import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.inputmethod.ui.EmojiPickerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Search keeps the main IME at keyboard height and moves the compact picker into a separate
 * popup window above it, avoiding a resize of the IME Surface.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StatusBarControllerEmojiSearchStackTest {

    @Test
    fun emojiPickerWithActiveSearchUsesPopupAboveSoftwareKeyboard() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setSoftwareKeyboardMode(
            context,
            SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
        )
        val controller = StatusBarController(context, StatusBarController.Mode.INPUT_VIEW)

        // Render the plain software keyboard once so its height is known.
        controller.update(snapshot(symPage = 0))
        val layout = controller.layoutRoot()
        layoutRoot(layout)

        // Open the emoji picker (SYM page 4) without search.
        controller.update(snapshot(symPage = 4))
        layoutRoot(layout)
        val container = controller.container()
        assertEquals(1, container.childCount)
        val picker = controller.picker()

        // Activate the search panel and re-render (the service forces a re-render via callback).
        picker.invokeSetSearchPanelVisible(true)
        controller.update(snapshot(symPage = 4))
        layoutRoot(layout)

        assertEquals(1, container.childCount)
        val stackedKeyboard = container.getChildAt(0)
        val popup = controller.searchPopup()
        assertSame(picker, popup.contentView)
        assertTrue(
            "stacked keyboard height must be positive, was ${stackedKeyboard.height}",
            stackedKeyboard.height > 0
        )
        assertEquals(container.height, stackedKeyboard.height)

        // Toggling search off must restore the picker-only layout.
        picker.invokeSetSearchPanelVisible(false)
        controller.update(snapshot(symPage = 4))
        layoutRoot(layout)
        assertEquals(1, container.childCount)
        assertSame(picker, container.getChildAt(0))
        assertTrue(picker.height > 0)

        // Leaving the emoji page must release the popup content so no window leaks with the IME.
        picker.invokeSetSearchPanelVisible(true)
        controller.update(snapshot(symPage = 4))
        controller.update(snapshot(symPage = 0))
        assertEquals(null, controller.searchPopup().contentView)
    }

    private fun snapshot(symPage: Int): StatusBarController.StatusSnapshot {
        return StatusBarController.StatusSnapshot(
            capsLockEnabled = false,
            shiftPhysicallyPressed = false,
            shiftOneShot = false,
            ctrlLatchActive = false,
            ctrlPhysicallyPressed = false,
            ctrlOneShot = false,
            ctrlLatchFromNavMode = false,
            altLatchActive = false,
            altPhysicallyPressed = false,
            altOneShot = false,
            symPage = symPage
        )
    }

    private fun layoutRoot(layout: ViewGroup) {
        layout.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        )
        layout.layout(0, 0, layout.measuredWidth, layout.measuredHeight)
    }

    private fun StatusBarController.layoutRoot(): ViewGroup {
        return privateField("statusBarLayout").get(this) as ViewGroup
    }

    private fun StatusBarController.container(): ViewGroup {
        return privateField("emojiKeyboardContainer").get(this) as ViewGroup
    }

    private fun StatusBarController.picker(): EmojiPickerView {
        return privateField("emojiPickerView").get(this) as EmojiPickerView
    }

    private fun StatusBarController.searchPopup(): PopupWindow {
        return privateField("emojiPickerSearchPopup").get(this) as PopupWindow
    }

    private fun EmojiPickerView.invokeSetSearchPanelVisible(visible: Boolean) {
        val method = EmojiPickerView::class.java.getDeclaredMethod(
            "setSearchPanelVisible",
            Boolean::class.javaPrimitiveType
        )
        method.isAccessible = true
        method.invoke(this, visible)
    }

    private fun Any.privateField(name: String) =
        javaClass.getDeclaredField(name).apply { isAccessible = true }
}
