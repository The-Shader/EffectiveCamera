package com.fireblade.effectivecamera.graphics.services

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.fireblade.effectivecamera.graphics.effects.EffectConfig
import com.fireblade.effectivecamera.graphics.effects.FloatEffectAttribute
import com.fireblade.effectivecamera.graphics.effects.IRenderEffect

class EffectViewModel : ViewModel() {
  private val effectConfig: MutableLiveData<EffectConfig> =  MutableLiveData(EffectConfig("Normal"))

  fun getEffectConfig(): LiveData<EffectConfig> = effectConfig

  fun changeEffectConfig(config: EffectConfig) {
    effectConfig.value = config
  }

  fun changeProperty(property: FloatEffectAttribute) {
    effectConfig.value?.let {
      it.properties[property.name]?.currentValue = property.currentValue
    }
  }
}