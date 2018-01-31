/*
Copyright 2018 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.google.android.imaging.pixelvisualcorecamera.api2;

import static android.hardware.camera2.CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM;
import static android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import com.google.android.imaging.pixelvisualcorecamera.common.Orientation;
import com.google.android.imaging.pixelvisualcorecamera.common.Utils;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 *  Manages the state of an API 2 camera.
 */
public final class Camera2Controller {

  /** Callback for when a captured image is available. */
  public interface OnImageAvailableListener {

    /**
     * Provides temporary access to the most recently acquired Image. Perform all accesses
     * of the image within this callback. Do not close the image. Called on a background thread.
     */
    void onImageAvailable(Image image);
  }

  private static final String TAG = "PvcCamCon2";
  private static final double ZOOM_SCALE_1_00 = 1.0;

  private final Context context;
  private final int displayRotationCode;
  private final View captureButton;
  private final View doubleShotButton;
  private final AutoFitTextureView textureView;
  private OnImageAvailableListener clientOnImageAvailableListener;

  /** Acquire this semaphore before attempting to open/close the camera. */
  private final Semaphore openCloseLock = new Semaphore(1);
  private String cameraId;
  private CameraDevice cameraDevice;
  private Size previewSize;
  private CameraCaptureSession captureSession;
  private ImageReader imageReader;
  private boolean stillCapturePending;
  private Handler backgroundHandler;
  private boolean doubleShotPending;
  private boolean nonHdrPlusShotPending;
  private Size outputSize;
  private int outputOrientation;

  /**
   *  This builder acts as a cache for intermediate preview requests during the capture sequence.
   *  Care should be taken that operations on this builder are overwritten by later steps.
   *  E.g., after setting an AF trigger on the builder, reset the trigger to idle.
   */
  private CaptureRequest.Builder previewRequestBuilder;

  /**
   * This is the cached previewRequest for the nominal preview state. Use this request
   * when starting a 'normal' preview capture request.
   */
  private CaptureRequest previewRequest;

  /** A scale factor from 1.0 to maxDigitalZoom. Applied to all preview and capture requests. */
  private double zoomSetting = ZOOM_SCALE_1_00;

  /** Camera characteristic used to position the zoom cropping region. */
  private Rect activeArraySize;

  /** Camera characteristic for the maximum digital zoom. */
  private double maxDigitalZoom = ZOOM_SCALE_1_00;

  public Camera2Controller(
      Context context,
      int displayRotationCode,
      Button captureButton,
      View doubleShotButton,
      AutoFitTextureView textureView,
      OnImageAvailableListener clientOnImageAvailableListener) {
    this.context = context;
    this.displayRotationCode = displayRotationCode;
    this.captureButton = captureButton;
    this.doubleShotButton = doubleShotButton;
    this.textureView = textureView;
    this.clientOnImageAvailableListener = clientOnImageAvailableListener;
  }

  // ===============================================================================================
  // Public Interface
  // ===============================================================================================

  /** Set the handler on which to run background operations. */
  public void setBackgroundHandler(Handler backgroundHandler) {
    this.backgroundHandler = backgroundHandler;
  }

  // When the screen is turned off and turned back on, the SurfaceTexture is already
  // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
  // a camera and start preview from here (otherwise, we wait until the surface is ready in
  // the SurfaceTextureListener).
  public void acquireCamera(String cameraId, Size outputSize, double zoom) throws IOException {
    Log.d(TAG, String.format("acquireCamera(cameraId=%s, zoom=x%.2f)", cameraId, zoom));
    if (backgroundHandler == null) {
      throw new IllegalStateException("A background handler must be set before opening the camera");
    }
    zoomSetting = zoom;

    this.cameraId = cameraId;
    this.outputSize = outputSize;
    if (textureView.isAvailable()) {
      openCamera(textureView.getWidth(), textureView.getHeight(), outputSize);
    } else {
      Log.d(TAG, "textureView not available, starting listener");
      textureView.setSurfaceTextureListener(surfaceTextureListener);
    }
  }

