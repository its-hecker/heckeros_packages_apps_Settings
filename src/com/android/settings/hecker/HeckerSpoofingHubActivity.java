/* SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.hecker;
/** Private upgrade alias for earlier HeckerOS builds. */
public final class HeckerSpoofingHubActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        startActivity(new android.content.Intent(this,HeckerIntegrityActivity.class));
        finish();
    }
}
