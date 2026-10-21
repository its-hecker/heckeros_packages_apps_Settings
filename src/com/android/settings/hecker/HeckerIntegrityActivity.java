/* SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.hecker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.security.spoof.HeckerSpoofConfig;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.android.settings.R;

import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
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
    private int mImportKind;
    private final List<Button> mConfigButtons = new ArrayList<>();
    private final Map<Integer, TextView> mConfigStatus = new LinkedHashMap<>();

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        mImportKind = savedState == null ? 0 : savedState.getInt("import_kind", 0);
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
        mImport.setOnClickListener(view -> pick(0));
        content.addView(mImport);
        mDisable = new Button(this);
        mDisable.setText(R.string.hecker_integrity_disable);
        mDisable.setOnClickListener(view -> {
            if (Settings.Global.putString(getContentResolver(), KEY, null)) {
                mStatus.setText(R.string.hecker_integrity_disabled_restart);
            } else mStatus.setText(R.string.hecker_integrity_save_failed);
        });
        content.addView(mDisable);
        addSection(content, R.string.hecker_spoof_game_title, R.string.hecker_spoof_game_description,
                HeckerSpoofConfig.GAMES, false);
        addSection(content, R.string.hecker_spoof_target_title, R.string.hecker_spoof_target_description,
                HeckerSpoofConfig.TARGETS, true);
        addSection(content, R.string.hecker_spoof_keybox_title, R.string.hecker_spoof_keybox_description,
                HeckerSpoofConfig.KEYBOX, false);
        addSection(content, R.string.hecker_spoof_patch_title, R.string.hecker_spoof_patch_description,
                HeckerSpoofConfig.PATCH, true);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);
        String current = Settings.Global.getString(getContentResolver(), KEY);
        mStatus.setText(current == null || current.isEmpty()
                ? getString(R.string.hecker_integrity_disabled)
                : getString(R.string.hecker_integrity_current, currentModel(current)));
        refreshConfigStatus();
    }

    private void pick(int kind) {
        mImportKind = kind;
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), PICK_PROFILE);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("import_kind", mImportKind);
        super.onSaveInstanceState(state);
    }

    @Override protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        mImportKind = state.getInt("import_kind", 0);
    }

    private Button button(LinearLayout content, int title, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(title);
        button.setOnClickListener(listener);
        content.addView(button);
        mConfigButtons.add(button);
        return button;
    }

    private void addSection(LinearLayout content, int title, int description, int kind, boolean editable) {
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(20);
        heading.setPadding(0, 40, 0, 12);
        heading.setAccessibilityHeading(true);
        content.addView(heading);
        TextView detail = new TextView(this);
        detail.setText(description);
        content.addView(detail);
        TextView status = new TextView(this);
        content.addView(status);
        mConfigStatus.put(kind, status);
        if (editable) button(content, R.string.hecker_spoof_edit, view -> edit(kind));
        button(content, R.string.hecker_spoof_import, view -> pick(kind));
        button(content, kind == HeckerSpoofConfig.KEYBOX ? R.string.hecker_spoof_remove_keybox
                : R.string.hecker_spoof_disable, view -> saveConfig(kind, null));
    }

    private void refreshConfigStatus() {
        mWorker.execute(() -> {
            for (Map.Entry<Integer, TextView> entry : mConfigStatus.entrySet()) {
                boolean configured;
                try {
                    String value = HeckerSpoofConfig.get(entry.getKey());
                    configured = value != null && !value.isEmpty();
                } catch (RuntimeException error) { continue; }
                final boolean present = configured;
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) entry.getValue().setText(present
                            ? R.string.hecker_spoof_configured : R.string.hecker_spoof_unconfigured);
                });
            }
        });
    }

    private void edit(int kind) {
        try {
            EditText input = new EditText(this);
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            input.setMinLines(5);
            String current = HeckerSpoofConfig.get(kind);
            input.setText(current == null ? "" : current);
            input.setHint(kind == HeckerSpoofConfig.TARGETS ? R.string.hecker_spoof_target_hint
                    : R.string.hecker_spoof_patch_hint);
            new AlertDialog.Builder(this).setTitle(kind == HeckerSpoofConfig.TARGETS
                    ? R.string.hecker_spoof_target_title : R.string.hecker_spoof_patch_title)
                    .setView(input).setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok,
                            (dialog, which) -> saveConfig(kind, input.getText().toString()))
                    .show();
        } catch (RuntimeException error) { showError(error); }
    }

    private void saveConfig(int kind, String value) {
        busy();
        mWorker.execute(() -> {
            try {
                HeckerSpoofConfig.put(kind, value);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    idle();
                    mStatus.setText(R.string.hecker_spoof_saved);
                    refreshConfigStatus();
                });
            } catch (RuntimeException error) { showError(error); }
        });
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
        for (Button button : mConfigButtons) button.setEnabled(false);
        mStatus.setText(R.string.hecker_integrity_loading);
    }

    private void idle() {
        mFetch.setEnabled(true);
        mImport.setEnabled(true);
        mDisable.setEnabled(true);
        for (Button button : mConfigButtons) button.setEnabled(true);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_PROFILE || result != RESULT_OK || data == null || data.getData() == null) return;
        final android.net.Uri uri = data.getData();
        final int kind = mImportKind;
        busy();
        mWorker.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalArgumentException("Unable to open profile");
                int limit = kind == 0 ? 65536 : HeckerSpoofConfig.MAX_BYTES;
                byte[] bytes = input.readNBytes(limit + 1);
                if (bytes.length > limit) throw new IllegalArgumentException("Profile is too large");
                String text = new String(bytes, StandardCharsets.UTF_8).trim();
                if (kind != 0) {
                    HeckerSpoofConfig.put(kind, text);
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        idle();
                        mStatus.setText(R.string.hecker_spoof_saved);
                        refreshConfigStatus();
                    });
                    return;
                }
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
        idle();
        if (Settings.Global.putString(getContentResolver(), KEY, new JSONObject(profile).toString())) {
            mStatus.setText(getString(R.string.hecker_integrity_saved,
                    profile.getOrDefault("MODEL", "Custom profile")));
        } else mStatus.setText(R.string.hecker_integrity_save_failed);
    }

    private void showError(Exception error) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            idle();
            mStatus.setText(getString(R.string.hecker_integrity_fetch_failed,
                    error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
        });
    }

    @Override public boolean onNavigateUp() { finish(); return true; }
    @Override protected void onDestroy() { mWorker.shutdownNow(); super.onDestroy(); }
}
