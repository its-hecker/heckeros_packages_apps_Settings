/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-FileCopyrightText: 2026 HeckerOS
 * SPDX-License-Identifier: Apache-2.0
 * Adapted from Evolution-X/packages_apps_Evolver cnb@84b7ff421f5ce2c0dcdc3f573597f5d4d658f9ba,
 * TrickyStoreAppSettings.kt. HeckerOS stores canonical modes through its private system service.
 */
package com.android.settings.hecker;

import java.util.*;
import org.json.*;

/** One format for the native app list, imported target.txt and verification-mode controls. */
public final class SpoofingTargets {
    public enum Mode {
        AUTO(""), LEAF_HACK("?"), GENERATE("!"), SKIP("-");
        public final String suffix;
        Mode(String suffix){this.suffix=suffix;}
        public static Mode from(String value){
            String name=value.toUpperCase(Locale.ROOT);
            if(name.equals("CERT_GEN")||name.equals("CERT"))return GENERATE;
            if(name.equals("LEAF"))return LEAF_HACK;
            return valueOf(name);
        }
    }
    public static final int MAX_TARGETS=512;
    public static boolean validPackage(String name){
        return name!=null&&name.length()<=255&&(name.equals("android")
                ||name.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"));
    }
    public static LinkedHashMap<String,Mode> parse(String input){
        LinkedHashMap<String,Mode> values=new LinkedHashMap<>();
        if(input==null||input.isBlank())return values;
        if(input.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>256*1024)throw new IllegalArgumentException("Target list is too large");
        try{
            if(input.trim().startsWith("[")){
                JSONArray array=new JSONArray(input);
                for(int i=0;i<array.length();i++){
                    JSONObject item=array.getJSONObject(i);
                    put(values,item.getString("package"),Mode.from(item.optString("mode","AUTO")));
                }
            }else{
                for(String line:input.split("\\R")){
                    line=line.trim();if(line.isEmpty()||line.startsWith("#"))continue;
                    Mode mode=Mode.AUTO;
                    for(Mode candidate:Mode.values())if(!candidate.suffix.isEmpty()&&line.endsWith(candidate.suffix)){
                        mode=candidate;line=line.substring(0,line.length()-1).trim();break;
                    }
                    put(values,line,mode);
                }
            }
        }catch(JSONException e){throw new IllegalArgumentException("Invalid target list");}
        return values;
    }
    private static void put(Map<String,Mode> result,String name,Mode mode){
        if(!validPackage(name)||result.size()>=MAX_TARGETS&&!result.containsKey(name))throw new IllegalArgumentException("Invalid target package or too many targets");
        result.put(name,mode);
    }
    public static String json(Map<String,Mode> targets){
        JSONArray result=new JSONArray();
        try{for(Map.Entry<String,Mode> entry:targets.entrySet()){
            if(!validPackage(entry.getKey())||targets.size()>MAX_TARGETS)throw new IllegalArgumentException("Invalid target list");
            result.put(new JSONObject().put("package",entry.getKey()).put("mode",entry.getValue().name()));
        }}catch(JSONException e){throw new IllegalArgumentException("Invalid target list");}
        return result.toString();
    }
    private SpoofingTargets(){}
}
