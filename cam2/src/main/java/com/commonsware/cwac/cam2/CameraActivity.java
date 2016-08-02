/***
 Copyright (c) 2015-2016 CommonsWare, LLC

 Licensed under the Apache License, Version 2.0 (the "License"); you may
 not use this file except in compliance with the License. You may obtain
 a copy of the License at http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/

package com.commonsware.cwac.cam2;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.KeyEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Stock activity for taking pictures. Supports the same
 * protocol, in terms of extras and return data, as does
 * ACTION_IMAGE_CAPTURE.
 */
public class CameraActivity extends AbstractCameraActivity
    implements ConfirmationFragment.Contract {

  /**
   * Extra name for indicating whether a confirmation screen
   * should appear after taking the picture, or whether taking
   * the picture should immediately return said picture. Defaults
   * to true, meaning that the user should confirm the picture.
   */
  public static final String EXTRA_CONFIRM="cwac_cam2_confirm";

  /**
   * Extra name for whether hardware keys such as
   * ZOOM_IN, ZOOM_OUT, VOLUME_UP and VOLUME_DOWN can
   * be used to change zoom.
   */
  public static final String EXTRA_HARDWARE_ZOOM="cwac_cam2_hardware_zoom";

  /**
   * Extra name for whether a preview frame should be saved
   * to getExternalCacheDir() at the point when a picture
   * is taken. This is for debugging purposes, to compare
   * the preview frame with both the taken picture and what
   * you see on the activity's preview. It is very unlikely
   * that you will want this enabled in a production app.
   * Defaults to false.
   */
  public static final String EXTRA_DEBUG_SAVE_PREVIEW_FRAME=
    "cwac_cam2_save_preview";

  /**
   * Extra name for whether the camera should allow zoom and
   * how. Value should be a ZoomStyle (NONE, PINCH, SEEKBAR).
   * Default is NONE.
   */
  public static final String EXTRA_ZOOM_STYLE=
    "cwac_cam2_zoom_style";

  /**
   * Extra name for how much heap space we should try to use
   * to load the picture for the confirmation screen. Should
   * be a `float` greater than 0.0f and less than 1.0f.
   * Defaults to not being used.
   */
  public static final String EXTRA_CONFIRMATION_QUALITY=
    "cwac_cam2_confirmation_quality";

  /**
   * Extra name for boolean indicating if we should skip the
   * default logic to rotate the image based on the EXIF orientation
   * tag. Defaults to false (meaning: do the rotation if needed).
   */
  public static final String EXTRA_SKIP_ORIENTATION_NORMALIZATION=
    "cwac_cam2_skip_orientation_normalization";

  private static final String TAG_CONFIRM=ConfirmationFragment.class.getCanonicalName();
  private static final String[] PERMS={Manifest.permission.CAMERA};
  private ConfirmationFragment confirmFrag;
  private boolean needsThumbnail=false;

  @Override
  protected String[] getNeededPermissions() {
    return(PERMS);
  }

  @Override
  protected void init() {
    super.init();

    confirmFrag=(ConfirmationFragment)getFragmentManager().findFragmentByTag(TAG_CONFIRM);

    Uri output=getOutputUri();

    needsThumbnail=(output==null);

    if (confirmFrag==null) {
      confirmFrag=
        ConfirmationFragment
          .newInstance(normalizeOrientation());
      getFragmentManager()
          .beginTransaction()
          .add(android.R.id.content, confirmFrag, TAG_CONFIRM)
          .commit();
    }

    if (!cameraFrag.isVisible() && !confirmFrag.isVisible()) {
      getFragmentManager()
          .beginTransaction()
          .hide(confirmFrag)
          .show(cameraFrag)
          .commit();
    }
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (getIntent().getExtras().getBoolean(EXTRA_HARDWARE_ZOOM, false)) {
      if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_ZOOM_IN) {
        cameraFrag.getController().startZoom(CameraEngine.HardwareZoomDirection.ZOOM_IN);
        return true;
      }

      if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_ZOOM_OUT) {
        cameraFrag.getController().startZoom(CameraEngine.HardwareZoomDirection.ZOOM_OUT);
        return true;
      }
    }

    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    if (getIntent().getExtras().getBoolean(EXTRA_HARDWARE_ZOOM, false)) {
      if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_ZOOM_IN || keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_ZOOM_OUT) {
        cameraFrag.getController().stopZoom();
        return true;
      }
    }

    return super.onKeyUp(keyCode, event);
  }

  @SuppressWarnings("unused")
  public void onEventMainThread(CameraEngine.PictureTakenEvent event) {
    if (event.exception==null) {
      if (getIntent().getBooleanExtra(EXTRA_CONFIRM, true)) {
        confirmFrag.setImage(event.getImageContext(),
          getIntent().getExtras().getFloat(EXTRA_CONFIRMATION_QUALITY));

        getFragmentManager()
          .beginTransaction()
          .hide(cameraFrag)
          .show(confirmFrag)
          .commit();
      }
      else {
        completeRequest(event.getImageContext(), true);
      }
    }
    else {
      finish();
    }
  }

  @Override
  public void retakePicture() {
    getFragmentManager()
        .beginTransaction()
        .hide(confirmFrag)
        .show(cameraFrag)
        .commit();
  }

  @Override
  public void completeRequest(ImageContext imageContext, boolean isOK) {
    if (!isOK) {
      setResult(RESULT_CANCELED);
      finish();
    }
    else {
      if (needsThumbnail) {
        final Intent result=new Intent();

        result.putExtra("data",
          imageContext.buildResultThumbnail(normalizeOrientation()));

        findViewById(android.R.id.content).post(new Runnable() {
          @Override
          public void run() {
            setResult(RESULT_OK, result);
            removeFragments();
          }
        });
      }
      else {
        findViewById(android.R.id.content).post(new Runnable() {
          @Override
          public void run() {
            setResult(RESULT_OK, new Intent().setData(getOutputUri()));
            removeFragments();
          }
        });
      }
    }
  }

  @Override
  protected boolean needsOverlay() {
    return(true);
  }

  @Override
  protected boolean needsActionBar() {
    return(true);
  }

  @Override
  protected boolean isVideo() {
    return(false);
  }

  @Override
  protected void configEngine(CameraEngine engine) {
    if (getIntent()
      .getBooleanExtra(EXTRA_DEBUG_SAVE_PREVIEW_FRAME, false)) {
      engine
        .setDebugSavePreviewFile(new File(getExternalCacheDir(),
          "cam2-preview.jpg"));
    }

    List<FlashMode> flashModes=
      (List<FlashMode>)getIntent().getSerializableExtra(EXTRA_FLASH_MODES);

    if (flashModes==null) {
      flashModes=new ArrayList<FlashMode>();
    }

    if (flashModes!=null) {
      engine.setPreferredFlashModes(flashModes);
    }
  }

  @Override
  protected CameraFragment buildFragment() {
    return(CameraFragment.newPictureInstance(getOutputUri(),
        getIntent().getBooleanExtra(EXTRA_UPDATE_MEDIA_STORE, false),
        getIntent().getIntExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1),
        (ZoomStyle)getIntent().getSerializableExtra(EXTRA_ZOOM_STYLE),
        getIntent().getBooleanExtra(EXTRA_FACING_EXACT_MATCH, false),
        getIntent().getBooleanExtra(EXTRA_SKIP_ORIENTATION_NORMALIZATION, false)));
  }

  private void removeFragments() {
    getFragmentManager()
        .beginTransaction()
        .remove(confirmFrag)
        .remove(cameraFrag)
        .commit();
  }

  private boolean normalizeOrientation() {
    boolean result=!getIntent()
      .getBooleanExtra(EXTRA_SKIP_ORIENTATION_NORMALIZATION, false);

    return(result);
  }

  /**
   * Class to build an Intent used to start the CameraActivity.
   * Call setComponent() on the Intent if you are using your
   * own subclass of CameraActivity.
   */
  public static class IntentBuilder
    extends AbstractCameraActivity.IntentBuilder<IntentBuilder> {
    /**
     * Standard constructor. May throw a runtime exception
     * if the environment is not set up properly (see
     * validateEnvironment() on Utils).
     *
     * @param ctxt any Context will do
     */
    public IntentBuilder(Context ctxt) {
      super(ctxt, CameraActivity.class);
    }

    @Override
    public Intent buildChooserBaseIntent() {
      return(new Intent(MediaStore.ACTION_IMAGE_CAPTURE));
    }

    /**
     * Call to skip the confirmation screen, so once the user
     * takes the picture, you get control back right away.
     *
     * @return the builder, for further configuration
     */
    public IntentBuilder skipConfirm() {
      result.putExtra(EXTRA_CONFIRM, false);

      return(this);
    }

    /**
     * Call to enable use for hardware keys to control zoom
     * of the camera.
     *
     * @return the builder, for further configuration
     */
    public IntentBuilder useHardwareZoom() {
      result.putExtra(EXTRA_HARDWARE_ZOOM, true);

      return(this);
    }

    /**
     * Call to skip examining the picture for the EXIF orientation
     * tag and rotating the image if needed.
     *
     * @return the builder, for further configuration
     */
    public IntentBuilder skipOrientationNormalization() {
      result.putExtra(EXTRA_SKIP_ORIENTATION_NORMALIZATION, true);

      return(this);
    }

    public IntentBuilder debugSavePreviewFrame() {
      result.putExtra(EXTRA_DEBUG_SAVE_PREVIEW_FRAME, true);

      return(this);
    }

    /**
     * Call to configure the ZoomStyle to be used. Default
     * is NONE.
     *
     * @return the builder, for further configuration
     */
    public IntentBuilder zoomStyle(ZoomStyle zoomStyle) {
      result.putExtra(EXTRA_ZOOM_STYLE, zoomStyle);

      return(this);
    }

    /**
     * Call to set the quality factor for the confirmation screen.
     * Value should be greater than 0.0f and below 1.0f, and
     * represents the fraction of the app's heap size that we
     * should be willing to use for loading the confirmation
     * image. Defaults to not being used.
     *
     * @param quality something in (0.0f, 1.0f] range
     * @return the builder, for further configuration
     */
    public IntentBuilder confirmationQuality(float quality) {
      if (quality<=0.0f || quality>1.0f) {
        throw new IllegalArgumentException("Quality outside (0.0f, 1.0f] range!");
      }

      result.putExtra(EXTRA_CONFIRMATION_QUALITY, quality);

      return(this);
    }
  }
}
