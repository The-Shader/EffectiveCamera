package com.fireblade.effectivecamera.utils

import android.util.Log
import com.fireblade.effectivecamera.graphics.effects.EffectConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

class FileManager {

  companion object {
    @JvmStatic
    fun saveEffectConfig(filePath: File, effectConfig: EffectConfig) {

      val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

      val effectConfigFileName = "CurrentEffect_$timeStamp"

      if (!filePath.exists()) {
        if (!filePath.mkdirs()) {
          Log.d("Downloads", "directory doesn't exist")
          return
        }
      }

      try {
        val effectConfigFile = File(filePath, "$effectConfigFileName.txt")
        val fileOutputStream = FileOutputStream(effectConfigFile)
        val fileWriter = OutputStreamWriter(fileOutputStream)

        effectConfig.properties.map { property ->
          val line = "${property.key} - ${property.value.currentValue}\n"
          fileWriter.write(line)
        }
        fileWriter.flush()
        fileWriter.close()

        fileOutputStream.flush()
        fileOutputStream.close()
      } catch (e: IOException) {
        println("IO Error: " + e.message)
      } finally {
        //fileOutputStream.close()

      }
    }
  }
}