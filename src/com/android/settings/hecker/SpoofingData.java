/* SPDX-FileCopyrightText: 2026 HeckerOS
 * SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.hecker;

import android.content.*;
import android.content.pm.*;
import android.os.Process;
import android.provider.Settings;
import android.security.spoof.HeckerSpoofConfig;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.json.*;

/** Maps Evolution X's screens to HeckerOS's existing UID-scoped private configuration store. */
final class SpoofingData {
    static final String PROFILE="hecker_pif_config", PIF_ENABLED="hecker_pif_enabled";
    static final Set<String> XPOSED=Set.of("org.lsposed.manager","io.github.lsposed.manager",
            "de.robv.android.xposed.installer","org.meowcat.edxposed.manager","com.solohsu.android.edxp.manager",
            "io.va.exposed","com.topjohnwu.lsplant.manager","me.weishu.exposed");
    private static final String[] DEFAULTS={"com.android.vending","com.google.android.gsf","com.google.android.gms",
            "com.google.android.contactkeys","com.google.android.ims","com.google.android.safetycore",
            "com.google.android.apps.walletnfcrel","com.google.android.googlequicksearchbox"};
    final Context context;
    final SharedPreferences prefs;
    SpoofingData(Context context){this.context=context.getApplicationContext();prefs=this.context.getSharedPreferences("hecker_spoofing_hub",Context.MODE_PRIVATE);}
    static boolean canManage(){return Process.myUid()==Process.SYSTEM_UID;}
    static boolean eligible(ApplicationInfo info){
        int id=info.uid%100000;
        return info.uid/100000==0&&id>=10000&&id<=19999&&!XPOSED.contains(info.packageName);
    }
    String get(int kind){return HeckerSpoofConfig.get(kind);}
    void put(int kind,String value){HeckerSpoofConfig.put(kind,value);}
    boolean pifEnabled(){return Settings.Global.getInt(context.getContentResolver(),PIF_ENABLED,1)!=0;}
    void pifEnabled(boolean enabled){if(!Settings.Global.putInt(context.getContentResolver(),PIF_ENABLED,enabled?1:0))throw new IllegalStateException("Could not save the enable switch");}
    boolean trickyEnabled(){
        try{String value=get(HeckerSpoofConfig.OPTIONS);return value==null||new JSONObject(value).optBoolean("trickystore_enabled",true);}
        catch(JSONException error){throw new IllegalStateException("Invalid saved options");}
    }
    void trickyEnabled(boolean enabled){
        try{put(HeckerSpoofConfig.OPTIONS,new JSONObject().put("trickystore_enabled",enabled).toString());}
        catch(JSONException error){throw new IllegalStateException(error);}
    }
    Map<String,String> profile(){
        String raw=Settings.Global.getString(context.getContentResolver(),PROFILE);
        if(raw==null||raw.isBlank())return new LinkedHashMap<>();
        return parseProfile(raw);
    }
    static Map<String,String> parseProfile(String raw){
        if(raw.getBytes(StandardCharsets.UTF_8).length>65536)throw new IllegalArgumentException("Profile is too large");
        Map<String,String> fields=new LinkedHashMap<>();
        try{
            if(raw.trim().startsWith("{")){
                JSONObject input=new JSONObject(raw);
                for(Iterator<String> keys=input.keys();keys.hasNext();){String key=keys.next();fields.put(key,String.valueOf(input.get(key)));}
            }else for(String line:raw.split("\\R")){
                line=line.trim();if(line.isEmpty()||line.startsWith("#"))continue;
                int separator=line.indexOf('=');if(separator<=0)throw new IllegalArgumentException("Invalid profile property");
                fields.put(line.substring(0,separator).trim(),line.substring(separator+1).trim());
            }
        }catch(JSONException bad){throw new IllegalArgumentException("Invalid profile JSON");}
        return PifProfile.validate(fields);
    }
    void profile(Map<String,String> value,boolean imported){
        Map<String,String> validated=PifProfile.validate(value);
        if(!Settings.Global.putString(context.getContentResolver(),PROFILE,new JSONObject(validated).toString()))throw new IllegalStateException("Could not save the profile");
        prefs.edit().putBoolean("pif_imported",imported).putLong("pif_fetched",System.currentTimeMillis()).apply();
        // Preserve explicit component overrides; only a plain all-components date follows a new profile.
        String patch=validated.get("SECURITY_PATCH"),existing=get(HeckerSpoofConfig.PATCH);
        if(patch!=null&&(existing==null||existing.isBlank()||simplePatch(existing)))put(HeckerSpoofConfig.PATCH,patch);
    }
    private static boolean simplePatch(String raw){
        try{JSONObject value=new JSONObject(raw);return value.length()==1&&value.has("all");}catch(JSONException bad){return raw.trim().matches("\\d{4}-\\d{2}-\\d{2}");}
    }
    void deleteProfile(){
        if(!Settings.Global.putString(context.getContentResolver(),PROFILE,null))throw new IllegalStateException("Could not delete the profile");
        prefs.edit().remove("pif_imported").remove("pif_fetched").apply();
    }
    LinkedHashMap<String,SpoofingTargets.Mode> targets(){return SpoofingTargets.parse(get(HeckerSpoofConfig.TARGETS));}
    void targets(Map<String,SpoofingTargets.Mode> values){put(HeckerSpoofConfig.TARGETS,SpoofingTargets.json(values));}
    LinkedHashMap<String,SpoofingTargets.Mode> defaults(){
        LinkedHashMap<String,SpoofingTargets.Mode> result=new LinkedHashMap<>();
        for(String pkg:DEFAULTS)try{
            ApplicationInfo info=context.getPackageManager().getApplicationInfo(pkg,0);
            if(eligible(info))result.put(pkg,SpoofingTargets.Mode.AUTO);
        }catch(PackageManager.NameNotFoundException ignored){}
        return result;
    }
    void patch(String date){LocalDate.parse(date);put(HeckerSpoofConfig.PATCH,date);}
    String patch(){
        try{String value=get(HeckerSpoofConfig.PATCH);if(value==null||value.isBlank())return "";
            JSONObject json=new JSONObject(value);String all=json.optString("all");
            return all.matches("\\d{8}")?all.substring(0,4)+"-"+all.substring(4,6)+"-"+all.substring(6):value;
        }catch(JSONException bad){return "";}
    }
    static String error(Throwable error){
        if(error instanceof SecurityException)return "Compatibility management requires primary-user Settings";
        if(error instanceof IllegalArgumentException)return error.getMessage()==null?"Invalid configuration":error.getMessage();
        if(error instanceof java.io.IOException)return error.getMessage()==null?"The source is unavailable":error.getMessage();
        return "The configuration could not be updated";
    }
}
