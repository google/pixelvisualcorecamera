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

import android.content.Context;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.Toast;

/**
 * Utilities for working with Toasts.
 */
public final class Toasts {

  /* Pushes the toast down from the top of the screen. */
  private static final int MARGIN_TOP_PX = 100;
  private static final int LONG_DELAY = 3500; // 3.5 seconds
  private static final int SHORT_DELAY = 2000; // 2 seconds

  private static Toast lastToast;
  private static long lastToastEndTime;
  private static String lastMessage;

  /**
   * @param duration must be LENGTH_SHORT or LENGTH_LONG
   */
  public static void showToast(Context context, String message, int duration) {
    long currentTime = SystemClock.elapsedRealtime();
    if (currentTime < lastToastEndTime) {
      message = message + "\n" + lastMessage;
      lastToast.cancel();
    }

    Toast t = Toast.makeText(context, message, duration);
    t.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, MARGIN_TOP_PX);
    t.show();

    lastToast = t;
    lastMessage = message;
    lastToastEndTime = currentTime + (duration == Toast.LENGTH_SHORT ? SHORT_DELAY : LONG_DELAY);
  }

  private Toasts() {}
}
