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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.hardware.Camera.CameraInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;
import com.google.android.imaging.pixelvisualcorecamera.R;
import com.google.android.imaging.pixelvisualcorecamera.common.FileSystem;
import com.google.android.imaging.pixelvisualcorecamera.common.Intents;
import com.google.android.imaging.pixelvisualcorecamera.common.Preferences;
import com.google.android.imaging.pixelvisualcorecamera.common.Toasts;
import com.google.android.imaging.pixelvisualcorecamera.common.Utils;
import java.io.IOException;

/**
 *  Primary activity for an API 2 camera.
 */
public class CameraApi2Activity extends Activity {

  private static final String TAG = "PvcCamApi2";

  private static final String CAMERA_FACING_BACK = "0";
  private static final String CAMERA_FACING_FRONT = "1";
  private static final String STATE_ZOOM = "zoom";

  private String cameraId;
  private HandlerThread backgroundThread;
  private Handler backgroundHandler;
  private Camera2Controller cameraController;
  private Preferences preferences;
  private Size outputSize;
  private ScaleGestureDetector zoomScaleGestureDetector;
  private ZoomScaleGestureListener zoomScaleGestureListener;
  private boolean resumed;
  private boolean cameraAcquired;

  private final Camera2Controller.OnImageAvailableListener onImageAvailableListener =
      (image) -> FileSystem.saveImage(getApplicationContext(), image, /*isApi1*/ false);

  // ===============================================================================================
  // Activity Framework Callbacks
  // ===============================================================================================

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.d(TAG, "[onCreate]");
    preferences = new Preferences(this);
    setContentView(R.layout.camera2);
    AutoFitTextureView textureView = findViewById(R.id.camera_preview);
    Utils.setSystemUiOptionsForFullscreen(this);

    Button captureButton = findViewById(R.id.button_capture);
    captureButton.setOnClickListener(v -> cameraController.takePicture());
    Button doubleShotButton = findViewById(R.id.doubleshot_button);
    doubleShotButton.setVisibility(View.VISIBLE);
    doubleShotButton.setOnClickListener(v -> cameraController.takeDoubleShot());

    cameraController = new Camera2Controller(
        getApplicationContext(),
        getWindowManager().getDefaultDisplay().getRotation(),
        captureButton,
        doubleShotButton,
        textureView,
        onImageAvailableListener);

    initTopControls();
    configureOutputSize();

    zoomScaleGestureListener = new ZoomScaleGestureListener(
        cameraController, findViewById(R.id.zoom_level_label), STATE_ZOOM);
    zoomScaleGestureListener.restoreInstanceState(savedInstanceState);
    zoomScaleGestureDetector = new ScaleGestureDetector(this, zoomScaleGestureListener);
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    Log.d(TAG, "[onWindowFocusChanged] hasFocus: " + hasFocus);
    if (hasFocus) {
      Utils.setSystemUiOptionsForFullscreen(this);
      acquireCameraIfReady();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    Log.d(TAG, "[onResume]");
    startBackgroundThread();
    cameraController.setBackgroundHandler(backgroundHandler);
    zoomScaleGestureListener.initZoomParameters(cameraId);
    resumed = true;
    acquireCameraIfReady();
  }

  @Override
  public void onPause() {
    super.onPause();
    Log.d(TAG, "[onPause]");
    resumed = false;
    cameraController.closeCamera();
    cameraAcquired = false;
    stopBackgroundThread();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    zoomScaleGestureListener.saveInstanceState(outState);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    return zoomScaleGestureDetector.onTouchEvent(event);
  }

  /** Acquires the camera if the window has focus and the activity has been resumed. */
  private void acquireCameraIfReady() {
    if (!cameraAcquired && resumed && hasWindowFocus()) {
      try {
        cameraController.acquireCamera(cameraId, outputSize, zoomScaleGestureListener.getZoom());
        cameraAcquired = true;
      } catch (IOException e) {
        String errorMessage = "Failed to acquire camera";
        Toasts.showToast(this, errorMessage, Toast.LENGTH_LONG);
        Log.w(TAG, errorMessage, e);
        finish();
      }
    }
  }

