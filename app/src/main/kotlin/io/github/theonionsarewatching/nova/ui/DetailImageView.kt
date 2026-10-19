package io.github.theonionsarewatching.nova.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.exifinterface.media.ExifInterface
import coil.dispose
import coil.load
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.decoder.SkiaImageDecoder
import com.davemorrissey.labs.subscaleview.decoder.SkiaImageRegionDecoder
import io.github.theonionsarewatching.nova.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/** Decodes only the visible high-resolution tiles from the private attachment.
 *  No resized file, public album or full-size camera bitmap is created. */
class DetailImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private var tiled = SubsamplingScaleImageView(context)
    // Animated GIFs and formats without Android region-decoder support retain
    // Coil playback. Only those formats use the bounded bitmap fallback.
    private val fallback = object : AppCompatImageView(context) {
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val handled = fallbackTouch(event)
            if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            return handled
        }
        override fun performClick(): Boolean { super.performClick(); return true }
    }.apply { scaleType = ImageView.ScaleType.MATRIX }
    private val error = TextView(context).apply {
        setText(R.string.image_load_failed)
        setTextColor(android.graphics.Color.WHITE)
        gravity = android.view.Gravity.CENTER
        visibility = View.GONE
    }
    private var file: File? = null
    private var animated = false
    private var started = false
    private var generation = 0
    private var loadJob: Job? = null
    private var usingFallback = false
    private var fallbackZoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var fitRequested = true

    /** Touch zoom alone changes the activity's browsing/panning mode.
     *  Keypad zoom mode may deliberately stay active at the fit scale. */
    var onGestureZoom: ((Float) -> Unit)? = null

    val ready: Boolean get() = if (usingFallback) fallback.drawable != null else tiled.isReady
    val zoom: Float get() = if (usingFallback) fallbackZoom else
        if (tiled.isReady) tiled.scale / tiled.minScale.coerceAtLeast(0.00001f) else 1f
    val maxZoom: Float get() = if (usingFallback) max(8f, 1f / fallbackFit()) else
        if (tiled.isReady) tiled.maxScale / tiled.minScale.coerceAtLeast(0.00001f) else 8f

    init {
        addView(tiled, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(fallback, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(error, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        fallback.visibility = View.GONE
    }

    private fun configureTiled() {
        tiled.setRegionDecoderFactory { SkiaImageRegionDecoder(Bitmap.Config.ARGB_8888) }
        tiled.setBitmapDecoderFactory { SkiaImageDecoder(Bitmap.Config.ARGB_8888) }
        tiled.setMaxTileSize(512)
        tiled.setExecutor(decoders)
        tiled.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
        tiled.setQuickScaleEnabled(false)
        tiled.setOnImageEventListener(object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
            override fun onReady() {
                // Always allow original-pixel inspection, even when a very
                // large image needs much more than 4x screen-fit magnification.
                tiled.maxScale = max(1f, tiled.minScale * 8f)
                tiled.setDoubleTapZoomScale(min(tiled.maxScale, tiled.minScale * 4f))
            }
            override fun onImageLoadError(e: Exception) = useFallback(e)
            override fun onTileLoadError(e: Exception) = useFallback(e)
        })
        tiled.setOnStateChangedListener(object : SubsamplingScaleImageView.OnStateChangedListener {
            override fun onScaleChanged(newScale: Float, origin: Int) {
                if (origin == SubsamplingScaleImageView.ORIGIN_TOUCH ||
                    origin == SubsamplingScaleImageView.ORIGIN_DOUBLE_TAP_ZOOM) {
                    fitRequested = zoom <= 1.02f
                    onGestureZoom?.invoke(zoom)
                }
            }
            override fun onCenterChanged(newCenter: PointF?, origin: Int) {}
        })
    }

    fun bind(image: File, isAnimated: Boolean) {
        clear()
        file = image
        animated = isAnimated
        load()
    }

    private fun load() {
        val image = file ?: return
        if (started) return
        started = true
        fitRequested = true
        // A retired decoder can finish asynchronously. Give every binding its
        // own tile view so old work can never replace the new page's image.
        tiled = SubsamplingScaleImageView(context)
        addView(tiled, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        configureTiled()
        usingFallback = false
        error.visibility = View.GONE
        tiled.visibility = View.VISIBLE
        fallback.visibility = View.GONE
        val token = generation
        if (animated) { useFallback(); return }
        loadJob = CoroutineScope(Dispatchers.Main.immediate).launch {
            val exif = withContext(Dispatchers.IO) {
                try { ExifInterface(image).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }
                catch (_: Exception) { 1 }
            }
            if (token != generation) return@launch
            tiled.orientation = when (exif) {
                5, 6, 7 -> 90
                3 -> 180
                8 -> 270
                else -> 0
            }
            // The tiled decoder handles rotation; these two reflected view
            // axes cover the four mirrored EXIF orientations as well.
            tiled.scaleX = if (exif == 2 || exif == 5) -1f else 1f
            tiled.scaleY = if (exif == 4 || exif == 7) -1f else 1f
            tiled.setImage(ImageSource.uri(image.absolutePath))
        }
    }

    private fun useFallback(cause: Exception? = null) {
        if (!started || usingFallback) return
        val image = file ?: return
        usingFallback = true
        tiled.recycle()
        tiled.visibility = View.GONE
        fallback.visibility = View.VISIBLE
        fallbackZoom = 1f; panX = 0f; panY = 0f
        cause?.let {
            io.github.theonionsarewatching.nova.util.DiagLog.log(
                context, "image-viewer", "region decoder fallback: ${it.javaClass.simpleName}"
            )
        }
        fallback.load(image) {
            size(1536, 1536)
            allowHardware(false)
            bitmapConfig(Bitmap.Config.ARGB_8888)
            listener(
                onSuccess = { _, _ -> post { updateFallbackMatrix() } },
                onError = { _, _ -> error.visibility = View.VISIBLE }
            )
        }
    }

    fun zoomTo(relative: Float) {
        if (!ready) return
        val target = relative.coerceIn(1f, maxZoom)
        fitRequested = target <= 1.02f
        if (usingFallback) {
            fallbackZoom = target
            updateFallbackMatrix()
        } else {
            tiled.setScaleAndCenter(target * tiled.minScale, tiled.center)
        }
    }

    fun panBy(dx: Float, dy: Float) {
        if (!ready) return
        if (usingFallback) {
            panX += dx; panY += dy
            updateFallbackMatrix()
        } else {
            val center = tiled.center ?: return
            tiled.setScaleAndCenter(tiled.scale, PointF(
                center.x - dx * tiled.scaleX / tiled.scale,
                center.y - dy * tiled.scaleY / tiled.scale
            ))
        }
    }

    fun resetZoom() {
        fitRequested = true
        if (usingFallback) {
            fallbackZoom = 1f; panX = 0f; panY = 0f
            updateFallbackMatrix()
        } else tiled.resetScaleAndCenter()
    }

    private fun fallbackFit(): Float {
        val drawable = fallback.drawable ?: return 1f
        return min(width.toFloat() / drawable.intrinsicWidth.coerceAtLeast(1),
            height.toFloat() / drawable.intrinsicHeight.coerceAtLeast(1)).coerceAtLeast(0.00001f)
    }

    private fun updateFallbackMatrix() {
        val drawable = fallback.drawable ?: return
        val scale = fallbackFit() * fallbackZoom
        val w = drawable.intrinsicWidth * scale
        val h = drawable.intrinsicHeight * scale
        panX = panX.coerceIn(-max(0f, (w - width) / 2), max(0f, (w - width) / 2))
        panY = panY.coerceIn(-max(0f, (h - height) / 2), max(0f, (h - height) / 2))
        fallback.imageMatrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate((width - w) / 2 + panX, (height - h) / 2 + panY)
        }
    }

    private val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onDoubleTap(e: MotionEvent): Boolean {
            zoomTo(if (zoom > 1.02f) 1f else min(4f, maxZoom))
            onGestureZoom?.invoke(zoom)
            return true
        }
    })
    private val pinches = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomTo(zoom * detector.scaleFactor)
            onGestureZoom?.invoke(zoom)
            return true
        }
    })

    private fun fallbackTouch(event: MotionEvent): Boolean {
        if (event.pointerCount > 1 || zoom > 1.02f) parent?.requestDisallowInterceptTouchEvent(true)
        taps.onTouchEvent(event)
        pinches.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1 && !pinches.isInProgress && zoom > 1.02f) {
                panBy(event.x - lastX, event.y - lastY)
            }
        }
        lastX = event.x; lastY = event.y
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (usingFallback) updateFallbackMatrix()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // Zoom's icon softkeys are taller than the normal text bar. SSIV
        // preserves its old scale when that resizes the viewport, which can
        // overwrite Back's pending fit transform. Reapply fit AFTER layout.
        if (changed && fitRequested && !usingFallback && tiled.isReady) tiled.resetScaleAndCenter()
    }

    private fun release() {
        generation++
        loadJob?.cancel(); loadJob = null
        fallback.dispose(); fallback.setImageDrawable(null)
        tiled.setOnImageEventListener(null)
        tiled.setOnStateChangedListener(null)
        tiled.recycle()
        removeView(tiled)
        started = false
    }
    fun clear() { release(); file = null; onGestureZoom = null }
    override fun onDetachedFromWindow() { release(); super.onDetachedFromWindow() }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); load() }

    companion object {
        private val decoders = Executors.newFixedThreadPool(2) { task ->
            Thread(task, "keymsg-image-tiles").apply { isDaemon = true }
        }
    }
}
