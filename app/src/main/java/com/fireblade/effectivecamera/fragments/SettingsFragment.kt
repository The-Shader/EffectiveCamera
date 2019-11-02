package com.fireblade.effectivecamera.fragments

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import com.fireblade.effectivecamera.R
import com.fireblade.effectivecamera.graphics.effects.EffectConfig
import com.fireblade.effectivecamera.graphics.effects.FloatEffectAttribute
import com.fireblade.effectivecamera.graphics.services.EffectViewModel
import com.fireblade.effectivecamera.settings.SettingsItem
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.OnItemClickListener
import com.xwray.groupie.kotlinandroidextensions.ViewHolder
import kotlinx.android.synthetic.main.fragment_settings.*
import kotlin.math.abs

class SettingsFragment : Fragment(), View.OnTouchListener {

  private var previousTouchPos = -1

  private lateinit var velocityTracker: VelocityTracker

  private var settingsPropertyFactor: Float = 1.0f

  private lateinit var selectedEffectAttribute: FloatEffectAttribute

  private val settingsAdapter: GroupAdapter<ViewHolder> by lazy { GroupAdapter<ViewHolder>() }

  private val effectViewModel: EffectViewModel by lazy {
    activity?.let { fragmentActivity ->
      ViewModelProviders.of(fragmentActivity)[EffectViewModel::class.java]
    } ?: EffectViewModel()
  }

  private val effectObserver = Observer<EffectConfig> {
    settingsAdapter.addAll( it.properties.map { attribute -> SettingsItem(attribute.value) })
    selectedEffectAttribute = it.brightness
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? = inflater.inflate(R.layout.fragment_settings, container, false)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    settings_control_list.adapter = settingsAdapter
    settings_control_list.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

    settingsAdapter.apply {
      setOnItemClickListener(onItemClickListener)
    }

    effectViewModel.getEffectConfig().observeForever(effectObserver)

    settings_layout.setOnTouchListener(this)
  }

  override fun onDestroy() {

    effectViewModel.getEffectConfig().removeObserver(effectObserver)
    super.onDestroy()
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

  private val onItemClickListener = OnItemClickListener { item, _ ->
    if (item is SettingsItem) {
      selectedEffectAttribute = item.floatEffectAttribute()
      val range = (selectedEffectAttribute.maxValue.toString().toFloat() - selectedEffectAttribute.minValue.toString().toFloat()).toInt()
      val propertyValue = selectedEffectAttribute.currentValue.toString().toFloat() - selectedEffectAttribute.minValue.toString().toFloat()
      val factor = if (selectedEffectAttribute.name != "hueRotation") 100.0f else 1f

      settings_progress_bar.apply {
        max = (range * factor).toInt()
        progress = (propertyValue * factor).toInt()
      }
      settingsPropertyFactor = settings_progress_bar.max.toFloat() / 200.0f
      settings_text_view.text = getString(R.string.settings_property, selectedEffectAttribute.displayName, propertyValue.toString())
    }
  }

  private fun changeProgress(value: Int) {

    settings_progress_bar.progress += value
    selectedEffectAttribute.currentValue =
      if (selectedEffectAttribute.name == "hueRotation") {
        (settings_progress_bar.progress.toFloat() - 360)
      } else {
        settings_progress_bar.progress.toFloat() / 100.0f
      }

    effectViewModel.changeProperty(selectedEffectAttribute)

    settingsAdapter.notifyDataSetChanged()

    settings_text_view.text = getString(R.string.settings_property, selectedEffectAttribute.displayName, selectedEffectAttribute.currentValue.toString())
  }
}