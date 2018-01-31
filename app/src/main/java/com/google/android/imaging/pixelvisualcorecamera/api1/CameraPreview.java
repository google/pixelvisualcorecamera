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

import android.content.Context;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import java.io.IOException;

/**
 * API1 Preview View.
 */
public final class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

  private static final String TAG = "PvcCamPreview";

  private final SurfaceHolder mHolder;
  private final Camera1Controller controller;
  private boolean surfaceValid;

  public CameraPreview(Context context, Camera1Controller controller) {
    super(context);
    this.controller = controller;

    // Install a SurfaceHolder.Callback so we get notified when the
    // underlying surface is created and destroyed.
    mHolder = getHolder();
    mHolder.addCallback(this);
  }

  /** Stop the preview if it is running. Then start the preview. */
  public void reset() {
    if (controller.isPreviewActive()) {
      try {
        controller.stopPreview();
      } catch (Exception e) {
        Log.w(TAG, "tried to stopPreview", e);
      }
    }

    if (surfaceValid) {
      try {
        Log.i(TAG, "reset() starting preview");
        controller.setPreviewDisplay(mHolder);
        controller.startPreview();
      } catch (Exception e) {
        Log.w(TAG, "Error starting camera preview: ", e);
      }
    }
  }

  /**
   * The preview is started on surfaceChanged(), which is always called after surfaceCreated().
   */
  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    Log.d(TAG, "surfaceCreated");
    surfaceValid = true;
    try {
      controller.setPreviewDisplay(holder);
    } catch (IOException e) {
      Log.d(TAG, "Error setting camera preview", e);
    }
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
    Log.d(TAG, "surfaceChanged");
    if (mHolder.getSurface() == null){
      Log.w(TAG, "surfaceChanged, but surface doesn't exist!");
      return;
    }

    // Stop the preview if active.
    if (controller.isPreviewActive()) {
      try {
        controller.stopPreview();
      } catch (Exception e) {
        Log.w(TAG, "caught exception stopping preview", e);
      }
    }

    // Start preview with new settings.
    try {
      Log.i(TAG, "surfaceChanged() starting preview");
      controller.startPreview();
    } catch (Exception e){
      Log.i(TAG, "Error starting camera preview: ", e);
    }
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    Log.d(TAG, "surfaceDestroyed");
    surfaceValid = false;
  }
}