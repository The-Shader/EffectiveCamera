package com.fireblade.effectivecamera.graphics.common

import android.opengl.GLES11Ext
import android.opengl.GLES20.*
import java.nio.FloatBuffer
import java.nio.IntBuffer

class Shader(override val shaderProgram: Int) : IShader {

  override fun activate() {
    glUseProgram(shaderProgram)
  }

  override fun deActivate() {
    glUseProgram(0)
  }

  override fun setIntAttribute(attrName: String, value: Int) {

    val index = glGetUniformLocation(shaderProgram, attrName)

    glUniform1i(index, value)
  }

  override fun setIntVec2Attribute(attrName: String, val1: Int, val2: Int) {

    val index = glGetUniformLocation(shaderProgram, attrName)

    glUniform2i(index, val1, val2)
  }

  override fun setFloatAttribute(attrName: String, value: Float) {

    val index = glGetUniformLocation(shaderProgram, attrName)

    glUniform1f(index, value)
  }

  override fun setFloatVec2Attribute(attrName: String, val1: Float, val2: Float) {

    val index = glGetUniformLocation(shaderProgram, attrName)

    glUniform2f(index, val1, val2)
  }

  override fun setFloatVec3Attribute(attrName: String, val1: Float, val2: Float, val3: Float) {

    val index = glGetUniformLocation(shaderProgram, attrName)

    glUniform3f(index, val1, val2, val3)
  }

  fun setVertexFloatAttribute(attriName: String, buffer: FloatBuffer, dim: Int, stride: Int) {

    val index = glGetAttribLocation(shaderProgram, attriName)

    glVertexAttribPointer(index, dim, GL_FLOAT, false, stride, buffer)

    glEnableVertexAttribArray(index)
  }

  fun setTexture(texture: Int, index: Int, attrName: String, textureType: Int) {
    glActiveTexture(index)

    glBindTexture(textureType, texture)

    glUniform1i(glGetUniformLocation(shaderProgram, attrName), 0)
  }

  fun unsetTexture(textureType: Int) {
    glBindTexture(textureType, 0)
  }

  fun setExternalTexture(texture: Int, index: Int, attrName: String) {
    setTexture(texture, index, attrName, GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
  }

  fun unsetExternalTexture() {
    unsetTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
  }

  fun setFrameBuffer(frameBuffer: Int) {
    glBindFramebuffer(GL_FRAMEBUFFER, frameBuffer)
  }

  fun unsetFrameBuffer() {
    glBindFramebuffer(GL_FRAMEBUFFER, 0)
  }


  fun setUniformMatrix4(attrName: String, value: FloatArray) {

    glUniformMatrix4fv(glGetUniformLocation(shaderProgram, attrName), 1, false, value, 0)
  }

  fun drawToScreen() {
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
  }

  fun drawToFrameBuffer() {

    val colorAttachment = IntBuffer.allocate(1)

    colorAttachment.put(GL_COLOR_ATTACHMENT0)

    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)

    glFlush()

    glFinish()
  }
}