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

import android.app.ActionBar;
import android.app.Activity;
import android.view.View;

public final class Utils {

  /** Max preview width that is guaranteed by Camera2 API */
  public static final int MAX_PREVIEW_WIDTH = 1920;

  /** Max preview height that is guaranteed by Camera2 API */
  public static final int MAX_PREVIEW_HEIGHT = 1080;

  public static void setSystemUiOptionsForFullscreen(Activity activity) {
    View decorView = activity.getWindow().getDecorView();
    decorView.setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            // hide status bar
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_FULLSCREEN
    );

    ActionBar actionBar = activity.getActionBar();
    if (actionBar != null) {
      actionBar.hide();
    }
  }

  private Utils() {}
}
