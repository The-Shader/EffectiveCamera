package com.fireblade.effectivecamera.graphics.common

interface IShader {

  val shaderProgram: Int

  fun activate()

  fun deActivate()

  fun setIntAttribute(attrName: String, value: Int)

  fun setIntVec2Attribute(attrName: String, val1: Int, val2: Int)

  fun setFloatAttribute(attrName: String, value: Float)

  fun setFloatVec2Attribute(attrName: String, val1: Float, val2: Float)

  fun setFloatVec3Attribute(attrName: String, val1: Float, val2: Float, val3: Float)
}