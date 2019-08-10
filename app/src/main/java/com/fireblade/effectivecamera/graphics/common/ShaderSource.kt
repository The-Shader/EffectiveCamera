package com.fireblade.effectivecamera.graphics.common

import android.opengl.GLES20
import android.util.Log

sealed class ShaderSource {
  abstract val program: Int
  abstract val name: String

  protected fun compileShaderSource(shaderType: Int, source: String): Int {
    var shader = GLES20.glCreateShader(shaderType)

    val compiled = IntArray(1)

    GLES20.glShaderSource(shader, source)
    GLES20.glCompileShader(shader)
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)

    if (compiled[0] == 0) {
      //Log.("Could not compile $source")
      //Timber.v("Could not compile: ${GLES20.glGetShaderInfoLog(shader)}")
      GLES20.glDeleteShader(shader)
      shader = 0
    }
    return shader
  }
}

data class RenderShaderSource(
  private val vertexShader: String,
  private val pixelShader: String,
  override val name: String
) : ShaderSource() {
  override val program: Int by lazy {
    loadRenderProgram(vertexShader, pixelShader)
  }


  private fun loadRenderProgram(vertexSource: String, fragmentSource: String): Int {

    val vertexShader = compileShaderSource(GLES20.GL_VERTEX_SHADER, vertexSource)

    val fragmentShader = compileShaderSource(GLES20.GL_FRAGMENT_SHADER, fragmentSource)

    val program = GLES20.glCreateProgram()
    GLES20.glAttachShader(program, vertexShader)
    GLES20.glAttachShader(program, fragmentShader)
    GLES20.glLinkProgram(program)

    return program
  }
}