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

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/** App wide preferences. */
public final class Preferences {

  private static final String TAG = "PvcCamPref";

  private static final String PREFERENCE_FILENAME = "com.google.android.imaging.pixelvisualcorecamera.PREFS";
  private static final String PREF_BOOL_API1 = "api1";
  private static final boolean PREF_BOOL_API1_DEFAULT = false;
  private static final String PREF_INT_CAMERA = "camera_id";
  private static final int PREF_INT_CAMERA_DEFAULT = 0;

  private final Context context;

  public Preferences(Context context) {
    this.context = context;
  }

  public boolean isModeApi1() {
    return getBoolean(PREF_BOOL_API1, PREF_BOOL_API1_DEFAULT);
  }

  public void setModeApi1(boolean isApi1) {
    setBoolean(PREF_BOOL_API1, isApi1);
  }

  public int getCameraId() {
    return getInt(PREF_INT_CAMERA, PREF_INT_CAMERA_DEFAULT);
  }

  public void setCameraId(int cameraId) {
    setInt(PREF_INT_CAMERA, cameraId);
  }

  private boolean getBoolean(String boolKeyName, boolean defaultValue) {
    SharedPreferences pref = context.getSharedPreferences(PREFERENCE_FILENAME, MODE_PRIVATE);
    boolean temp = pref.getBoolean(boolKeyName, defaultValue);
    Log.i(TAG, "pref " + boolKeyName + " -> " + temp);
    return temp;
  }

  private void setBoolean(String boolKeyName, boolean value) {
    SharedPreferences pref = context.getSharedPreferences(PREFERENCE_FILENAME, MODE_PRIVATE);
    pref.edit().putBoolean(boolKeyName, value).apply();
    Log.i(TAG, "pref " + boolKeyName + " <- " + value);
  }

  private int getInt(String intKeyName, int defaultValue) {
    SharedPreferences pref = context.getSharedPreferences(PREFERENCE_FILENAME, MODE_PRIVATE);
    return pref.getInt(intKeyName, defaultValue);
  }

  private void setInt(String intKeyName, int value) {
    SharedPreferences pref = context.getSharedPreferences(PREFERENCE_FILENAME, MODE_PRIVATE);
    pref.edit().putInt(intKeyName, value).apply();
  }
}
