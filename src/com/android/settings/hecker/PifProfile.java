/* SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.hecker;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Validates public build metadata before it reaches the framework's process-local adapter. */
public final class PifProfile {
    private static final Set<String> FIELDS = Set.of("MANUFACTURER", "MODEL", "PRODUCT",
            "DEVICE", "FINGERPRINT", "SECURITY_PATCH", "DEVICE_INITIAL_SDK_INT", "BRAND",
            "HARDWARE", "ID", "RELEASE", "INCREMENTAL", "TYPE", "TAGS");
    private static final Set<String> FLAGS = Set.of("spoofBuild", "spoofProps", "spoofProvider",
            "spoofSignature", "spoofVendingBuild", "spoofVendingSdk", "spoofPhotos", "DEBUG");

    public static Map<String, String> validate(Map<String, String> source) {
        if (source.size() > 32) throw new IllegalArgumentException("Too many profile fields");
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (!FIELDS.contains(key) && !FLAGS.contains(key)) {
                throw new IllegalArgumentException("Unsupported profile field: " + key);
            }
            if (value == null || value.isBlank() || value.length() > 512
                    || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0
                    || value.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("Invalid value for " + key);
            }
            if (FLAGS.contains(key) && !Set.of("0", "1", "true", "false").contains(value)) {
                throw new IllegalArgumentException("Invalid switch: " + key);
            }
            result.put(key, value);
        }
        String fingerprint = result.get("FINGERPRINT");
        if (fingerprint == null || !fingerprint.matches(
                "[^/\\s:]+/[^/\\s:]+/[^/\\s:]+:[^/\\s:]+/[^/\\s:]+/[^/\\s:]+:[^/\\s:]+/[^/\\s:]+")) {
            throw new IllegalArgumentException("A valid build fingerprint is required");
        }
        if (result.containsKey("SECURITY_PATCH")) LocalDate.parse(result.get("SECURITY_PATCH"));
        if (result.containsKey("DEVICE_INITIAL_SDK_INT")) {
            int sdk = Integer.parseInt(result.get("DEVICE_INITIAL_SDK_INT"));
            if (sdk < 23 || sdk > 99) throw new IllegalArgumentException("Invalid initial SDK");
        }
        return result;
    }
}
