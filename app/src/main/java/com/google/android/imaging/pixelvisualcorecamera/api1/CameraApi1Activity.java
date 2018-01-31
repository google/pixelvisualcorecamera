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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;
import com.google.android.imaging.pixelvisualcorecamera.R;
import com.google.android.imaging.pixelvisualcorecamera.api1.Camera1Controller.CaptureCallback;
import com.google.android.imaging.pixelvisualcorecamera.common.FileSystem;
import com.google.android.imaging.pixelvisualcorecamera.common.Intents;
import com.google.android.imaging.pixelvisualcorecamera.common.Preferences;
import com.google.android.imaging.pixelvisualcorecamera.common.Toasts;
import com.google.android.imaging.pixelvisualcorecamera.common.Utils;
import java.io.IOException;

/**
 * Primary activity for an API 1 camera.
 */
@SuppressWarnings("deprecation")
public final class CameraApi1Activity extends Activity {

  private static final String TAG = "PvcCamApi1";
  private static final String STATE_ZOOM = "zoom";

  private Camera1Controller cameraController;
  private CameraPreview mPreview;
  private Preferences preferences;

  private int cameraId;
  private ScaleGestureDetector zoomScaleGestureDetector;
  private ZoomScaleGestureListener zoomScaleGestureListener;

  // ===============================================================================================
  // Activity Framework Callbacks
  // ===============================================================================================

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.d(TAG, "[onCreate]");
    preferences = new Preferences(this);
    setContentView(R.layout.camera1);
    Utils.setSystemUiOptionsForFullscreen(this);
    Button captureButton = findViewById(R.id.button_capture);
    captureButton.setOnClickListener(v -> cameraController.takePicture(captureCallback));

    cameraController = new Camera1Controller(captureButton);

    zoomScaleGestureListener = new ZoomScaleGestureListener(
        cameraController, findViewById(R.id.zoom_level_label), STATE_ZOOM);
    zoomScaleGestureListener.restoreInstanceState(savedInstanceState);
    zoomScaleGestureDetector = new ScaleGestureDetector(this, zoomScaleGestureListener);

    initTopControls();
  }

  /** Configure the system UI elements when we receive focus. */
  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    Log.d(TAG, "[onWindowFocusChanged] hasFocus = " + hasFocus);
    if (hasFocus) {
      Utils.setSystemUiOptionsForFullscreen(this);
    }
  }

  /** Acquire the camera and start the preview. */
  @Override
  public void onResume() {
    super.onResume();
    Log.d(TAG, "[onResume]");
    try {
      cameraController.acquireCamera(cameraId);
      configureOutputSize();
      cameraController.setDefaultParameters(getWindowManager().getDefaultDisplay().getRotation());
      zoomScaleGestureListener.initZoomParameters();
      cameraController.setZoom(zoomScaleGestureListener.getZoom());
      configurePreview();
    } catch (IOException e) {
      String errorMessage = "Failed to acquire camera";
      Toasts.showToast(this, errorMessage, Toast.LENGTH_LONG);
      Log.w(TAG, errorMessage, e);
      finish();
    }
  }

  /** Release the camera. */
  @Override
  protected void onPause() {
    super.onPause();
    Log.d(TAG, "[onPause]");
    if (cameraController.isAcquired()) {
      cameraController.releaseCamera();
    }
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

  // ===============================================================================================
  // UI Management
  // ===============================================================================================

  private void configurePreview() {
    if (mPreview == null) {
      FrameLayout preview = findViewById(R.id.camera_preview);
      mPreview = new CameraPreview(this, cameraController);
      preview.addView(mPreview);
    } else {
      mPreview.reset();
    }
  }

  // ===============================================================================================
  // Capture Callback
  // ===============================================================================================

  private final CaptureCallback captureCallback = new CaptureCallback() {
    @Override
    public void onPictureTaken(byte[] bytes, Camera camera) {
      FileSystem.saveImage(CameraApi1Activity.this, bytes, /*isApi1=*/ true);
      // Post this on the UI thread to allow the controller state machine to complete it's
      // transitions.
      mPreview.post(() -> mPreview.reset());
    }
  };

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
    button.setText("API 1");
    button.setOnClickListener(v -> {
      Log.i(TAG, "switching to API 2");
      preferences.setModeApi1(false);
      finish();
      startActivity(Intents.createApi2Intent());
    });
  }

  /** Initializes cameraId state from global preferences. */
  private void initCameraSelection() {
    cameraId = preferences.getCameraId();
    if (cameraId > CameraInfo.CAMERA_FACING_FRONT) {
      Log.e(TAG, "out of bounds camera id: " + cameraId);
      cameraId = CameraInfo.CAMERA_FACING_BACK;
      preferences.setCameraId(cameraId);
    }
  }

  private void setCameraIconForCurrentCamera() {
    ImageButton button = findViewById(R.id.control_camera_selection);
    switch (cameraId) {
      case CameraInfo.CAMERA_FACING_BACK:
        button.setImageResource(R.drawable.ic_camera_rear_white_24);
        break;
      case CameraInfo.CAMERA_FACING_FRONT:
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
      if (cameraController.isAcquired()) {
        Log.i(TAG, "changing cameras, releasing camera");
        cameraController.releaseCamera();
      }

      // Swap camera ids.
      switch (cameraId) {
        case CameraInfo.CAMERA_FACING_BACK:
          cameraId = CameraInfo.CAMERA_FACING_FRONT;
          break;
        case CameraInfo.CAMERA_FACING_FRONT:
          cameraId = CameraInfo.CAMERA_FACING_BACK;
          break;
        default:
          Log.e(TAG, "out of bounds camera id: " + cameraId);
          cameraId = CameraInfo.CAMERA_FACING_BACK;
      }
      preferences.setCameraId(cameraId);
      setCameraIconForCurrentCamera();
      zoomScaleGestureListener.reset();

      Log.i(TAG, "restarting with new camera");
      try {
        cameraController.acquireCamera(cameraId);
        configureOutputSize();
        cameraController.setDefaultParameters(getWindowManager().getDefaultDisplay().getRotation());
        zoomScaleGestureListener.initZoomParameters();
        cameraController.setZoom(zoomScaleGestureListener.getZoom());
        configurePreview();
      }  catch (IOException e) {
        Log.w(TAG, "failed to acquire camera", e);
      }
    }
  };

  /** Call this after a camera has been acquired. */
  private void configureOutputSize() {
    Log.d(TAG, "configureOutputSize");
    android.util.Size[] supportedSizes = cameraController.getSupportedPictureSizes();
    cameraController.setPictureSize(supportedSizes[0]);
  }
}
