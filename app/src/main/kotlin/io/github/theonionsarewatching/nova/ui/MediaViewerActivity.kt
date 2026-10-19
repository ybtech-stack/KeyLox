package io.github.theonionsarewatching.nova.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.dispose
import coil.load
import coil.request.videoFrameMillis
import io.github.theonionsarewatching.nova.R
import io.github.theonionsarewatching.nova.data.PartEntity
import io.github.theonionsarewatching.nova.data.Repo
import io.github.theonionsarewatching.nova.databinding.ActivityMediaBinding
import io.github.theonionsarewatching.nova.databinding.ItemMediaPageBinding
import kotlinx.coroutines.launch
import java.io.File

class MediaViewerActivity : BaseActivity() {

    companion object {
        const val EXTRA_CONVO_ID = "convo_id"
        const val EXTRA_PART_ID = "part_id"
    }

    private lateinit var binding: ActivityMediaBinding
    private lateinit var repo: Repo
    private var parts: List<PartEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = Repo.get(this)

        val convoId = intent.getLongExtra(EXTRA_CONVO_ID, -1L)
        val partId = savedInstanceState?.getLong(EXTRA_PART_ID)
            ?: intent.getLongExtra(EXTRA_PART_ID, -1L)

        binding.pager.orientation = ViewPager2.ORIENTATION_HORIZONTAL

