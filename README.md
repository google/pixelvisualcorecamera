# Pixel Visual Core Camera

The Pixel Visual Core Camera application was designed to provide Android
developers with a simple example on how to enable Pixel Visual Core in their
camera applications to accelerate HDR+ processing using Camera API 1 and 2.

## Device Requirements

Pixel Visual Core is available in Google Pixel 2 and Pixel 3 phones.

## Software Requirements

Applications should target API Level 26 (or greater) to get access to Pixel
Visual Core functionality. Pixel Visual Core has been available to developers
since Android Oreo build OPM1.171019.011, as a developer option. Pixel Visual Core
is officially enabled by default starting from Android Oreo build
OPM1.171019.019.

## How to enable HDR+ with Pixel Visual Core

### Camera API 1

-   `takePicture()` uses Pixel Visual Core when the following settings are applied:
    -   Effect mode set to `EFFECT_NONE`.
    -   Flash mode set to `FLASH_MODE_OFF`.
    -   While balance set to `WHITE_BALANCE_AUTO`.
    -   No exposure compensation.

### Camera API 2

-   Pixel Visual Core is enabled only for `TEMPLATE_STILL_CAPTURE` requests.
-   `CONTROL_ENABLE_ZSL` shall be set to true.
-   Pixel Visual Core is enabled for capture requests that only include JPEG and
    YUV outputs.

## Pixel Visual Core Camera Features

-   Pixel Visual Core HDR+ processing with Camera API 1 and 2.
-   Double shot, take two shots back to back:
    - The first shot is HDR+, processed with Pixel Visual Core.
    - The second shot uses default processing.
-   Zoom control.
-   Front and back camera support.

This is not an officially supported Google product.

