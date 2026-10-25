/* SPDX-FileCopyrightText: Evolution X
 * SPDX-FileCopyrightText: 2026 HeckerOS
 * SPDX-License-Identifier: Apache-2.0
 * Adapts Evolution X's missing/stale-profile refresh behavior to an opt-in Settings job. */
package com.android.settings.hecker;

import android.app.job.*;
import android.content.*;
import android.os.*;
import android.security.spoof.HeckerSpoofConfig;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

public final class SpoofingMaintenance extends JobService {
    private static final int JOB=0x48435350;
    private static final String NAMESPACE="hecker.spoofing";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile AtomicBoolean stopped=new AtomicBoolean();
    static void schedule(Context context){
        if(!SpoofingData.canManage())return;
        JobScheduler scheduler=context.getSystemService(JobScheduler.class).forNamespace(NAMESPACE);
        SpoofingData data=new SpoofingData(context);
        if(!data.prefs.getBoolean("auto_refresh",false)){scheduler.cancel(JOB);return;}
        if(scheduler.getPendingJob(JOB)!=null)return;
        scheduler.schedule(new JobInfo.Builder(JOB,new ComponentName(context,SpoofingMaintenance.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED).setPersisted(true)
                .setPeriodic(24*60*60*1000L,60*60*1000L).build());
    }
    @Override public boolean onStartJob(JobParameters parameters){
        AtomicBoolean cancellation=new AtomicBoolean();stopped=cancellation;
        worker.execute(()->{try{refresh(new SpoofingData(this),cancellation::get);}catch(RuntimeException unavailable){}finally{if(!cancellation.get())jobFinished(parameters,false);}});return true;
    }
    @Override public boolean onStopJob(JobParameters parameters){stopped.set(true);return true;}
    @Override public void onDestroy(){stopped.set(true);worker.shutdownNow();super.onDestroy();}
    static boolean due(long last,long now){return last<=0||last>now||now-last>=24*60*60*1000L;}
    private static boolean enabled(SpoofingData data,BooleanSupplier stopped){return !stopped.getAsBoolean()&&data.prefs.getBoolean("auto_refresh",false)&&SpoofingData.canManage();}
    static void refresh(SpoofingData data,BooleanSupplier stopped){
        if(!enabled(data,stopped))return;
        long now=System.currentTimeMillis();
        if(data.pifEnabled()&&!data.prefs.getBoolean("pif_imported",false)&&due(data.prefs.getLong("pif_attempt",0),now)){
            data.prefs.edit().putLong("pif_attempt",now).apply();
            try{
                Map<String,String> before=data.profile();String patch=before.get("SECURITY_PATCH");
                boolean stale=before.isEmpty();
                if(patch!=null&&before.getOrDefault("FINGERPRINT","").contains(":CANARY/"))stale|=LocalDate.parse(patch).plusDays(27).isBefore(LocalDate.now());
                if(stale){
                    Map<String,String> devices=PixelCanaryProfileFetcher.devices();
                    String code=before.getOrDefault("DEVICE","bluejay");if(!devices.containsKey(code))code=devices.keySet().iterator().next();
                    Map<String,String> fresh=PixelCanaryProfileFetcher.fetch(code,devices.get(code));
                    for(String key:before.keySet())if(key.startsWith("spoof")||key.equals("DEBUG"))fresh.put(key,before.get(key));
                    if(enabled(data,stopped)&&data.pifEnabled()&&!data.prefs.getBoolean("pif_imported",false)&&data.profile().equals(before))data.profile(fresh,false);
                }
            }catch(Exception unavailable){/* Keep the existing configuration; never substitute a sample profile. */}
        }
        if(!enabled(data,stopped)||!data.trickyEnabled())return;
        if(!due(data.prefs.getLong("keybox_attempt",0),now))return;
        data.prefs.edit().putLong("keybox_attempt",now).apply();
        try{
            String before=data.get(HeckerSpoofConfig.KEYBOX);
            String source=data.prefs.getString("keybox_source",null);
            if("user".equals(source)||(source==null&&before!=null&&!before.isBlank()))return;
            KeyboxStatus existing=KeyboxStatus.check(before,true);
            if(existing.state==KeyboxStatus.State.VALID||existing.state==KeyboxStatus.State.UNKNOWN)return;
            String fresh=KeyboxStatus.xml(SpoofingHttp.get(SpoofingHttp.KEYBOX,256*1024));
            KeyboxStatus checked=KeyboxStatus.check(fresh,true);
            if(checked.state!=KeyboxStatus.State.VALID&&checked.state!=KeyboxStatus.State.EXPIRING)return;
            if(enabled(data,stopped)&&data.trickyEnabled()&&!"user".equals(data.prefs.getString("keybox_source",null))&&Objects.equals(before,data.get(HeckerSpoofConfig.KEYBOX))){
                data.put(HeckerSpoofConfig.KEYBOX,fresh);
                data.prefs.edit().putString("keybox_source","provider").putLong("keybox_fetch",System.currentTimeMillis()).apply();
            }
        }catch(Exception unavailable){/* An unavailable/invalid source must never erase a working keybox. */}
    }
}
