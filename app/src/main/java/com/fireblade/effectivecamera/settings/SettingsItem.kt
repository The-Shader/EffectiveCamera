package com.fireblade.effectivecamera.settings

import com.fireblade.effectivecamera.R
import com.fireblade.effectivecamera.graphics.effects.FloatEffectAttribute
import com.xwray.groupie.kotlinandroidextensions.Item
import com.xwray.groupie.kotlinandroidextensions.ViewHolder
import kotlinx.android.synthetic.main.settings_list_item.view.*

class SettingsItem (private val effectAttribute: FloatEffectAttribute): Item() {

  override fun getLayout(): Int = R.layout.settings_list_item

  override fun bind(viewHolder: ViewHolder, position: Int) {
    viewHolder.itemView.settings_description.text = effectAttribute.displayName
    viewHolder.itemView.settings_value.text = effectAttribute.currentValue.toString()
  }

  fun floatEffectAttribute() = effectAttribute
}