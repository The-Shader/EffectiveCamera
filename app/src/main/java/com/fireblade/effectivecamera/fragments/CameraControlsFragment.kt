package com.fireblade.effectivecamera.fragments

import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.fireblade.effectivecamera.CameraActivity
import com.fireblade.effectivecamera.R
import com.fireblade.effectivecamera.graphics.effects.EffectConfig
import com.fireblade.effectivecamera.graphics.services.EffectViewModel
import com.fireblade.effectivecamera.utils.FileManager
import kotlinx.android.synthetic.main.camera_ui_container.*
import java.io.File

class CameraControlsFragment : Fragment() {

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? = inflater.inflate(R.layout.camera_ui_container, container, false)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    export_settings_button.background = resources.getDrawable(R.drawable.ic_export, null)

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

    export_settings_button?.setOnClickListener {
      (activity as CameraActivity).let { fragmentActivity ->
        export_settings_button.background = resources.getDrawable(R.drawable.ic_export_progress, null)
        export_settings_button.isEnabled = false
        val effectViewModel: EffectViewModel = ViewModelProviders.of(fragmentActivity)[EffectViewModel::class.java]
        val effectConfig = effectViewModel.getEffectConfig()
        FileManager.saveEffectConfig(context?.getExternalFilesDir("Effect Configs")?: File("") ,effectConfig.value ?: EffectConfig("Normal"))
        val handler = Handler()
        handler.postDelayed( {
          export_settings_button.background = resources.getDrawable(R.drawable.ic_export, null)
          export_settings_button.isEnabled = true
        }, 1000)


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