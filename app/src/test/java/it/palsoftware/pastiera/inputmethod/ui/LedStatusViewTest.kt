package it.palsoftware.pastiera.inputmethod.ui

import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PointF
import android.view.View
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.inputmethod.StatusBarController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LedStatusViewTest {
    @Test
    fun roundedOverlayPassesCenterTouchesThroughButRetainsBottomStripGestures() {
        val leds = LedStatusView(RuntimeEnvironment.getApplication()).apply {
            bottomCornerRadiiPx = 100 to 100
        }
        val view = leds.ensureView()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(110, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, 1000, 110)
        fun touch(x: Float, y: Float): Boolean {
            val event = android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_DOWN, x, y, 0)
            return try { view.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        org.junit.Assert.assertFalse(touch(50f, 50f))
        org.junit.Assert.assertFalse(touch(500f, 50f))
        assertTrue(touch(500f, 109f))
        leds.bottomCornerRadiiPx = null
        assertTrue(touch(500f, 50f))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun roundedIndicatorsStayInTwoHorizontalRowsInsideTheContour() {
        val leds = LedStatusView(RuntimeEnvironment.getApplication()).apply {
            layout = ModifierLedLayouts.TITAN_2_ELITE
            bottomCornerRadiiPx = 100 to 100
        }
        val view = leds.ensureView()
        fun measureAndLayout() {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(1_000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.AT_MOST)
            )
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        }
        measureAndLayout()
        assertTrue(view.height >= 100)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        fun hasPaint(left: Int, right: Int, top: Int, bottom: Int): Boolean =
            (left until right).any { x ->
                (top until bottom).any { y -> Color.alpha(bitmap.getPixel(x, y)) > 0 }
            }
        assertTrue("Left indicators must stay near the lower edge", hasPaint(50, 220, 85, 100))
        assertTrue("Right indicators must stay near the lower edge", hasPaint(780, 950, 85, 100))
        assertTrue("Indicators must not climb the side contour", !hasPaint(0, 1_000, 0, 70))
        assertTrue("The glass corner must stay clear", !hasPaint(0, 10, 90, 100))

        leds.bottomCornerRadiiPx = null
        measureAndLayout()
        assertTrue(view.height < 100)
        assertEquals(164, view.getChildAt(0).width)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun contourIntegratedIndicatorsUseOnlyTheLowerCornerRails() {
        val leds = LedStatusView(RuntimeEnvironment.getApplication()).apply {
            layout = ModifierLedLayouts.TITAN_2_ELITE
            bottomCornerRadiiPx = 100 to 100
            contourIntegrated = true
            contourGeometry = LedStatusView.ContourGeometry(
                buttonTopPx = 20f,
                leftButtonEndPx = 120f,
                rightButtonStartPx = 880f,
                leftButtonContour = LedStatusView.ButtonContour(
                    listOf(
                        PointF(30f, 25f), PointF(20f, 25f), PointF(10f, 35f),
                        PointF(10f, 60f), PointF(30f, 90f), PointF(120f, 100f)
                    ),
                    borderHalfWidthPx = 1f
                ),
                rightButtonContour = LedStatusView.ButtonContour(
                    listOf(
                        PointF(970f, 25f), PointF(980f, 25f), PointF(990f, 35f),
                        PointF(990f, 60f), PointF(970f, 90f), PointF(880f, 100f)
                    ),
                    borderHalfWidthPx = 1f
                )
            )
        }
        val view = leds.ensureView()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1_000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(110, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        fun hasPaint(left: Int, right: Int, top: Int, bottom: Int): Boolean =
            (left until right).any { x ->
                (top until bottom).any { y -> Color.alpha(bitmap.getPixel(x, y)) > 0 }
            }

        assertTrue("Left contour rails must be visible", hasPaint(0, 150, 0, 110))
        assertTrue("Right contour rails must be visible", hasPaint(850, 1_000, 0, 110))
        assertTrue("The rails must start at the rounded top corners", hasPaint(0, 1_000, 20, 30))
        assertTrue("No rail stroke may cross the button top edge", !hasPaint(0, 1_000, 0, 20))
        assertTrue("The LEDs must stay inside the outer button regions", !hasPaint(125, 875, 0, 110))
    }

    @Test
    fun titan2EliteLayoutProjectsFiveSegmentsOntoTwoRows() {
        val ledStatusView = LedStatusView(RuntimeEnvironment.getApplication()).apply {
            layout = ModifierLedLayouts.TITAN_2_ELITE
        }
        val view = ledStatusView.ensureView()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1_000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.AT_MOST)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)

        assertEquals(5, view.childCount)
        assertTrue(view.getChildAt(0).top < view.getChildAt(2).top)
        assertTrue(view.getChildAt(1).top < view.getChildAt(3).top)
        assertTrue(view.getChildAt(3).left < view.getChildAt(4).left)
        assertEquals(0, view.getChildAt(0).left)
        assertEquals(164, view.getChildAt(0).width)
        assertEquals(1_000, view.getChildAt(4).right)
    }

    @Test
    fun activeShiftColorIsCoupledAcrossBothOuterSegments() {
        val ledStatusView = LedStatusView(RuntimeEnvironment.getApplication()).apply {
            layout = ModifierLedLayouts.TITAN_2_ELITE
        }
        val view = ledStatusView.ensureView()

        ledStatusView.update(
            StatusBarController.StatusSnapshot(
                capsLockEnabled = false,
                shiftPhysicallyPressed = false,
                shiftOneShot = true,
                ctrlLatchActive = false,
                ctrlPhysicallyPressed = false,
                ctrlOneShot = false,
                ctrlLatchFromNavMode = false,
                altLatchActive = false,
                altPhysicallyPressed = false,
                altOneShot = false,
                symPage = 0
            )
        )

        val activeBlue = Color.rgb(100, 150, 255)
        assertEquals(activeBlue, view.getChildAt(2).getTag(R.id.led_previous_color))
        assertEquals(activeBlue, view.getChildAt(4).getTag(R.id.led_previous_color))
    }

    @Test
    fun heldSymUsesTheActiveColorBeforeASymPageIsOpened() {
        val ledStatusView = LedStatusView(RuntimeEnvironment.getApplication()).apply {
            layout = ModifierLedLayouts.TITAN_2_ELITE
        }
        val view = ledStatusView.ensureView()

        ledStatusView.update(
            StatusBarController.StatusSnapshot(
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
                symPage = 0,
                symPhysicallyPressed = true
            )
        )

        assertEquals(
            Color.rgb(100, 150, 255),
            view.getChildAt(1).getTag(R.id.led_previous_color)
        )
    }
}
