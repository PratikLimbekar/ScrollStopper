package com.example.scrollstopper

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

class SocialGuardService : AccessibilityService() {

    companion object {
        private const val TAG = "SocialGuard"
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"

        private const val BACK_COOLDOWN_MS = 800L
        private const val REEL_GRACE_MS = 1200L
        private const val REEL_NULL_TOLERANCE_MS = 600L
        private const val REEL_CHANGE_CONFIRM_MS = 300L
        private const val DM_ORIGIN_WINDOW_MS = 4000L     // a DM must have been seen this recently
        private const val HOME_SCROLL_THRESHOLD_PX = 120
        private const val HOME_SCROLL_WINDOW_MS = 1500L
        private const val BLOCK_DM_CHAT_SCROLL = false

        // A scrolled view must cover at least this share of screen height to count as a reel swipe.
        private const val REEL_PAGER_MIN_HEIGHT_RATIO = 0.85f

        private const val REEL_ACTIVE_MARKER = "Double-tap to play or pause"
    }

    private enum class Browse { UNKNOWN, HOME, DM }

    private class Signals {
        var reelsText = false
        var friendsText = false
        var createReel = false
        var reelIdentity: String? = null   // visible, genuinely playing reel only
        var audioCall = false
        var videoCall = false
        var messageInput = false
        var homeFeed = false
        var commentInput = false
        var gridThumb = false
        override fun toString() =
            "reelsText=$reelsText friendsText=$friendsText createReel=$createReel " +
                    "reelId=$reelIdentity audioCall=$audioCall videoCall=$videoCall " +
                    "msgInput=$messageInput homeFeed=$homeFeed commentInput=$commentInput" + " gridThumb=$gridThumb"
    }

    private var lastBrowse = Browse.UNKNOWN
    private var lastDmSeenAt = 0L
    private var lastGridSeenAt = 0L

    private var inReel = false
    private var reelAllowed = false
    private var reelIdentity: String? = null
    private var reelEnteredAt = 0L
    private var lastReelSeenAt = 0L
    private var pendingReelId: String? = null
    private var pendingReelSince = 0L

