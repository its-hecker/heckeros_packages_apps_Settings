/* SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.hecker;
/** Legacy private component kept for old Settings/Suite links. */
public final class HeckerIntegrityActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state){super.onCreate(state);startActivity(new android.content.Intent(this,HeckerSpoofingHubActivity.class));finish();}
}
