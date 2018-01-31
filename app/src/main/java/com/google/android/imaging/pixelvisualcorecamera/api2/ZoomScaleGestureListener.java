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

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener;
import android.view.View;
import android.widget.TextView;
import java.util.Locale;

/** API 2 zoom controller. Changes the zoom in response to scale events. */
public final class ZoomScaleGestureListener extends SimpleOnScaleGestureListener {

  // Adjusts the sensitivity of the scale gesture. Device pixels per x1.00 zoom factor.
  private static final int DP_PER_ZOOM_FACTOR = 100;
  private static final double DEFAULT_ZOOM = 1.0;

  private final Camera2Controller controller;
  private final TextView label;
  private final String stateKey;
  private double maxZoom;
  private double zoomLevel;
  private float startingSpan;
  private double intermediateZoomLevel;

  /**
   * @param label the TextView to update as the zoom changes
   * @param stateKey the bundle key with which to store/retrieve the state
   */
  ZoomScaleGestureListener(
      @NonNull Camera2Controller controller,
      @NonNull TextView label,
      String stateKey) {
    if (TextUtils.isEmpty(stateKey)) {
      throw new IllegalArgumentException("A non-empty state key is required");
    }
    this.controller = controller;
    this.label = label;
    this.stateKey = stateKey;
  }

  /** Reset the state when swapping cameras. */
  public void reset() {
    zoomLevel = DEFAULT_ZOOM;
  }

  public double getZoom() {
    return zoomLevel;
  }

  /** Must be called while the camera is acquired. */
  void initZoomParameters(String cameraId) {
    maxZoom = controller.getMaxZoom(cameraId);
  }

  public void restoreInstanceState(Bundle savedInstanceState) {
    zoomLevel = (savedInstanceState != null)
        ? savedInstanceState.getDouble(stateKey, DEFAULT_ZOOM) : DEFAULT_ZOOM;
  }

  public void saveInstanceState(Bundle outState) {
    outState.putDouble(stateKey, zoomLevel);
  }

  private String formatZoomLabel(double zoomLevel) {
    return String.format(Locale.US, "x%.2f", zoomLevel);
  }

  @Override
  public boolean onScaleBegin(ScaleGestureDetector detector) {
    label.setText(formatZoomLabel(zoomLevel));
    label.setVisibility(View.VISIBLE);
    startingSpan = detector.getCurrentSpan();
    return true;
  }

  @Override
  public boolean onScale(ScaleGestureDetector detector) {
    float currentSpan = detector.getCurrentSpan();
    double distanceChange = currentSpan - startingSpan;
    double zoomLevelChange = (distanceChange / DP_PER_ZOOM_FACTOR);

    // Clamp the zoom level to valid intervals.
    intermediateZoomLevel = Math.min(
        Math.max(zoomLevel + zoomLevelChange, DEFAULT_ZOOM),
        maxZoom);
    controller.setZoom(intermediateZoomLevel);
    label.setText(formatZoomLabel(intermediateZoomLevel));

    return true;
  }

  @Override
  public void onScaleEnd(ScaleGestureDetector detector) {
    zoomLevel = intermediateZoomLevel;
    label.setVisibility(View.GONE);
  }
}