        // Keep the softkey labels centered within their cells.
        binding.softkeyBar.softLeft.gravity = android.view.Gravity.CENTER
        binding.softkeyBar.softRight.gravity = android.view.Gravity.CENTER
        softkeys = Softkeys(this, binding.softkeyBar).also {
            it.set(
                getString(R.string.back), getString(R.string.play), getString(R.string.softkey_options),
                onLeft = { finish() },
                onCenter = { playCurrent() },
                onRight = { viewerOptions() },
                onMenu = { viewerOptions() }
            )
        }

        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (zoomMode) exitZoom()
                stopPlayback()
                updateCounter(position)
                updateMediaSoftkeys()
            }
        })

        lifecycleScope.launch {
            parts = repo.db.parts().mediaForConvo(convoId)
            if (parts.isEmpty()) { finish(); return@launch }
            binding.pager.adapter = PageAdapter(parts,
                onTap = { activateCurrent() },
                onGestureZoom = { photo, amount ->
                    if (photo === currentImageView()) {
                        zoomMode = amount > 1.02f
                        zoomedPhoto = if (zoomMode) photo else null
                        zoomScale = amount
                        binding.pager.isUserInputEnabled = !zoomMode
                        if (zoomMode) updateZoomSoftkeys() else updateMediaSoftkeys()
                    }
                })
            val start = parts.indexOfFirst { it.id == partId }.coerceAtLeast(0)
            binding.pager.setCurrentItem(start, false)
            updateCounter(start)
            updateMediaSoftkeys()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        currentPart()?.let { outState.putLong(EXTRA_PART_ID, it.id) }
        super.onSaveInstanceState(outState)
    }

    private fun updateCounter(position: Int) {
        binding.mediaCounter.text = getString(R.string.media_counter, position + 1, parts.size)
        val p = parts.getOrNull(position)
        binding.mediaName.text = p?.fileName ?: ""
    }

    private fun currentPart(): PartEntity? = parts.getOrNull(binding.pager.currentItem)

    /** id of the part whose video session is active (playing OR paused) */
    private var activeVideoPartId = -1L

    /** the live player behind the VideoView — needed for PRECISE seeking:
     *  default seeks snap BACKWARD to the previous keyframe, so a forward hop
     *  smaller than the keyframe gap lands where it started (looked dead) */
    private var activeMediaPlayer: android.media.MediaPlayer? = null

    private val progressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val progressTicker = object : Runnable {
        override fun run() {
            updateVideoProgress()
            if (isVideoPlaying()) progressHandler.postDelayed(this, 500)
        }
    }

    private fun fmtTime(ms: Int): String {
        val total = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(total / 60, total % 60)
    }

    private fun updateVideoProgress() {
        val vv = currentVideoView()
        if (vv == null || activeVideoPartId == -1L) {
            binding.videoProgressRow.visibility = View.GONE
            return
        }
        try {
            val dur = vv.duration
            if (dur <= 0) return
            binding.videoProgressRow.visibility = View.VISIBLE
            binding.videoSeekBar.max = dur
            binding.videoSeekBar.progress = vv.currentPosition.coerceIn(0, dur)
            binding.videoElapsed.text = fmtTime(vv.currentPosition)
            binding.videoTotal.text = fmtTime(dur)
        } catch (_: Exception) {}
    }

    private fun startProgressTicker() {
        progressHandler.removeCallbacks(progressTicker)
        progressHandler.post(progressTicker)
    }

    private fun currentVideoView(): android.widget.VideoView? {
        val p = currentPart() ?: return null
        if (!p.isVideo()) return null
        return binding.pager.findViewWithTag("video_${p.id}")
    }

    private fun isVideoPlaying(): Boolean =
        try { currentVideoView()?.isPlaying == true } catch (_: Exception) { false }

    /** Center softkey reads Play/Pause for videos and Zoom for images. */
    private fun updateMediaSoftkeys(forcePlaying: Boolean? = null) {
        val p = currentPart()
        val playing = forcePlaying ?: isVideoPlaying()
        val center = when {
            p != null && p.isVideo() && playing -> getString(R.string.pause)
            p != null && p.isVideo() -> getString(R.string.play)
            p != null && p.isImage() -> getString(R.string.zoom)
            else -> ""
        }
        softkeys?.set(
            getString(R.string.back), center, getString(R.string.softkey_options),
            onLeft = { finish() },
            onCenter = { activateCurrent() },
            onRight = { viewerOptions() },
            onMenu = { viewerOptions() }
        )
        // "Zoom" renders exactly like Back and Options — same size, same face
        binding.softkeyBar.softCenter.setTextSize(
            android.util.TypedValue.COMPLEX_UNIT_PX, binding.softkeyBar.softLeft.textSize
        )
        binding.softkeyBar.softCenter.typeface = binding.softkeyBar.softLeft.typeface
        // the viewer's bar shows even with softkeys off: pictures always
        // offer Zoom in the center, videos Play
        binding.softkeyBar.root.visibility =
            if (io.github.theonionsarewatching.nova.util.Prefs.get(this).touchMode)
                View.GONE else View.VISIBLE
    }

    // ---------------- zoom mode ----------------
    // center D-pad on a picture enters zoom: # zooms in, * zooms out (each
    // label shows only when that step is possible), the D-pad pans instead of
    // switching pictures, BACK returns to the normal viewer. Videos untouched.

    private var zoomMode = false
    private var zoomScale = 1f
    private var zoomedPhoto: DetailImageView? = null

    private fun activateCurrent() {
        if (currentPart()?.isImage() == true) enterZoom() else playCurrent()
    }

    private fun currentImageView(): DetailImageView? {
        if (currentPart()?.isImage() != true) return null
        val rv = binding.pager.getChildAt(0) as? RecyclerView ?: return null
        val page = rv.layoutManager?.findViewByPosition(binding.pager.currentItem) ?: return null
        return page.findViewById(R.id.pagePhoto)
    }

    private fun enterZoom() {
        val photo = currentImageView()?.takeIf { it.ready } ?: return
        zoomMode = true
        zoomedPhoto = photo
        zoomScale = photo.zoom
        binding.pager.isUserInputEnabled = false
        updateZoomSoftkeys()
    }

    private fun exitZoom() {
        zoomedPhoto?.resetZoom()
        zoomedPhoto = null
        zoomMode = false
        zoomScale = 1f
        binding.pager.isUserInputEnabled = true
        updateMediaSoftkeys()
    }

    private fun zoomStep(zoomIn: Boolean) {
        val photo = zoomedPhoto ?: return
        zoomScale = if (zoomIn) (zoomScale * 1.5f).coerceAtMost(photo.maxZoom)
            else (zoomScale / 1.5f).let { if (it < 1.15f) 1f else it }
        photo.zoomTo(zoomScale)
        updateZoomSoftkeys()
    }

    private fun panBy(dx: Float, dy: Float) { zoomedPhoto?.panBy(dx, dy) }

    private fun updateZoomSoftkeys() {
        val canOut = zoomScale > 1f
        val canIn = zoomScale < (zoomedPhoto?.maxZoom ?: 1f) - 0.01f
        val barOn = softkeys?.shouldShow() == true
        softkeys?.set(
            if (canOut) getString(R.string.zoom_out_label) else null,
            null,
            if (canIn) getString(R.string.zoom_in_label) else null,
            onLeft = { zoomStep(false) },
            onCenter = null,
            onRight = { zoomStep(true) },
            // MENU defaults to the left action — that would make the MENU key
            // zoom out. Keep MENU inert inside zoom mode (BACK exits).
            onMenu = {}
        )
        // softkey bar ON: the softkeys themselves zoom — each shows just its
        // magnifier. Bar OFF (forced-visible viewer bar): * and # zoom, with
        // the magnifiers sitting right beside those key labels.
        if (canOut) {
            binding.softkeyBar.softLeft.text = iconLabel(
                if (barOn) "" else "* ", R.drawable.ic_zoom_out, binding.softkeyBar.softLeft
            )
        }
        if (canIn) {
            binding.softkeyBar.softRight.text = iconLabel(
                if (barOn) "" else "# ", R.drawable.ic_zoom_in, binding.softkeyBar.softRight
            )
        }
        binding.softkeyBar.root.visibility =
            if (io.github.theonionsarewatching.nova.util.Prefs.get(this).touchMode)
                View.GONE else View.VISIBLE
    }

    /** "<txt> [icon]" as one centered piece, icon sized off the label font. */
    private fun iconLabel(
        txt: String, iconRes: Int, tv: android.widget.TextView
    ): CharSequence {
        val d = androidx.core.content.ContextCompat.getDrawable(this, iconRes)!!.mutate()
        val sz = (tv.textSize * 1.6f).toInt()
        d.setBounds(0, 0, sz, sz)
        d.setTint(tv.currentTextColor)
        val ssb = android.text.SpannableStringBuilder(txt).append("\u25A1")
        ssb.setSpan(
            android.text.style.ImageSpan(d, android.text.style.ImageSpan.ALIGN_BOTTOM),
            ssb.length - 1, ssb.length, 0
        )
        return ssb
    }

    private fun playCurrent() {
        val p = currentPart() ?: return
        if (!p.isVideo()) return
        val vv = binding.pager.findViewWithTag<android.widget.VideoView>("video_${p.id}") ?: return
        val poster = binding.pager.findViewWithTag<android.widget.ImageView>("poster_${p.id}")
        val badge = binding.pager.findViewWithTag<android.widget.ImageView>("badge_${p.id}")
        try {
            if (vv.isPlaying) {
                // PAUSE, not stop: position is kept, poster stays hidden so the
                // paused frame remains visible
                vv.pause()
                updateMediaSoftkeys(forcePlaying = false)
                updateVideoProgress() // freeze the bar at the paused position
                return
            }
            if (activeVideoPartId == p.id) {
                // resume a paused session
                vv.start()
                updateMediaSoftkeys(forcePlaying = true)
                startProgressTicker()
                return
            }
            vv.setVideoPath(p.filePath)
            vv.setOnPreparedListener { mp ->
                activeMediaPlayer = mp
                // the still frame sits ABOVE the video surface — hide it now
                poster?.visibility = View.GONE
                badge?.visibility = View.GONE
                updateMediaSoftkeys(forcePlaying = true)
            }
            vv.setOnCompletionListener {
                poster?.visibility = View.VISIBLE
                badge?.visibility = View.VISIBLE
                activeVideoPartId = -1L
                activeMediaPlayer = null
                updateMediaSoftkeys()
                binding.videoProgressRow.visibility = View.GONE
            }
            vv.setOnErrorListener { _, _, _ ->
                poster?.visibility = View.VISIBLE
                activeVideoPartId = -1L
                activeMediaPlayer = null
                updateMediaSoftkeys()
                videoPlaybackFailed()
                true
            }
            activeVideoPartId = p.id
            vv.start()
            updateMediaSoftkeys(forcePlaying = true)
            startProgressTicker()
        } catch (_: Exception) {
            videoPlaybackFailed()
        }
    }

    /** Skip a bit on a tap; hold for continuous fast-forward / rewind. */
    private fun seekBy(forward: Boolean, repeatCount: Int) {
        val vv = currentVideoView() ?: return
        try {
            // taps use a large stride because seeking snaps to keyframes — a
            // small hop can land back on the same frame and look like nothing
            val delta = if (repeatCount == 0) 10_000 else 15_000
            val dur = vv.duration
            if (dur <= 0) return
            // clamp short of the end: seeking TO the end fires the completion
            // handler, which resets to the poster — it looked like a restart
            val ceiling = (dur - 1500).coerceAtLeast(0)
            val target = (vv.currentPosition + if (forward) delta else -delta)
                .coerceIn(0, ceiling)
            val mp = activeMediaPlayer
            if (mp != null && android.os.Build.VERSION.SDK_INT >= 26) {
                // SEEK_CLOSEST decodes forward from the prior keyframe to land
                // exactly where asked — forward taps actually move now
                mp.seekTo(target.toLong(), android.media.MediaPlayer.SEEK_CLOSEST)
            } else {
                vv.seekTo(target)
            }
            updateVideoProgress()
        } catch (_: Exception) {}
    }

    private fun videoPlaybackFailed() {
        stopPlayback()
        android.widget.Toast.makeText(this, R.string.video_playback_failed,
            android.widget.Toast.LENGTH_LONG).show()
    }

    private fun openWith(p: PartEntity) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, "$packageName.fileprovider", File(p.filePath)
            )
            val viewIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, p.mimeType)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(viewIntent, getString(R.string.player_open_with)))
        } catch (_: Exception) {
            android.widget.Toast.makeText(this, R.string.no_app_for_file,
                android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /** MENU: fallback actions for files this phone's codecs struggle with. */
    private fun viewerOptions() {
        val p = currentPart() ?: return
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setItems(arrayOf(
                getString(R.string.player_open_with), getString(R.string.save)
            )) { _, which ->
                when (which) {
                    0 -> openWith(p)
                    1 -> saveCurrent()
                }
            }
            .show()
    }

    private fun stopPlayback() {
        // stop whichever page's VideoView is active, restore its poster frame
        for (p in parts) {
            val vv = binding.pager.findViewWithTag<android.widget.VideoView>("video_${p.id}") ?: continue
            try { vv.stopPlayback() } catch (_: Exception) {}
            binding.pager.findViewWithTag<android.widget.ImageView>("poster_${p.id}")
                ?.visibility = View.VISIBLE
        }
        activeVideoPartId = -1L
        activeMediaPlayer = null
        updateMediaSoftkeys()
        progressHandler.removeCallbacks(progressTicker)
        binding.videoProgressRow.visibility = View.GONE
    }

    private fun saveCurrent() {
        val p = currentPart() ?: return
        lifecycleScope.launch {
            val loc = Saver.save(this@MediaViewerActivity, File(p.filePath), p.fileName, p.mimeType)
            android.widget.Toast.makeText(
                this@MediaViewerActivity,
                if (loc != null) getString(R.string.saved_to, loc) else getString(R.string.save_failed),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onPause() {
        super.onPause()
        stopPlayback()
        if (zoomMode) updateZoomSoftkeys()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (zoomMode) {
            // volume passes through; everything else belongs to zoom mode
            if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
            ) return super.dispatchKeyEvent(event)
            // PHYSICAL SOFTKEYS ZOOM: the bar's zoom actions were wired but this
            // branch swallowed every key before handleKey ever saw it — labels
            // showed, softkeys did nothing. ORDER MATTERS: zoom's own keys are
            // handled FIRST — on plenty of flips a softkey is physically the
            // BACK key, and letting handleKey see BACK would turn "exit zoom"
            // into "zoom in" with no way out (same for softkeys that emit
            // D-pad/*/# codes, which would break panning). Softkeys only get
            // the codes zoom mode doesn't already own.
            val zoomOwned = when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_POUND, KeyEvent.KEYCODE_STAR,
                KeyEvent.KEYCODE_BACK -> true
                else -> false
            }
            if (!zoomOwned && softkeys?.handleKey(event) == true) return true
            if (event.action == KeyEvent.ACTION_DOWN) {
                val step = 60 * resources.displayMetrics.density
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> panBy(step, 0f)
                    KeyEvent.KEYCODE_DPAD_RIGHT -> panBy(-step, 0f)
                    KeyEvent.KEYCODE_DPAD_UP -> panBy(0f, step)
                    KeyEvent.KEYCODE_DPAD_DOWN -> panBy(0f, -step)
                    KeyEvent.KEYCODE_POUND -> zoomStep(true)
                    KeyEvent.KEYCODE_STAR -> zoomStep(false)
                    KeyEvent.KEYCODE_BACK -> exitZoom()
                }
            }
            return true
        }
        if (softkeys?.handleKey(event) == true) return true
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (isVideoPlaying()) {
                        // tap = skip back a bit; hold = rewind
                        seekBy(forward = false, repeatCount = event.repeatCount)
                        return true
                    }
                    binding.pager.setCurrentItem((binding.pager.currentItem - 1).coerceAtLeast(0), true)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (isVideoPlaying()) {
                        // tap = skip forward a bit; hold = fast-forward
                        seekBy(forward = true, repeatCount = event.repeatCount)
                        return true
                    }
                    binding.pager.setCurrentItem(
                        (binding.pager.currentItem + 1).coerceAtMost((parts.size - 1).coerceAtLeast(0)), true
                    )
                    return true
                }
                KeyEvent.KEYCODE_MENU -> {
                    viewerOptions()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    activateCurrent()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    class PageAdapter(
        private val parts: List<PartEntity>,
        private val onTap: () -> Unit,
        private val onGestureZoom: (DetailImageView, Float) -> Unit
    ) : RecyclerView.Adapter<PageAdapter.VH>() {

        class VH(val b: ItemMediaPageBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemMediaPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = parts.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val p = parts[position]
            holder.b.pagePhoto.clear()
            holder.b.pagePhoto.visibility = View.GONE
            holder.b.pageImage.setImageDrawable(null)
            holder.b.pageImage.tag = null
            holder.b.pageVideo.tag = null
            holder.b.pagePlayBadge.tag = null
            holder.b.pageImage.visibility = View.GONE
            holder.b.pageVideo.visibility = View.GONE
            holder.b.pageLabel.visibility = View.GONE
            holder.b.pagePlayBadge.visibility = View.GONE
            when {
                p.isImage() -> {
                    holder.b.pagePhoto.visibility = View.VISIBLE
                    holder.b.pagePhoto.bind(File(p.filePath), p.mimeType.equals("image/gif", true))
                    holder.b.pagePhoto.onGestureZoom = { amount -> onGestureZoom(holder.b.pagePhoto, amount) }
                }
                p.isVideo() -> {
                    holder.b.pageVideo.visibility = View.VISIBLE
                    holder.b.pageVideo.tag = "video_${p.id}"
                    holder.b.pageImage.visibility = View.VISIBLE
                    holder.b.pageImage.tag = "poster_${p.id}"
                    holder.b.pageImage.load(File(p.filePath)) {
                        videoFrameMillis(1000) // frame zero is often black
                    }
                    // play badge on a DEDICATED overlay view (not a foreground on
                    // the async-loaded poster, which didn't render reliably). The
                    // overlay is tagged so playback can hide it.
                    holder.b.pagePlayBadge.visibility = View.VISIBLE
                    holder.b.pagePlayBadge.tag = "badge_${p.id}"
                }
                else -> {
                    holder.b.pageLabel.visibility = View.VISIBLE
                    holder.b.pageLabel.text = p.fileName
                }
            }
            holder.itemView.setOnClickListener { onTap() }
        }

        override fun onViewRecycled(holder: VH) {
            holder.b.pagePhoto.clear()
            holder.b.pageImage.dispose()
            try { holder.b.pageVideo.stopPlayback() } catch (_: Exception) { }
            super.onViewRecycled(holder)
        }
    }
}
