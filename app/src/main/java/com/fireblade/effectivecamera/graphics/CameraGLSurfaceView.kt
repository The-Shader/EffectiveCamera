package com.fireblade.effectivecamera.graphics

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Size
import android.view.SurfaceHolder
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.fireblade.effectivecamera.CameraActivity
import com.fireblade.effectivecamera.graphics.common.RenderEffect
import com.fireblade.effectivecamera.graphics.effects.EffectConfig
import com.fireblade.effectivecamera.graphics.services.EffectViewModel

class CameraGLSurfaceView(context: Context, attrs: AttributeSet) : GLSurfaceView(context, attrs) {

  private var renderer: CameraGLRenderer

  private var listenerHandler: Handler = Handler(Looper.getMainLooper())
  var surfaceEventListeners: List<IRendererEvents> = listOf()

  private val effectViewModel: EffectViewModel by lazy {

    (context as CameraActivity).let { fragmentActivity ->
      ViewModelProviders.of(fragmentActivity)[EffectViewModel::class.java]
    }
  }

  lateinit var effectObserver: Observer<EffectConfig>

  init {
    setEGLContextClientVersion(3)
    renderer = CameraGLRenderer(this)
    setRenderer(renderer)
    renderMode = RENDERMODE_WHEN_DIRTY
  }

  override fun surfaceDestroyed(holder: SurfaceHolder?) {
    effectViewModel.getEffectConfig().removeObserver(effectObserver)
    super.surfaceDestroyed(holder)
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

  fun initializeEffect(effectConfig: EffectConfig) {

    renderer.initializeEffect(effectConfig)
    effectObserver = Observer {
      renderer.setEffectConfig(it)
    }
    effectViewModel.getEffectConfig().observeForever(effectObserver)
  }

  fun setRenderEffect(renderEffect: RenderEffect) {
    renderer.setActiveEffect(renderEffect)
  }

  fun setEffectConfig(effectConfig: EffectConfig) {
    renderer.setEffectConfig(effectConfig)
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