/*
 * Copyright (c) 2014-present, Facebook, Inc. All rights reserved.
 *
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Facebook.
 *
 * As with any software that integrates with the Facebook platform, your use of
 * this software is subject to the Facebook Developer Principles and Policies
 * [http://developers.facebook.com/policy/]. This copyright notice shall be
 * included in all copies or substantial portions of the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.facebook.internal;

import static com.facebook.FacebookSdk.WEB_DIALOG_THEME;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.facebook.AccessToken;
import com.facebook.FacebookDialogException;
import com.facebook.FacebookException;
import com.facebook.FacebookGraphResponseException;
import com.facebook.FacebookOperationCanceledException;
import com.facebook.FacebookRequestError;
import com.facebook.FacebookSdk;
import com.facebook.FacebookServiceException;
import com.facebook.GraphRequest;
import com.facebook.GraphRequestAsyncTask;
import com.facebook.GraphResponse;
import com.facebook.common.R;
import com.facebook.internal.qualityvalidation.Excuse;
import com.facebook.internal.qualityvalidation.ExcusesForDesignViolations;
import com.facebook.login.LoginTargetApp;
import com.facebook.share.internal.ShareConstants;
import com.facebook.share.internal.ShareInternalUtility;
import com.facebook.share.widget.ShareDialog;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * com.facebook.internal is solely for the use of other packages within the Facebook SDK for
 * Android. Use of any of the classes in this package is unsupported, and they may be modified or
 * removed without warning at any time.
 *
 * <p>This class provides a mechanism for displaying Facebook Web dialogs inside a Dialog. Helper
 * methods are provided to construct commonly-used dialogs, or a caller can specify arbitrary
 * parameters to call other dialogs.
 */
@ExcusesForDesignViolations(@Excuse(type = "MISSING_UNIT_TEST", reason = "Legacy"))
public class WebDialog extends Dialog {

  private static final String LOG_TAG = Logger.LOG_TAG_BASE + "WebDialog";

  private static final String DISPLAY_TOUCH = "touch";
  private static final String PLATFORM_DIALOG_PATH_REGEX = "^/(v\\d+\\.\\d+/)??dialog/.*";
  private static final int API_EC_DIALOG_CANCEL = 4201;
  static final boolean DISABLE_SSL_CHECK_FOR_TESTING = false;

  // width below which there are no extra margins
  private static final int NO_PADDING_SCREEN_WIDTH = 480;
  // width beyond which we're always using the MIN_SCALE_FACTOR
  private static final int MAX_PADDING_SCREEN_WIDTH = 800;
  // height below which there are no extra margins
  private static final int NO_PADDING_SCREEN_HEIGHT = 800;
  // height beyond which we're always using the MIN_SCALE_FACTOR
  private static final int MAX_PADDING_SCREEN_HEIGHT = 1280;

  // the minimum scaling factor for the web dialog (50% of screen size)
  private static final double MIN_SCALE_FACTOR = 0.5;
  // translucent border around the webview
  private static final int BACKGROUND_GRAY = 0xCC000000;

  private static final int DEFAULT_THEME = R.style.com_facebook_activity_theme;

  private String url;
  private String expectedRedirectUrl = ServerProtocol.DIALOG_REDIRECT_URI;
  private OnCompleteListener onCompleteListener;
  private WebView webView;
  private ProgressDialog spinner;
  private ImageView crossImageView;
  private FrameLayout contentFrameLayout;
  private UploadStagingResourcesTask uploadTask;
  private boolean listenerCalled = false;
  private boolean isDetached = false;
  private boolean isPageFinished = false;
  private static volatile int webDialogTheme;
  private static InitCallback initCallback;

  // Used to work around an Android Autofill bug - see Utility.mustFixWindowParamsForAutofill
  private WindowManager.LayoutParams windowParams;

  public interface InitCallback {
    public void onInit(WebView webView);
  }

  protected static void initDefaultTheme(Context context) {
    if (context == null) {
      return;
    }

    ApplicationInfo ai;
    try {
      ai =
          context
              .getPackageManager()
              .getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
    } catch (PackageManager.NameNotFoundException e) {
      return;
    }

    if (ai == null || ai.metaData == null) {
      return;
    }

    if (webDialogTheme == 0) {
      setWebDialogTheme(ai.metaData.getInt(WEB_DIALOG_THEME));
    }
  }

