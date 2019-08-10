package com.fireblade.effectivecamera.graphics

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Size
import com.fireblade.effectivecamera.graphics.common.RenderEffect


class CameraGLSurfaceView(context: Context, attrs: AttributeSet) : GLSurfaceView(context, attrs) {

  private var renderer: CameraGLRenderer

  private var listenerHandler: Handler = Handler(Looper.getMainLooper())
  var surfaceEventListeners: List<IRendererEvents> = listOf()

  init {
    setEGLContextClientVersion(3)
    renderer = CameraGLRenderer(this)
    setRenderer(renderer)
    renderMode = RENDERMODE_WHEN_DIRTY
  }

  fun getPreviewSurfaceTex(): SurfaceTexture {
    return renderer.getPreviewTexture()
  }

  fun getCaptureSurfaceTex(): SurfaceTexture {
    return renderer.getCaptureTexture()
  }

  fun setCaptureResolution(resolution: Size) {
    renderer.setCaptureResolution(resolution)
  }

  fun initializeEffect() {

    renderer.initializeEffect()
  }

  fun setRenderEffect(renderEffect: RenderEffect) {
    renderer.setActiveEffect(renderEffect)
  }

  fun renderingContextInitialized() {
    listenerHandler.post {
      surfaceEventListeners.map {
        it.onRenderingContextCreated()
      }
    }
  }

  fun captureFinished() {
    listenerHandler.post {
      surfaceEventListeners.map {
        it.onCaptureFinished()
      }
    }
  }

  override fun onResume() {
    super.onResume()
    renderer.onResume()
  }

  override fun onPause() {
    renderer.onPause()
    super.onPause()
  }
}