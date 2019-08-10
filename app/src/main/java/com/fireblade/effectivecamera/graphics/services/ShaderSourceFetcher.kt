package com.fireblade.effectivecamera.graphics.services

import android.content.Context
import android.content.res.AssetManager
import android.opengl.GLES31
import com.fireblade.effectivecamera.graphics.common.RenderShaderSource
import java.io.BufferedReader
import java.io.InputStreamReader

class ShaderSourceFetcher(context: Context) : IShaderSourceFetcher {

  private val assetManager: AssetManager = context.assets

  val VERTEX_SHADER_FILE_EXT = ".vert"
  val FRAGMENT_SHADER_FILE_EXT = ".frag"
  val COMPUTE_SHADER_FILE_EXT = ".glsl"

  val CORE_SHADER = "core.glsl"
  val BLENDING_MODES = "blendingModes.glsl"

  override fun loadShaderProgram(vertexShaderFileName: String, pixelShaderFileName: String): RenderShaderSource {
    return RenderShaderSource(loadShaderFromAsset(vertexShaderFileName + VERTEX_SHADER_FILE_EXT),
      loadShaderFromAsset(CORE_SHADER) /*+ loadShaderFromAsset(BLENDING_MODES)*/ + loadShaderFromAsset(pixelShaderFileName + FRAGMENT_SHADER_FILE_EXT),
      pixelShaderFileName)
  }

  private fun loadShaderFromAsset(shaderName: String): String {
    val shaderFile = BufferedReader(InputStreamReader(assetManager.open(shaderName)))
    return buildString {
      shaderFile.readLines()
        .forEach { append("$it\n") }
    }
  }

  override fun loadComputeProgram(resourceName: String): Int {

    val computeShader = compileShaderSource(GLES31.GL_COMPUTE_SHADER, resourceName + COMPUTE_SHADER_FILE_EXT)

    val program = GLES31.glCreateProgram()
    GLES31.glAttachShader(program, computeShader)
    GLES31.glLinkProgram(program)

    return program
  }

  override fun loadRenderProgram(vertexProgramName: String, fragmentProgramName: String): Int {

    val vertexShader = compileShaderSource(GLES31.GL_VERTEX_SHADER, vertexProgramName + VERTEX_SHADER_FILE_EXT)

    val fragmentShader = compileShaderSource(GLES31.GL_FRAGMENT_SHADER, fragmentProgramName + FRAGMENT_SHADER_FILE_EXT)

    val program = GLES31.glCreateProgram()
    GLES31.glAttachShader(program, vertexShader)
    GLES31.glAttachShader(program, fragmentShader)
    GLES31.glLinkProgram(program)

    return program
  }

  private fun createShaderSource(shaderName: String): String =
    buildString {
      BufferedReader(InputStreamReader(assetManager.open(shaderName))).readLines()
        .forEach { append("$it\n") }
    }

  private fun compileShaderSource(shaderType: Int, shaderName: String): Int {
    var shader = GLES31.glCreateShader(shaderType)

    val compiled = IntArray(1)

    var coreSrc = ""

    if (shaderType == GLES31.GL_FRAGMENT_SHADER) {
      coreSrc = loadShaderFromAsset(CORE_SHADER) + loadShaderFromAsset(BLENDING_MODES)
    }

    val shaderSrc = coreSrc + createShaderSource(shaderName)

    GLES31.glShaderSource(shader, shaderSrc)
    GLES31.glCompileShader(shader)
    GLES31.glGetShaderiv(shader, GLES31.GL_COMPILE_STATUS, compiled, 0)

    if (compiled[0] == 0) {
      //Timber.e("Could not compile $shaderName")
      //Timber.e("Could not compile: ${GLES31.glGetShaderInfoLog(shader)}")
      GLES31.glDeleteShader(shader)
      shader = 0
    }
    return shader
  }
}