  public static WebDialog newInstance(
      Context context, String action, Bundle parameters, int theme, OnCompleteListener listener) {
    initDefaultTheme(context);

    return new WebDialog(context, action, parameters, theme, LoginTargetApp.FACEBOOK, listener);
  }

  public static WebDialog newInstance(
      Context context,
      String action,
      Bundle parameters,
      int theme,
      LoginTargetApp targetApp,
      OnCompleteListener listener) {
    initDefaultTheme(context);

    return new WebDialog(context, action, parameters, theme, targetApp, listener);
  }

  /**
   * Gets the theme used by {@link com.facebook.internal.WebDialog}
   *
   * @return the theme
   */
  public static int getWebDialogTheme() {
    Validate.sdkInitialized();
    return webDialogTheme;
  }

  /**
   * Sets the theme used by {@link com.facebook.internal.WebDialog}
   *
   * @param theme A theme to use
   */
  public static void setWebDialogTheme(int theme) {
    webDialogTheme = (theme != 0) ? theme : DEFAULT_THEME;
  }

  /**
   * Interface that implements a listener to be called when the user's interaction with the dialog
   * completes, whether because the dialog finished successfully, or it was cancelled, or an error
   * was encountered.
   */
  public interface OnCompleteListener {
    /**
     * Called when the dialog completes.
     *
     * @param values on success, contains the values returned by the dialog
     * @param error on an error, contains an exception describing the error
     */
    void onComplete(Bundle values, FacebookException error);
  }

  /**
   * Constructor which can be used to display a dialog with an already-constructed URL.
   *
   * @param context the context to use to display the dialog
   * @param url the URL of the Web Dialog to display; no validation is done on this URL, but it
   *     should be a valid URL pointing to a Facebook Web Dialog
   */
  protected WebDialog(Context context, String url) {
    this(context, url, getWebDialogTheme());
  }

  /**
   * Constructor which can be used to display a dialog with an already-constructed URL and a custom
   * theme.
   *
   * @param context the context to use to display the dialog
   * @param url the URL of the Web Dialog to display; no validation is done on this URL, but it
   *     should be a valid URL pointing to a Facebook Web Dialog
   * @param theme identifier of a theme to pass to the Dialog class
   */
  private WebDialog(Context context, String url, int theme) {
    super(context, theme == 0 ? getWebDialogTheme() : theme);
    this.url = url;
  }

  /**
   * Constructor which will construct the URL of the Web dialog based on the specified parameters.
   *
   * @param context the context to use to display the dialog
   * @param action the portion of the dialog URL following "dialog/"
   * @param parameters parameters which will be included as part of the URL
   * @param theme identifier of a theme to pass to the Dialog class
   * @param listener the listener to notify, or null if no notification is desired
   */
  private WebDialog(
      Context context, String action, Bundle parameters, int theme, OnCompleteListener listener) {
    this(context, action, parameters, theme, LoginTargetApp.FACEBOOK, listener);
  }

