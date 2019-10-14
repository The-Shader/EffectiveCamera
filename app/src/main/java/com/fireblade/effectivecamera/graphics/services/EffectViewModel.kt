package com.fireblade.effectivecamera.graphics.services

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.fireblade.effectivecamera.graphics.effects.EffectConfig

class EffectViewModel : ViewModel() {
  private val effectConfig: MutableLiveData<EffectConfig> =  MutableLiveData(EffectConfig("Normal")) /*by lazy {
    MutableLiveData<EffectConfig>().also {
      EffectConfig("Normal")
    }
  }*/

  fun getEffectConfig(): LiveData<EffectConfig> = effectConfig

  fun changeEffectConfig(config: EffectConfig) {
    effectConfig.value = config
  }
}