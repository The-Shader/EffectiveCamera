package com.fireblade.effectivecamera.fragments

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.camera2.*
import android.hardware.display.DisplayManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.DisplayMetrics
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.*
import android.webkit.MimeTypeMap
import android.widget.ImageButton
import androidx.camera.core.ImageCapture
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.Navigation
import com.fireblade.effectivecamera.*
import com.fireblade.effectivecamera.graphics.IRendererEvents
import com.fireblade.effectivecamera.utils.*
import kotlinx.android.synthetic.main.fragment_camera.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CameraFragment : Fragment(), IRendererEvents {

  private lateinit var container: ConstraintLayout
  private lateinit var outputDirectory: File
  private lateinit var broadcastManager: LocalBroadcastManager

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

  private var cameraNum = 1   // LENS_FACING_FRONT = 0
                              // LENS_FACING_BACK = 1
                              // LENS_FACING_EXTERNAL = 2

  private fun Bundle.cameraNumber(): Int = getInt(getString(R.string.camera_num), 1)

  // Volume down button receiver
  private val volumeDownReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      val keyCode = intent.getIntExtra(KEY_EVENT_EXTRA, KeyEvent.KEYCODE_UNKNOWN)
      when (keyCode) {
        // When the volume down button is pressed, simulate a shutter button click
        KeyEvent.KEYCODE_VOLUME_DOWN -> {
          val shutter = container
            .findViewById<ImageButton>(R.id.camera_capture_button)
          shutter.simulateClick()
        }
      }
    }
  }

  /** Internal reference of the [DisplayManager] */
  private lateinit var displayManager: DisplayManager

  /**
   * We need a display listener for orientation changes that do not trigger a configuration
   * change, for example if we choose to override config change in manifest or for 180-degree
   * orientation changes.
   */
  private val displayListener = object : DisplayManager.DisplayListener {
    override fun onDisplayAdded(displayId: Int) = Unit
    override fun onDisplayRemoved(displayId: Int) = Unit
    override fun onDisplayChanged(displayId: Int) = view?.let { view ->
      if (displayId == this@CameraFragment.displayId) {
      }
    } ?: Unit
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Mark this as a retain fragment, so the lifecycle does not get restarted on config change

    retainInstance = true
    savedInstanceState?.apply {
      cameraNum = cameraNumber()
    }
  }

  override fun onResume() {
    super.onResume()
    // Make sure that all permissions are still present, since user could have removed them
    //  while the app was on paused state
    if (!PermissionsFragment.hasPermissions(requireContext())) {
      Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
        CameraFragmentDirections.actionCameraToPermissions()
      )
    } else {
      startBackgroundThread()

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

  override fun onDestroyView() {
    super.onDestroyView()

    // Unregister the broadcast receivers and listeners
    broadcastManager.unregisterReceiver(volumeDownReceiver)
    displayManager.unregisterDisplayListener(displayListener)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?): View? =
    inflater.inflate(R.layout.fragment_camera, container, false)

  /** Define callback that will be triggered after a photo has been taken and saved to disk */
  private val imageSavedListener = object : ImageCapture.OnImageSavedListener {
    override fun onError(
      error: ImageCapture.UseCaseError, message: String, exc: Throwable?) {
      Log.e(TAG, "Photo capture failed: $message")
      exc?.printStackTrace()
    }

    override fun onImageSaved(photoFile: File) {

      // Implicit broadcasts will be ignored for devices running API
      // level >= 24, so if you only target 24+ you can remove this statement
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        requireActivity().sendBroadcast(
          Intent(Camera.ACTION_NEW_PICTURE, Uri.fromFile(photoFile)))
      }

      // If the folder selected is an external media directory, this is unnecessary
      // but otherwise other apps will not be able to access our images unless we
      // scan them using [MediaScannerConnection]
      val mimeType = MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(photoFile.extension)
      MediaScannerConnection.scanFile(
        context, arrayOf(photoFile.absolutePath), arrayOf(mimeType), null)
    }
  }

  @SuppressLint("MissingPermission")
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    container = view as ConstraintLayout
    broadcastManager = LocalBroadcastManager.getInstance(view.context)

    // Set up the intent filter that will receive events from our main activity
    val filter = IntentFilter().apply { addAction(KEY_EVENT_ACTION) }
    broadcastManager.registerReceiver(volumeDownReceiver, filter)

    // Every time the orientation of device changes, recompute layout
    displayManager = camera_preview.context
      .getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    displayManager.registerDisplayListener(displayListener, null)

    // Determine the output directory
    outputDirectory = CameraActivity.getOutputDirectory(requireContext())


    camera_preview.surfaceEventListeners = listOf(this)
    // Wait for the views to be properly laid out
    camera_preview.post {
      // Keep track of the display in which this view is attached
      displayId = camera_preview.display.displayId

      // Build UI controls and bind all camera use cases
      updateCameraUi()
      bindCameraUseCases()
    }
  }

  /** Declare and bind preview, capture and analysis use cases */
  private fun bindCameraUseCases() {

    // Get screen metrics used to setup camera for full screen resolution
    val metrics = DisplayMetrics().also { camera_preview.display.getRealMetrics(it) }
    val screenAspectRatio = Rational(metrics.widthPixels, metrics.heightPixels)
  }

  /** Method used to re-draw the camera UI controls, called every time configuration changes */
  @SuppressLint("RestrictedApi")
  private fun updateCameraUi() {

    // Remove previous UI if any
    container.findViewById<ConstraintLayout>(R.id.camera_ui_container)?.let {
      container.removeView(it)
    }

    // Inflate a new view containing all UI for controlling the camera
    val controls = View.inflate(requireContext(), R.layout.camera_ui_container, container)

    // Listener for button used to capture photo
    controls.findViewById<ImageButton>(R.id.camera_capture_button).setOnClickListener {

    }
    // Listener for button used to switch cameras
    controls.findViewById<ImageButton>(R.id.camera_switch_button).setOnClickListener {
    }
  }

  private fun setResolutions(width: Int, height: Int) {
    activity?.let {
      val manager = it.getSystemService(Context.CAMERA_SERVICE) as CameraManager
      try {
        for (cameraID in manager.cameraIdList) {
          val characteristics = manager.getCameraCharacteristics(cameraID)
          if (characteristics.get(CameraCharacteristics.LENS_FACING) != cameraNum) {
            continue
          }

          mCameraID = cameraID
          val resolutions = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(
            SurfaceTexture::class.java
          )
          cameraResolution = Collections.max(resolutions.toMutableList(), CompareSizesByArea())
          camera_preview.setCaptureResolution(cameraResolution)
          previewResolution =
            getPreviewResolution(resolutions, width, height)//getPreferredResolution(resolutions, width, height)
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

    if (preferredResolutions.isNotEmpty()) {
      return Collections.max(preferredResolutions, CompareSizesByArea())
    } else if (acceptableResolutions.isNotEmpty()) {
      return Collections.max(acceptableResolutions, CompareSizesByArea())
    } else {
      return findNearestResolution(resolutions, Size(screenWidth, screenHeight))
    }
  }

  private fun getPreferredResolution(resolutions: Array<Size>, screenWidth: Int, screenHeight: Int): Size {
    val screenAspectRatio = screenWidth.toFloat() / screenHeight.toFloat()
    val preferredResolutions: ArrayList<Size> = arrayListOf()
    var minDiff = 5.0f
    var preferredResolution = Size(screenWidth, screenHeight)
    for (resolution in resolutions) {
      if ((screenWidth == resolution.width && screenHeight == resolution.height) || (screenWidth == resolution.height && screenHeight == resolution.width)) {
        return resolution
      }
      val aspectRatio = resolution.width.toFloat() / resolution.height.toFloat()
      if (aspectRatio == screenAspectRatio) {
        preferredResolutions.add(resolution)
      } else if (abs(aspectRatio - screenAspectRatio) < minDiff) {
        minDiff = abs(aspectRatio - screenAspectRatio)
        preferredResolution = resolution
      }
    }
    if (preferredResolutions.isNotEmpty()) {

      return findNearestResolution(preferredResolutions.toTypedArray(), Size(screenWidth, screenHeight))
    } else {
      return preferredResolution
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

    activity?.let {
      val manager =  it.getSystemService(Context.CAMERA_SERVICE) as CameraManager
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

    override fun onClosed(camera: CameraDevice?) {
      cameraOpenCloseLock.release()
      camera?.close()
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
      previewRequestBuilder?.addTarget(previewSurface)

      captureRequestBuilder = mCameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
      captureRequestBuilder?.addTarget(captureSurface)

      mCameraDevice?.createCaptureSession(
        Arrays.asList(previewSurface, captureSurface),
        object : CameraCaptureSession.StateCallback() {
          override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
            if (null == mCameraDevice) {
              return
            }

            captureSession = cameraCaptureSession
            try {
              previewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
              previewRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
              previewRequestBuilder?.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)

              captureRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
              captureRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
              captureRequestBuilder?.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
              captureRequestBuilder?.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)
              captureRequestBuilder?.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)

              captureSession?.setRepeatingRequest(previewRequestBuilder?.build(), null, backgroundHandler)
            } catch (e: CameraAccessException) {
              //Timber.i("createCaptureSession - Camera Access Exception: $e")
            }

          }

          override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
          }
        }, null)
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
    private const val TAG = "EffectiveCamera"
    private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
    private const val PHOTO_EXTENSION = ".jpg"

    /** Helper function used to create a timestamped file */
    private fun createFile(baseFolder: File, format: String, extension: String) =
      File(baseFolder, SimpleDateFormat(format, Locale.US)
        .format(System.currentTimeMillis()) + extension)
  }
}