  /**
   * Constructor which will construct the URL of the Web dialog based on the specified parameters.
   *
   * @param context the context to use to display the dialog
   * @param action the portion of the dialog URL following "dialog/"
   * @param parameters parameters which will be included as part of the URL
   * @param theme identifier of a theme to pass to the Dialog class
   * @param targetApp the target app associated with the oauth dialog
   * @param listener the listener to notify, or null if no notification is desired
   */
  private WebDialog(
      Context context,
      String action,
      Bundle parameters,
      int theme,
      LoginTargetApp targetApp,
      OnCompleteListener listener) {
    super(context, theme == 0 ? getWebDialogTheme() : theme);

    if (parameters == null) {
      parameters = new Bundle();
    }

    final boolean isChromeOS = Utility.isChromeOS(context);

    this.expectedRedirectUrl =
        isChromeOS
            ? ServerProtocol.DIALOG_REDIRECT_CHROME_OS_URI
            : ServerProtocol.DIALOG_REDIRECT_URI;

    // our webview client only handles the redirect uri we specify, so just hard code it here
    parameters.putString(ServerProtocol.DIALOG_PARAM_REDIRECT_URI, this.expectedRedirectUrl);

    parameters.putString(ServerProtocol.DIALOG_PARAM_DISPLAY, DISPLAY_TOUCH);

    parameters.putString(ServerProtocol.DIALOG_PARAM_CLIENT_ID, FacebookSdk.getApplicationId());

    parameters.putString(
        ServerProtocol.DIALOG_PARAM_SDK_VERSION,
        String.format(Locale.ROOT, "android-%s", FacebookSdk.getSdkVersion()));

    onCompleteListener = listener;

    if (action.equals(ShareDialog.WEB_SHARE_DIALOG)
        && parameters.containsKey(ShareConstants.WEB_DIALOG_PARAM_MEDIA)) {
      this.uploadTask = new UploadStagingResourcesTask(action, parameters);
    } else {
      Uri uri;
      switch (targetApp) {
        case INSTAGRAM:
          uri =
              Utility.buildUri(
                  ServerProtocol.getInstagramDialogAuthority(),
                  ServerProtocol.INSTAGRAM_OAUTH_PATH,
                  parameters);
          break;
        default:
          uri =
              Utility.buildUri(
                  ServerProtocol.getDialogAuthority(),
                  FacebookSdk.getGraphApiVersion() + "/" + ServerProtocol.DIALOG_PATH + action,
                  parameters);
      }
      this.url = uri.toString();
    }
  }

  /**
   * Sets the listener which will be notified when the dialog finishes.
   *
   * @param listener the listener to notify, or null if no notification is desired
   */
  public void setOnCompleteListener(OnCompleteListener listener) {
    onCompleteListener = listener;
  }

  /**
   * Gets the listener which will be notified when the dialog finishes.
   *
   * @return the listener, or null if none has been specified
   */
  public OnCompleteListener getOnCompleteListener() {
    return onCompleteListener;
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      if (webView != null && webView.canGoBack()) {
        webView.goBack();
        return true;
      } else {
        cancel();
      }
    }