    private var lastBackAt = 0L
    private var homeScrollAcc = 0
    private var homeScrollWindowStart = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName?.toString() != INSTAGRAM_PACKAGE) return

        val now = SystemClock.uptimeMillis()
        if (now - lastBackAt < BACK_COOLDOWN_MS) return

        val root = rootInActiveWindow ?: return
        val s = Signals()
        try {
            scan(root, s)
        } finally {
            @Suppress("DEPRECATION") root.recycle()
        }

        val isDm = (s.audioCall && s.videoCall) || s.messageInput
        val reelId = s.reelIdentity
        val isScroll = event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED

        val reelsTabSignature = (s.reelsText && s.friendsText) || s.createReel
        val fromDm = lastBrowse == Browse.DM && now - lastDmSeenAt < DM_ORIGIN_WINDOW_MS
        val fromGrid = now - lastGridSeenAt < DM_ORIGIN_WINDOW_MS
        val originOk = fromDm || fromGrid
        val isReelsTab = reelsTabSignature &&
                !(reelId != null && (originOk || (inReel && reelAllowed)))

        // 1) Reels tab: always blocked.
        if (isReelsTab) {
            block("Reels tab", s)
            return
        }

        // 2) A real reel is playing.
        if (reelId != null) {
            lastReelSeenAt = now

            if (!inReel) {
                inReel = true
                reelIdentity = reelId
                reelEnteredAt = now
                pendingReelId = null
                reelAllowed = originOk

                if (!reelAllowed) {
                    block("Reel opened from $lastBrowse", s)
                } else {
                    Log.d(TAG, "ALLOW: one reel from DM ($reelId)")
                }
                return
            }

            if (reelAllowed) {
                if (reelId != reelIdentity) {
                    if (pendingReelId != reelId) {
                        pendingReelId = reelId
                        pendingReelSince = now
                    } else if (now - pendingReelSince >= REEL_CHANGE_CONFIRM_MS) {
                        block("Reel changed: $reelIdentity -> $reelId", s)
                        return
                    }
                } else {
                    pendingReelId = null
                }

                if (isScroll && now - reelEnteredAt > REEL_GRACE_MS && isVertical(event)) {
                    val pager = isReelPagerScroll(event)
                    Log.d(TAG, "REEL SCROLL pager=$pager commentInput=${s.commentInput} " +
                            "srcHeight=${sourceHeightRatio(event)}")
                    if (pager && !s.commentInput) {
                        block("Scrolled inside DM reel", s)
                        return
                    }
                }
            }
            return
        }

        // 3) No playing reel. Tolerate flicker, then end the session.
        if (inReel) {
            if (now - lastReelSeenAt < REEL_NULL_TOLERANCE_MS) return
            Log.d(TAG, "Reel session ended")
            inReel = false
            reelAllowed = false
            reelIdentity = null
            pendingReelId = null
        }

        val newBrowse = when {
            isDm -> Browse.DM
            s.homeFeed -> Browse.HOME
            else -> null
        }
        if (isDm) lastDmSeenAt = now
        if (s.gridThumb) lastGridSeenAt = now

        if (newBrowse != null && newBrowse != lastBrowse) {
            Log.d(TAG, "Surface: $lastBrowse -> $newBrowse")
            lastBrowse = newBrowse
            homeScrollAcc = 0
        }

        // 4) Scroll handling outside reels.
        if (isScroll && isVertical(event)) {
            if (isDm) {
                if (BLOCK_DM_CHAT_SCROLL) block("Scrolled in DM chat", s)
                return
            }
            val inHome = sourceInHomeFeed(event)
            Log.d(TAG, "SCROLL inHomeFeed=$inHome class=${event.className}")
            if (inHome) {
                if (now - homeScrollWindowStart > HOME_SCROLL_WINDOW_MS) {
                    homeScrollWindowStart = now
                    homeScrollAcc = 0
                }
                homeScrollAcc += verticalDelta(event)
                if (homeScrollAcc >= HOME_SCROLL_THRESHOLD_PX) {
                    block("Scrolled on Home", s)
                }
            }
        }
    }

    private fun scan(node: AccessibilityNodeInfo, s: Signals) {
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()

        if (text == "Reels") s.reelsText = true
        if (text == "Friends") s.friendsText = true
        if (text == "Message..." || desc == "Message...") s.messageInput = true
        if (text?.startsWith("Add a comment") == true ||
            desc?.startsWith("Add a comment") == true
        ) s.commentInput = true

        if (desc != null) {
            if (desc.startsWith("Reel by ") && desc.contains(" at row ")) s.gridThumb = true
            when (desc) {
                "Create a reel" -> s.createReel = true
                "Audio call" -> s.audioCall = true
                "Video call" -> s.videoCall = true
                "Instagram Home feed" -> s.homeFeed = true
                else -> if (s.reelIdentity == null &&
                    desc.startsWith("Reel by ") &&
                    desc.contains(REEL_ACTIVE_MARKER) &&   // excludes grid thumbnails
                    node.isVisibleToUser
                ) {
                    s.reelIdentity = desc.removePrefix("Reel by ")
                        .substringBefore(". $REEL_ACTIVE_MARKER")
                        .trim()
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            scan(child, s)
            @Suppress("DEPRECATION") child.recycle()
        }
    }

    private fun sourceInHomeFeed(event: AccessibilityEvent): Boolean {
        var node: AccessibilityNodeInfo? = event.source ?: return false
        var depth = 0
        while (node != null && depth < 40) {
            if (node.contentDescription?.toString() == "Instagram Home feed") return true
            node = node.parent
            depth++
        }
        return false
    }

    /** Height of the scrolled view as a share of the screen. Reel pager ~1.0, comment list much less. */
    private fun sourceHeightRatio(event: AccessibilityEvent): Float {
        val src = event.source ?: return 1f
        val r = Rect()
        src.getBoundsInScreen(r)
        val screenH = resources.displayMetrics.heightPixels.toFloat()
        return if (screenH > 0f) r.height() / screenH else 1f
    }

    private fun isReelPagerScroll(event: AccessibilityEvent): Boolean =
        sourceHeightRatio(event) >= REEL_PAGER_MIN_HEIGHT_RATIO

    private fun isVertical(event: AccessibilityEvent): Boolean =
        if (Build.VERSION.SDK_INT >= 28) abs(event.scrollDeltaY) > abs(event.scrollDeltaX)
        else true

    private fun verticalDelta(event: AccessibilityEvent): Int =
        if (Build.VERSION.SDK_INT >= 28) abs(event.scrollDeltaY)
        else HOME_SCROLL_THRESHOLD_PX

    private fun block(reason: String, s: Signals) {
        Log.d(TAG, "BLOCK: $reason | $s | lastBrowse=$lastBrowse")
        performGlobalAction(GLOBAL_ACTION_BACK)
        lastBackAt = SystemClock.uptimeMillis()
        inReel = false
        reelAllowed = false
        reelIdentity = null
        pendingReelId = null
        homeScrollAcc = 0
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }
}