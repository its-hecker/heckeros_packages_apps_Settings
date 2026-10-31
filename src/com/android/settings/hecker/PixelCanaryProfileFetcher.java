/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-FileCopyrightText: 2026 HeckerOS
 * SPDX-License-Identifier: Apache-2.0
 * Adapted from Evolution-X/frameworks_base cnb@6e9da54c4d0e71acca90091106c846919395410c,
 * PixelDeviceRepository.java. Fetch real public metadata; do not silently substitute a bundled profile.
 */
package com.android.settings.hecker;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;
import org.json.*;

final class PixelCanaryProfileFetcher {
    private static final String GOOGLE="https://developer.android.com";
    static Map<String,String> devices() throws IOException{
        String versions=SpoofingHttp.get(GOOGLE+"/about/versions",2*1024*1024);
        TreeSet<Integer> candidates=new TreeSet<>(Comparator.reverseOrder());
        Matcher match=Pattern.compile("/about/versions/(\\d+)").matcher(versions);
        while(match.find())candidates.add(Integer.parseInt(match.group(1)));
        IOException last=new IOException("Google did not publish a compatible device list");
        int checked=0;
        for(int version:candidates){
            if(++checked>4)break;
            try{
                String landing=SpoofingHttp.get(GOOGLE+"/about/versions/"+version,2*1024*1024);
                Matcher qpr=Pattern.compile("(/about/versions/"+version+"/qpr(\\d+)/download-ota)").matcher(landing);
                TreeMap<Integer,String> pages=new TreeMap<>(Comparator.reverseOrder());
                while(qpr.find())pages.put(Integer.parseInt(qpr.group(2)),qpr.group(1));
                if(pages.isEmpty())pages.put(0,"/about/versions/"+version+"/download-ota");
                String page=pages.firstEntry().getValue();
                LinkedHashMap<String,String> names=new LinkedHashMap<>(parseDevices(SpoofingHttp.get(GOOGLE+page,2*1024*1024)));
                try{parseDevices(SpoofingHttp.get(GOOGLE+page.replace("/download-ota","/download"),2*1024*1024)).forEach(names::putIfAbsent);}catch(IOException ignored){}
                if(!names.isEmpty())return names;
            }catch(IOException error){last=error;}
        }
        throw last;
    }
    static Map<String,String> parseDevices(String html){
        LinkedHashMap<String,String> result=new LinkedHashMap<>();
        Matcher rows=Pattern.compile("<tr\\b[^>]*\\bid=[\"']([a-z0-9_]+)[\"'][^>]*>\\s*<td[^>]*>(.*?)</td>",Pattern.DOTALL|Pattern.CASE_INSENSITIVE).matcher(html);
        while(rows.find()){
            String name=rows.group(2).replaceAll("<[^>]*>","").replace("&nbsp;"," ").replace("&amp;","&").replaceAll("\\s+"," ").trim();
            if(name.startsWith("Pixel ")&&result.size()<64)result.put(rows.group(1).replace("_beta",""),name);
        }
        return result;
    }
    static Map<String,String> fetch(String device,String model) throws IOException{
        if(device==null||!device.matches("[a-z0-9_]{1,40}")||model==null||!model.startsWith("Pixel "))throw new IOException("Invalid Pixel selection");
        String flash=SpoofingHttp.get("https://flash.android.com",2*1024*1024);
        Matcher key=Pattern.compile("AIza[0-9A-Za-z_-]{35}").matcher(flash);
        if(!key.find())throw new IOException("Google Flash Tool metadata is unavailable");
        String apiKey=key.group(),product=device+"_beta";
        String json=SpoofingHttp.get("https://content-flashstation-pa.googleapis.com/v1/builds?product="
                +URLEncoder.encode(product,StandardCharsets.UTF_8)+"&key="+URLEncoder.encode(apiKey,StandardCharsets.UTF_8),2*1024*1024,
                Map.of("Referer","https://flash.android.com","X-Goog-Api-Key",apiKey));
        return fromBuilds(json,device,model);
    }
    static Map<String,String> fromBuilds(String json,String device,String model) throws IOException{
        try{
            JSONArray builds=new JSONObject(json).getJSONArray("flashstationBuild");JSONObject best=null;long rank=-1;
            for(int i=0;i<builds.length();i++){
                JSONObject candidate=builds.optJSONObject(i);if(candidate==null)continue;
                JSONObject metadata=candidate.optJSONObject("previewMetadata");
                if(metadata==null||!metadata.optBoolean("canary",false))continue;
                long number;try{number=Long.parseLong(candidate.optString("buildId"));}catch(NumberFormatException bad){continue;}
                if(number>rank&&!candidate.optString("releaseCandidateName").isBlank()){rank=number;best=candidate;}
            }
            if(best==null)throw new IOException("Google has no Canary build for this device");
            String id=best.getString("releaseCandidateName"),incremental=best.getString("buildId");
            Matcher month=Pattern.compile("canary-(\\d{4})(\\d{2})").matcher(best.getJSONObject("previewMetadata").optString("id"));
            String patch;
            if(month.find())patch=month.group(1)+"-"+month.group(2)+"-05";
            else{
                Matcher date=Pattern.compile("\\.(\\d{2})(\\d{2})(\\d{2})\\.").matcher(id);
                if(!date.find())throw new IOException("Canary metadata has no patch month");
                patch="20"+date.group(1)+"-"+date.group(2)+"-05";
            }
            LocalDate.parse(patch);
            Map<String,String> profile=new LinkedHashMap<>();
            profile.put("MANUFACTURER","Google");profile.put("BRAND","google");profile.put("MODEL",model);
            profile.put("PRODUCT",device+"_beta");profile.put("DEVICE",device);profile.put("ID",id);
            profile.put("FINGERPRINT","google/"+device+"_beta/"+device+":CANARY/"+id+"/"+incremental+":user/release-keys");
            profile.put("SECURITY_PATCH",patch);profile.put("DEVICE_INITIAL_SDK_INT","32");
            return PifProfile.validate(profile);
        }catch(IOException error){throw error;}catch(Exception invalid){throw new IOException("Invalid Google Canary metadata");}
    }
    private PixelCanaryProfileFetcher(){}
}
