package com.fireblade.effectivecamera.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.fireblade.effectivecamera.CameraActivity
import com.fireblade.effectivecamera.R
import kotlinx.android.synthetic.main.camera_ui_container.*

class CameraControlsFragment : Fragment() {

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? = inflater.inflate(R.layout.camera_ui_container, container, false)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    camera_settings_button.setOnClickListener {
      activity?.let { fragmentActivity ->
        (fragmentActivity as CameraActivity).cameraNavigator.navigateToCameraSettings()
      }
    }

    camera_switch_button.setOnClickListener {
      activity?.let { fragmentActivity ->
        (fragmentActivity as CameraActivity).swapCamera()
      }
    }
  }

  override fun onResume() {
    super.onResume()
    activity?.let { fragmentActivity ->
      (fragmentActivity as CameraActivity).setFullScreen()
    }
  }
}