    return super.onKeyDown(keyCode, event);
  }

  @Override
  public void dismiss() {
    if (webView != null) {
      webView.stopLoading();
    }
    if (!isDetached) {
      if (spinner != null && spinner.isShowing()) {
        spinner.dismiss();
      }
    }
    super.dismiss();
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (uploadTask != null && uploadTask.getStatus() == AsyncTask.Status.PENDING) {
      uploadTask.execute();
      spinner.show();
    } else {
      resize();
    }
  }

  @Override
  protected void onStop() {
    if (uploadTask != null) {
      uploadTask.cancel(true);
      spinner.dismiss();
    }
    super.onStop();
  }

  @Override
  public void onDetachedFromWindow() {
    isDetached = true;
    super.onDetachedFromWindow();
  }

  @Override
  public void onAttachedToWindow() {
    isDetached = false;

    if (Utility.mustFixWindowParamsForAutofill(getContext())
        && windowParams != null
        && windowParams.token == null) {
      windowParams.token = getOwnerActivity().getWindow().getAttributes().token;
      Utility.logd(LOG_TAG, "Set token on onAttachedToWindow(): " + windowParams.token);
    }

    super.onAttachedToWindow();
  }

  @Override
  public void onWindowAttributesChanged(WindowManager.LayoutParams params) {
    if (params.token == null) {
      // Always store the last params, so the token can be updated when the dialog is
      // attached to the window.
      windowParams = params;
    }

    super.onWindowAttributesChanged(params);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    spinner = new ProgressDialog(getContext());
    spinner.requestWindowFeature(Window.FEATURE_NO_TITLE);
    spinner.setMessage(getContext().getString(R.string.com_facebook_loading));
    // Stops people from accidently cancelling the login flow
    spinner.setCanceledOnTouchOutside(false);
    spinner.setOnCancelListener(
        new OnCancelListener() {
          @Override
          public void onCancel(DialogInterface dialogInterface) {
            cancel();
          }
        });

    requestWindowFeature(Window.FEATURE_NO_TITLE);
    contentFrameLayout = new FrameLayout(getContext());

    // First calculate how big the frame layout should be
    resize();
    getWindow().setGravity(Gravity.CENTER);

    // resize the dialog if the soft keyboard comes up
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

    /* Create the 'x' image, but don't add to the contentFrameLayout layout yet
     * at this point, we only need to know its drawable width and height
     * to place the webview
     */
    createCrossImage();

    if (this.url != null) {
      /* Now we know 'x' drawable width and height,
       * layout the webview and add it the contentFrameLayout layout
       */
      int crossWidth = crossImageView.getDrawable().getIntrinsicWidth();
      setUpWebView(crossWidth / 2 + 1);
    }

    /* Finally add the 'x' image to the contentFrameLayout layout and
     * add contentFrameLayout to the Dialog view
     */
    contentFrameLayout.addView(
        crossImageView,
        new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    setContentView(contentFrameLayout);
  }

  protected void setExpectedRedirectUrl(String expectedRedirectUrl) {
    this.expectedRedirectUrl = expectedRedirectUrl;
  }

  protected Bundle parseResponseUri(String urlString) {
    Uri u = Uri.parse(urlString);

    Bundle b = Utility.parseUrlQueryString(u.getQuery());
    b.putAll(Utility.parseUrlQueryString(u.getFragment()));

    return b;
  }

  protected boolean isListenerCalled() {
    return listenerCalled;
  }

  protected boolean isPageFinished() {
    return isPageFinished;
  }

  protected WebView getWebView() {
    return webView;
  }

  public void resize() {
    WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
    Display display = wm.getDefaultDisplay();
    DisplayMetrics metrics = new DisplayMetrics();
    display.getMetrics(metrics);
    // always use the portrait dimensions to do the scaling calculations so we always get a portrait
    // shaped
    // web dialog
    int width =
        metrics.widthPixels < metrics.heightPixels ? metrics.widthPixels : metrics.heightPixels;
    int height =
        metrics.widthPixels < metrics.heightPixels ? metrics.heightPixels : metrics.widthPixels;

    int dialogWidth =
        Math.min(
            getScaledSize(
                width, metrics.density, NO_PADDING_SCREEN_WIDTH, MAX_PADDING_SCREEN_WIDTH),
            metrics.widthPixels);
    int dialogHeight =
        Math.min(
            getScaledSize(
                height, metrics.density, NO_PADDING_SCREEN_HEIGHT, MAX_PADDING_SCREEN_HEIGHT),
            metrics.heightPixels);

    getWindow().setLayout(dialogWidth, dialogHeight);
  }

  /**
   * Returns a scaled size (either width or height) based on the parameters passed.
   *
   * @param screenSize a pixel dimension of the screen (either width or height)
   * @param density density of the screen
   * @param noPaddingSize the size at which there's no padding for the dialog
   * @param maxPaddingSize the size at which to apply maximum padding for the dialog
   * @return a scaled size.
   */
  private int getScaledSize(int screenSize, float density, int noPaddingSize, int maxPaddingSize) {
    int scaledSize = (int) ((float) screenSize / density);
    double scaleFactor;
    if (scaledSize <= noPaddingSize) {
      scaleFactor = 1.0;
    } else if (scaledSize >= maxPaddingSize) {
      scaleFactor = MIN_SCALE_FACTOR;
    } else {
      // between the noPadding and maxPadding widths, we take a linear reduction to go from 100%
      // of screen size down to MIN_SCALE_FACTOR
      scaleFactor =
          MIN_SCALE_FACTOR
              + ((double) (maxPaddingSize - scaledSize))
                  / ((double) (maxPaddingSize - noPaddingSize))
                  * (1.0 - MIN_SCALE_FACTOR);
    }
    return (int) (screenSize * scaleFactor);
  }

  protected void sendSuccessToListener(Bundle values) {
    if (onCompleteListener != null && !listenerCalled) {
      listenerCalled = true;
      onCompleteListener.onComplete(values, null);
      dismiss();
    }
  }

  protected void sendErrorToListener(Throwable error) {
    if (onCompleteListener != null && !listenerCalled) {
      listenerCalled = true;
      FacebookException facebookException;
      if (error instanceof FacebookException) {
        facebookException = (FacebookException) error;
      } else {
        facebookException = new FacebookException(error);
      }
      onCompleteListener.onComplete(null, facebookException);
      dismiss();
    }
  }

  public void cancel() {
    if (onCompleteListener != null && !listenerCalled) {
      sendErrorToListener(new FacebookOperationCanceledException());
    }
  }

  private void createCrossImage() {
    crossImageView = new ImageView(getContext());
    // Dismiss the dialog when user click on the 'x'
    crossImageView.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            cancel();
          }
        });
    Drawable crossDrawable = getContext().getResources().getDrawable(R.drawable.com_facebook_close);
    crossImageView.setImageDrawable(crossDrawable);
    /* 'x' should not be visible while webview is loading
     * make it visible only after webview has fully loaded
     */
    crossImageView.setVisibility(View.INVISIBLE);
  }

  @SuppressLint("SetJavaScriptEnabled")
  private void setUpWebView(int margin) {
    LinearLayout webViewContainer = new LinearLayout(getContext());
    webView =
        new WebView(getContext()) {
          /* Prevent NPE on Motorola 2.2 devices
           * See https://groups.google.com/forum/?fromgroups=#!topic/android-developers/ktbwY2gtLKQ
           */
          @Override
          public void onWindowFocusChanged(boolean hasWindowFocus) {
            try {
              super.onWindowFocusChanged(hasWindowFocus);
            } catch (NullPointerException e) {
            }
          }
        };
    if (initCallback != null) {
      initCallback.onInit(webView);
    }
    webView.setVerticalScrollBarEnabled(false);
    webView.setHorizontalScrollBarEnabled(false);
    webView.setWebViewClient(new DialogWebViewClient());
    webView.getSettings().setJavaScriptEnabled(true);
    webView.loadUrl(url);
    webView.setLayoutParams(
        new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    webView.setVisibility(View.INVISIBLE);
    webView.getSettings().setSavePassword(false);
    webView.getSettings().setSaveFormData(false);
    webView.setFocusable(true);
    webView.setFocusableInTouchMode(true);
    webView.setOnTouchListener(
        new View.OnTouchListener() {
          @Override
          public boolean onTouch(View v, MotionEvent event) {
            if (!v.hasFocus()) {
              v.requestFocus();
            }
            return false;
          }
        });

    webViewContainer.setPadding(margin, margin, margin, margin);
    webViewContainer.addView(webView);
    webViewContainer.setBackgroundColor(BACKGROUND_GRAY);
    contentFrameLayout.addView(webViewContainer);
  }

  public static void setInitCallback(InitCallback callback) {
    initCallback = callback;
  }

  private class DialogWebViewClient extends WebViewClient {
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
      Utility.logd(LOG_TAG, "Redirect URL: " + url);
      Uri parsedURL = Uri.parse(url);
      boolean isPlatformDialogURL =
          parsedURL.getPath() != null
              && Pattern.matches(PLATFORM_DIALOG_PATH_REGEX, parsedURL.getPath());

      if (url.startsWith(WebDialog.this.expectedRedirectUrl)) {
        Bundle values = parseResponseUri(url);

        String error = values.getString("error");
        if (error == null) {
          error = values.getString("error_type");
        }

        String errorMessage = values.getString("error_msg");
        if (errorMessage == null) {
          errorMessage = values.getString("error_message");
        }
        if (errorMessage == null) {
          errorMessage = values.getString("error_description");
        }
        String errorCodeString = values.getString("error_code");
        int errorCode = FacebookRequestError.INVALID_ERROR_CODE;
        if (!Utility.isNullOrEmpty(errorCodeString)) {
          try {
            errorCode = Integer.parseInt(errorCodeString);
          } catch (NumberFormatException ex) {
            errorCode = FacebookRequestError.INVALID_ERROR_CODE;
          }
        }

        if (Utility.isNullOrEmpty(error)
            && Utility.isNullOrEmpty(errorMessage)
            && errorCode == FacebookRequestError.INVALID_ERROR_CODE) {
          sendSuccessToListener(values);
        } else if (error != null
            && (error.equals("access_denied") || error.equals("OAuthAccessDeniedException"))) {
          cancel();
        } else if (errorCode == API_EC_DIALOG_CANCEL) {
          cancel();
        } else {
          FacebookRequestError requestError =
              new FacebookRequestError(errorCode, error, errorMessage);
          sendErrorToListener(new FacebookServiceException(requestError, errorMessage));
        }
        return true;
      } else if (url.startsWith(ServerProtocol.DIALOG_CANCEL_URI)) {
        cancel();
        return true;
      } else if (isPlatformDialogURL || url.contains(DISPLAY_TOUCH)) {
        return false;
      }
      // launch non-dialog URLs in a full browser
      try {
        getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        return true;
      } catch (ActivityNotFoundException e) {
        return false;
      }
    }

    @Override
    public void onReceivedError(
        WebView view, int errorCode, String description, String failingUrl) {
      super.onReceivedError(view, errorCode, description, failingUrl);
      sendErrorToListener(new FacebookDialogException(description, errorCode, failingUrl));
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
      if (DISABLE_SSL_CHECK_FOR_TESTING) {
        handler.proceed();
      } else {
        super.onReceivedSslError(view, handler, error);

        handler.cancel();
        sendErrorToListener(new FacebookDialogException(null, ERROR_FAILED_SSL_HANDSHAKE, null));
      }
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
      Utility.logd(LOG_TAG, "Webview loading URL: " + url);
      super.onPageStarted(view, url, favicon);
      if (!isDetached) {
        spinner.show();
      }
    }

    @Override
    public void onPageFinished(WebView view, String url) {
      super.onPageFinished(view, url);
      if (!isDetached) {
        spinner.dismiss();
      }
      /*
       * Once web view is fully loaded, set the contentFrameLayout background to be transparent
       * and make visible the 'x' image.
       */
      contentFrameLayout.setBackgroundColor(Color.TRANSPARENT);
      webView.setVisibility(View.VISIBLE);
      crossImageView.setVisibility(View.VISIBLE);
      isPageFinished = true;
    }
  }

  public static class Builder {
    private Context context;
    private String applicationId;
    private String action;
    private int theme;
    private OnCompleteListener listener;
    private Bundle parameters;
    private AccessToken accessToken;

    /**
     * Constructor that builds a dialog using either the current access token, or the application id
     * specified in the application/meta-data.
     *
     * @param context the Context within which the dialog will be shown.
     * @param action the portion of the dialog URL following www.facebook.com/dialog/. See
     *     https://developers.facebook.com/docs/reference/dialogs/ for details.
     * @param parameters a Bundle containing parameters to pass as part of the URL.
     */
    public Builder(Context context, String action, Bundle parameters) {
      accessToken = AccessToken.getCurrentAccessToken();
      if (!AccessToken.isCurrentAccessTokenActive()) {
        String applicationId = Utility.getMetadataApplicationId(context);
        if (applicationId != null) {
          this.applicationId = applicationId;
        } else {
          throw new FacebookException(
              "Attempted to create a builder without a valid"
                  + " access token or a valid default Application ID.");
        }
      }

      finishInit(context, action, parameters);
    }

    /**
     * Constructor that builds a dialog without an authenticated user.
     *
     * @param context the Context within which the dialog will be shown.
     * @param applicationId the application ID to be included in the dialog URL.
     * @param action the portion of the dialog URL following www.facebook.com/dialog/. See
     *     https://developers.facebook.com/docs/reference/dialogs/ for details.
     * @param parameters a Bundle containing parameters to pass as part of the URL.
     */
    public Builder(Context context, String applicationId, String action, Bundle parameters) {
      if (applicationId == null) {
        applicationId = Utility.getMetadataApplicationId(context);
      }
      Validate.notNullOrEmpty(applicationId, "applicationId");
      this.applicationId = applicationId;

      finishInit(context, action, parameters);
    }

    /**
     * Sets a theme identifier which will be passed to the underlying Dialog.
     *
     * @param theme a theme identifier which will be passed to the Dialog class
     * @return the builder
     */
    public Builder setTheme(int theme) {
      this.theme = theme;
      return this;
    }

    /**
     * Sets the listener which will be notified when the dialog finishes.
     *
     * @param listener the listener to notify, or null if no notification is desired
     * @return the builder
     */
    public Builder setOnCompleteListener(OnCompleteListener listener) {
      this.listener = listener;
      return this;
    }

    /**
     * Constructs a WebDialog using the parameters provided. The dialog is not shown, but is ready
     * to be shown by calling Dialog.show().
     *
     * @return the WebDialog
     */
    public WebDialog build() {
      if (accessToken != null) {
        parameters.putString(ServerProtocol.DIALOG_PARAM_APP_ID, accessToken.getApplicationId());
        parameters.putString(ServerProtocol.DIALOG_PARAM_ACCESS_TOKEN, accessToken.getToken());
      } else {
        parameters.putString(ServerProtocol.DIALOG_PARAM_APP_ID, applicationId);
      }

      return WebDialog.newInstance(context, action, parameters, theme, listener);
    }

    public String getApplicationId() {
      return applicationId;
    }

    public Context getContext() {
      return context;
    }

    public int getTheme() {
      return theme;
    }

    public Bundle getParameters() {
      return parameters;
    }

    public WebDialog.OnCompleteListener getListener() {
      return listener;
    }

    private void finishInit(Context context, String action, Bundle parameters) {
      this.context = context;
      this.action = action;
      if (parameters != null) {
        this.parameters = parameters;
      } else {
        this.parameters = new Bundle();
      }
    }
  }

  private class UploadStagingResourcesTask extends AsyncTask<Void, Void, String[]> {
    private String action;
    private Bundle parameters;
    private Exception[] exceptions;

    UploadStagingResourcesTask(String action, Bundle parameters) {
      this.action = action;
      this.parameters = parameters;
    }

    @Override
    protected String[] doInBackground(Void... args) {
      final String[] params = parameters.getStringArray(ShareConstants.WEB_DIALOG_PARAM_MEDIA);
      final String[] results = new String[params.length];
      exceptions = new Exception[params.length];

      final CountDownLatch latch = new CountDownLatch(params.length);
      final ConcurrentLinkedQueue<GraphRequestAsyncTask> tasks = new ConcurrentLinkedQueue<>();

      final AccessToken accessToken = AccessToken.getCurrentAccessToken();
      try {
        for (int i = 0; i < params.length; i++) {
          if (isCancelled()) {
            for (AsyncTask task : tasks) {
              task.cancel(true);
            }
            return null;
          }
          final Uri uri = Uri.parse(params[i]);
          final int writeIndex = i;
          if (Utility.isWebUri(uri)) {
            results[writeIndex] = uri.toString();
            latch.countDown();
            continue;
          }
          final GraphRequest.Callback callback =
              new GraphRequest.Callback() {
                @Override
                public void onCompleted(GraphResponse response) {
                  try {
                    final FacebookRequestError error = response.getError();
                    if (error != null) {
                      String message = error.getErrorMessage();
                      if (message == null) {
                        message = "Error staging photo.";
                      }
                      throw new FacebookGraphResponseException(response, message);
                    }
                    final JSONObject data = response.getJSONObject();
                    if (data == null) {
                      throw new FacebookException("Error staging photo.");
                    }
                    final String stagedImageUri = data.optString("uri");
                    if (stagedImageUri == null) {
                      throw new FacebookException("Error staging photo.");
                    }
                    results[writeIndex] = stagedImageUri;
                  } catch (Exception e) {
                    exceptions[writeIndex] = e;
                  }
                  latch.countDown();
                }
              };

          GraphRequestAsyncTask task =
              ShareInternalUtility.newUploadStagingResourceWithImageRequest(
                      accessToken, uri, callback)
                  .executeAsync();
          tasks.add(task);
        }
        latch.await();
      } catch (Exception e) {
        for (AsyncTask task : tasks) {
          task.cancel(true);
        }
        return null;
      }

      return results;
    }

    @Override
    protected void onPostExecute(String[] results) {
      spinner.dismiss();

      for (Exception e : exceptions) {
        if (e != null) {
          sendErrorToListener(e);
          return;
        }
      }

      if (results == null) {
        sendErrorToListener(new FacebookException("Failed to stage photos for web dialog"));
        return;
      }

      List<String> resultList = Arrays.asList(results);
      if (resultList.contains(null)) {
        sendErrorToListener(new FacebookException("Failed to stage photos for web dialog"));
        return;
      }

      Utility.putJSONValueInBundle(
          parameters, ShareConstants.WEB_DIALOG_PARAM_MEDIA, new JSONArray(resultList));

      Uri uri =
          Utility.buildUri(
              ServerProtocol.getDialogAuthority(),
              FacebookSdk.getGraphApiVersion() + "/" + ServerProtocol.DIALOG_PATH + action,
              parameters);

      WebDialog.this.url = uri.toString();
      int crossWidth = crossImageView.getDrawable().getIntrinsicWidth();
      setUpWebView(crossWidth / 2 + 1);
    }
  }
}
