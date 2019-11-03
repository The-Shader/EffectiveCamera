package com.fireblade.effectivecamera

import android.content.Context
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Size
import android.view.Surface
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.Navigation
import com.fireblade.effectivecamera.fragments.CameraControlsFragmentDirections
import com.fireblade.effectivecamera.fragments.PermissionsFragment
import com.fireblade.effectivecamera.graphics.IRendererEvents
import com.fireblade.effectivecamera.graphics.services.EffectViewModel
import com.fireblade.effectivecamera.navigation.CameraNavigator
import com.fireblade.effectivecamera.navigation.ICameraNavigator
import com.fireblade.effectivecamera.utils.CompareSizesByArea
import kotlinx.android.synthetic.main.fragment_camera.camera_preview
import java.io.File
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


const val KEY_EVENT_ACTION = "key_event_action"
const val KEY_EVENT_EXTRA = "key_event_extra"
private const val IMMERSIVE_FLAG_TIMEOUT = 500L

class CameraActivity :
  AppCompatActivity(),
  IRendererEvents {

  private lateinit var effectViewModel: EffectViewModel

  private lateinit var outputDirectory: File

  private var displayId = -1
  private var mCameraDevice: CameraDevice? = null
  private var captureSession: CameraCaptureSession? = null
  private var previewRequestBuilder: CaptureRequest.Builder? = null
  private var captureRequestBuilder: CaptureRequest.Builder? = null

  private var mCameraID: String = ""
  private var cameraResolution: Size = Size(0, 0)
  private var previewResolution: Size = Size(1280, 720)

  private var backgroundThread: HandlerThread? = null
  private var backgroundHandler: Handler? = null
  private val cameraOpenCloseLock = Semaphore(1)

  /** Internal reference of the [DisplayManager] */
  private lateinit var displayManager: DisplayManager

  val cameraNavigator: ICameraNavigator by lazy {
    CameraNavigator.build { fragmentManager = supportFragmentManager }
  }

  /**
   * We need a display listener for orientation changes that do not trigger a configuration
   * change, for example if we choose to override config change in manifest or for 180-degree
   * orientation changes.
   */
  private val displayListener = object : DisplayManager.DisplayListener {
    override fun onDisplayAdded(displayId: Int) = Unit
    override fun onDisplayRemoved(displayId: Int) = Unit
    override fun onDisplayChanged(displayId: Int) = Unit
  }

  private var cameraNum = 1   // LENS_FACING_FRONT = 0
                              // LENS_FACING_BACK = 1
                              // LENS_FACING_EXTERNAL = 2

  private fun Bundle.cameraNumber(): Int = getInt(getString(R.string.camera_num), 1)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    effectViewModel = ViewModelProviders.of(this)[EffectViewModel::class.java]

    setContentView(R.layout.activity_camera)

    savedInstanceState?.apply {
      cameraNum = cameraNumber()
    }
  }

  override fun onResume() {
    super.onResume()

    // Make sure that all permissions are still present, since user could have removed them
    //  while the app was on paused state
    if (!PermissionsFragment.hasPermissions(this)) {
      Navigation.findNavController(this, R.id.nav_fragment).navigate(
        CameraControlsFragmentDirections.actionCameraControlsFragmentToPermissionsFragment()//.actionCameraToPermissions()
      )
    } else {
      startBackgroundThread()

      initialize()

      camera_preview.onResume()
    }
  }

  override fun onPause() {
    closeCamera()
    stopBackgroundThread()
    camera_preview.onPause()
    super.onPause()
  }

  override fun onRenderingContextCreated() {
    val size = Point()
    displayManager.getDisplay(displayId).getRealSize(size)

    val width = max(size.x, size.y)
    val height = min(size.x, size.y)

    setResolutions(width, height)

    openCamera()

    camera_preview.initializeEffect()
  }

  override fun onCaptureFinished() {

  }

  fun setFullScreen() {
    window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE
        // Set the content to appear under the system bars so that the
        // content doesn't resize when the system bars hide and show.
        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        // Hide the nav bar and status bar
        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        or View.SYSTEM_UI_FLAG_FULLSCREEN)
  }

  fun swapCamera() {
    closeCamera()

    cameraNum = if (cameraNum == 1) 0 else 1

    onRenderingContextCreated()

    openCamera()
  }

  private fun setResolutions(width: Int, height: Int) {
    val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    try {
      for (cameraID in manager.cameraIdList) {
        val characteristics = manager.getCameraCharacteristics(cameraID)
        if (characteristics.get(CameraCharacteristics.LENS_FACING) != cameraNum) {
          continue
        }

        mCameraID = cameraID
        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.let { streamConfigurationMap ->
          val resolutions = streamConfigurationMap.getOutputSizes(SurfaceTexture::class.java)
          cameraResolution = Collections.max(resolutions.toMutableList(), CompareSizesByArea())
          camera_preview.setCaptureResolution(cameraResolution)

          previewResolution =
            getPreviewResolution(
              resolutions,
              width,
              height
            )
        }
        break
      }
    } catch (e: CameraAccessException) {
      //Timber.i("setResolutions - Camera Access Exception: $e")
    } catch (e: IllegalArgumentException) {
      //Timber.i("setResolutions - Illegal Argument Exception: $e")
    } catch (e: SecurityException) {
      //Timber.i("setResolutions -  Security Exception: $e")
    }
  }

  private fun initialize() {
    // Every time the orientation of device changes, recompute layout
    displayManager = camera_preview.context
      .getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    displayManager.registerDisplayListener(displayListener, null)

    // Determine the output directory
    outputDirectory = getOutputDirectory(this)

    cameraNavigator.navigateToCameraControls()

    camera_preview.surfaceEventListeners = listOf(this)
    // Wait for the views to be properly laid out
    camera_preview.post {
      // Keep track of the display in which this view is attached
      displayId = camera_preview.display.displayId
    }
  }

  private fun getPreviewResolution(resolutions: Array<Size>, screenWidth: Int, screenHeight: Int): Size {
    val screenAspectRatio = screenWidth.toFloat() / screenHeight.toFloat()
    val minResolution = Size(2 * screenWidth / 3, 2 * screenHeight / 3)

    val preferredResolutions = ArrayList<Size>()
    val acceptableResolutions = ArrayList<Size>(9)

    resolutions.map { resolution ->
      if (resolution.width <= screenWidth && resolution.height <= screenHeight && (resolution.width.toFloat() / resolution.height.toFloat() == screenAspectRatio)) {
        if (resolution.width > minResolution.width && resolution.height >= minResolution.height) {
          preferredResolutions.add(resolution)
        } else {
          acceptableResolutions.add(resolution)
        }
      }
    }

    return when {
      preferredResolutions.isNotEmpty() -> Collections.max(preferredResolutions, CompareSizesByArea())
      acceptableResolutions.isNotEmpty() -> Collections.max(acceptableResolutions, CompareSizesByArea())
      else -> findNearestResolution(resolutions, Size(screenWidth, screenHeight))
    }
  }

  private fun findNearestResolution(resolutions: Array<Size>, targetResolution: Size): Size {
    val difference = { p0: Size, p1: Size ->
      abs(p0.width * p0.height - p1.width * p1.height)
    }
    var nearest = resolutions.first()
    resolutions.map {
      if (difference(targetResolution, it) < difference(targetResolution, nearest)) {
        nearest = it
      }
    }
    return nearest
  }

  private fun openCamera() {

    val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    try {
      if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
        throw RuntimeException("Time out waiting to lock camera opening.")
      }
      manager.openCamera(mCameraID, cameraStateCallback, backgroundHandler)
    } catch (e: CameraAccessException) {
      //Timber.i("OpenCamera - Camera Access Exception: $e")
    } catch (e: IllegalArgumentException) {
      //Timber.i("OpenCamera - Illegal Argument Exception: $e")
    } catch (e: SecurityException) {
      //Timber.i("OpenCamera - Security Exception: $e")
    } catch (e: InterruptedException) {
      //Timber.i("OpenCamera - Interrupted Exception: $e")
    }
  }

  private fun closeCamera() {
    try {
      cameraOpenCloseLock.acquire()
      if (captureSession != null) {
        captureSession?.close()
        captureSession = null
      }
      if (mCameraDevice != null) {
        mCameraDevice?.close()
        mCameraDevice = null
      }
    } catch (e: InterruptedException) {
      //Timber.i("Interrupted while trying to lock camera closing. $e")
    } finally {
      cameraOpenCloseLock.release()
    }
  }

  private val cameraStateCallback = object : CameraDevice.StateCallback() {

    override fun onOpened(cameraDevice: CameraDevice) {
      cameraOpenCloseLock.release()
      mCameraDevice = cameraDevice
      createCameraPreviewSession()
    }

    override fun onClosed(camera: CameraDevice) {
      cameraOpenCloseLock.release()
      camera.close()
      mCameraDevice = null
    }

    override fun onDisconnected(cameraDevice: CameraDevice) {
      cameraOpenCloseLock.release()
      cameraDevice.close()
      mCameraDevice = null
    }

    override fun onError(cameraDevice: CameraDevice, error: Int) {
      cameraOpenCloseLock.release()
      cameraDevice.close()
      mCameraDevice = null
    }
  }

  private fun createCameraPreviewSession() {
    try {
      camera_preview.getPreviewSurfaceTex().setDefaultBufferSize(previewResolution.width, previewResolution.height)

      val previewSurface = Surface(camera_preview.getPreviewSurfaceTex())

      camera_preview.getCaptureSurfaceTex().setDefaultBufferSize(cameraResolution.width, cameraResolution.height)

      val captureSurface = Surface(camera_preview.getCaptureSurfaceTex())

      previewRequestBuilder = mCameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)

      previewRequestBuilder?.let { previewBuilder ->
        previewBuilder.addTarget(previewSurface)

        captureRequestBuilder =
          mCameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        captureRequestBuilder?.addTarget(captureSurface)

        mCameraDevice?.createCaptureSession(
          listOf(previewSurface, captureSurface),
          object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
              if (null == mCameraDevice) {
                return
              }

              captureSession = cameraCaptureSession
              try {
                previewBuilder.set(
                  CaptureRequest.CONTROL_AF_MODE,
                  CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                )
                previewBuilder.set(
                  CaptureRequest.CONTROL_AE_MODE,
                  CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                )
                previewBuilder.set(
                  CaptureRequest.CONTROL_AWB_MODE,
                  CaptureRequest.CONTROL_AWB_MODE_AUTO
                )

                captureRequestBuilder?.set(
                  CaptureRequest.CONTROL_AF_MODE,
                  CaptureRequest.CONTROL_AF_MODE_AUTO
                )
                captureRequestBuilder?.set(
                  CaptureRequest.CONTROL_AE_MODE,
                  CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                )
                captureRequestBuilder?.set(
                  CaptureRequest.CONTROL_AWB_MODE,
                  CaptureRequest.CONTROL_AWB_MODE_AUTO
                )
                captureRequestBuilder?.set(
                  CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                  CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START
                )
                captureRequestBuilder?.set(
                  CaptureRequest.CONTROL_AF_TRIGGER,
                  CaptureRequest.CONTROL_AF_TRIGGER_START
                )

                captureSession?.setRepeatingRequest(
                  previewBuilder.build(),
                  null,
                  backgroundHandler
                )
              } catch (e: CameraAccessException) {
                //Timber.i("createCaptureSession - Camera Access Exception: $e")
              }

            }

            override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
            }
          }, null
        )
      }
    } catch (e: CameraAccessException) {
      //Timber.i("createCameraPreviewSession - Camera Access Exception: $e")
    }
  }

  private fun startBackgroundThread() {
    backgroundThread = HandlerThread("CameraBackground", Process.THREAD_PRIORITY_DISPLAY)
    backgroundThread?.let {
      it.start()
      backgroundHandler = Handler(it.looper)
    }
  }

  private fun stopBackgroundThread() {
    backgroundThread?.quitSafely()
    try {
      backgroundThread?.join()
      backgroundThread = null
      backgroundHandler = null
    } catch (e: InterruptedException) {
      //Log.i("stopBackgroundThread $e")
    }
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