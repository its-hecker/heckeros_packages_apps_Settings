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
    private com.airbnb.lottie.LottieAnimationView mArt;
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
        if (getActionBar() != null) getActionBar().hide();
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        content.setPadding(padding, padding, padding, padding);
        LinearLayout hero = card();
        mArt = new com.airbnb.lottie.LottieAnimationView(this);
        mArt.setAnimation(R.raw.hecker_integrity);
        mArt.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        mArt.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        hero.addView(mArt, new LinearLayout.LayoutParams(-1, dp(156)));
        TextView description = label(15, false);
        description.setText(R.string.hecker_integrity_description);
        hero.addView(description); content.addView(hero);
        LinearLayout profile = card();
        mStatus = label(16, true);
        mStatus.setPadding(0, padding, 0, padding);
        mStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        profile.addView(mStatus);
        mFetch = new Button(this);
        mFetch.setText(R.string.hecker_integrity_fetch);
        mFetch.setOnClickListener(view -> fetchProfile());
        styleButton(mFetch);
        profile.addView(mFetch);
        mImport = new Button(this);
        mImport.setText(R.string.hecker_integrity_import);
        mImport.setOnClickListener(view -> pick(0));
        styleButton(mImport);
        profile.addView(mImport);
        mDisable = new Button(this);
        mDisable.setText(R.string.hecker_integrity_disable);
        mDisable.setOnClickListener(view -> {
            if (Settings.Global.putString(getContentResolver(), KEY, null)) {
                mStatus.setText(R.string.hecker_integrity_disabled_restart);
            } else mStatus.setText(R.string.hecker_integrity_save_failed);
        });
        styleButton(mDisable);
        profile.addView(mDisable);
        content.addView(profile);
        addSection(content, R.string.hecker_spoof_game_title, R.string.hecker_spoof_game_description,
                HeckerSpoofConfig.GAMES, false);
        addSection(content, R.string.hecker_spoof_target_title, R.string.hecker_spoof_target_description,
                HeckerSpoofConfig.TARGETS, true);
        addSection(content, R.string.hecker_spoof_keybox_title, R.string.hecker_spoof_keybox_description,
                HeckerSpoofConfig.KEYBOX, false);
        addSection(content, R.string.hecker_spoof_patch_title, R.string.hecker_spoof_patch_description,
                HeckerSpoofConfig.PATCH, true);
        present(content);
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
        styleButton(button);
        button.setText(title);
        button.setOnClickListener(listener);
        content.addView(button);
        mConfigButtons.add(button);
        return button;
    }

    private void addSection(LinearLayout content, int title, int description, int kind, boolean editable) {
        LinearLayout section = card();
        TextView heading = label(20, true);
        heading.setText(title);
        heading.setAccessibilityHeading(true);
        section.addView(heading);
        TextView detail = label(14, false);
        detail.setText(description); section.addView(detail);
        TextView status = label(14, true); section.addView(status);
        mConfigStatus.put(kind, status);
        if (editable) button(section, R.string.hecker_spoof_edit, view -> edit(kind));
        button(section, R.string.hecker_spoof_import, view -> pick(kind));
        button(section, kind == HeckerSpoofConfig.KEYBOX ? R.string.hecker_spoof_remove_keybox
                : R.string.hecker_spoof_disable, view -> saveConfig(kind, null));
        content.addView(section);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private boolean dark() { return (getResources().getConfiguration().uiMode & 48) == 32; }
    private int foreground() { return dark() ? 0xffeef4ff : 0xff15243c; }
    private TextView label(int size, boolean bold) {
        TextView t = new TextView(this); t.setTextSize(size); t.setTextColor(foreground());
        t.setPadding(0, dp(6), 0, dp(6));
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return t;
    }
    private android.graphics.drawable.GradientDrawable surface(int color, int radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(20), dp(16), dp(20), dp(16));
        c.setBackground(surface(dark() ? 0xff17253c : 0xffedf3ff, 26));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(16); c.setLayoutParams(lp); return c;
    }
    private void styleButton(Button b) {
        b.setAllCaps(false); b.setTextSize(14); b.setTextColor(foreground());
        b.setMinHeight(dp(48)); b.setPadding(dp(16), dp(12), dp(16), dp(12));
        b.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x335eb8ff),
                surface(dark() ? 0xff283f61 : 0xffd6e5ff, 18), null));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8); b.setLayoutParams(lp);
    }
    private void present(LinearLayout content) {
        getWindow().setDecorFitsSystemWindows(false);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(dark() ? 0xff0c1526 : 0xfff7f9ff);
        LinearLayout toolbar = new LinearLayout(this); toolbar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(16), dp(8), dp(16), dp(8)); toolbar.setMinimumHeight(dp(56));
        Button back = new Button(this); styleButton(back); back.setText("‹"); back.setTextSize(28);
        back.setContentDescription(getString(R.string.hecker_integrity_back)); back.setOnClickListener(v -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(52), -2));
        TextView title = label(22, true); title.setText(getTitle()); title.setAccessibilityHeading(true);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(toolbar);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.displayCutout() | android.view.WindowInsets.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return android.view.WindowInsets.CONSUMED;
        });
        setContentView(root); root.post(root::requestApplyInsets);
    }
    @Override protected void onStart() {
        super.onStart();
        if (mArt != null && android.animation.ValueAnimator.areAnimatorsEnabled()) mArt.playAnimation();
        else if (mArt != null) mArt.setProgress(.5f);
    }
    @Override protected void onStop() { if (mArt != null) mArt.cancelAnimation(); super.onStop(); }

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
