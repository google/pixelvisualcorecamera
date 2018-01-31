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

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest.permission;
import android.app.Activity;
import android.support.v4.content.ContextCompat;
import android.widget.Toast;

/**
 * Utility for interacting with the permission system.
 */
public final class AppPermissions {

  private final Activity activity;

  public AppPermissions(Activity activity) {
    this.activity = activity;
  }

  /** Returns true if the app has all needed permissions. */
  public boolean checkPermissions() {
    int cameraPermission = ContextCompat.checkSelfPermission(activity, permission.CAMERA);
    int writeExternalStoragePermission =
        ContextCompat.checkSelfPermission(activity, permission.WRITE_EXTERNAL_STORAGE);
    return cameraPermission == PERMISSION_GRANTED
        && writeExternalStoragePermission == PERMISSION_GRANTED;
  }

  /** Request permissions from the user. */
  public void requestPermissions(int requestCode) {
    activity.requestPermissions(
        new String[]{permission.CAMERA, permission.WRITE_EXTERNAL_STORAGE},
        requestCode);
  }

  /**
   * Call this from the activity's onRequestPermissionResult for the previously
   * given requestCode. Returns true if the permissions have been granted.
   * Terminates the activity and shows usage if the permissions were not granted.
   */
  public boolean onRequestPermissionResult(int[] grantResults) {
    boolean hasPermissions = grantResults.length > 1
        && grantResults[0] == PERMISSION_GRANTED
        && grantResults[1] == PERMISSION_GRANTED;
    if (!hasPermissions) {
      Toasts.showToast(
          activity,
          "Camera and Storage permissions are required to use the app",
          Toast.LENGTH_LONG);
      activity.finish();
    }
    return hasPermissions;
  }
}
