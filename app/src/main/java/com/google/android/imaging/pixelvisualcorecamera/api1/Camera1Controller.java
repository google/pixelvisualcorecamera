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

package com.google.android.imaging.pixelvisualcorecamera.api1;

import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.FaceDetectionListener;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;
import com.google.android.imaging.pixelvisualcorecamera.common.Orientation;
import com.google.android.imaging.pixelvisualcorecamera.common.Utils;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * Manages the state of an API 1 camera.
 */
@SuppressWarnings("deprecation")
final class Camera1Controller {

  private static final String TAG = "PcvCamCon1";

  private static final int STATE_NOT_ACQUIRED = 0;
  private static final int STATE_ACQUIRED = 1;
  private static final int STATE_PREVIEW = 2;
  private static final int STATE_CAPTURE = 3;
  private static final String[] STATE_NAMES = {
      "Not acquired", "Acquired", "Preview", "Capture"
  };

  private final View captureButton;
  private Camera camera;
  private int state = STATE_NOT_ACQUIRED;
  private int cameraId;

  private final AutoFocusCallback autoFocusCb = (success, camera1) ->
      Log.d(TAG, "autofocus, success: " + success);

  private final FaceDetectionListener faceDetectionListener = (faces, camera1) ->
      Log.d(TAG, "detected " + faces.length + " faces");

  public interface CaptureCallback {

    /**
     * Called when the capture is complete. Parameters are passed through from
     * Camera.PictureCallback#onPictureTaken. The metrics recorder hosts diagnostic information
     * from the capture process.
     */
    void onPictureTaken(byte[] bytes, Camera camera);
  }

  public Camera1Controller(View captureButton) {
    this.captureButton = captureButton;
    moveToState(STATE_NOT_ACQUIRED);
  }

  // ===============================================================================================
  // Configuration
  // ===============================================================================================

