package com.fireblade.effectivecamera.graphics.common

import android.opengl.GLES31.*
import com.fireblade.effectivecamera.graphics.effects.EffectConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class RenderEffect(private val shaderProgram: RenderShaderSource, private val effectConfig: EffectConfig) : IRenderEffect {

  var frameBufferObject: Int = 0

  val shader by lazy { Shader(shaderProgram.program) }

  var cameraTexture: Texture2D = Texture2D()

  var textureTransformMatrix: FloatArray = floatArrayOf(0.0f)

  private val vertexBuffer: FloatBuffer by lazy {
    ByteBuffer.allocateDirect(8 * 4)
      .order(ByteOrder.nativeOrder())
      .asFloatBuffer()
      .apply {
        put(floatArrayOf(1.0f, -1.0f, -1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f))
        position(0)
      }
  }

  private val previewTexCoordinates: FloatBuffer by lazy {
    ByteBuffer.allocateDirect(8 * 4)
      .order(ByteOrder.nativeOrder())
      .asFloatBuffer()
      .apply {
        put(floatArrayOf(0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f))
        position(0)
      }
  }

  private val captureTexCoordinates: FloatBuffer by lazy {
    ByteBuffer.allocateDirect(8 * 4)
      .order(ByteOrder.nativeOrder())
      .asFloatBuffer()
      .apply {
        put(floatArrayOf(0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f))
        position(0)
      }
  }

  fun setTexture(texture: Int) {

    cameraTexture = Texture2D("cameraTexture", GL_TEXTURE0, texture)
  }

  fun setTextureMatrix(textureMatrix: FloatArray) {
    textureTransformMatrix = textureMatrix
  }

  fun setAttributes(captureRequested: Boolean) {

    shader.setVertexFloatAttribute("position", vertexBuffer, 2, 8)

    if (captureRequested) {
      shader.setVertexFloatAttribute("texCoords", captureTexCoordinates, 2, 8)

      shader.setFrameBuffer(frameBufferObject)
    } else {
      shader.setVertexFloatAttribute("texCoords", previewTexCoordinates, 2, 8)
    }


    shader.setUniformMatrix4("textureTransformMatrix", textureTransformMatrix)

    shader.setExternalTexture(cameraTexture.textureID, cameraTexture.index, cameraTexture.name)

    effectConfig.properties.map { attribute ->
      shader.setFloatAttribute(attribute.key, attribute.value.currentValue)
    }
  }

  fun unsetAttributes() {
    shader.unsetExternalTexture()
  }

  override fun render(captureRequested: Boolean) {

    shader.activate()

    setAttributes(captureRequested)

    if (captureRequested) {
      shader.drawToFrameBuffer()

    } else {
      shader.drawToScreen()
    }

    unsetAttributes()

    shader.deActivate()
  }
}