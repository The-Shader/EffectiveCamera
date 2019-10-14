package com.fireblade.effectivecamera.graphics

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES31.*
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Size
import android.view.Surface
import androidx.lifecycle.ViewModelProvider
import com.fireblade.effectivecamera.graphics.common.IRenderEffect
import com.fireblade.effectivecamera.graphics.common.RenderEffect
import com.fireblade.effectivecamera.graphics.common.RenderShaderSource
import com.fireblade.effectivecamera.graphics.common.ShaderSource
import com.fireblade.effectivecamera.graphics.effects.EffectConfig
import com.fireblade.effectivecamera.graphics.services.ShaderSourceFetcher
import kotlinx.android.synthetic.main.fragment_camera.*
import java.nio.ByteBuffer
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CameraGLRenderer(val view: CameraGLSurfaceView) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

  private val cameraTextures: IntArray = IntArray(2)

  private var renderEffect: IRenderEffect? = null

  private var imageTextures: IntArray = IntArray(1)
  private var frameBuffers: IntArray = IntArray(1)
  private var renderBuffers: IntArray = IntArray(1)

  private var previewSurfaceTexture: SurfaceTexture = SurfaceTexture(0)

  private var captureSurfaceTexture: SurfaceTexture = SurfaceTexture(0)

  private var previewTextureTransform = FloatArray(16)

  private var captureTextureTransform = FloatArray(16)

  private var rotationInDegrees = Surface.ROTATION_0 * 90f

  private var textureTransformCoordinates: FloatArray = floatArrayOf(0.0f, -1.0f)

  private var effectLock = Semaphore(1)

  private var glInit = false

  private var frameResolution = Size(0, 0)

  private var cameraResolution: Size = Size(0, 0)

  private var screenResolution = Size(0, 0)

  private val drawCommands = mutableListOf<()->Unit>()

  private lateinit var shaderSource: RenderShaderSource

  internal fun onResume() {
    glInit = true
  }

  internal fun onPause() {
    glInit = false
  }

  override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {

    initExternalTextures()
    previewSurfaceTexture = SurfaceTexture(cameraTextures[0])
    previewSurfaceTexture.setOnFrameAvailableListener(this)

    captureSurfaceTexture = SurfaceTexture(cameraTextures[1])
    captureSurfaceTexture.setOnFrameAvailableListener(this)

    view.context?.let {
      val shaderManager = ShaderSourceFetcher(it)
      shaderSource = shaderManager.loadShaderProgram("basicVertex", "effect")
      //initializeEffect(EffectConfig("Normal"))
    }

    view.renderingContextInitialized()

    glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
  }

  override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {

    applyRotation()

    val ss = Point()

    view.display.getRealSize(ss)
    screenResolution = Size(ss.x, ss.y)
    setViewPort(screenResolution)
    glInit = true
  }

  fun setViewPort(renderTargetSize: Size) {

    glViewport(0, 0, renderTargetSize.width, renderTargetSize.height)
  }

  override fun onDrawFrame(unused: GL10) {
    glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
    if (!glInit) return

    if (effectLock.tryAcquire(100, TimeUnit.MILLISECONDS)) {

      synchronized(this) {
        if (drawCommands.isNotEmpty()) {
          val drawCall = drawCommands.first()
          drawCommands.remove(drawCall)
          drawCall.invoke()
        }
      }
      effectLock.release()
    }
  }

  private fun applyRenderEffect() {

    renderEffect?.let { it as RenderEffect}?.also { effect ->
      effect.setTexture(cameraTextures[0])
      effect.setTextureMatrix(previewTextureTransform)
      effect.render()
    }

//    if (renderEffect == null) {
//      return
//    }
//
//    if (renderEffect is RenderEffect) {
//
//      val effect = renderEffect as RenderEffect
//
//      effect.setTexture(cameraTextures[0])
//      effect.setTextureMatrix(previewTextureTransform)
//      effect.render()
//    }
  }

  private fun applyCaptureRenderEffect() {
    renderEffect?.let { it as RenderEffect}?.also { effect ->
      initImageTextures()
      initRenderBuffers()

      (renderEffect as RenderEffect?)?.frameBufferObject = frameBuffers[0]
      effect.setTexture(cameraTextures[1])
      effect.setTextureMatrix(captureTextureTransform)
      effect.render(true)
    }
//    if (renderEffect == null) {
//      return
//    }
//
//    if (renderEffect is RenderEffect) {
//
//      val effect = renderEffect as RenderEffect
//
//      initImageTextures()
//      initRenderBuffers()
//
//      (renderEffect as RenderEffect?)?.frameBufferObject = frameBuffers[0]
//      effect.setTexture(cameraTextures[1])
//      effect.setTextureMatrix(captureTextureTransform)
//      effect.render(true)
//    }
  }

  fun initializeEffect(effectConfig: EffectConfig) {

    view.context?.let {
      val shaderManager = ShaderSourceFetcher(it)

      shaderSource = shaderManager.loadShaderProgram("basicVertex", "effect")

      val renderEffect = RenderEffect(shaderSource, effectConfig)

      setActiveEffect(renderEffect)
    }
  }

  fun setActiveEffect(effect: IRenderEffect) {

    if (effectLock.tryAcquire(100, TimeUnit.MILLISECONDS)) {

      renderEffect = effect

      effectLock.release()
    }
  }

  fun setEffectConfig(effectConfig: EffectConfig) {

    val renderEffect = RenderEffect(shaderSource, effectConfig)

    setActiveEffect(renderEffect)
  }

  fun getPreviewTexture(): SurfaceTexture {
    return previewSurfaceTexture
  }

  fun getCaptureTexture(): SurfaceTexture {
    return captureSurfaceTexture
  }

  private fun applyRotation() {

    when (view.display.rotation) {
      Surface.ROTATION_0 -> {
        textureTransformCoordinates = floatArrayOf(-1f, -1f)
        rotationInDegrees = 180f
        frameResolution = Size(cameraResolution.height, cameraResolution.width)
      }
      Surface.ROTATION_90 -> {
        textureTransformCoordinates = floatArrayOf(0f, -1f)
        rotationInDegrees = 90f
        frameResolution = Size(cameraResolution.width, cameraResolution.height)
      }
      Surface.ROTATION_270 -> {
        textureTransformCoordinates = floatArrayOf(-1f, 0f)
        rotationInDegrees = 270f
        frameResolution = Size(cameraResolution.width, cameraResolution.height)
      }
      else -> {
        textureTransformCoordinates = floatArrayOf(0f, 0f)
        rotationInDegrees = 0f
        frameResolution = Size(cameraResolution.height, cameraResolution.width)
      }
    }
  }

  fun setCaptureResolution(resolution: Size) {
    cameraResolution = resolution

    applyRotation()

    glInit = true
  }

  fun initExternalTextures() {

    glDeleteTextures(cameraTextures.size, cameraTextures, 0)

    glGenTextures(cameraTextures.size, cameraTextures, 0)

    cameraTextures.map {
      glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, it)
      glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
      glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
      glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
      glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
    }
  }

  fun initImageTextures() {

    glDeleteTextures(imageTextures.size, imageTextures, 0)

    glGenTextures(imageTextures.size, imageTextures, 0)

    imageTextures.map {
      glBindTexture(GL_TEXTURE_2D, it)
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
      glTexStorage2D(GL_TEXTURE_2D, 1, GL_RGBA8, frameResolution.width, frameResolution.height)
      glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, frameResolution.width, frameResolution.height, GL_RGBA, GL_UNSIGNED_BYTE, null)
    }
  }

  fun initRenderBuffers() {

    glDeleteFramebuffers(1, frameBuffers, 0)

    glDeleteRenderbuffers(1, renderBuffers, 0)

    glGenFramebuffers(1, frameBuffers, 0)

    glGenRenderbuffers(1, renderBuffers, 0)

    glActiveTexture(GL_TEXTURE1)

    glBindTexture(GL_TEXTURE_2D, imageTextures[0])

    glBindRenderbuffer(GL_RENDERBUFFER, renderBuffers[0])

    glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT16, frameResolution.width, frameResolution.height)

    glBindFramebuffer(GL_FRAMEBUFFER, frameBuffers[0])

    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, imageTextures[0], 0)

    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, renderBuffers[0])

    glBindFramebuffer(GL_FRAMEBUFFER, 0)

    glBindRenderbuffer(GL_RENDERBUFFER, 0)

    glBindTexture(GL_TEXTURE_2D, 0)
  }

  fun saveTexture(width: Int, height: Int): Bitmap {
    val buffer = ByteBuffer.allocate(width * height * 4)
    glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.copyPixelsFromBuffer(buffer)
    let { (renderEffect as RenderEffect).shader }.unsetFrameBuffer()

    return bitmap
  }

  private fun drawPreview() {
    previewSurfaceTexture.updateTexImage()

    previewSurfaceTexture.getTransformMatrix(previewTextureTransform)

    Matrix.rotateM(previewTextureTransform, 0, rotationInDegrees, 0f, 0f, 1f)

    Matrix.translateM(previewTextureTransform, 0, textureTransformCoordinates[0], textureTransformCoordinates[1], 0f)

    applyRenderEffect()
  }

  private fun captureFrame() {

    setViewPort(frameResolution)

    glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

    captureSurfaceTexture.updateTexImage()

    captureSurfaceTexture.getTransformMatrix(captureTextureTransform)

    Matrix.rotateM(captureTextureTransform, 0, rotationInDegrees, 0f, 0f, 1f)

    Matrix.translateM(captureTextureTransform, 0, textureTransformCoordinates[0], textureTransformCoordinates[1], 0f)

    applyCaptureRenderEffect()

    val image = saveTexture(frameResolution.width, frameResolution.height)
    view.captureFinished()
    //FileManager.saveImage(image)

    setViewPort(screenResolution)
  }

  override fun onFrameAvailable(st: SurfaceTexture) {

    synchronized(this) {
      if (st == captureSurfaceTexture) {
        drawCommands.add {
          captureFrame()
        }
      }
      else {
        drawCommands.add {
          drawPreview()
        }
      }
      view.requestRender()
    }
  }
}