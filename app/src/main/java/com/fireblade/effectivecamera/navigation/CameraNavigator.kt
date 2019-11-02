package com.fireblade.effectivecamera.navigation

import androidx.fragment.app.FragmentManager
import com.fireblade.effectivecamera.R
import com.fireblade.effectivecamera.fragments.CameraControlsFragment
import com.fireblade.effectivecamera.fragments.SettingsFragment

class CameraNavigator(
  private val fragmentManager: FragmentManager
) : ICameraNavigator {

  private constructor(builder: Builder) : this(builder.fragmentManager)

  companion object {

    fun build(block: Builder.() -> Unit) = Builder().apply(block).build()
  }

  class Builder {
    lateinit var fragmentManager: FragmentManager

    fun build() = CameraNavigator(this)
  }

  override fun navigateToCameraControls() {
    fragmentManager.beginTransaction().apply {
      replace(R.id.camera_fragment, CameraControlsFragment(), "CameraControlsFragment")
    }.commitAllowingStateLoss()
  }

  override fun navigateToCameraSettings() {
    fragmentManager.beginTransaction().apply {
      replace(R.id.camera_fragment, SettingsFragment(), "SettingsFragment")
      addToBackStack("SettingsFragment")
    }.commit()
  }
}