/*
 * Copyright (C) 2025 AxionOS
 * Copyright (C) 2026 HeckerOS
 * SPDX-License-Identifier: Apache-2.0
 * Adapted from AxionAOSP/android_packages_apps_Personalizations PifRepository.kt
 * at 03560671ff9f6530cdb45b5e5eb74cfa72b8541c.
 */
package com.android.settings.hecker;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fetches public beta metadata from Google; never downloads or installs the OTA. */
final class PixelBetaProfileFetcher {
    private static final String GOOGLE = "https://developer.android.com";
    private static final int MAX_PAGE_BYTES = 2 * 1024 * 1024;

    static Map<String, String> fetch() throws IOException {
        String versionsPage = get(GOOGLE + "/about/versions", MAX_PAGE_BYTES, false);
        Matcher versions = Pattern.compile("/about/versions/(\\d+)").matcher(versionsPage);
        TreeSet<Integer> ordered = new TreeSet<>(Comparator.reverseOrder());
        while (versions.find()) ordered.add(Integer.parseInt(versions.group(1)));
        IOException last = new IOException("Google did not list a beta OTA profile");
        for (int version : ordered) {
            try {
                String page = get(GOOGLE + "/about/versions/" + version, MAX_PAGE_BYTES, false);
                Matcher qpr = Pattern.compile("/about/versions/" + version
                        + "/qpr(\\d+)/download-ota").matcher(page);
                TreeSet<Integer> releases = new TreeSet<>(Comparator.reverseOrder());
                while (qpr.find()) releases.add(Integer.parseInt(qpr.group(1)));
                List<String> candidates = new ArrayList<>();
                for (int release : releases) {
                    candidates.add(GOOGLE + "/about/versions/" + version
                            + "/qpr" + release + "/download-ota");
                }
                candidates.add(GOOGLE + "/about/versions/" + version + "/download-ota");
                for (String candidate : candidates) {
                    try {
                        String html = get(candidate, MAX_PAGE_BYTES, false);
                        Matcher links = Pattern.compile("href=\"(https://dl\\.google\\.com/[^\"<>]*ota/"
                                + "([^/\"<>]+_beta)[^\"<>]*)\"").matcher(html);
                        while (links.find()) {
                            String product = links.group(2);
                            String model = modelForProduct(html, product);
                            try {
                                return fromMetadata(get(links.group(1).replace("&amp;", "&"),
                                        4096, true), product, model);
                            } catch (IOException | IllegalArgumentException error) {
                                last = new IOException("Unable to parse Google beta metadata", error);
                            }
                        }
                    } catch (IOException error) { last = error; }
                }
            } catch (IOException error) { last = error; }
        }
        throw last;
    }

    static String modelForProduct(String html, String product) throws IOException {
        // Google's actual OTA URLs can live in modals below the device table.
        // Match the product filename to its row rather than the last preceding cell.
        Matcher rows = Pattern.compile("<tr\\b[^>]*>(.*?)</tr>", Pattern.DOTALL).matcher(html);
        while (rows.find()) {
            String row = rows.group(1);
            if (!row.contains(product + "-ota-")) continue;
            Matcher cell = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL).matcher(row);
            if (!cell.find()) continue;
            String model = cell.group(1).replaceAll("<[^>]*>", "")
                    .replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
            if (model.startsWith("Pixel ")) return model;
        }
        throw new IOException("Cannot match the beta product to Google's device table");
    }

    static Map<String, String> fromMetadata(String metadata, String product, String model)
            throws IOException {
        String fingerprint = property(metadata, "post-build");
        String patch;
        try { patch = property(metadata, "post-security-patch-level"); }
        catch (IOException missingPostPatch) { patch = property(metadata, "security-patch-level"); }
        Map<String, String> profile = new LinkedHashMap<>();
        profile.put("MANUFACTURER", "Google");
        profile.put("MODEL", model);
        profile.put("PRODUCT", product);
        profile.put("DEVICE", product.replace("_beta", ""));
        profile.put("FINGERPRINT", fingerprint);
        profile.put("SECURITY_PATCH", patch);
        return PifProfile.validate(profile);
    }

    private static String property(String metadata, String key) throws IOException {
        Matcher value = Pattern.compile("(?m)(?:^|\\n)" + Pattern.quote(key)
                + "=([^\\r\\n\\x00]+)").matcher(metadata);
        // ZIP entry header bytes can precede the first key; require only its exact key spelling.
        if (!value.find()) {
            value = Pattern.compile(Pattern.quote(key) + "=([^\\r\\n\\x00]+)")
                    .matcher(metadata);
            if (!value.find()) throw new IOException("OTA metadata has no " + key);
        }
        return value.group(1).trim();
    }

    private static String get(String address, int limit, boolean partial) throws IOException {
        URL url = new URL(address);
        if (!"https".equals(url.getProtocol()) || !("developer.android.com".equals(url.getHost())
                || "dl.google.com".equals(url.getHost()))) {
            throw new IOException("Unexpected metadata host");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        if (partial) connection.setRequestProperty("Range", "bytes=0-4095");
        try {
            int status = connection.getResponseCode();
            if (status != 200 && !(partial && status == 206)) {
                throw new IOException("Google metadata request returned " + status);
            }
            try (InputStream input = connection.getInputStream()) {
                byte[] bytes = input.readNBytes(partial ? limit : limit + 1);
                if (!partial && bytes.length > limit) throw new IOException("Metadata page too large");
                return new String(bytes, partial ? StandardCharsets.ISO_8859_1
                        : StandardCharsets.UTF_8);
            }
        } finally { connection.disconnect(); }
    }
}
