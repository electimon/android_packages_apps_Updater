package org.lineageos.updater;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.icu.text.DateFormat;
import android.icu.text.NumberFormat;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.StrictMode;
import android.text.format.Formatter;
import android.util.Log;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONObject;
import org.lineageos.updater.controller.UpdaterController;
import org.lineageos.updater.controller.UpdaterService;
import org.lineageos.updater.misc.BuildInfoUtils;
import org.lineageos.updater.misc.StringGenerator;
import org.lineageos.updater.misc.Utils;
import org.lineageos.updater.model.UpdateInfo;
import org.lineageos.updater.model.UpdateStatus;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class UpdatesActivity extends AppCompatActivity {
    private static final String TAG = "Updates";
    private Exception exception;
    private UpdatesActivity activity;


    private ImageView headerIcon;
    private TextView headerTitle;
    private TextView headerStatus;
    private Button btnPrimary;
    private Button btnSecondary;
    private Button btnExtra;
    private WebView webView;
    private TextView progressText;
    private ProgressBar progressBar;

    private int htmlColor;
    private String htmlCurrentBuild = "";
    private String htmlChangelog = "";

    private UpdaterService mUpdaterService;
    private BroadcastReceiver mBroadcastReceiver;
    private UpdaterController mUpdaterController;

    private UpdateInfo update;
    private String updateId = "";
    private Boolean updateCheck;

    private class PageHandler extends AsyncTask<Void, Void, Page> {
        @Override
        protected Page doInBackground(Void... voids) {
            if (updateId != "" && update != null) {
                if (update.getStatus() == UpdateStatus.STARTING) {
                    return pageUpdateStarting();
                } else if (mUpdaterController.isDownloading(updateId)) {
                    return pageUpdateDownloading();
                } else if (mUpdaterController.isInstallingUpdate(updateId) || update.getStatus() == UpdateStatus.INSTALLING) {
                    return pageUpdateInstalling();
                } else if (mUpdaterController.isVerifyingUpdate(updateId)) {
                    return pageUpdateVerifying();
                } else if (mUpdaterController.isWaitingForReboot(updateId)) {
                    return pageInstallComplete();
                } else if (updateCheck) {
                    return pageUpdateChecking();
                }
            }
            return pageCheckForUpdates();
        }

        @Override
        protected void onPostExecute(Page result) {
            super.onPostExecute(result);
            setPage(result);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.page_updates);

        //Allow doing stupid things like running network operations on the main activity thread
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        activity = this;
        headerIcon = findViewById(R.id.header_icon);
        headerTitle = findViewById(R.id.header_title);
        headerStatus = findViewById(R.id.header_status);
        btnPrimary = findViewById(R.id.btn_primary);
        btnSecondary = findViewById(R.id.btn_secondary);
        btnExtra = findViewById(R.id.btn_extra);
        webView = findViewById(R.id.webview);
        progressText = findViewById(R.id.progress_text);
        progressBar = findViewById(R.id.progress_bar);

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                if (downloadId == "") {
                    return;
                }
                update = mUpdaterService.getUpdaterController().getUpdate(downloadId);

                if (UpdaterController.ACTION_UPDATE_STATUS.equals(intent.getAction())) {
                    Page page = pageUpdateRetryDownload();
                    switch (update.getStatus()) {
                        case PAUSED_ERROR:
                            setPage(page);
                            break;
                        case VERIFICATION_FAILED:
                            page.strStatus = getString(R.string.snack_download_verification_failed);
                            setPage(page);
                            break;
                        case VERIFIED:
                            install();
                            break;
                    }
                } else if (UpdaterController.ACTION_DOWNLOAD_PROGRESS.equals(intent.getAction())) {
                    Page page = pageUpdateDownloading();

                    String downloaded = Formatter.formatShortFileSize(activity, update.getFile().length());
                    String total = Formatter.formatShortFileSize(activity, update.getFileSize());
                    String percentage = NumberFormat.getPercentInstance().format(update.getProgress() / 100.f);

                    page.progPercent = update.getProgress();
                    page.progStep = percentage + " - " + downloaded + " / " + total;

                    if (update.getEta() > 0) {
                        CharSequence etaString = StringGenerator.formatETA(activity, update.getEta() * 1000);
                        page.progStep += " - " + etaString;
                    }

                    setPage(page);
                } else if (UpdaterController.ACTION_INSTALL_PROGRESS.equals(intent.getAction())) {
                    setPage(pageUpdateInstalling());
                } else if (UpdaterController.ACTION_UPDATE_REMOVED.equals(intent.getAction())) {
                    setPage(pageCheckForUpdates());
                } else {
                    setPage(pageError("Unknown intent: " + intent.getAction()));
                }
            }
        };

        htmlColor = this.getResources().getColor(R.color.theme_accent3, this.getTheme());
        htmlCurrentBuild = String.format("%s<br />%s<br />%s",
                getString(R.string.header_android_version, Build.VERSION.RELEASE),
                getString(R.string.header_build_security_patch, BuildInfoUtils.getBuildSecurityPatchTimestamp()),
                getString(R.string.header_build_date, StringGenerator.getDateLocalizedUTC(this,
                        DateFormat.LONG, BuildInfoUtils.getBuildDateTimestamp())));

        new PageHandler().execute();
    }

    public void setPage(Page page) {
        page.render(headerIcon, headerTitle, headerStatus, btnPrimary, btnSecondary, btnExtra, progressText, progressBar, webView);
    }

    private Page pageError(String error) {
        Page page = new Page();
        page.icon = R.drawable.ic_settings;
        page.strTitle = "ERROR";
        page.strStatus = "An unhandled exception has occurred";
        page.btnPrimaryText = "Try again";
        page.btnPrimaryClickListener = v -> {
            setPage(pageCheckForUpdates());
        };
        page.btnSecondaryText = "Exit";
        page.btnSecondaryClickListener = v -> {
            this.finish();
            System.exit(1);
        };
        page.htmlContent = error.toString() + "<p><img src='file:///android_asset/error.gif' width='100%' /></p>";
        page.htmlColor = htmlColor;
        return page;
    }

    private Page pageCheckForUpdates() {
        Page page = new Page();
        page.icon = R.drawable.ic_menu_refresh;
        page.strStatus = "Your system is up to date";
        page.btnPrimaryText = "Check for updates";
        page.btnPrimaryClickListener = v -> {
            refresh();
        };
        page.btnExtraText = "ERROR";
        page.btnExtraClickListener = v -> {
            setPage(pageError("nothing happened this time lol"));
        };
        page.htmlContent = htmlCurrentBuild;
        page.htmlColor = htmlColor;
        return page;
    }

    private Page pageUpdateChecking() {
        Page page = new Page();
        page.icon = R.drawable.ic_menu_refresh;
        page.strStatus = "Checking for updates...";
        return page;
    }

    private Page pageUpdateAvailable() {
        Page page = new Page();
        page.icon = R.drawable.ic_google_system_update;
        page.strStatus = "System update available";
        page.btnPrimaryText = "Download";
        page.btnPrimaryClickListener = v -> {
            download();
        };
        page.htmlContent = htmlChangelog;
        page.htmlColor = htmlColor;
        return page;
    }

    private Page pageUpdateStarting() {
        Page page = new Page();
        page.icon = R.drawable.ic_google_system_update;
        page.strStatus = "Starting...";
        page.htmlContent = htmlChangelog;
        page.htmlColor = htmlColor;
        return page;
    }

    private Page pageUpdateDownloading() {
        Page page = new Page();
        page.icon = R.drawable.ic_google_system_update;
        page.strStatus = "Downloading...";
        page.btnPrimaryText = "Pause";
        page.btnPrimaryClickListener = v -> {
            downloadPause();
        };
        page.btnSecondaryText = "Cancel";
        page.btnSecondaryClickListener = v -> {
            downloadCancel();
        };
        page.progStep = "Waiting to download...";
        page.htmlContent = htmlChangelog;
        page.htmlColor = htmlColor;
        return page;
    }

    private Page pageUpdateRetryDownload() {
        Page page = pageUpdateDownloading();
        page.strStatus = getString(R.string.snack_download_failed);
        page.btnPrimaryText = "Retry";
        page.btnPrimaryClickListener = v -> {
            download();
        };
        return page;
    }

    private Page pageUpdateVerifying() {
        Page page = new Page();
        page.icon = R.drawable.ic_google_system_update;
        page.strStatus = "Verifying...";
        page.htmlContent = htmlChangelog;
        page.htmlColor = htmlColor;
        return page;
    }

    private Page pageUpdateInstalling() {
        Page page = new Page();
        page.icon = R.drawable.ic_install;
        page.strStatus = "Installing...";
        page.btnPrimaryText = "Cancel";
        page.btnPrimaryClickListener = v -> {
            installCancel();
        };
        page.htmlContent = htmlChangelog;
        page.htmlColor = htmlColor;
        return page;
    }

    private Page pageInstallComplete() {
        Page page = new Page();
        page.icon = R.drawable.ic_restart;
        page.strStatus = "Install complete!";
        page.btnPrimaryText = "Reboot";
        page.btnPrimaryClickListener = v -> {
            reboot();
        };
        page.htmlContent = htmlChangelog;
        page.htmlColor = htmlColor;
        return page;
    }

    private void refresh() {
        if (mUpdaterController == null) {
            Log.e(TAG, "mUpdaterController is null during update check");
            return;
        }

        setPage(pageUpdateChecking());
        updateCheck = true;

        new Thread(() -> {
            try  {
                String urlOTA = Utils.getServerURL(this);
                URL url = new URL(urlOTA);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

                InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"), 8);
                String jsonOTA = "";
                String line = null;
                while ((line = reader.readLine()) != null) {
                    jsonOTA += line + "\n";
                }

                JSONObject obj = new JSONObject(jsonOTA);
                if (obj.isNull("version")) {
                    Log.d(TAG, "Failed to find version in updates JSON");
                    return;
                }

                try {
                    update = Utils.parseJsonUpdate(obj);
                    updateId = update.getDownloadId();
                    mUpdaterController.addUpdate(update);

                    List<String> updatesOnline = new ArrayList<>();
                    updatesOnline.add(update.getDownloadId());
                    mUpdaterController.setUpdatesAvailableOnline(updatesOnline, true);
                } catch (Exception e) {
                    Log.e(TAG, "Error while parsing updates JSON: " + e);
                    exception = e;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error while downloading updates JSON: " + e);
                exception = e;
            }
        }).run();
        updateCheck = false;

        if (exception != null) {
            setPage(pageError(exception.toString()));
            return;
        }

        if (updateId != "" && update != null && BuildInfoUtils.getBuildDateTimestamp() < update.getTimestamp()) {
            //fake the changelog for now
            htmlChangelog = LoadAssetData("changelog.html");
            setPage(pageUpdateAvailable());
        } else {
            Page page = pageCheckForUpdates();
            page.strStatus = "No updates found";
            setPage(page);
        }
    }

    private void download() {
        setPage(pageUpdateDownloading());
        if (mUpdaterController.isDownloading(updateId)) {
            mUpdaterController.pauseDownload(updateId);
            mUpdaterController.deleteUpdate(updateId);
        }
        mUpdaterController.startDownload(updateId);
    }

    private void downloadPause() {
        Page page = pageUpdateDownloading();
        page.btnPrimaryText = "Resume";
        page.btnPrimaryClickListener = v -> {
            setPage(pageUpdateDownloading());
            mUpdaterController.resumeDownload(updateId);
        };
        setPage(page);
    }

    private void downloadCancel() {
        mUpdaterController.pauseDownload(updateId);
        mUpdaterController.deleteUpdate(updateId);
        setPage(pageUpdateAvailable());
    }

    private void install() {
        setPage(pageUpdateInstalling());
        Utils.triggerUpdate(this, updateId);
    }

    private void installCancel() {
        Intent intent = new Intent(this, UpdaterService.class);
        intent.setAction(UpdaterService.ACTION_INSTALL_STOP);
        this.startService(intent);
    }

    private void reboot() {
        PowerManager pm = this.getSystemService(PowerManager.class);
        pm.reboot(null);
    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(this, UpdaterService.class);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UpdaterController.ACTION_UPDATE_STATUS);
        intentFilter.addAction(UpdaterController.ACTION_DOWNLOAD_PROGRESS);
        intentFilter.addAction(UpdaterController.ACTION_INSTALL_PROGRESS);
        intentFilter.addAction(UpdaterController.ACTION_UPDATE_REMOVED);
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, intentFilter);
    }

    @Override
    public void onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        if (mUpdaterService != null) {
            unbindService(mConnection);
        }
        super.onStop();
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            UpdaterService.LocalBinder binder = (UpdaterService.LocalBinder) service;
            mUpdaterService = binder.getService();
            mUpdaterController = mUpdaterService.getUpdaterController();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mUpdaterController = null;
            mUpdaterService = null;
        }
    };

    public String LoadAssetData(String inFile) {
        String contents = "";

        try {
            Context context = this.getApplicationContext();
            InputStream file = context.getAssets().open(inFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(file));
            String line;

            while ((line = reader.readLine()) != null) {
                if (contents != "")
                    contents += "\n";
                contents += line;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return contents;
    }
}
