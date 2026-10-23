/* -*- Mode: C++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4; fill-column: 100 -*- */
/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.libreoffice.androidlib;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

public class LOActivity extends Activity {

    final static String TAG = "LOActivity";

    private static final String IS_COPY_UNPACK = "IS_COPY_UNPACK";

    private String urlToLoad;
    private COWebView mWebView = null;
    private SharedPreferences sPrefs;
    private Handler mMainHandler = null;

    private boolean documentLoaded = false;

    private ProgressDialog mProgressDialog = null;

    private boolean mMobileWizardVisible = false;
    private boolean mIsEditModeActive = false;
    private boolean isByeSent = false;
    private boolean isSlideShowActive = false;


    private static boolean copyFromAssets(AssetManager assetManager, String fromAssetPath, String targetDir) {
        try {
            String[] files = assetManager.list(fromAssetPath);
            boolean res = true;
            for (String file : files) {
                String[] dirOrFile = assetManager.list(fromAssetPath + "/" + file);
                if (dirOrFile.length == 0) {
                    // noinspection ResultOfMethodCallIgnored
                    new File(targetDir).mkdirs();
                    res &= copyAsset(assetManager, fromAssetPath + "/" + file, targetDir + "/" + file);
                } else {
                    res &= copyFromAssets(assetManager, fromAssetPath + "/" + file, targetDir + "/" + file);
                }
            }
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "copyFromAssets failed: " + e.getMessage());
            return false;
        }
    }

    private static boolean copyAsset(AssetManager assetManager, String fromAssetPath, String toPath) {
        ReadableByteChannel source = null;
        FileChannel dest = null;
        try {
            try {
                source = Channels.newChannel(assetManager.open(fromAssetPath));
                dest = new FileOutputStream(toPath).getChannel();
                long bytesTransferred = 0;
                // might not copy all at once, so make sure everything gets copied....
                ByteBuffer buffer = ByteBuffer.allocate(4096);
                while (source.read(buffer) > 0) {
                    buffer.flip();
                    bytesTransferred += dest.write(buffer);
                    buffer.clear();
                }
                Log.v(TAG, "Success copying " + fromAssetPath + " to " + toPath + " bytes: " + bytesTransferred);
                return true;
            } finally {
                if (dest != null) dest.close();
                if (source != null) source.close();
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, "file " + fromAssetPath + " not found! " + e.getMessage());
            return false;
        } catch (IOException e) {
            Log.e(TAG, "failed to copy file " + fromAssetPath + " from assets to " + toPath + " - " + e.getMessage());
            return false;
        }
    }

    private Handler getMainHandler() {
        if (mMainHandler == null) {
            mMainHandler = new Handler(getMainLooper());
        }
        return mMainHandler;
    }

    /**
     * True if the App is running under ChromeOS.
     */
    public static boolean isChromeOS(Context context) {
        return context.getPackageManager().hasSystemFeature("org.chromium.arc.device_management");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.lolib_activity_main);
        mProgressDialog = new ProgressDialog(this);

        init();
    }

    /**
     * Initialize the app - copy the assets and create the UI.
     */
    private void init() {
        if (sPrefs.getBoolean(IS_COPY_UNPACK, false)) {
            // all is fine, we have already copied the assets
            showFile();
            return;
        }
        mProgressDialog.indeterminate(R.string.office_file_loading);

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                // copy the new assets
                if (copyFromAssets(getAssets(), "unpack", getApplicationInfo().dataDir)) {
                    sPrefs.edit().putBoolean(IS_COPY_UNPACK, true).apply();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                showFile();
            }
        }.execute();
    }


    private void showFile() {
        String filePath = getIntent().getStringExtra("filePath");

        urlToLoad = filePath;

        mWebView = findViewById(R.id.browser);
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        mWebView.addJavascriptInterface(this, "COOLMessageHandler");

        getMainHandler();
        loadDocument();
    }

