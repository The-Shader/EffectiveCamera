package com.fireblade.effectivecamera.graphics.services

import com.fireblade.effectivecamera.graphics.common.RenderShaderSource

interface IShaderSourceFetcher {
  fun loadShaderProgram(vertexShaderFileName: String, pixelShaderFileName: String): RenderShaderSource

  fun loadRenderProgram(vertexProgramName: String, fragmentProgramName: String): Int
  fun loadComputeProgram(resourceName: String): Int
}