package org.lineageos.updater;

import android.app.Activity;
import android.graphics.Color;
import android.media.Image;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class Page {
    public int icon = 0;
    public String strTitle = "";
    public String strStatus = "";
    public String btnPrimaryText = "";
    public View.OnClickListener btnPrimaryClickListener;
    public String btnSecondaryText = "";
    public View.OnClickListener btnSecondaryClickListener;
    public String btnExtraText = "";
    public View.OnClickListener btnExtraClickListener;
    public String progStep = "";
    public int progPercent = 0;
    public String htmlContent = "";
    public String htmlContentLast = "";
    public int htmlColor = 0;

    public void render(ImageView headerIcon, TextView headerTitle, TextView headerStatus, Button btnPrimary, Button btnSecondary, Button btnExtra, TextView progressText, ProgressBar progressBar, WebView webView) {
        headerIcon.setVisibility(View.GONE);
        headerTitle.setVisibility(View.GONE);
        headerStatus.setVisibility(View.GONE);
        btnPrimary.setVisibility(View.GONE);
        btnSecondary.setVisibility(View.GONE);
        btnExtra.setVisibility(View.GONE);
        progressText.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        webView.setVisibility(View.GONE);

        progressBar.setScaleY(1.8f);

        if (icon != 0) {
            headerIcon.setImageResource(icon);
            headerIcon.setVisibility(View.VISIBLE);
        }

        if (strTitle != "") {
            headerTitle.setText(strTitle);
            headerTitle.setVisibility(View.VISIBLE);
        }

        if (strStatus != "") {
            headerStatus.setText(strStatus);
            headerStatus.setVisibility(View.VISIBLE);
        }

        if (btnPrimaryText != "" && btnPrimaryClickListener != null) {
            btnPrimary.setText(btnPrimaryText);
            setBtnClickListener(btnPrimary, btnPrimaryClickListener);
            btnPrimary.setVisibility(View.VISIBLE);
        }
        if (btnSecondaryText != "" && btnSecondaryClickListener != null) {
            btnSecondary.setText(btnSecondaryText);
            setBtnClickListener(btnSecondary, btnSecondaryClickListener);
            btnSecondary.setVisibility(View.VISIBLE);
        }
        if (btnExtraText != "" && btnExtraClickListener != null) {
            btnExtra.setText(btnExtraText);
            setBtnClickListener(btnExtra, btnExtraClickListener);
            btnExtra.setVisibility(View.VISIBLE);
        }

        progressText.setText(progStep);
        if (progStep != "") {
            progressText.setText(progStep);
            progressBar.setProgress(progPercent, true);

            progressText.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.VISIBLE);
        }

        if (htmlContent != "") {
            String hexColor = "";
            if (htmlColor != 0) {
                int colorTextR = Color.red(htmlColor);
                int colorTextG = Color.green(htmlColor);
                int colorTextB = Color.blue(htmlColor);
                hexColor = String.format("; color: #%02x%02x%02x", colorTextR, colorTextG, colorTextB);
            }
            String html = "<html><head><style>@font-face { font-family: harmony; src: url('file:///android_asset/harmonyos_sans_regular.ttf'); } body { font-family: harmony; font-size: medium; text-align: justify" + hexColor + "; }</style></head><body>" + htmlContent + "</body></html>";
            if (html != htmlContentLast) {
                htmlContentLast = html;
                webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
                webView.setBackgroundColor(Color.TRANSPARENT);
            }

            webView.setVisibility(View.VISIBLE);
        }
    }

    private void setBtnClickListener(Button btn, View.OnClickListener clickListener) {
        btn.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onClick(v);
            }
        });
    }
}