//    @Override
//    public void onBackPressed() {
//        setResult(Activity.RESULT_OK);
//        finish();
//    }

    @Override
    protected void onDestroy() {
        if (!documentLoaded && isByeSent) {
            super.onDestroy();
            return;
        }

        // Remove the webview from the hierarchy & destroy
        if (mWebView != null) {
            final ViewGroup viewGroup = (ViewGroup) mWebView.getParent();
            if (viewGroup != null) {
                viewGroup.removeView(mWebView);
            }
            mWebView.destroy();
            mWebView = null;
        }

        documentLoaded = false;
        if (!isByeSent) {
            isByeSent = true;
            try {
                postMobileMessageNative("BYE");
            } catch (Exception ignored) {}
        }

        if (mProgressDialog != null) {
            try {
                mProgressDialog.dismiss();
            } catch (Exception ignored) {}
        }

        super.onDestroy();
        Log.i(TAG, "onDestroy() - we know we are leaving the document");
    }

    /**
     * Show the Saving progress and finish the app.
     */
    private void finishWithProgress() {
        if (!documentLoaded || isByeSent) {
            finishAndRemoveTask();
            return;
        }
        isByeSent = true;
        documentLoaded = false;
        if (mProgressDialog != null) {
            try {
                mProgressDialog.indeterminate(R.string.office_file_exiting);
            } catch (Exception ignored) {}
        }

        new Thread(() -> {
            try {
                postMobileMessageNative("BYE");
            } catch (Exception e) {
                Log.e(TAG, "Error posting native BYE", e);
            }
            runOnUiThread(() -> {
                if (mProgressDialog != null) {
                    try {
                        mProgressDialog.dismiss();
                    } catch (Exception ignored) {}
                }
                finishAndRemoveTask();
            });
        }).start();
    }

    private void enterSlideShowMode() {
        isSlideShowActive = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
        callFakeWebsocketOnMessage("'presentation'");
        callFakeWebsocketOnMessage("'fullscreen'");
        Toast.makeText(this, "Slideshow started. Tap or swipe to advance, back to exit.", Toast.LENGTH_SHORT).show();
    }

    private void exitSlideShowMode() {
        isSlideShowActive = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        callFakeWebsocketOnMessage("'presentationstop'");
    }

    @Override
    public void onBackPressed() {
        if (isSlideShowActive) {
            exitSlideShowMode();
            return;
        }

        if (!documentLoaded) {
            finishAndRemoveTask();
            return;
        }

        if (mMobileWizardVisible) {
            callFakeWebsocketOnMessage("'mobile: mobilewizardback'");
            return;
        } else if (mIsEditModeActive) {
            callFakeWebsocketOnMessage("'mobile: readonlymode'");
            return;
        }

        finishWithProgress();
    }

    private void loadDocument() {
        mProgressDialog.determinate(R.string.office_file_loading);

        // setup the COOLWSD
        ApplicationInfo applicationInfo = getApplicationInfo();
        String dataDir = applicationInfo.dataDir;
        Log.i(TAG, String.format("Initializing LibreOfficeKit, dataDir=%s\n", dataDir));

        String cacheDir = getApplication().getCacheDir().getAbsolutePath();
        String apkFile = getApplication().getPackageResourcePath();
        AssetManager assetManager = getResources().getAssets();
        String uiMode = "classic";
        String userName = "Guest User";
        createCOOLWSD(dataDir, cacheDir, apkFile, assetManager, urlToLoad, uiMode, userName);

        // trigger the load of the document
        String finalUrlToLoad = "file:///android_asset/dist/cool.html?file_path=" + urlToLoad + "&closebutton=1";

        // set the language
        String language = getResources().getConfiguration().locale.toLanguageTag();

        Log.i(TAG, "Loading with language:  " + language);

        finalUrlToLoad += "&lang=" + language;

        // if (isDocEditable) {
        //     finalUrlToLoad += "&permission=edit";
        // } else {
        //     finalUrlToLoad += "&permission=readonly";
        // }

        finalUrlToLoad += "&permission=edit";

        // if (isDocDebuggable) {
        //     finalUrlToLoad += "&debug=true";
        // }

        // if (isLargeScreen() && !isChromeOS())
        //     finalUrlToLoad += "&userinterfacemode=notebookbar";
        // load the page
        mWebView.loadUrl(finalUrlToLoad);

        documentLoaded = true;
    }

    static {
        System.loadLibrary("androidapp");
    }

    /**
     * Initialize the COOLWSD to load 'loadFileURL'.
     */
    public native void createCOOLWSD(String dataDir, String cacheDir, String apkFile, AssetManager assetManager, String loadFileURL, String uiMode, String userName);

    /**
     * Passing messages from JS (instead of the websocket communication).
     */
    @JavascriptInterface
    public void postMobileMessage(String message) {
        Log.d(TAG, "postMobileMessage: " + message);
        // the command and the rest (that can potentially contain spaces too)
        String[] messageAndParameterArray = message.split(" ", 2);
        if (beforeMessageFromWebView(messageAndParameterArray)) {
            postMobileMessageNative(message);
            afterMessageFromWebView(messageAndParameterArray);
        }
    }

    /**
     * Call the post method form C++
     */
    public native void postMobileMessageNative(String message);

    /**
     * Passing messages from JS (instead of the websocket communication).
     */
    @JavascriptInterface
    public void postMobileError(String message) {
        // TODO handle this
        Log.d(TAG, "postMobileError: " + message);
    }

    /**
     * Passing messages from JS (instead of the websocket communication).
     */
    @JavascriptInterface
    public void postMobileDebug(String message) {
        // TODO handle this
        Log.d(TAG, "postMobileDebug: " + message);
    }

    /**
     * Provide the info that this app is actually running under ChromeOS - so
     * has to mostly look like on desktop.
     */
    @JavascriptInterface
    public boolean isChromeOS() {
        return isChromeOS(this);
    }

    @JavascriptInterface
    public void copyToClipboard(String text) {
        if (text == null || text.isEmpty()) return;
        getMainHandler().post(() -> {
            try {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    ClipData clip = ClipData.newPlainText("Hexaro Office", text);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(LOActivity.this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to copy to clipboard", e);
            }
        });
    }

    @JavascriptInterface
    public String getClipboardText() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = clipboard.getPrimaryClip().getItemAt(0).getText();
                return text != null ? text.toString() : "";
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get clipboard text", e);
        }
        return "";
    }

    private void populateClipboard() {
        try {
            postMobileMessageNative("gettextselection");
        } catch (Exception e) {
            Log.e(TAG, "Error requesting text selection", e);
        }
    }

    /**
     * Passing message the other way around - from Java to the FakeWebSocket in JS.
     */
    void callFakeWebsocketOnMessage(final String message) {
        // call from the UI thread
        if (mWebView != null)
            mWebView.post(new Runnable() {
                public void run() {
                    if (mWebView == null) {
                        Log.i(TAG, "Skipped forwarding to the WebView: " + message);
                        return;
                    }
                    mWebView.loadUrl("javascript:window.TheFakeWebSocket.onmessage({'data':" + message + "});");
                }
            });

        // Intercept text selection content and copy to Android clipboard
        if (message.startsWith("'textselectioncontent: ")) {
            runOnUiThread(() -> {
                try {
                    String raw = message.substring("'textselectioncontent: ".length());
                    if (raw.endsWith("'")) {
                        raw = raw.substring(0, raw.length() - 1);
                    }
                    String plainText = android.text.Html.fromHtml(raw, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim();
                    if (!plainText.isEmpty()) {
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            ClipData clip = ClipData.newPlainText("Hexaro Office", plainText);
                            clipboard.setPrimaryClip(clip);
                            Toast.makeText(LOActivity.this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error copying text selection", e);
                }
            });
        }

        // update progress bar when loading
        if (message.startsWith("'statusindicator") || message.startsWith("'error:")) {
            runOnUiThread(new Runnable() {
                public void run() {
                    // update progress bar if it exists
                    final String statusIndicatorSetValue = "'statusindicatorsetvalue: ";
                    if (message.startsWith(statusIndicatorSetValue)) {
                        int start = statusIndicatorSetValue.length();
                        int end = message.indexOf("'", start);
                        int progress = 0;
                        try {
                            progress = Integer.parseInt(message.substring(start, end));
                        } catch (Exception e) {

                        }
                        mProgressDialog.determinateProgress(progress);
                    } else if (message.startsWith("'statusindicatorfinish:") || message.startsWith("'error:")) {
                        if (mProgressDialog != null) {
                            mProgressDialog.dismiss();
                        }
                    }
                }
            });
        }
    }

    /**
     * return true to pass the message to the native part or false to block the message
     */
    private boolean beforeMessageFromWebView(String[] messageAndParam) {
        switch (messageAndParam[0]) {
            case "BYE":
                finishWithProgress();
                return false;
            case "SLIDESHOW": {
                getMainHandler().post(this::enterSlideShowMode);
                return false;
            }
            case "LIGHT_SCREEN": {
                getMainHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    }
                });
                return false;
            }
            case "MOBILEWIZARD": {
                switch (messageAndParam[1]) {
                    case "show":
                        mMobileWizardVisible = true;
                        break;
                    case "hide":
                        mMobileWizardVisible = false;
                        break;
                }
                return false;
            }
            case "EDITMODE": {
                switch (messageAndParam[1]) {
                    case "on":
                        mIsEditModeActive = true;
                        break;
                    case "off":
                        mIsEditModeActive = false;
                        break;
                }
                return false;
            }
            case "hideProgressbar": {
                if (mProgressDialog != null)
                    mProgressDialog.dismiss();
                return false;
            }
            case "loadwithpassword": {
                mProgressDialog.determinate(R.string.office_file_loading);
                return true;
            }
        }
        return true;
    }

    private void afterMessageFromWebView(String[] messageAndParameterArray) {
        switch (messageAndParameterArray[0]) {
            case "uno":
                switch (messageAndParameterArray[1]) {
                    case ".uno:Copy":
                    case ".uno:Cut":
                        populateClipboard();
                        break;
                    default:
                        break;
                }
                break;
            default:
                break;
        }
    }

}

