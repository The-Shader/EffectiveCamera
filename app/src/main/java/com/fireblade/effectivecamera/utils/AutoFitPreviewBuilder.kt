package com.fireblade.effectivecamera.utils

import android.content.Context
import android.graphics.Matrix
import android.hardware.display.DisplayManager
import android.util.Size
import android.view.*
import androidx.camera.core.Preview
import androidx.camera.core.PreviewConfig
import java.lang.ref.WeakReference
import java.util.*

class AutoFitPreviewBuilder private constructor(
  config: PreviewConfig,
  viewFinderRef: WeakReference<TextureView>
) {

  val preview: Preview

  private var bufferRotation = 0

  private var viewFinderRotation: Int? = null

  private var bufferDimens: Size = Size(0, 0)

  private var viewFinderDimens: Size = Size(0, 0)

  private var viewFinderDisplay: Int = -1

  private lateinit var displayManager: DisplayManager

  private val displayListener = object : DisplayManager.DisplayListener {
    override fun onDisplayAdded(displayId: Int) = Unit
    override fun onDisplayRemoved(displayId: Int) = Unit
    override fun onDisplayChanged(displayId: Int) {
      val viewFinder = viewFinderRef.get() ?: return
      if (displayId == viewFinderDisplay) {
        val display = displayManager.getDisplay(displayId)
        val rotation = getDisplaySurfaceRotation(display)
        updateTransform(viewFinder, rotation, bufferDimens, viewFinderDimens)
      }
    }
  }

  init {
    // Make sure that the view finder reference is valid
    val viewFinder = viewFinderRef.get() ?:
    throw IllegalArgumentException("Invalid reference to view finder used")

    // Initialize the display and rotation from texture view information
    viewFinderDisplay = viewFinder.display.displayId
    viewFinderRotation = getDisplaySurfaceRotation(viewFinder.display) ?: 0

    // Initialize public use-case with the given config
    preview = Preview(config)

    // Every time the view finder is updated, recompute layout
    preview.onPreviewOutputUpdateListener = Preview.OnPreviewOutputUpdateListener {
      val viewFinder =
        viewFinderRef.get() ?: return@OnPreviewOutputUpdateListener

      // To update the SurfaceTexture, we have to remove it and re-add it
      val parent = viewFinder.parent as ViewGroup
      parent.removeView(viewFinder)
      parent.addView(viewFinder, 0)

      // Update internal texture
      viewFinder.surfaceTexture = it.surfaceTexture

      // Apply relevant transformations
      bufferRotation = it.rotationDegrees
      val rotation = getDisplaySurfaceRotation(viewFinder.display)
      updateTransform(viewFinder, rotation, it.textureSize, viewFinderDimens)
    }

    // Every time the provided texture view changes, recompute layout
    viewFinder.addOnLayoutChangeListener { view, left, top, right, bottom, _, _, _, _ ->
      val viewFinder = view as TextureView
      val newViewFinderDimens = Size(right - left, bottom - top)
      val rotation = getDisplaySurfaceRotation(viewFinder.display)
      updateTransform(viewFinder, rotation, bufferDimens, newViewFinderDimens)
    }

    // Every time the orientation of device changes, recompute layout
    // NOTE: This is unnecessary if we listen to display orientation changes in the camera
    //  fragment and call [Preview.setTargetRotation()] (like we do in this sample), which will
    //  trigger [Preview.OnPreviewOutputUpdateListener] with a new
    //  [PreviewOutput.rotationDegrees]. CameraX Preview use case will not rotate the frames for
    //  us, it will just tell us about the buffer rotation with respect to sensor orientation.
    //  In this sample, we ignore the buffer rotation and instead look at the view finder's
    //  rotation every time [updateTransform] is called, which gets triggered by
    //  [CameraFragment] display listener -- but the approach taken in this sample is not the
    //  only valid one.
    displayManager = viewFinder.context
      .getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    displayManager.registerDisplayListener(displayListener, null)

    // Remove the display listeners when the view is detached to avoid holding a reference to
    //  it outside of the Fragment that owns the view.
    // NOTE: Even though using a weak reference should take care of this, we still try to avoid
    //  unnecessary calls to the listener this way.
    viewFinder.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
      override fun onViewAttachedToWindow(view: View?) =
        displayManager.registerDisplayListener(displayListener, null)
      override fun onViewDetachedFromWindow(view: View?) =
        displayManager.unregisterDisplayListener(displayListener)
    })
  }

  /** Helper function that fits a camera preview into the given [TextureView] */
  private fun updateTransform(textureView: TextureView?, rotation: Int?, newBufferDimens: Size,
                              newViewFinderDimens: Size) {
    // This should not happen anyway, but now the linter knows
    val textureView = textureView ?: return

    if (rotation == viewFinderRotation &&
      Objects.equals(newBufferDimens, bufferDimens) &&
      Objects.equals(newViewFinderDimens, viewFinderDimens)) {
      // Nothing has changed, no need to transform output again
      return
    }

    if (rotation == null) {
      // Invalid rotation - wait for valid inputs before setting matrix
      return
    } else {
      // Update internal field with new inputs
      viewFinderRotation = rotation
    }

    if (newBufferDimens.width == 0 || newBufferDimens.height == 0) {
      // Invalid buffer dimens - wait for valid inputs before setting matrix
      return
    } else {
      // Update internal field with new inputs
      bufferDimens = newBufferDimens
    }

    if (newViewFinderDimens.width == 0 || newViewFinderDimens.height == 0) {
      // Invalid view finder dimens - wait for valid inputs before setting matrix
      return
    } else {
      // Update internal field with new inputs
      viewFinderDimens = newViewFinderDimens
    }

    val matrix = Matrix()

    // Compute the center of the view finder
    val centerX = viewFinderDimens.width / 2.0f
    val centerY = viewFinderDimens.height / 2.0f

    // Correct preview output to account for display rotation
    matrix.postRotate(-viewFinderRotation!!.toFloat(), centerX, centerY)

    // Buffers are rotated relative to the device's 'natural' orientation: swap width and height
    val bufferRatio = bufferDimens.height / bufferDimens.width.toFloat()

    val scaledWidth: Int
    val scaledHeight: Int
    // Match longest sides together -- i.e. apply center-crop transformation
    if (viewFinderDimens.width > viewFinderDimens.height) {
      scaledHeight = viewFinderDimens.width
      scaledWidth = Math.round(viewFinderDimens.width * bufferRatio)
    } else {
      scaledHeight = viewFinderDimens.height
      scaledWidth = Math.round(viewFinderDimens.height * bufferRatio)
    }

    // Compute the relative scale value
    val xScale = scaledWidth / viewFinderDimens.width.toFloat()
    val yScale = scaledHeight / viewFinderDimens.height.toFloat()

    // Scale input buffers to fill the view finder
    matrix.preScale(xScale, yScale, centerX, centerY)

    // Finally, apply transformations to our TextureView
    textureView.setTransform(matrix)
  }

  companion object {
    private val TAG = AutoFitPreviewBuilder::class.java.simpleName

    /** Helper function that gets the rotation of a [Display] in degrees */
    fun getDisplaySurfaceRotation(display: Display?) = (display?.rotation ?: 0) * 90

    /**
     * Main entrypoint for users of this class: instantiates the adapter and returns an instance
     * of [Preview] which automatically adjusts in size and rotation to compensate for
     * config changes.
     */
    fun build(config: PreviewConfig, viewFinder: TextureView) =
      AutoFitPreviewBuilder(config, WeakReference(viewFinder)).preview
  }

}