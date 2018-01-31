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

package com.google.android.imaging.pixelvisualcorecamera.common;

import android.util.Log;
import android.view.Surface;

/**
 * Utility for working with device and camera orientations.
 */
public final class Orientation {

  private static final String TAG = "PvcCamOrient";

  /** Returns the camera (jpeg) output orientation. */
  public static int getOutputOrientation(
      boolean lensFacingFront, int displayRotationCode, int sensorOrientationDegrees) {

    int degrees = convertRotationToDegrees(displayRotationCode);
    Log.d(TAG, "display rotation = " + degrees + "°, "
        + "camera orientation = " + sensorOrientationDegrees + "°");
    int result;
    if (lensFacingFront){
      result = (sensorOrientationDegrees + degrees) % 360;
    } else {
      result = (sensorOrientationDegrees - degrees + 360) % 360;
    }

    Log.i(TAG, "output orientation -> " + result + "°");
    return result;
  }

  /**
   * Returns the orientation for the camera preview. Only used in api 1.
   * This is managed automatically by api 2.
   */
  public static int getPreviewOrientation(
      boolean lensFacingFront, int displayRotationCode, int sensorOrientation) {

    int degrees = convertRotationToDegrees(displayRotationCode);
    int result;
    if (lensFacingFront){
      result = (sensorOrientation + degrees) % 360;
      result = (360 - result) % 360;
    } else {
      result = (sensorOrientation - degrees + 360) % 360;
    }

    Log.i(TAG, "preview orientation -> " + result + "°");
    return result;
  }

  private static int convertRotationToDegrees(int rotationCode) {
    int degrees;
    switch (rotationCode) {
      case Surface.ROTATION_0:
        degrees = 0;
        break;

      case Surface.ROTATION_90:
        degrees = 90;
        break;

      case Surface.ROTATION_180:
        degrees = 180;
        break;

      case Surface.ROTATION_270:
        degrees = 270;
        break;
      default:
        throw new IllegalStateException("Invalid rotation code: " + rotationCode);
    }
    return degrees;
  }

  private Orientation() {}
}