  // ===============================================================================================
  // Top Controls
  // ===============================================================================================

  private void initTopControls() {
    initApiSwitch();
    initCameraSelection();
    setCameraIconForCurrentCamera();
    ImageButton cameraSelectionButton = findViewById(R.id.control_camera_selection);
    cameraSelectionButton.setOnClickListener(cameraSelectionOnClickListener);
  }

  @SuppressLint("SetTextI18n")
  private void initApiSwitch() {
    Button button = findViewById(R.id.api_selector);
    button.setText("API 2");
    button.setOnClickListener(v -> {
      Log.i(TAG, "switching to API 1");
      preferences.setModeApi1(true);
      finish();
      startActivity(Intents.createApi1Intent());
    });
  }

  /** Initializes cameraId state from global preferences. */
  @SuppressWarnings("deprecation")
  private void initCameraSelection() {
    int cameraIdInt = preferences.getCameraId();
    if (cameraIdInt > CameraInfo.CAMERA_FACING_FRONT) {
      Log.e(TAG, "out of bounds camera id: " + cameraIdInt);
      cameraIdInt = CameraInfo.CAMERA_FACING_BACK;
      preferences.setCameraId(cameraIdInt);
    }
    cameraId = String.valueOf(cameraIdInt);
  }

  private void setCameraIconForCurrentCamera() {
    ImageButton button = findViewById(R.id.control_camera_selection);
    switch (cameraId) {
      case CAMERA_FACING_BACK:
        button.setImageResource(R.drawable.ic_camera_rear_white_24);
        break;
      case CAMERA_FACING_FRONT:
        button.setImageResource(R.drawable.ic_camera_front_white_24);
        break;
      default:
        break;
    }
  }

  /** Handles clicks on the camera selection button. */
  private final OnClickListener cameraSelectionOnClickListener = new OnClickListener() {

    @Override
    public void onClick(View view) {
      Log.i(TAG, "changing cameras, releasing camera");
      // Swap camera ids.
      switch (cameraId) {
        case CAMERA_FACING_BACK:
          cameraId = CAMERA_FACING_FRONT;
          break;
        case CAMERA_FACING_FRONT:
          cameraId = CAMERA_FACING_BACK;
          break;
        default:
          Log.e(TAG, "unrecognized camera id: " + cameraId);
          cameraId = CAMERA_FACING_BACK;
      }
      preferences.setCameraId(Integer.valueOf(cameraId));
      setCameraIconForCurrentCamera();

      Log.i(TAG, "restarting with new camera");
      zoomScaleGestureListener.reset();
      closeAndOpenCamera();
    }
  };

  /** Call this when the camera is not acquired, to select the output size when it is acquired. */
  private void configureOutputSize() {
    Log.d(TAG, "configureOutputSize");
    Size[] sizes = cameraController.getSupportedPictureSizes(cameraId);
    outputSize = sizes[0];
  }

  /** Closes the camera and reacquires with the given outputSize. */
  private void closeAndOpenCamera() {
    Log.d(TAG, "closeAndOpenCamera");
    cameraController.closeCamera();
    cameraAcquired = false;
    configureOutputSize();
    zoomScaleGestureListener.initZoomParameters(cameraId);
    try {
      cameraController.acquireCamera(cameraId, outputSize, zoomScaleGestureListener.getZoom());
      cameraAcquired = true;
    } catch (IOException e) {
      Log.w(TAG, e);
    }
  }

  // ===============================================================================================
  // Thread
  // ===============================================================================================

  /** Starts a background thread and its {@link Handler}. */
  private void startBackgroundThread() {
    backgroundThread = new HandlerThread("CameraBackground");
    backgroundThread.start();
    backgroundHandler = new Handler(backgroundThread.getLooper());
  }

  /** Stops the background thread and its {@link Handler}. */
  private void stopBackgroundThread() {
    backgroundThread.quitSafely();
    try {
      backgroundThread.join();
      backgroundThread = null;
      backgroundHandler = null;
    } catch (InterruptedException e) {
      Log.w(TAG, "Interrupted while shutting background thread down", e);
    }
  }
}
