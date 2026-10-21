package dev.glaze

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.annotation.RequiresApi
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Tracks attached glass hosts and records backdrops without them.
 *
 * Glass is never drawn into any backdrop, so glass never refracts glass and display lists
 * can't form a cycle (A's lens → B's host → B's lens → A's host → …).
 */
// Views are held weakly, or only within a frame's cache that is dropped whenever a host detaches
@SuppressLint("StaticFieldLeak")
internal object GlazeRegistry {

    private val hosts = ArrayList<WeakReference<View>>()
    private var version = 0

    /** > 0 while a backdrop is being recorded; containers skip their own lens then. */
    var capturing = 0
        private set

    fun add(host: View) {
        hosts.removeAll { it.get() == null || it.get() === host }
        hosts.add(WeakReference(host))
        version++
    }

    fun remove(host: View) {
        hosts.removeAll { it.get() == null || it.get() === host }
        version++
        branches.clear()
        cachedSource = null
    }

    // —— Branch set: every glass host under the source plus all its ancestors up to the source.
    // Cached per frame and source, since each glass on screen asks for the same set.
    private val branches: MutableSet<View> = Collections.newSetFromMap(IdentityHashMap())
    private var cachedSource: View? = null
    private var cachedFrame = -1L
    private var cachedVersion = -1

    private fun branchesFor(source: View): Set<View> {
        val frame = AnimationUtils.currentAnimationTimeMillis()
        if (source === cachedSource && frame == cachedFrame && version == cachedVersion) return branches
        branches.clear()
        for (ref in hosts) {
            val host = ref.get() ?: continue
            if (!host.isAttachedToWindow) continue
            val chain = ArrayList<View>(16)
            var v: View = host
            var reached = false
            while (true) {
                chain.add(v)
                val p = v.parent
                if (p === source) {
                    reached = true
                    break
                }
                if (p !is View) break
                v = p
            }
            if (reached) branches.addAll(chain)
        }
        cachedSource = source
        cachedFrame = frame
        cachedVersion = version
        return branches
    }

    private fun isGlass(view: View): Boolean = view is GlazeView

    // Reused to avoid per-frame allocation
    private val hostLocation = IntArray(2)
    private val childLocation = IntArray(2)

    /**
     * Draws [source] into [canvas] (origin at the source's top-left), leaving out every glass host.
     *
     * Views on a path to glass are drawn with the public [View.draw], which never touches their
     * RenderNode, and their glass-bearing children are hidden, then drawn on top one level down.
     * Everything else goes in by reference to its existing display list: no pixel copies.
     * Trade-off (from upstream): a glass-bearing branch is drawn after its siblings, so a
     * sibling stacked above it may be covered in the refraction.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun drawBackdrop(canvas: Canvas, source: View) {
        val set = branchesFor(source)
        capturing++
        try {
            drawLevel(canvas, source, set)
        } finally {
            capturing--
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun drawLevel(canvas: Canvas, host: View, set: Set<View>) {
        var hidden: ArrayList<View>? = null
        if (host is ViewGroup) {
            for (i in 0 until host.childCount) {
                val child = host.getChildAt(i)
                if (child.visibility == View.VISIBLE && child in set) {
                    if (hidden == null) hidden = ArrayList(4)
                    hidden.add(child)
                }
            }
        }

        val save = canvas.save()
        // The public draw() skips the scroll offset the framework applies, so add it here
        canvas.translate(-host.scrollX.toFloat(), -host.scrollY.toFloat())
        // setTransitionVisibility flips the flag without invalidating
        hidden?.forEach { it.setTransitionVisibility(View.INVISIBLE) }
        try {
            host.draw(canvas)
        } finally {
            hidden?.forEach { it.setTransitionVisibility(View.VISIBLE) }
            canvas.restoreToCount(save)
        }
        if (hidden == null) return

        host.getLocationOnScreen(hostLocation)
        val clip = (host as? ViewGroup)?.clipChildren != false ||
            host.canScrollVertically(1) || host.canScrollVertically(-1) ||
            host.canScrollHorizontally(1) || host.canScrollHorizontally(-1)
        for (child in hidden) {
            if (isGlass(child)) continue
            child.getLocationOnScreen(childLocation)
            val s = canvas.save()
            if (clip) canvas.clipRect(0f, 0f, host.width.toFloat(), host.height.toFloat())
            canvas.translate(
                (childLocation[0] - hostLocation[0]).toFloat(),
                (childLocation[1] - hostLocation[1]).toFloat()
            )
            // Screen positions already include the child's alpha-free transforms; apply its alpha
            if (child.alpha < 1f) {
                canvas.saveLayerAlpha(0f, 0f, child.width.toFloat(), child.height.toFloat(), (child.alpha * 255).toInt())
            }
            drawLevel(canvas, child, set)
            canvas.restoreToCount(s)
            // drawLevel reused the location arrays; restore the host's
            host.getLocationOnScreen(hostLocation)
        }
    }
}
