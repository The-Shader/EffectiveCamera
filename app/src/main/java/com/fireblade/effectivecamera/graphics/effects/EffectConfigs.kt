package com.fireblade.effectivecamera.graphics.effects

data class EffectConfig(val name: String, val properties: Map<String, Float> = mutableMapOf(
  "brightness" to 1.0f,
  "saturation" to 1.0f,
  "contrast" to 1.0f,
  "sepia" to  0.0f,
  "grayscale" to 0.0f,
  "hueRotation" to 0.0f,
  "opacity" to 0.0f,
  "invert" to 0.0f,
  "pixelFactor" to 0.0f
)) {

  val brightness: Float by properties //= 1.0f // Range: [0.0, 5.0]
  val saturation: Float by properties//= 1.0f, // Range: [0.0, 5.0]
  val contrast: Float by properties//= 1.0f, // Range: [0.0, 5.0]
  val sepia: Float by properties//= 0.0f, // Range: [0.0, 1.0]
  val grayscale: Float by properties//= 0.0f, // Range: [0.0, 1.0]
  val hueRotation: Float by properties//= 0, // Range: [-360, 360]
  val opacity: Float by properties// = 1.0f, // Range: [0.0, 1.0]
  val invert: Float by properties//= 0.0f,
  val pixelFactor: Float by properties//= 0.0f // Range: [0.0, 30.0]* => scaled with texture size and aspect ratio
}