  /**
   * Configures camera parameters common to all configurations.
   * Must be called before preview started.
   */
  public void setDefaultParameters(int displayRotation) {
    Log.i(TAG, "setDefaultParameters");
    assertState(STATE_ACQUIRED,
          "Default parameters may only be set before a preview is started");

    CameraInfo info = new CameraInfo();
    Camera.getCameraInfo(cameraId, info);
    boolean lensFacingFront = (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT);

    int previewOrientationDegrees =
        Orientation.getPreviewOrientation(lensFacingFront, displayRotation, info.orientation);
    camera.setDisplayOrientation(previewOrientationDegrees);
    Parameters params = camera.getParameters();

    // We happen to know the preview sizes available for Pixel 2.
    params.setPreviewSize(Utils.MAX_PREVIEW_WIDTH, Utils.MAX_PREVIEW_HEIGHT);
    params.setRotation(
        Orientation.getOutputOrientation(lensFacingFront, displayRotation, info.orientation));

    // Continuous picture is not supported Pixel 2's front camera.
    List<String> supportFocusModes = params.getSupportedFocusModes();
    if (supportFocusModes.contains(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
      Log.i(TAG, "setting continuous picture focus mode");
      params.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
    }

    // HDR+: Flash mode must be off.
    params.setFlashMode(Parameters.FLASH_MODE_OFF);

    // HDR+: Color effect must be none.
    params.setColorEffect(Parameters.EFFECT_NONE);

    // HDR+: White balance must be auto.
    params.setWhiteBalance(Parameters.WHITE_BALANCE_AUTO);

    camera.setParameters(params);
  }

  /** Set the preview display before the preview is started. */
  public void setPreviewDisplay(SurfaceHolder previewDisplay) throws IOException {
    assertState(STATE_ACQUIRED, "SurfaceHolder may only be set prior to preview");
    camera.setPreviewDisplay(previewDisplay);
  }

  // ===============================================================================================
  // Camera Control
  // ===============================================================================================

  /** Acquire the camera. Note, some parameters must be set before starting a preview. */
  public void acquireCamera(int cameraId) throws IOException {
    Log.i(TAG, "acquireCamera");
    assertState(STATE_NOT_ACQUIRED, "Attempting to acquire camera while already holding a camera");
    this.cameraId = cameraId;
    camera = Camera.open(cameraId);
    if (camera != null) {
      moveToState(STATE_ACQUIRED);
    } else {
      throw new IOException("Failed to open camera");
    }
    camera.setFaceDetectionListener(faceDetectionListener);
  }

  /** Starts the preview stream. The camera must be acquired first. */
  public void startPreview() {
    Log.i(TAG, "startPreview");
    assertState(STATE_ACQUIRED, "Preview may only be started when camera is acquired");
    camera.startPreview();
    moveToState(STATE_PREVIEW);
  }

  /** Stops the preview stream. */
  public void stopPreview() {
    Log.i(TAG, "stopPreview");
    assertNotState(STATE_NOT_ACQUIRED, "Preview may only be started when camera is acquired");
    camera.stopPreview();
    moveToState(STATE_ACQUIRED);
  }

  /**
   * Initiate still image capture. Acquire focus lock if in auto-focus mode.
   * The camera internally transitions to acquired after a capture is complete,
   * the preview is not automatically restarted. The shutterCallback must
   * call #captureComplete before restarting the preview.
   */
  public void takePicture(CaptureCallback captureCallback) {
    Log.i(TAG, "takePicture");
    assertState(STATE_PREVIEW, "Preview must be started before taking a picture");

    Camera.PictureCallback internalCallback = (bytes, cameraId) -> {
      Log.d(TAG, "takePicture: callback started");
      captureCallback.onPictureTaken(bytes, cameraId);
      captureComplete();
      Log.d(TAG, "takePicture: callback complete");
    };

    camera.takePicture(null, null, internalCallback);
    moveToState(STATE_CAPTURE);
  }

  /** Release the camera. */
  public void releaseCamera() {
    Log.i(TAG, "releaseCamera");
    assertNotState(STATE_NOT_ACQUIRED, "Attempting to release camera while not holding a camera");
    camera.release();
    camera = null;
    moveToState(STATE_NOT_ACQUIRED);
  }

  /**
   * Call this from the shutter callback after capturing an image and
   * before restarting the preview.
   */
  private void captureComplete() {
    Log.d(TAG, "captureComplete");
    moveToState(STATE_ACQUIRED);
  }

  private void startFaceDetection() {
    Log.i(TAG, "focus: face detection started");
    camera.startFaceDetection();
  }

  public int getMaxZoom() {
    assertNotState(STATE_NOT_ACQUIRED, "Camera must be acquired before querying zoom");
    return camera.getParameters().getMaxZoom();
  }

  public int[] getZoomRatios() {
    assertNotState(STATE_NOT_ACQUIRED, "Camera must be acquired before querying zoom");
    List<Integer> ratios = camera.getParameters().getZoomRatios();
    int[] zoomRatios = new int[ratios.size()];
    Iterator<Integer> it = ratios.iterator();
    for (int i = 0; i < ratios.size(); i++) {
      zoomRatios[i] = it.next();
    }
    return zoomRatios;
  }

  public void setZoom(int level) {
    assertNotState(STATE_NOT_ACQUIRED, "Camera must be acquired before modifying zoom");
    Log.d(TAG, "setZoom(" + level + ")");
    Parameters params = camera.getParameters();
    params.setZoom(level);
    camera.setParameters(params);
  }

  // ===============================================================================================
  // State Management
  // ===============================================================================================

  private void moveToState(int newState) {
    Log.i(TAG, "last state: " + STATE_NAMES[state] + ", new state: " + STATE_NAMES[newState]);
    switch(newState) {
      case STATE_NOT_ACQUIRED:
        if (captureButton != null) {
          captureButton.setEnabled(false);
        }
        break;
      case STATE_ACQUIRED:
        if (captureButton != null) {
          captureButton.setEnabled(false);
        }
        break;
      case STATE_PREVIEW:
        if (captureButton != null) {
          captureButton.setEnabled(true);
        }
        // Face detection is not required for HDR+ shots.
        startFaceDetection();

        break;
      case STATE_CAPTURE:
        if (captureButton != null) {
          captureButton.setEnabled(false);
        }
        break;
      default:
        throw new IllegalStateException("unrecognized state: " + newState);
    }
    state = newState;
  }

  private void assertState(int expectedState, String message) {
    if (state != expectedState) {
      throw new IllegalStateException(
          String.format("Current state: %d, expected: %d, %s", state, expectedState, message));
    }
  }

  private void assertNotState(int disallowedState, String message) {
    if (state == disallowedState) {
      throw new IllegalStateException(
          String.format("Current state: %d, %s", state, message));
    }
  }

  /** Returns true if the camera is currently acquired. */
  public boolean isAcquired() {
    return state != STATE_NOT_ACQUIRED;
  }

  /** Returns true if the preview is active. */
  public boolean isPreviewActive() {
    return state == STATE_PREVIEW;
  }

  public android.util.Size[] getSupportedPictureSizes() {
    assertNotState(STATE_NOT_ACQUIRED, "A camera must be acquired before fetching parameters");
    List<Size> sizes = camera.getParameters().getSupportedPictureSizes();
    android.util.Size[] supportedSizes = new android.util.Size[sizes.size()];
    for (int i = 0; i < sizes.size(); i++) {
      Size s = sizes.get(i);
      supportedSizes[i] = new android.util.Size(s.width, s.height);
    }
    return supportedSizes;
  }

  public void setPictureSize(android.util.Size size) {
    Log.i(TAG, String.format("setting picture size (%d, %d)", size.getWidth(), size.getHeight()));
    assertNotState(STATE_NOT_ACQUIRED, "A camera must be acquired before setting parameters");
    Parameters params = camera.getParameters();
    params.setPictureSize(size.getWidth(), size.getHeight());
    camera.setParameters(params);
  }
}
