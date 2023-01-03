package org.lineageos.updater;

import android.app.Activity;
import android.graphics.Color;
import android.media.Image;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Objects;

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
    public int htmlColor = 0;
    public Runnable runnable = new Runnable() {
        @Override
        public void run() {
            Log.d(mContext.TAG, "This page does not run custom code!");
        }
    };
    public Boolean runnableRan = false;

    public UpdatesActivity mContext;

    public void render(UpdatesActivity context) {
        this.mContext = context;
        context.headerIcon.setVisibility(View.GONE);
        context.headerTitle.setVisibility(View.GONE);
        context.headerStatus.setVisibility(View.GONE);
        context.btnPrimary.setVisibility(View.GONE);
        context.btnSecondary.setVisibility(View.GONE);
        context.btnExtra.setVisibility(View.GONE);
        context.progressText.setVisibility(View.GONE); //Good separator between headerStatus and webView
        context.progressBar.setVisibility(View.GONE);
        context.webView.setVisibility(View.GONE);

        context.progressBar.setScaleY(1.0f);

        if (icon != 0) {
            context.headerIcon.setImageResource(icon);
            context.headerIcon.setVisibility(View.VISIBLE);
        }

        if (!Objects.equals(strTitle, "")) {
            context.headerTitle.setText(strTitle);
            context.headerTitle.setVisibility(View.VISIBLE);
        }

        if (!Objects.equals(strStatus, "")) {
            context.headerStatus.setText(strStatus);
            context.headerStatus.setVisibility(View.VISIBLE);
        }

        if (!Objects.equals(btnPrimaryText, "") && btnPrimaryClickListener != null) {
            context.btnPrimary.setText(btnPrimaryText);
            setBtnClickListener(context.btnPrimary, btnPrimaryClickListener);
            context.btnPrimary.setVisibility(View.VISIBLE);
            if (Objects.equals(btnSecondaryText, ""))
                context.btnSecondary.setVisibility(View.INVISIBLE);
            if (Objects.equals(btnExtraText, ""))
                context.btnExtra.setVisibility(View.INVISIBLE);
        }
        if (!Objects.equals(btnSecondaryText, "") && btnSecondaryClickListener != null) {
            context.btnSecondary.setText(btnSecondaryText);
            setBtnClickListener(context.btnSecondary, btnSecondaryClickListener);
            context.btnSecondary.setVisibility(View.VISIBLE);
            if (Objects.equals(btnPrimaryText, ""))
                context.btnPrimary.setVisibility(View.INVISIBLE);
            if (Objects.equals(btnExtraText, ""))
                context.btnExtra.setVisibility(View.INVISIBLE);
        }
        if (!Objects.equals(btnExtraText, "") && btnExtraClickListener != null) {
            context.btnExtra.setText(btnExtraText);
            setBtnClickListener(context.btnExtra, btnExtraClickListener);
            context.btnExtra.setVisibility(View.VISIBLE);
            if (Objects.equals(btnPrimaryText, ""))
                context.btnPrimary.setVisibility(View.INVISIBLE);
            if (Objects.equals(btnSecondaryText, ""))
                context.btnSecondary.setVisibility(View.INVISIBLE);
        }

        context.progressText.setText(progStep);
        if (!Objects.equals(progStep, "")) {
            context.progressText.setText(progStep);
            context.progressBar.setProgress(progPercent, true);

            context.progressText.setVisibility(View.VISIBLE);
            context.progressBar.setVisibility(View.VISIBLE);
        }

        if (!Objects.equals(htmlContent, "")) {
            String hexColor = "";
            if (htmlColor != 0) {
                int colorTextR = Color.red(htmlColor);
                int colorTextG = Color.green(htmlColor);
                int colorTextB = Color.blue(htmlColor);
                hexColor = String.format("; color: #%02x%02x%02x", colorTextR, colorTextG, colorTextB);
            }
            String html = "<html><head><style>body { font-size: light" + hexColor + "; }</style></head><body>" + htmlContent + "</body></html>";
            if (!html.equals(context.htmlContentLast)) {
                context.htmlContentLast = html;
                Log.d(UpdatesActivity.TAG, "Last HTML didn't match, using new HTML: " +     context.htmlContentLast);

                context.webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
                context.webView.setBackgroundColor(Color.TRANSPARENT);
            }

            context.webView.setVisibility(View.VISIBLE);
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
