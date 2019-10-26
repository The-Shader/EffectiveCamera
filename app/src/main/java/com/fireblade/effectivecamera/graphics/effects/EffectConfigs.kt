package com.fireblade.effectivecamera.graphics.effects

data class EffectConfig(val name: String, val properties: Map<String, FloatEffectAttribute> = mutableMapOf(
  "brightness" to FloatEffectAttribute("brightness", "Brightness", 1.0f, 0.0f, 5.0f),
  "saturation" to FloatEffectAttribute("saturation", "Saturation", 1.0f, 0.0f, 5.0f),
  "contrast" to FloatEffectAttribute("contrast", "Contrast", 1.0f, 0.0f, 5.0f),
  "sepia" to FloatEffectAttribute("sepia", "Sepia", 0.0f, 0.0f, 1.0f),
  "grayscale" to FloatEffectAttribute("grayscale", "Grayscale", 0.0f, 0.0f, 1.0f),
  "hueRotation" to FloatEffectAttribute("hueRotation", "Hue Rotation (Degrees)", 0.0f, -360.0f, 360.0f),
  "opacity" to FloatEffectAttribute("opacity", "Blend Amount", 1.0f, 0.0f, 1.0f),
  "invert" to FloatEffectAttribute("invert", "Invert Colors", 0.0f, 0.0f, 1.0f),
  "pixelFactor" to FloatEffectAttribute("pixelFactor", "Pixelation", 0.0f, 0.0f, 30.0f)
)) {

  val brightness: FloatEffectAttribute by properties //= 1.0f // Range: [0.0, 5.0]
  val saturation: FloatEffectAttribute by properties//= 1.0f, // Range: [0.0, 5.0]
  val contrast: FloatEffectAttribute by properties//= 1.0f, // Range: [0.0, 5.0]
  val sepia: FloatEffectAttribute by properties//= 0.0f, // Range: [0.0, 1.0]
  val grayscale: FloatEffectAttribute by properties//= 0.0f, // Range: [0.0, 1.0]
  val hueRotation: FloatEffectAttribute by properties//= 0, // Range: [-360, 360]
  val opacity: FloatEffectAttribute by properties// = 1.0f, // Range: [0.0, 1.0]
  val invert: FloatEffectAttribute by properties//= 0.0f,
  val pixelFactor: FloatEffectAttribute by properties//= 0.0f // Range: [0.0, 30.0]* => scaled with texture size and aspect ratio
}