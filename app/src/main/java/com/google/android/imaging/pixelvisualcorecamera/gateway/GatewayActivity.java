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

package com.google.android.imaging.pixelvisualcorecamera.gateway;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import com.google.android.imaging.pixelvisualcorecamera.common.AppPermissions;
import com.google.android.imaging.pixelvisualcorecamera.common.Intents;
import com.google.android.imaging.pixelvisualcorecamera.common.Preferences;

/**
 * A simple activity that acquires permissions, decides which API to use,
 * and then starts the corresponding activity.
 */
public final class GatewayActivity extends Activity {

  private static final int REQUEST_CODE = 1;

  private AppPermissions appPermissions;
  private boolean isModeApi1;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    isModeApi1 = new Preferences(this).isModeApi1();
    appPermissions = new AppPermissions(this);
    if (appPermissions.checkPermissions()) {
      startCameraActivity();
    } else {
      appPermissions.requestPermissions(REQUEST_CODE);
    }
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQUEST_CODE) {
      if (appPermissions.onRequestPermissionResult(grantResults)) {
        startCameraActivity();
      }
    }
  }

  private void startCameraActivity() {
    finish();
    startActivity(isModeApi1 ? Intents.createApi1Intent() : Intents.createApi2Intent());
  }
}
