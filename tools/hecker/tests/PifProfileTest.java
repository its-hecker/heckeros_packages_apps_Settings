package com.android.settings.hecker;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
public final class PifProfileTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        String fp = "google/shiba_beta/shiba:17/CP21.260330.011/123456:user/release-keys";
        Map<String,String> fields = new LinkedHashMap<>(Map.of("FINGERPRINT",fp,
                "MODEL","Pixel 8","SECURITY_PATCH","2026-05-05"));
        check(PifProfile.validate(fields).get("FINGERPRINT").equals(fp),"valid profile");
        fields.put("keybox", "not allowed");
        try { PifProfile.validate(fields); throw new AssertionError("unsupported field accepted"); }
        catch(IllegalArgumentException expected) {}
        fields.remove("keybox"); fields.put("FINGERPRINT", "broken");
        try { PifProfile.validate(fields); throw new AssertionError("invalid fingerprint accepted"); }
        catch(IllegalArgumentException expected) {}
        Map<String,String> metadata = PixelBetaProfileFetcher.fromMetadata(
                "PK\u0003\u0004headerpost-build="+fp+"\nsecurity-patch-level=2026-05-05\n",
                "shiba_beta","Pixel 8");
        check("shiba".equals(metadata.get("DEVICE")),"beta device naming");
        check(fp.equals(metadata.get("FINGERPRINT")),"partial OTA metadata extraction");
        try { PixelBetaProfileFetcher.fromMetadata("post-build="+fp+"\n", "shiba_beta", "Pixel 8");
              throw new AssertionError("missing patch accepted"); }
        catch(IOException expected) {}
        System.out.println("Integrity profile and OTA metadata validation: PASS");
    }
}
