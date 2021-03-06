package com.fireblade.effectivecamera.utils

import android.os.Build
import android.view.DisplayCutout
import android.view.View
import android.widget.ImageButton
import androidx.annotation.RequiresApi

const val FLAGS_FULLSCREEN =
  View.SYSTEM_UI_FLAG_LOW_PROFILE or
      View.SYSTEM_UI_FLAG_FULLSCREEN or
      View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
      View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
      View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
      View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

const val ANIMATION_FAST_MILLIS = 50L
const val ANIMATION_SLOW_MILLIS = 100L

fun ImageButton.simulateClick(delay: Long = ANIMATION_FAST_MILLIS) {
  performClick()
  isPressed = true
  invalidate()
  postDelayed({
    invalidate()
    isPressed = false
  }, delay)
}


@RequiresApi(Build.VERSION_CODES.P)
fun View.padWithDisplayCutout() {

  /** Helper method that applies padding from cutout's safe insets */
  fun doPadding(cutout: DisplayCutout) = setPadding(
    cutout.safeInsetLeft,
    cutout.safeInsetTop,
    cutout.safeInsetRight,
    cutout.safeInsetBottom)

  // Apply padding using the display cutout designated "safe area"
  rootWindowInsets?.displayCutout?.let { doPadding(it) }

  // Set a listener for window insets since view.rootWindowInsets may not be ready yet
  setOnApplyWindowInsetsListener { view, insets ->
    insets.displayCutout?.let { doPadding(it) }
    insets
  }
}