  public void closeCamera() {
    Log.d(TAG, "closeCamera");
    try {
      openCloseLock.acquire();
      if (null != captureSession) {
        captureSession.close();
        captureSession = null;
      }
      if (cameraDevice != null) {
        cameraDevice.close();
        cameraDevice = null;
      }
      if (null != imageReader) {
        imageReader.close();
        imageReader = null;
      }
      maxDigitalZoom = ZOOM_SCALE_1_00;
      zoomSetting = ZOOM_SCALE_1_00;
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
    } finally {
      openCloseLock.release();
    }
  }

  /** Initiate a still image capture. */
  public void takePicture() {
    lockFocus();
  }

  /** Initiate capture of back to back HDR+ and non-HDR shots. */
  public void takeDoubleShot() {
    doubleShotPending = true;
    lockFocus();
  }

  /** Retrieves the maximum digital zoom as a scale factor. */
  public double getMaxZoom(String cameraId) {
    CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    try {
      // Extract characteristics for the current camera.
      CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
      maxDigitalZoom = characteristics.get(SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
    } catch (CameraAccessException e) {
      Log.w(TAG, e);
    }
    Log.d(TAG, String.format("getMaxZoom(%s) -> %.2f", cameraId, maxDigitalZoom));
    return maxDigitalZoom;
  }

  /** Sets the digital zoom. Must be called while the preview is active. */
  public void setZoom(double zoomSetting) {
    Log.d(TAG, String.format("setZoom(x%.2f)", zoomSetting));
    if (zoomSetting > maxDigitalZoom) {
      throw new IllegalArgumentException("out of bounds zoom");
    }
    this.zoomSetting = zoomSetting;

    try {
      captureSession.stopRepeating();
      setCropRegion(previewRequestBuilder, zoomSetting);
      previewRequest = previewRequestBuilder.build();
      captureSession.setRepeatingRequest(previewRequest, captureCallback, backgroundHandler);
    } catch (CameraAccessException e) {
      Log.w(TAG, e);
    }
  }

  // ===============================================================================================

  private void openCamera(int width, int height, Size outputSize) throws IOException {
    Log.i(TAG, String.format("openCamera(%d, %d, outputSize(%d, %d))",
        width, height, outputSize.getWidth(), outputSize.getHeight()));
    this.outputSize = outputSize;
    if (!initCameraCharacteristics()) {
      throw new IOException("Failed init camera characteristics");
    }
    setUpCameraOutputs(width, height, outputSize);

    CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    try {
      // The lock is released in the callbacks.
      if (!openCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
        throw new RuntimeException("Time out waiting to lock camera opening.");
      }
      //noinspection MissingPermission
      manager.openCamera(cameraId, cameraStateCallback, backgroundHandler);
    } catch (CameraAccessException e) {
      throw new IOException("Failed to open camera", e);
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
    }
  }

  private final CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {

    @Override
    public void onOpened(@NonNull CameraDevice cameraDevice) {
      Log.i(TAG, "CameraDevice onOpened() " + cameraDevice.getId());
      openCloseLock.release();
      Camera2Controller.this.cameraDevice = cameraDevice;
      createCameraPreviewSession();
    }

    @Override
    public void onDisconnected(@NonNull CameraDevice cameraDevice) {
      Log.i(TAG, "CameraDevice onDisconnected() " + cameraDevice.getId());
      openCloseLock.release();
      cameraDevice.close();
      Camera2Controller.this.cameraDevice = null;
    }

    @Override
    public void onError(@NonNull CameraDevice cameraDevice, int error) {
      Log.w(TAG, "CameraDevice onError() id: " + cameraDevice.getId() + " error: " + error);
      openCloseLock.release();
      cameraDevice.close();
      Camera2Controller.this.cameraDevice = null;
    }
  };

  // Only active if the TextureView is not initially available.
  private final TextureView.SurfaceTextureListener surfaceTextureListener
      = new TextureView.SurfaceTextureListener() {

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
      Log.i(TAG, "onSurfaceTextureAvailable()");
      try {
        openCamera(width, height, outputSize);
      } catch (IOException e) {
        Log.w(TAG, "Failed to open camera", e);
      }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
      Log.i(TAG, "onSurfaceTextureSizeChanged()");
      configureTransform(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
      Log.d(TAG, "onSurfaceTextureDestroyed()");
      return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
  };

  // ===============================================================================================
  // Output Configuration
  // ===============================================================================================

  /**
   * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
   * still image is ready to be saved. This is called on a background thread.
   */
  private final ImageReader.OnImageAvailableListener onImageAvailableListener = reader -> {
    Log.d(TAG, "onImageAvailable()");
    Image image = reader.acquireNextImage();
    if (clientOnImageAvailableListener != null) {
      clientOnImageAvailableListener.onImageAvailable(image);
    }
    image.close();
  };

  /** Fetches the camera characteristics for the current camera. */
  private boolean initCameraCharacteristics() {
    CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    try {
      // Extract characteristics for the current camera.
      CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

      activeArraySize = characteristics.get(SENSOR_INFO_ACTIVE_ARRAY_SIZE);
      maxDigitalZoom = characteristics.get(SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);

      // Require a stream configuration map. Don't know why there wouldn't be one.
      StreamConfigurationMap map = characteristics.get(
          CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
      if (map == null) {
        throw new CameraAccessException(CameraAccessException.CAMERA_ERROR);
      }

      int facing = characteristics.get(CameraCharacteristics.LENS_FACING);
      boolean lensFacingFront = (facing == CameraCharacteristics.LENS_FACING_FRONT);
      int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
      outputOrientation = Orientation.getOutputOrientation(
          lensFacingFront, displayRotationCode, sensorOrientation);

      return true;
    } catch (CameraAccessException | NullPointerException e) {
      // NPE's can be thrown when unboxing some of the characteristics. This should never happen
      // on Pixel{1,2}.
      Log.w(TAG, "Failed to inspect camera characteristics", e);
    }
    return false;
  }

  /**
   * Configures the output surfaces.
   *
   * @param width  The width of available size for camera preview
   * @param height The height of available size for camera preview
   */
  private void setUpCameraOutputs(int width, int height, Size outputSize) {
    Log.d(TAG, String.format("setUpCameraOutputs(w=%d, h=%d)", width, height));

    configureImageReader(outputSize);

    // We know which preview sizes Pixel 2 supports; just use those directly.
    previewSize = new Size(Utils.MAX_PREVIEW_WIDTH, Utils.MAX_PREVIEW_HEIGHT);
    Log.d(TAG, String.format("preview size (%d, %d)",
        previewSize.getWidth(), previewSize.getHeight()));

    // Disable autofit. Due to the system UI the preview does not fill the screen with
    // the correct aspect ratio. The view is laid out to fill the screen. This introduces
    // a small amount of distortion to the preview but fills the screen.
    textureView.setAspectRatio(0, 0);
    configureTransform(width, height);
  }

  private void configureImageReader(Size s) {
    imageReader = ImageReader.newInstance(s.getWidth(), s.getHeight(),
        ImageFormat.JPEG, /*maxImages*/2);
    imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);
  }

  private void configureTransform(int width, int height) {
    textureView.configureTransform(displayRotationCode, width, height, previewSize);
  }

  public Size[] getSupportedPictureSizes(String cameraId) {
    Size[] outputSizes = null;
    CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    try {
      CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
      StreamConfigurationMap map = characteristics.get(
          CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
      if (map != null) {
        outputSizes = map.getOutputSizes(ImageFormat.JPEG);
      }
    } catch (CameraAccessException e) {
      Log.w(TAG, e);
    }

    return outputSizes;
  }

  // ===============================================================================================
  // Capture Session/Request Construction
  // ===============================================================================================

  /**
   * Creates a new {@link CameraCaptureSession} for camera preview.
   */
  private void createCameraPreviewSession() {
    try {
      SurfaceTexture texture = textureView.getSurfaceTexture();
      assert texture != null;

      // We configure the size of default buffer to be the size of camera preview we want.
      texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

      // This is the output Surface we need to start preview.
      Surface surface = new Surface(texture);

      // We set up a CaptureRequest.Builder with the output Surface.
      previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      previewRequestBuilder.addTarget(surface);

      // Here, we create a CameraCaptureSession for camera preview and capture. All surfaces
      // that are used during a CaptureSession are provided here.
      cameraDevice.createCaptureSession(Arrays.asList(surface , imageReader.getSurface()),
          new CameraCaptureSession.StateCallback() {

            @Override
            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
              Log.d(TAG, "CaptureSession callback onConfigured()");
              // The camera is already closed
              if (null == cameraDevice) {
                return;
              }

              // When the session is ready, we start displaying the preview.
              captureSession = cameraCaptureSession;
              try {
                commonCaptureSessionConfig(previewRequestBuilder);
                previewRequest = previewRequestBuilder.build();
                captureSession.setRepeatingRequest(
                    previewRequest, captureCallback, backgroundHandler);
              } catch (CameraAccessException e) {
                Log.w(TAG, e);
              }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
              Log.w(TAG, "preview configuration failed");
            }

            @Override
            public void onReady(@NonNull CameraCaptureSession session) {
              Log.d(TAG, "CaptureSession callback onReady()");
              if (stillCapturePending) {
                startStillCapture();
                stillCapturePending = false;
              }
            }
          }, null
      );
    } catch (CameraAccessException e) {
      Log.w(TAG, "Camera preview configuration failed", e);
    }
  }

  private void commonCaptureSessionConfig(CaptureRequest.Builder builder) {
    builder.set(
        CaptureRequest.CONTROL_AF_MODE,
        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

    builder.set(
        CaptureRequest.CONTROL_AE_MODE,
        CaptureRequest.CONTROL_AE_MODE_ON);

    // HDR+: Flash mode must be off.
    builder.set(
        CaptureRequest.FLASH_MODE,
        CaptureRequest.FLASH_MODE_OFF);

    // HDR+: Scene modes besides FACE PRIORITY may cause HDR+ processing to be disabled.
    builder.set(
        CaptureRequest.CONTROL_SCENE_MODE,
        CaptureRequest.CONTROL_SCENE_MODE_FACE_PRIORITY);

    builder.set(
        CaptureRequest.CONTROL_MODE,
        CaptureRequest.CONTROL_MODE_USE_SCENE_MODE);

    builder.set(
        CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE,
        CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON);

    setCropRegion(builder, zoomSetting);
  }

  /** Trigger a precapture metering sequence. */
  private void setAePrecaptureTriggerStart(CaptureRequest.Builder builder) {
    builder.set(
        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
  }

  /** Return the AE precapture system to an idle state. */
  private void setAePrecaptureTriggerIdle(CaptureRequest.Builder builder) {
    builder.set(
        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
  }

  /** Cancels an active AF trigger. */
  private void setAfTriggerCancel(CaptureRequest.Builder builder) {
    builder.set(
        CaptureRequest.CONTROL_AF_TRIGGER,
        CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
  }

  /** Returns the AF system to an idle state. */
  private void setAfTriggerIdle(CaptureRequest.Builder builder) {
    builder.set(
        CaptureRequest.CONTROL_AF_TRIGGER,
        CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
  }

  /** Triggers auto-focusing. */
  private void setAfTriggerStart(CaptureRequest.Builder builder) {
    builder.set(
        CaptureRequest.CONTROL_AF_TRIGGER,
        CameraMetadata.CONTROL_AF_TRIGGER_START);
  }

  private void setCaptureSessionOrientation(CaptureRequest.Builder builder) {
    builder.set(CaptureRequest.JPEG_ORIENTATION, outputOrientation);
  }

  private void setCropRegion(CaptureRequest.Builder builder, double zoom) {
    Log.d(TAG, String.format("setCropRegion(x%.2f)", zoom));
    int width = (int) Math.floor(activeArraySize.width() / zoom);
    int left = (activeArraySize.width() - width) / 2;
    int height = (int) Math.floor(activeArraySize.height() / zoom);
    int top = (activeArraySize.height() - height) / 2;
    Log.d(TAG, String.format("crop region(left=%d, top=%d, right=%d, bottom=%d) zoom(%.2f)",
        left, top, left + width, top + height, zoom));

    builder.set(CaptureRequest.SCALER_CROP_REGION,
        new Rect(left, top, left + width, top + height));
  }

  // ===============================================================================================
  // Capture Callback
  // ===============================================================================================

  /** Camera state: Showing camera preview. */
  private static final int STATE_PREVIEW = 0;

  /** Camera state: Waiting for the focus to be locked. */
  private static final int STATE_WAITING_LOCK = 1;

  /** Camera state: Waiting for the exposure to be precapture state. */
  private static final int STATE_WAITING_PRECAPTURE = 2;

  /** Camera state: Waiting for the exposure state to be something other than precapture. */
  private static final int STATE_WAITING_NON_PRECAPTURE = 3;

  /** Camera state: Picture was taken. */
  private static final int STATE_PICTURE_TAKEN = 4;

  private static final String[] STATE_NAMES = new String[]{
      "Preview",
      "Waiting Lock",
      "Waiting Precapture Start",
      "Waiting Precapture Finish",
      "Picture Taken"
  };

  private int state = STATE_PREVIEW;
  private int lastState = state;

  /**
   * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
   */
  private final CameraCaptureSession.CaptureCallback captureCallback
      = new CameraCaptureSession.CaptureCallback() {

    /** Returns true if the result is equal to any one of the state flags. */
    private boolean resultStateIsOneOf(int resultState, int... stateFlags) {
      for (int flag : stateFlags) {
        if (resultState == flag) {
          return true;
        }
      }
      return false;
    }

    /** Returns true if the lens is in a locked position after auto-focus has been triggered. */
    private boolean lensLocked(int afState) {
      return resultStateIsOneOf(
        afState,
        CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED,
        CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED);
    }

    private boolean aeConverged(int aeState) {
      return resultStateIsOneOf(aeState, CaptureResult.CONTROL_AE_STATE_CONVERGED);
    }

    private boolean precaptureStarted(int aeState) {
      return resultStateIsOneOf(aeState,
          CaptureResult.CONTROL_AE_STATE_PRECAPTURE,
          CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED);
    }

    private boolean precaptureFinished(int aeState) {
      return !resultStateIsOneOf(aeState, CaptureResult.CONTROL_AE_STATE_PRECAPTURE);
    }

    private void process(CaptureResult result) {
      switch (state) {
        case STATE_PREVIEW: {
          enableCaptureButtons(true);
          break;
        }
        case STATE_WAITING_LOCK: {
          enableCaptureButtons(false);
          Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
          if (afState == null) {
            captureStillPicture();
          } else if (lensLocked(afState)) {
            Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
            if (aeState == null || aeConverged(aeState)) {
              state = STATE_PICTURE_TAKEN;
              captureStillPicture();
            } else {
              runPrecaptureSequence();
            }
          }
          break;
        }
        case STATE_WAITING_PRECAPTURE: {
          Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
          if (aeState == null || precaptureStarted(aeState)) {
            state = STATE_WAITING_NON_PRECAPTURE;
          }
          break;
        }
        case STATE_WAITING_NON_PRECAPTURE: {
          Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
          if (aeState == null || precaptureFinished(aeState)) {
            state = STATE_PICTURE_TAKEN;
            captureStillPicture();
          }
          break;
        }
        case STATE_PICTURE_TAKEN:
          break;
        default:
          Log.e(TAG, "Invalid state: " + state);
      }
      if (state != lastState) {
        Log.i(TAG, "last state: " + STATE_NAMES[lastState] + ", new state: " + STATE_NAMES[state]);
        lastState = state;
      }
    }

    @Override
    public void onCaptureProgressed(@NonNull CameraCaptureSession session,
        @NonNull CaptureRequest request,
        @NonNull CaptureResult partialResult) {
      process(partialResult);
    }

    @Override
    public void onCaptureCompleted(@NonNull CameraCaptureSession session,
        @NonNull CaptureRequest request,
        @NonNull TotalCaptureResult result) {
      process(result);
    }
  };

  /**
   * Capture a still picture. This method should be called when we get a response in
   * {@link #captureCallback} from both {@link #lockFocus()}.
   */
  private void captureStillPicture() {
    try {
      if (null == cameraDevice) {
        return;
      }
      captureSession.stopRepeating();
      // Wait for the preview requests to stop before issuing the still capture request
      // to avoid capturing preview frames.
      stillCapturePending = true;
    } catch (CameraAccessException e) {
      Log.w(TAG, e);
    }
  }

  /**
   *  Once request queue has been flushed (onReady), kick off the capture
   *  to avoid capturing previews.
   */
  private void startStillCapture() {
    try {
      if (null == cameraDevice) {
        return;
      }

      // HDR+: The app must target api 26+ in order to pick up the set of default
      // parameters in TEMPLATE_STILL_CAPTURE that will enable HDR+ shots.
      CaptureRequest.Builder captureBuilder =
          cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

      // HDR+: The ImageReader is configured for JPEG output. HDR+ shots are enabled when
      // the outputs consist solely of JPEG and/or YUV surfaces.
      captureBuilder.addTarget(imageReader.getSurface());

      // Use the same AE and AF modes as the preview.
      commonCaptureSessionConfig(captureBuilder);
      setCaptureSessionOrientation(captureBuilder);

      // HDR+: CaptureRequest.CONTROL_ENABLE_ZSL is set to true for HDR+ shots.
      Log.d(TAG, "non-hdr+ shot pending: " + nonHdrPlusShotPending);
      captureBuilder.set(CaptureRequest.CONTROL_ENABLE_ZSL, !nonHdrPlusShotPending);
      nonHdrPlusShotPending = false;
      Log.d(TAG, "CONTROL_ENABLE_ZSL = " + captureBuilder.get(CaptureRequest.CONTROL_ENABLE_ZSL));

      CameraCaptureSession.CaptureCallback captureCallback
          = new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureCompleted(
            @NonNull CameraCaptureSession session,
            @NonNull CaptureRequest request,
            @NonNull TotalCaptureResult result) {
          Log.d(TAG, "onCaptureCompleted, double shot pending = " + doubleShotPending);
          if (doubleShotPending) {
            doubleShotPending = false;
            nonHdrPlusShotPending = true;
            lockFocus();
          } else {
            unlockFocus();
          }
        }
      };
      captureSession.capture(captureBuilder.build(), captureCallback, null);
    } catch (CameraAccessException e) {
      Log.w(TAG, e);
    }
  }

  /**
   * Lock the focus as the first step for a still image capture.
   */
  private void lockFocus() {
    try {
      state = STATE_WAITING_LOCK;
      setAfTriggerStart(previewRequestBuilder);
      captureSession.capture(previewRequestBuilder.build(), captureCallback, backgroundHandler);
      setAfTriggerIdle(previewRequestBuilder);
    } catch (CameraAccessException e) {
      Log.w(TAG, e);
    }
  }

  /**
   * Unlock the focus. This method should be called when still image capture sequence is
   * finished.
   */
  private void unlockFocus() {
    try {
      state = STATE_PREVIEW;

      // Send a single request to cancel any AF in progress.
      setAfTriggerCancel(previewRequestBuilder);
      captureSession.capture(previewRequestBuilder.build(), captureCallback, backgroundHandler);
      setAfTriggerIdle(previewRequestBuilder);

      // After this resume a normal preview.
      captureSession.setRepeatingRequest(previewRequest, captureCallback, backgroundHandler);
    } catch (CameraAccessException e) {
      Log.w(TAG, e);
    }
  }

  /**
   * Run the precapture sequence for capturing a still image. This method should be called when
   * we get a response in {@link #captureCallback} from {@link #lockFocus()}.
   */
  private void runPrecaptureSequence() {
    try {
      state = STATE_WAITING_PRECAPTURE;

      setAePrecaptureTriggerStart(previewRequestBuilder);
      captureSession.capture(previewRequestBuilder.build(), captureCallback, backgroundHandler);
      setAePrecaptureTriggerIdle(previewRequestBuilder);
    } catch (CameraAccessException e) {
      Log.w(TAG, e);
    }
  }

  /** Called from a background thread. */
  private void enableCaptureButtons(boolean enable) {
    // Ensure all UI operations happen on the main thread.
    captureButton.post(() -> captureButton.setEnabled(enable));
    doubleShotButton.post(() -> doubleShotButton.setEnabled(enable));
  }
}
