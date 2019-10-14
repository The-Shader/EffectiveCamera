package com.fireblade.effectivecamera.fragments

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.fireblade.effectivecamera.R
import com.fireblade.effectivecamera.graphics.effects.EffectConfig
import com.fireblade.effectivecamera.graphics.services.EffectViewModel
import com.fireblade.effectivecamera.settings.SettingsItem
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.kotlinandroidextensions.ViewHolder
import kotlinx.android.synthetic.main.fragment_settings.*
import kotlin.math.abs

class SettingsFragment : Fragment(), View.OnTouchListener {

  private var previousTouchPos = -1

  lateinit var velocityTracker: VelocityTracker

  var settingsPropertyDisplayName: String = "Placeholder"

  var settingsPropertyFactor: Float = 1.0f

  private val settingsAdapter: GroupAdapter<ViewHolder> by lazy { GroupAdapter<ViewHolder>() }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? = inflater.inflate(R.layout.fragment_settings, container, false)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    activity?.let { fragmentActivity ->
      val viewModel = ViewModelProvider.AndroidViewModelFactory(fragmentActivity.application).create(
        EffectViewModel::class.java)
      viewModel.getEffectConfig().observe(this, androidx.lifecycle.Observer<EffectConfig> {
        settingsAdapter.addAll( it.properties.map { attribute -> SettingsItem(attribute.value) })
      })
    }

    settings_layout.setOnTouchListener(this)
  }

  override fun onTouch(view: View, event: MotionEvent): Boolean {
    val currentTouchPos = event.x.toInt()

    if (previousTouchPos == -1) {
      previousTouchPos = currentTouchPos
    }

    val delta = currentTouchPos - previousTouchPos

    when (event.action) {

      MotionEvent.ACTION_DOWN -> {
        velocityTracker = VelocityTracker.obtain()
        velocityTracker.clear()
        velocityTracker.addMovement(event)
      }

      MotionEvent.ACTION_UP -> {
        velocityTracker.addMovement(event)
        velocityTracker.clear()
        velocityTracker.recycle()
      }

      MotionEvent.ACTION_MOVE ->  {
        velocityTracker.addMovement(event)
        velocityTracker.computeCurrentVelocity(1000)
        if(abs(delta) > 0) {
          val value = (velocityTracker.xVelocity * 0.01f * settingsPropertyFactor).toInt()
          changeProgress(value)
        }
        previousTouchPos = currentTouchPos
      }

      MotionEvent.ACTION_BUTTON_PRESS -> view.performClick()
    }

    return true
  }

  private fun changeProgress(value: Int) {
    /*settings_progress_bar.progress += value
    val propertyName = effectService.selectedEffectPropertyName()
    val propertyValue = if (propertyName == "hueRotation") (settings_progress_bar.progress.toFloat() - 360) else settings_progress_bar.progress.toFloat() / 100.0f

    effectService.selectEffectProperty(propertyName, propertyValue)
    settings_text_view.text = getString(R.string.settings_property, settingsPropertyDisplayName, propertyValue.toString())*/
  }
}