package it.palsoftware.pastiera.inputmethod.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.T2eCornerCalibration
import it.palsoftware.pastiera.T2eCornerGeometry
import it.palsoftware.pastiera.inputmethod.StatusBarController
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Compact controller around the LED strip at the bottom of the IME status bar.
 */
class LedStatusView(
    private val context: Context
) {
    internal data class ButtonContour(
        val points: List<PointF>,
        val borderHalfWidthPx: Float
    )

    internal data class ContourGeometry(
        val buttonTopPx: Float,
        val leftButtonEndPx: Float,
        val rightButtonStartPx: Float,
        val leftButtonContour: ButtonContour? = null,
        val rightButtonContour: ButtonContour? = null
    )

    companion object {
        internal const val LED_ZONE_HEIGHT_DP = 6.5f
        private const val CONTOUR_LED_STROKE_DP = 1.4f
        private const val CONTOUR_LED_GAP_DP = 2.2f
        private const val CONTOUR_EDGE_PADDING_DP = 1f
        // Keep the two clear gaps equal: outer rail -> inner rail -> button border.
        internal const val CONTOUR_BUTTON_INSET_DP =
            CONTOUR_EDGE_PADDING_DP + 2f * CONTOUR_LED_STROKE_DP + 2f * CONTOUR_LED_GAP_DP
        private const val LED_CONTENT_HEIGHT_DP = 5.5f
        private const val LED_TOP_PADDING_DP = LED_ZONE_HEIGHT_DP - LED_CONTENT_HEIGHT_DP
        private val LED_COLOR_GRAY_OFF = Color.argb(100, 17, 17, 17)
        private val LED_COLOR_RED_LOCKED = Color.rgb(247, 99, 0)
        private val LED_COLOR_BLUE_ACTIVE = Color.rgb(100, 150, 255)
    }

    private val ledHeight: Int by lazy {
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            LED_CONTENT_HEIGHT_DP,
            context.resources.displayMetrics
        ).toInt()
    }
    private val topPadding: Int by lazy {
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            LED_TOP_PADDING_DP,
            context.resources.displayMetrics
        ).toInt()
    }
    private val cornerRadius: Float by lazy {
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            3f,
            context.resources.displayMetrics
        )
    }

    private var container: ModifierLedCanvas? = null
    private val ledsByState = mutableMapOf<ModifierLedState, MutableList<View>>()
    private val segmentsByView = mutableMapOf<View, ModifierLedSegment>()

    var bottomCornerRadiiPx: Pair<Int, Int>? = null
        set(value) {
            if (field == value) return
            field = value
            container?.cornerRadiiPx = value
            container?.let { canvas ->
                for (index in 0 until canvas.childCount) canvas.getChildAt(index).invalidate()
            }
        }

    internal var layout: ModifierLedLayout = ModifierLedLayouts.DEFAULT
        set(value) {
            if (field == value) return
            field = value
            rebuildSegments()
        }

    var contourIntegrated: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            rebuildSegments()
            container?.invalidate()
        }

    internal var contourGeometry: ContourGeometry? = null
        set(value) {
            if (field == value) return
            field = value
            container?.let { canvas ->
                for (index in 0 until canvas.childCount) canvas.getChildAt(index).invalidate()
            }
        }

    var onLongPressListener: (() -> Unit)? = null
    var themeOverride: KeyboardThemeColors? = null

    fun ensureView(): ViewGroup {
        container?.let { return it }

        container = ModifierLedCanvas(context, ledHeight).apply {
            cornerRadiiPx = bottomCornerRadiiPx
            setPadding(0, topPadding, 0, 0)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnLongClickListener {
                onLongPressListener?.invoke()
                true
            }
        }

        rebuildSegments()

        return container!!
    }

    fun getView(): ViewGroup? = container

    fun update(snapshot: StatusBarController.StatusSnapshot) {
        val shiftLocked = snapshot.capsLockEnabled
        val shiftActive = (snapshot.shiftPhysicallyPressed || snapshot.shiftOneShot) && !shiftLocked
        updateLeds(ModifierLedState.SHIFT, shiftLocked, shiftActive)

        val ctrlLocked = snapshot.ctrlLatchActive
        val ctrlActive = (snapshot.ctrlPhysicallyPressed || snapshot.ctrlOneShot) && !ctrlLocked
        updateLeds(ModifierLedState.CTRL, ctrlLocked, ctrlActive)

        val altLocked = snapshot.altLatchActive
        val altActive = (snapshot.altPhysicallyPressed || snapshot.altOneShot) && !altLocked
        updateLeds(ModifierLedState.ALT, altLocked, altActive)

        updateSymLeds(snapshot.symPage, snapshot.symPhysicallyPressed)
    }

    private fun rebuildSegments() {
        val canvas = container ?: return
        ledsByState.clear()
        segmentsByView.clear()
        canvas.replaceSegments(layout.segments) { segment ->
            createLedView(themeOverride?.ledInactive ?: LED_COLOR_GRAY_OFF, segment).also { led ->
                ledsByState.getOrPut(segment.state) { mutableListOf() }.add(led)
            }
        }
    }

    private fun createLedView(initialColor: Int, segment: ModifierLedSegment): View {
        return View(context).apply {
            segmentsByView[this] = segment
            background = createDrawable(initialColor, segment)
            setTag(R.id.led_previous_color, initialColor)
        }
    }

    private fun createDrawable(color: Int, segment: ModifierLedSegment): GradientDrawable {
        return object : GradientDrawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }

            override fun draw(canvas: Canvas) {
                val radii = bottomCornerRadiiPx
                if (radii == null) {
                    super.draw(canvas)
                    return
                }
                if (contourIntegrated && layout == ModifierLedLayouts.TITAN_2_ELITE) {
                    drawContourIndicator(canvas, segment, paint, radii)
                    return
                }
                val width = bounds.width().toFloat()
                val height = bounds.height().toFloat()
                val inset = (1f - segment.y - segment.height / 2f) * ledHeight + topPadding
                paint.strokeWidth = segment.height * ledHeight
                paint.strokeCap = Paint.Cap.ROUND
                val calibration = T2eCornerCalibration.read(context)
                val centerY = height - calibration.offsetPx - inset + calibration.shiftYPx
                val boundaryY = centerY - calibration.shiftYPx
                val contourInset = paint.strokeWidth / 2f
                val left = T2eCornerGeometry.atY(
                    radii.first.toFloat(), height, boundaryY, calibration, contourInset
                ).x + calibration.shiftXPx
                val right = width - T2eCornerGeometry.atY(
                    radii.second.toFloat(), height, boundaryY, calibration, contourInset
                ).x + calibration.shiftXPx
                val availableWidth = (right - left).coerceAtLeast(0f)
                val cap = paint.strokeWidth / 2f
                val start = left + availableWidth * segment.x + cap
                val end = left + availableWidth * (segment.x + segment.width) - cap
                if (end > start) {
                    canvas.drawLine(start, centerY, end, centerY, paint)
                }
            }
        }.apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = this@LedStatusView.cornerRadius
        }
    }

    private fun drawContourIndicator(
        canvas: Canvas,
        segment: ModifierLedSegment,
        paint: Paint,
        @Suppress("UNUSED_PARAMETER") radii: Pair<Int, Int>
    ) {
        val stroke = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            CONTOUR_LED_STROKE_DP,
            context.resources.displayMetrics
        )
        val railGap = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            CONTOUR_LED_GAP_DP,
            context.resources.displayMetrics
        )
        val geometry = contourGeometry ?: return
        val leftSide = segment.x < 0.5f
        val outerRail = segment.state == ModifierLedState.SHIFT
        val buttonContour = (
            if (leftSide) geometry.leftButtonContour else geometry.rightButtonContour
        ) ?: return
        if (buttonContour.points.size < 2) return
        val distanceFromButtonCenterline = buttonContour.borderHalfWidthPx + railGap + stroke / 2f +
            if (outerRail) stroke + railGap else 0f
        val offsetPoints = buttonContour.points.mapIndexed { index, point ->
            val previous = buttonContour.points[(index - 1).coerceAtLeast(0)]
            val next = buttonContour.points[(index + 1).coerceAtMost(buttonContour.points.lastIndex)]
            val dx = next.x - previous.x
            val dy = next.y - previous.y
            val length = hypot(dx, dy).coerceAtLeast(0.001f)
            val normalX = if (leftSide) -dy / length else dy / length
            val normalY = if (leftSide) dx / length else -dx / length
            PointF(
                point.x + normalX * distanceFromButtonCenterline,
                point.y + normalY * distanceFromButtonCenterline
            )
        }
        val topCenterY = geometry.buttonTopPx + stroke / 2f
        val topTrimmed = ArrayList<PointF>(offsetPoints.size)
        for (index in 1 until offsetPoints.size) {
            val previous = offsetPoints[index - 1]
            val point = offsetPoints[index]
            if (topTrimmed.isEmpty()) {
                if (point.y < topCenterY) continue
                val denominator = point.y - previous.y
                val ratio = if (kotlin.math.abs(denominator) < 0.001f) 1f
                    else ((topCenterY - previous.y) / denominator).coerceIn(0f, 1f)
                topTrimmed += PointF(
                    previous.x + (point.x - previous.x) * ratio,
                    topCenterY
                )
            }
            topTrimmed += point
        }
        if (topTrimmed.size < 2) return

        val targetX = if (leftSide) geometry.leftButtonEndPx - stroke / 2f
            else geometry.rightButtonStartPx + stroke / 2f
        val trimmed = ArrayList<PointF>(topTrimmed.size)
        for (point in topTrimmed) {
            val reached = if (leftSide) point.x >= targetX else point.x <= targetX
            if (!reached) {
                trimmed += point
                continue
            }
            val previous = trimmed.lastOrNull()
            if (previous != null) {
                val denominator = point.x - previous.x
                val ratio = if (kotlin.math.abs(denominator) < 0.001f) 1f
                    else ((targetX - previous.x) / denominator).coerceIn(0f, 1f)
                trimmed += PointF(targetX, previous.y + (point.y - previous.y) * ratio)
            }
            break
        }
        if (trimmed.size < 2) return

        val cumulative = FloatArray(trimmed.size)
        for (index in 1 until trimmed.size) {
            cumulative[index] = cumulative[index - 1] + hypot(
                trimmed[index].x - trimmed[index - 1].x,
                trimmed[index].y - trimmed[index - 1].y
            )
        }
        val totalLength = cumulative.last().coerceAtLeast(0.001f)
        val range = when (segment.state) {
            ModifierLedState.SYM -> 0f to 0.46f
            ModifierLedState.CTRL -> 0.54f to 1f
            else -> 0f to 1f
        }
        fun pointAt(distance: Float): PointF {
            val target = distance.coerceIn(0f, totalLength)
            var index = 1
            while (index < cumulative.size && cumulative[index] < target) index++
            if (index >= cumulative.size) return trimmed.last()
            val segmentLength = (cumulative[index] - cumulative[index - 1]).coerceAtLeast(0.001f)
            val ratio = (target - cumulative[index - 1]) / segmentLength
            val from = trimmed[index - 1]
            val to = trimmed[index]
            return PointF(from.x + (to.x - from.x) * ratio, from.y + (to.y - from.y) * ratio)
        }
        val path = android.graphics.Path()
        val rangeStart = range.first * totalLength
        val rangeEnd = range.second * totalLength
        val first = pointAt(rangeStart)
        path.moveTo(first.x, first.y)
        for (index in 1 until trimmed.size) {
            if (cumulative[index] <= rangeStart) continue
            if (cumulative[index] >= rangeEnd) break
            path.lineTo(trimmed[index].x, trimmed[index].y)
        }
        val last = pointAt(rangeEnd)
        path.lineTo(last.x, last.y)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(path, paint)
    }

    private fun updateLeds(state: ModifierLedState, isLocked: Boolean, isActive: Boolean = false) {
        val theme = themeOverride
        val targetColor = when {
            isLocked -> theme?.ledLocked ?: LED_COLOR_RED_LOCKED
            isActive -> theme?.ledActive ?: LED_COLOR_BLUE_ACTIVE
            else -> theme?.ledInactive ?: LED_COLOR_GRAY_OFF
        }
        ledsByState[state].orEmpty().forEach { led -> animateLedColor(led, targetColor) }
    }

    private fun updateSymLeds(symPage: Int, physicallyPressed: Boolean) {
        val theme = themeOverride
        val targetColor = when (symPage) {
            1 -> theme?.ledActive ?: LED_COLOR_BLUE_ACTIVE
            2 -> theme?.ledLocked ?: LED_COLOR_RED_LOCKED
            3 -> theme?.ledActive ?: LED_COLOR_BLUE_ACTIVE
            4 -> theme?.ledActive ?: LED_COLOR_BLUE_ACTIVE
            else -> if (physicallyPressed) {
                theme?.ledActive ?: LED_COLOR_BLUE_ACTIVE
            } else {
                theme?.ledInactive ?: LED_COLOR_GRAY_OFF
            }
        }
        ledsByState[ModifierLedState.SYM].orEmpty().forEach { led -> animateLedColor(led, targetColor) }
    }

    private fun animateLedColor(led: View?, targetColor: Int) {
        led ?: return
        val previousColor = (led.getTag(R.id.led_previous_color) as? Int) ?: LED_COLOR_GRAY_OFF
        led.setTag(R.id.led_previous_color, targetColor)

        if (previousColor == targetColor) {
            led.background = createDrawable(targetColor, segmentsByView.getValue(led))
            return
        }

        ValueAnimator.ofArgb(previousColor, targetColor).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                segmentsByView[led]?.let { led.background = createDrawable(color, it) }
            }
        }.start()
    }

    private class ModifierLedCanvas(
        context: Context,
        private val contentHeightPx: Int
    ) : ViewGroup(context) {
        private val segments = mutableListOf<ModifierLedSegment>()
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            // Rounded indicators overlap the controls. Their transparent center
            // must not become a full-row long-press target above those controls.
            if (cornerRadiiPx != null && event.actionMasked == MotionEvent.ACTION_DOWN) {
                val edge = LED_ZONE_HEIGHT_DP * resources.displayMetrics.density
                if (event.x > edge && event.x < width - edge && event.y < height - edge) {
                    return false
                }
            }
            return super.dispatchTouchEvent(event)
        }

        var cornerRadiiPx: Pair<Int, Int>? = null
            set(value) {
                field = value
                requestLayout()
            }

        fun replaceSegments(
            newSegments: List<ModifierLedSegment>,
            createView: (ModifierLedSegment) -> View
        ) {
            removeAllViews()
            segments.clear()
            newSegments.forEach { segment ->
                segments.add(segment)
                addView(createView(segment))
            }
            requestLayout()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val measuredWidth = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
            val curveHeight = cornerRadiiPx?.let { maxOf(it.first, it.second) } ?: 0
            val desiredHeight = paddingTop + maxOf(contentHeightPx, curveHeight) + paddingBottom
            val measuredHeight = resolveSize(desiredHeight, heightMeasureSpec)
            val contentWidth = (measuredWidth - paddingLeft - paddingRight).coerceAtLeast(0)
            val availableHeight = (measuredHeight - paddingTop - paddingBottom).coerceAtLeast(0)

            for (index in 0 until childCount) {
                val child = getChildAt(index)
                val segment = segments[index]
                if (cornerRadiiPx != null) {
                    child.measure(
                        MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
                    )
                    continue
                }
                child.measure(
                    MeasureSpec.makeMeasureSpec(
                        (contentWidth * segment.width).roundToInt().coerceAtLeast(1),
                        MeasureSpec.EXACTLY
                    ),
                    MeasureSpec.makeMeasureSpec(
                        (availableHeight * segment.height).roundToInt().coerceAtLeast(1),
                        MeasureSpec.EXACTLY
                    )
                )
            }
            setMeasuredDimension(measuredWidth, measuredHeight)
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            val contentWidth = (right - left - paddingLeft - paddingRight).coerceAtLeast(0)
            val availableHeight = (bottom - top - paddingTop - paddingBottom).coerceAtLeast(0)
            for (index in 0 until childCount) {
                val child = getChildAt(index)
                val segment = segments[index]
                if (cornerRadiiPx != null) {
                    child.layout(0, 0, right - left, bottom - top)
                    continue
                }
                val childLeft = paddingLeft + (contentWidth * segment.x).roundToInt()
                val childTop = paddingTop + (availableHeight * segment.y).roundToInt()
                child.layout(
                    childLeft,
                    childTop,
                    childLeft + child.measuredWidth,
                    childTop + child.measuredHeight
                )
            }
        }

    }
}
