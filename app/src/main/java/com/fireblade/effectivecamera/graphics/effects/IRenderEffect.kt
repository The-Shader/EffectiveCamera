package com.fireblade.effectivecamera.graphics.effects

interface IRenderEffect {
  fun render(captureRequested: Boolean = false)
  fun setFrameBuffer(frameBuffer: Int)
  fun setTexture(texture: Int)
  fun setTextureMatrix(textureMatrix: FloatArray)
}