package com.fireblade.effectivecamera

import android.content.Context
import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.fireblade.effectivecamera.utils.FLAGS_FULLSCREEN
import java.io.File


const val KEY_EVENT_ACTION = "key_event_action"
const val KEY_EVENT_EXTRA = "key_event_extra"
private const val IMMERSIVE_FLAG_TIMEOUT = 500L

class CameraActivity :
    AppCompatActivity() {

  private lateinit var container: FrameLayout

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_camera)

    container = findViewById(R.id.fragment_container)
  }

  override fun onResume() {
    super.onResume()


    container.postDelayed({
      container.systemUiVisibility = FLAGS_FULLSCREEN
    }, IMMERSIVE_FLAG_TIMEOUT)
  }

  companion object {

    fun getOutputDirectory(context: Context): File {
      val appContext = context.applicationContext
      val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
        File(it, appContext.resources.getString(R.string.app_name)).apply {
          mkdirs()
        }
      }
      return if (mediaDir != null && mediaDir.exists())
        mediaDir else appContext.filesDir
    }
  }
}