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
import android.view.View;
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
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class UpdatesActivity extends AppCompatActivity {
    //Android flags
    public static final String TAG = "Updates";
    private Exception exception;
    private UpdatesActivity activity;

    //The map of the hour, "pageId" = Page
    private HashMap<String, Page> pages = new HashMap<String, Page>();

    //Layout to render the pages to
    public String pageIdActive;
    public ImageView headerIcon;
    public TextView headerTitle;
    public TextView headerStatus;
    public Button btnPrimary;
    public Button btnSecondary;
    public Button btnExtra;
    public TextView progressText;
    public ProgressBar progressBar;
    public WebView webView;
    public String htmlContentLast = "";

    //Special details
    private UpdateInfo update;
    private String updateId = "";
    private Boolean updateCheck = false;
    private int htmlColor = 0;
    private String htmlCurrentBuild = "";
    private String htmlChangelog = "";

    //Android services
    private UpdaterService mUpdaterService;
    private BroadcastReceiver mBroadcastReceiver;
    private UpdaterController mUpdaterController;

    private class PageHandler extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(Void... voids) {
            if (!Objects.equals(updateId, "") && update != null) {
                if (update.getStatus() == UpdateStatus.STARTING) {
                    return "updateStarting";
                } else if (mUpdaterController.isDownloading(updateId)) {
                    return "updateDownloading";
                } else if (mUpdaterController.isInstallingUpdate(updateId) || update.getStatus() == UpdateStatus.INSTALLING) {
                    return "updateInstalling";
                } else if (mUpdaterController.isVerifyingUpdate(updateId)) {
                    return "updateVerifying";
                } else if (mUpdaterController.isWaitingForReboot(updateId)) {
                    return "updateInstalled";
                }
            }
            return "updateChecking";
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            renderPage(result);
        }
    }

    public Page getPage(String pageId) {
        //Log.d(TAG, "Get page: " + pageId);
        return pages.get(pageId);
    }
    public void renderPage(String pageId) {
        //Log.d(TAG, "Render page: " + pageId);

        if (!Objects.equals(pageIdActive, "") && !Objects.equals(pageIdActive, pageId)) {
            Page pageLast = getPage(pageIdActive);
            if (pageLast != null) {
                pageLast.runnableRan = false;
            }
        }

        Page page = getPage(pageId);
        if (page == null) {
            page = getPage("error");
            page.htmlContent = "Unknown pageId: " + pageId;
        }

        page.render(this);
        if (!page.runnableRan) {
            page.runnableRan = true;
            page.runnable.run();
        }
        pageIdActive = pageId;
    }
    public void renderPageCustom(String pageId, Page page) {
        registerPage(pageId, page);
        renderPage(pageId);
    }
    private void registerPage(String pageId, Page page) {
        Log.d(TAG, "Register page: " + pageId);
        pages.put(pageId, page);
    }
    //A helpful wrapper that refreshes all of our pages for major content updates
    private void registerPages() {
        registerPage("error", pageError());
        registerPage("checkForUpdates", pageCheckForUpdates());
        registerPage("updateChecking", pageUpdateChecking());
        registerPage("updateAvailable", pageUpdateAvailable());
        registerPage("updateStarting", pageUpdateStarting());
        registerPage("updateDownloading", pageUpdateDownloading());
        registerPage("updatePaused", pageUpdatePaused());
        registerPage("updateRetryDownload", pageUpdateRetryDownload());
        registerPage("updateVerifying", pageUpdateVerifying());
        registerPage("updateInstalling", pageUpdateInstalling());
        registerPage("updateInstalled", pageUpdateInstalled());
        registerPage("updateInstallFailed", pageUpdateInstallFailed());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.page_updates);

        //Allow doing stupid things like running network operations on the main activity thread
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        //Prepare the environment before processing
        htmlColor = this.getResources().getColor(R.color.theme_accent3, this.getTheme());
        htmlCurrentBuild = String.format("<p style=\"letter-spacing: -0.02; font-size: 17px; line-height: 1.5;\"> %s<br />%s<br />%s </p>",
                getString(R.string.header_android_version, Build.VERSION.RELEASE),
                getString(R.string.header_build_security_patch, BuildInfoUtils.getBuildSecurityPatchTimestamp()),
                getString(R.string.header_build_date, StringGenerator.getDateLocalizedUTC(this,
                        DateFormat.LONG, BuildInfoUtils.getBuildDateTimestamp())));

        //Import and fill in the pages for the first time
        registerPages();

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
                if (Objects.equals(downloadId, "")) {
                    Log.e(TAG, "Received intent " + intent.getAction() + " without downloadId?");
                    return;
                }
                if (mUpdaterService == null) {
                    Log.e(TAG, "Received intent " + intent.getAction() + " without mUpdaterService?");
                    return;
                }
                update = mUpdaterService.getUpdaterController().getUpdate(downloadId);

                if (UpdaterController.ACTION_UPDATE_STATUS.equals(intent.getAction())) {
                    switch (update.getStatus()) {
                        case PAUSED_ERROR:
                            renderPage("updateRetryDownload");
                            break;
                        case VERIFICATION_FAILED:
                            Page page = getPage("updateRetryDownload");
                            page.strStatus = getString(R.string.snack_download_verification_failed);
                            renderPage("updateRetryDownload");
                            break;
                        case INSTALLATION_FAILED:
                            renderPage("updateInstallFailed");
                            break;
                        case VERIFIED:
                            install();
                            break;
                        case INSTALLED:
                            renderPage("updateInstalled");
                            break;
                    }
                } else if (UpdaterController.ACTION_DOWNLOAD_PROGRESS.equals(intent.getAction())) {
                    Page page = getPage("updateDownloading");

                    String downloaded = Formatter.formatShortFileSize(activity, update.getFile().length());
                    String total = Formatter.formatShortFileSize(activity, update.getFileSize());
                    String percentage = NumberFormat.getPercentInstance().format(update.getProgress() / 100.f);

                    page.progPercent = update.getProgress();
                    page.progStep = percentage + " • " + downloaded + " / " + total;

                    if (update.getEta() > 0) {
                        CharSequence etaString = StringGenerator.formatETA(activity, update.getEta() * 1000);
                        page.progStep += " • " + etaString;
                    }

                    if (Objects.equals(pageIdActive, "updateDownloading") || Objects.equals(pageIdActive, "checkForUpdates") || Objects.equals(pageIdActive, "updateAvailable"))
                        renderPage("updateDownloading");
                } else if (UpdaterController.ACTION_INSTALL_PROGRESS.equals(intent.getAction())) {
                    Page page = getPage("updateInstalling");

                    page.progPercent = update.getInstallProgress();
                    if (mUpdaterController.isInstallingABUpdate()) {
                        if (update.getFinalizing()) {
                            page.progStep = getString(R.string.system_update_optimizing_apps);
                        } else {
                            page.progStep = getString(R.string.system_update_installing_title_text);
                        }
                    } else {
                        page.progStep = getString(R.string.system_update_prepare_install);
                    }

                    if (Objects.equals(pageIdActive, "updateInstalling") || Objects.equals(pageIdActive, "checkForUpdates") || Objects.equals(pageIdActive, "updateAvailable") || Objects.equals(pageIdActive, "updateDownloading"))
                        renderPage("updateInstalling");
                } else if (UpdaterController.ACTION_UPDATE_REMOVED.equals(intent.getAction())) {
                    renderPage("checkForUpdates");
                } else {
                    Page page = getPage("error");
                    page.htmlContent = "Unknown intent: " + intent.getAction();
                    renderPage("error");
                }
            }
        };

        new PageHandler().execute();
    }

    private Page pageError() {
        Page page = new Page();
        page.runnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "This is the error code!");
            }
        };
        page.icon = R.drawable.ic_settings;
        page.strTitle = "ERROR";
        page.strStatus = "An unhandled exception has occurred";
        page.btnPrimaryText = "Try again";
        page.btnPrimaryClickListener = v -> {
            renderPage("checkForUpdates");
        };
        page.btnSecondaryText = "Exit";
        page.btnSecondaryClickListener = v -> {
            this.finish();
            System.exit(1);
        };
        page.htmlColor = htmlColor;
        return page;
    }

    private Page pageCheckForUpdates() {
        Page page = new Page();
        page.icon = R.drawable.ic_google_system_update;
        page.strStatus = getString(R.string.system_update_no_update_content_text);
        page.btnPrimaryText = getString(R.string.system_update_check_now_button_text);
        page.btnPrimaryClickListener = v -> {
            refresh();
        };
        page.btnExtraText = "ERROR";
        page.btnExtraClickListener = v -> {
            Page pageErr = getPage("error");
            pageErr.htmlContent = "nothing happened this time either lmao";
            renderPage("error");
        };
        page.htmlContent = htmlCurrentBuild;
        page.htmlColor = htmlColor;
        return page;
    }

    private Page pageUpdateChecking() {
        Page page = new Page();
        page.icon = R.drawable.ic_menu_refresh;
        page.strStatus = getString(R.string.system_update_update_checking);
        page.htmlContent = htmlCurrentBuild;
        page.htmlColor = htmlColor;
        return page;
    }

    private Page pageUpdateAvailable() {
        Page page = new Page();
        page.icon = R.drawable.ic_google_system_update;
        page.strStatus = getString(R.string.system_update_update_available_title_text);
        page.btnPrimaryText = getString(R.string.system_update_update_now);
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
        page.strStatus = getString(R.string.system_update_system_update_downloading_title_text);
        page.btnPrimaryText = getString(R.string.system_update_download_pause_button);
        page.btnPrimaryClickListener = v -> {
            downloadPause();
        };
        page.btnExtraText = getString(R.string.system_update_countdown_cancel_button);
        page.btnExtraClickListener = v -> {
            downloadCancel();
        };
        page.htmlContent = htmlChangelog;
        page.htmlColor = htmlColor;
        return page;
    }

    private Page pageUpdatePaused() {
        Page page = new Page();
        page.icon = R.drawable.ic_google_system_update;
        page.strStatus = getString(R.string.system_update_notification_title_update_paused);
        page.btnPrimaryText = getString(R.string.system_update_resume_button_text);
        page.btnPrimaryClickListener = v -> {
            downloadResume();
        };
        page.btnExtraText = getString(R.string.system_update_countdown_cancel_button);
        page.btnExtraClickListener = v -> {
            downloadCancel();
        };
        page.htmlContent = htmlChangelog;
        page.htmlColor = htmlColor;

        Page pageDownload = getPage("updateDownloading");
        page.progStep = pageDownload.progStep;
        page.progPercent = pageDownload.progPercent;

        return page;
    }

    private Page pageUpdateRetryDownload() {
        Page page = pageUpdateDownloading();
        page.strStatus = getString(R.string.system_update_download_error_notification_title);
        page.btnPrimaryText = getString(R.string.system_update_download_retry_button_text);
        page.btnPrimaryClickListener = v -> {
            download();
        };
        return page;
    }

    private Page pageUpdateVerifying() {
        Page page = new Page();
        page.icon = R.drawable.ic_google_system_update;
        page.strStatus = getString(R.string.system_update_installing_title_text);
        page.htmlContent = htmlChangelog;
        page.htmlColor = htmlColor;
        return page;
    }

    private Page pageUpdateInstalling() {
        Page page = new Page();
        page.icon = R.drawable.ic_install;
        page.strStatus = getString(R.string.system_update_installing_title_text);
        page.htmlContent = htmlChangelog;
        page.htmlColor = htmlColor;
        return page;
    }

    private Page pageUpdateInstalled() {
        Page page = new Page();
        page.icon = R.drawable.ic_restart;
        page.strStatus = getString(R.string.system_update_almost_done);
        page.btnPrimaryText = getString(R.string.system_update_restart_now);
        page.btnPrimaryClickListener = v -> {
            reboot();
        };
        page.htmlContent = htmlChangelog;
        page.htmlColor = htmlColor;
        return page;
    }

    private Page pageUpdateInstallFailed() {
        Page page = new Page();
        page.icon = R.drawable.ic_google_system_update;
        page.strStatus = getString(R.string.system_update_install_failed_title_text);
        page.btnPrimaryText = getString(R.string.system_update_update_failed);
        page.btnPrimaryClickListener = v -> {
            renderPage("checkForUpdates");
        };
        page.htmlContent = getString(R.string.system_update_activity_attempt_install_later_text);
        page.htmlColor = htmlColor;
        return page;
    }

    private void refresh() {
        if (mUpdaterController == null) {
            Log.e(TAG, "mUpdaterController is null during update check");
            renderPage("checkForUpdates");
            return;
        }
        if (update != null && update.getStatus() != UpdateStatus.UNKNOWN) {
            Log.d(TAG, "Skipping update check during update operation");
            return;
        }

        Log.d(TAG, "Checking for updates");
        updateCheck = true;
        if (!Objects.equals(pageIdActive, "updateChecking"))
            renderPage("updateChecking");

        new Thread(() -> {
            try  {
                Thread.sleep(500);

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

            updateCheck = false;

            if (exception != null) {
                Page page = getPage("checkForUpdates");
                page.strStatus = "No updates found";
                renderPage("checkForUpdates");
                return;
            }

            if (!Objects.equals(updateId, "") && update != null && BuildInfoUtils.getBuildDateTimestamp() < update.getTimestamp()) {
                //fake the changelog for now
                htmlChangelog = LoadAssetData("changelog.html");
                registerPages(); //Reload everything that might display the changelog
                renderPage("updateAvailable");
            } else {
                renderPage("checkForUpdates");
            }
        }).start();
    }

    private void download() {
        if (mUpdaterController.isDownloading(updateId) ||
            mUpdaterController.isInstallingUpdate(updateId) ||
            mUpdaterController.isVerifyingUpdate(updateId) ||
            mUpdaterController.isWaitingForReboot(updateId)
        ) {
            Log.e(TAG, "Tried to call download when update is already under way");
            return;
        }

        Page page = getPage("updateDownloading");
        page.progPercent = 0;
        renderPage("updateDownloading");

        mUpdaterController.pauseDownload(updateId);
        mUpdaterController.deleteUpdate(updateId);
        mUpdaterController.startDownload(updateId);
    }

    private void downloadCancel() {
        mUpdaterController.pauseDownload(updateId);
        mUpdaterController.deleteUpdate(updateId);
        renderPage("updateAvailable");
    }

    private void downloadPause() {
        mUpdaterController.pauseDownload(updateId);
        Page pagePaused = getPage("updatePaused");
        Page pageDownload = getPage("updateDownloading");
        pagePaused.progPercent = pageDownload.progPercent;
        pagePaused.progStep = pageDownload.progStep;
        renderPage("updatePaused");
    }

    private void downloadResume() {
        renderPage("updateDownloading");
        mUpdaterController.resumeDownload(updateId);
    }

    private void install() {
        renderPage("updateInstalling");
        Utils.triggerUpdate(this, updateId);
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

            Log.d(TAG, "Running automatic update check...");
            refresh();
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
