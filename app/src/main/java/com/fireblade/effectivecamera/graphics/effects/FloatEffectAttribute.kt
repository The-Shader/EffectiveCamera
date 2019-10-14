package com.fireblade.effectivecamera.graphics.effects

data class FloatEffectAttribute(
  val name: String,
  val displayName: String,
  var currentValue: Float,
  val minValue: Float,
  val maxValue: Float
)