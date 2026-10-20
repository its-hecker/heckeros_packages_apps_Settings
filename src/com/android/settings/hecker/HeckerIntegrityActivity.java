/* SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.hecker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.android.settings.R;

import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native Settings page; no launcher component or separate settings application. */
public final class HeckerIntegrityActivity extends Activity {
    private static final String KEY = "hecker_pif_config";
    private static final int PICK_PROFILE = 44;
    private final ExecutorService mWorker = Executors.newSingleThreadExecutor();
    private TextView mStatus;
    private Button mFetch;
    private Button mImport;
    private Button mDisable;

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setTitle(R.string.hecker_integrity_title);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        TextView description = new TextView(this);
        description.setText(R.string.hecker_integrity_description);
        content.addView(description);
        mStatus = new TextView(this);
        mStatus.setPadding(0, padding, 0, padding);
        mStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(mStatus);
        mFetch = new Button(this);
        mFetch.setText(R.string.hecker_integrity_fetch);
        mFetch.setOnClickListener(view -> fetchProfile());
        content.addView(mFetch);
        mImport = new Button(this);
        mImport.setText(R.string.hecker_integrity_import);
        mImport.setOnClickListener(view -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), PICK_PROFILE));
        content.addView(mImport);
        mDisable = new Button(this);
        mDisable.setText(R.string.hecker_integrity_disable);
        mDisable.setOnClickListener(view -> {
            if (Settings.Global.putString(getContentResolver(), KEY, null)) {
                mStatus.setText(R.string.hecker_integrity_disabled_restart);
            } else mStatus.setText(R.string.hecker_integrity_save_failed);
        });
        content.addView(mDisable);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);
        String current = Settings.Global.getString(getContentResolver(), KEY);
        mStatus.setText(current == null || current.isEmpty()
                ? getString(R.string.hecker_integrity_disabled)
                : getString(R.string.hecker_integrity_current, currentModel(current)));
    }

    private static String currentModel(String profile) {
        try { return new JSONObject(profile).optString("MODEL", "Custom profile"); }
        catch (Exception error) { return "Custom profile"; }
    }

    private void fetchProfile() {
        busy();
        mWorker.execute(() -> {
            try {
                Map<String, String> profile = PixelBetaProfileFetcher.fetch();
                runOnUiThread(() -> save(profile));
            } catch (Exception error) { showError(error); }
        });
    }

    private void busy() {
        mFetch.setEnabled(false);
        mImport.setEnabled(false);
        mDisable.setEnabled(false);
        mStatus.setText(R.string.hecker_integrity_loading);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_PROFILE || result != RESULT_OK || data == null || data.getData() == null) return;
        final android.net.Uri uri = data.getData();
        busy();
        mWorker.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalArgumentException("Unable to open profile");
                byte[] bytes = input.readNBytes(65537);
                if (bytes.length > 65536) throw new IllegalArgumentException("Profile exceeds 64 KB");
                String text = new String(bytes, StandardCharsets.UTF_8).trim();
                Map<String, String> fields = new LinkedHashMap<>();
                if (text.startsWith("{")) {
                    JSONObject json = new JSONObject(text);
                    Iterator<String> keys = json.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        fields.put(key, json.getString(key));
                    }
                } else {
                    for (String line : text.split("\\R")) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        int eq = line.indexOf('=');
                        if (eq <= 0) throw new IllegalArgumentException("Expected key=value");
                        fields.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                    }
                }
                Map<String, String> profile = PifProfile.validate(fields);
                runOnUiThread(() -> save(profile));
            } catch (Exception error) { showError(error); }
        });
    }

    private void save(Map<String, String> profile) {
        if (isFinishing() || isDestroyed()) return;
        mFetch.setEnabled(true);
        mImport.setEnabled(true);
        mDisable.setEnabled(true);
        if (Settings.Global.putString(getContentResolver(), KEY, new JSONObject(profile).toString())) {
            mStatus.setText(getString(R.string.hecker_integrity_saved,
                    profile.getOrDefault("MODEL", "Custom profile")));
        } else mStatus.setText(R.string.hecker_integrity_save_failed);
    }

    private void showError(Exception error) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            mFetch.setEnabled(true);
            mImport.setEnabled(true);
            mDisable.setEnabled(true);
            mStatus.setText(getString(R.string.hecker_integrity_fetch_failed,
                    error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
        });
    }

    @Override public boolean onNavigateUp() { finish(); return true; }
    @Override protected void onDestroy() { mWorker.shutdownNow(); super.onDestroy(); }
}
