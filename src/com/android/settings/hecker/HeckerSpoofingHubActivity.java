/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-FileCopyrightText: 2026 HeckerOS
 * SPDX-License-Identifier: Apache-2.0
 * Native Settings adaptation of Evolution X cnb@84b7ff421f5ce2c0dcdc3f573597f5d4d658f9ba:
 * Spoofing.kt, PlayIntegrityFix*.kt, TrickyStore*.kt and SpoofingSharedUi.kt.
 */
package com.android.settings.hecker;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Typeface;
import android.os.*;
import android.provider.Settings;
import android.security.spoof.HeckerSpoofConfig;
import android.text.*;
import android.view.*;
import android.widget.*;
import com.android.settings.R;
import com.airbnb.lottie.LottieAnimationView;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** One private native Settings host. Private key material never enters the Suite service UID. */
public final class HeckerSpoofingHubActivity extends Activity {
    private static final int IMPORT=45, PROFILE=0, TARGETS=1, KEYBOX=2, GAMES=3;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private SpoofingData data;private SpoofingUi ui;
    private String page="hub",search="";private int importKind;private boolean showSystem,busy,ready,started;
    private int generation;
    private int completedMessage=R.string.hub_saved;
    private boolean pifEnabled=true,trickyEnabled=true;
    private Map<String,String> profile=new LinkedHashMap<>();
    private LinkedHashMap<String,SpoofingTargets.Mode> targets=new LinkedHashMap<>();
    private String patch="",keybox="",games="";
    private KeyboxStatus status=new KeyboxStatus(KeyboxStatus.State.MISSING,0,0,0);
    private List<App> apps=new ArrayList<>();private TargetAdapter adapter;private ListView list;
    private LottieAnimationView art;
    private ScrollView scroll;private String shownPage="hub";
    private final Map<String,Integer> scrollPositions=new HashMap<>();
    private Parcelable targetPosition;private android.net.Uri pendingImport;
    private List<App> installedApps;
    private final class App {
        String pkg,label;ApplicationInfo info;boolean system,eligible;
        App(ApplicationInfo info){this.info=info;pkg=info.packageName;label=info.loadLabel(getPackageManager()).toString();system=(info.flags&ApplicationInfo.FLAG_SYSTEM)!=0;eligible=SpoofingData.eligible(info);}
        App(String pkg){this.pkg=pkg;label=pkg;}
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);if(getActionBar()!=null)getActionBar().hide();getWindow().setDecorFitsSystemWindows(false);
        data=new SpoofingData(this);ui=new SpoofingUi(this);
        if(state!=null){page=state.getString("page","hub");importKind=state.getInt("import_kind",0);search=state.getString("query","");showSystem=state.getBoolean("system",false);pendingImport=state.getParcelable("pending_import",android.net.Uri.class);targetPosition=state.getParcelable("target_position");}
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::back);
        render();reload();
    }
    @Override protected void onSaveInstanceState(Bundle out){rememberPosition();super.onSaveInstanceState(out);out.putString("page",page);out.putInt("import_kind",importKind);out.putString("query",search);out.putBoolean("system",showSystem);out.putParcelable("pending_import",pendingImport);out.putParcelable("target_position",targetPosition);}
    @Override protected void onStart(){super.onStart();started=true;play();}
    @Override protected void onStop(){started=false;if(art!=null)art.cancelAnimation();super.onStop();}
    @Override protected void onDestroy(){generation++;worker.shutdownNow();if(art!=null)art.cancelAnimation();super.onDestroy();}
    private void play(){if(art!=null&&started){
        if(android.animation.ValueAnimator.areAnimatorsEnabled()&&!getSystemService(PowerManager.class).isPowerSaveMode())art.playAnimation();else art.setProgress(.5f);
    }}
    private void back(){if(page.equals("targets"))open("tricky");else if(!page.equals("hub"))open("hub");else finish();}
    private void open(String page){this.page=page;render();}
    private interface Work {void run() throws Exception;}
    private void operation(Work work){
        if(busy||!ready)return;if(!SpoofingData.canManage()){toast(R.string.hub_primary_only);return;}
        busy=true;completedMessage=R.string.hub_saved;render();int request=++generation;
        worker.execute(()->{
            try{work.run();runOnUiThread(()->{if(alive(request)){busy=false;toast(completedMessage);reload();}});}
            catch(Exception error){runOnUiThread(()->{if(alive(request)){busy=false;Toast.makeText(this,getString(R.string.hub_error,SpoofingData.error(error)),Toast.LENGTH_LONG).show();render();}});}
        });
    }
    private boolean alive(int request){return request==generation&&!isDestroyed()&&!isFinishing();}
    private void reload(){
        if(!SpoofingData.canManage()){ready=true;render();return;}
        ready=false;render();
        int request=++generation;
        worker.execute(()->{
            try{
                boolean pe=data.pifEnabled(),te=data.trickyEnabled();Map<String,String> pp;
                try{pp=data.profile();}catch(IllegalArgumentException corrupt){pp=new LinkedHashMap<>();}
                if(data.get(HeckerSpoofConfig.TARGETS)==null)data.targets(data.defaults());
                LinkedHashMap<String,SpoofingTargets.Mode> tt=data.targets();
                String kk=data.get(HeckerSpoofConfig.KEYBOX),patch=data.patch(),games=data.get(HeckerSpoofConfig.GAMES);
                KeyboxStatus checked=keybox!=null&&keybox.equals(kk)?status:KeyboxStatus.check(kk,false);
                // Package labels are resolved once on the worker, not again after every toggle.
                if(installedApps==null){installedApps=new ArrayList<>();for(ApplicationInfo info:getPackageManager().getInstalledApplications(0))installedApps.add(new App(info));}
                List<App> found=new ArrayList<>(installedApps);Set<String> seen=new HashSet<>();
                for(App app:found)seen.add(app.pkg);
                for(String pkg:tt.keySet())if(!seen.contains(pkg))found.add(new App(pkg));
                final Map<String,String> resolved=pp;
                runOnUiThread(()->{if(!alive(request))return;pifEnabled=pe;trickyEnabled=te;profile=resolved;targets=tt;keybox=kk;this.patch=patch;this.games=games;status=checked;apps=found;ready=true;SpoofingMaintenance.schedule(this);render();consumeImport();});
            }catch(Exception error){runOnUiThread(()->{if(alive(request)){ready=true;Toast.makeText(this,getString(R.string.hub_error,SpoofingData.error(error)),Toast.LENGTH_LONG).show();render();}});}
        });
    }
    private void render(){
        if(isDestroyed()||isFinishing())return;rememberPosition();scroll=null;if(art!=null){art.cancelAnimation();art=null;}adapter=null;list=null;shownPage=page;
        int title=page.equals("tricky")?R.string.hub_tricky:page.equals("pif")?R.string.hub_pif:page.equals("targets")?R.string.hub_targets:page.equals("games")?R.string.hub_games:R.string.hub_title;
        setTitle(title);LinearLayout root=ui.root(title,this::back);
        if(page.equals("targets")){renderTargets(root);return;}
        LinearLayout content=ui.column();
        if(!SpoofingData.canManage()){TextView note=ui.text(R.string.hub_primary_only,15,false);note.setPadding(0,ui.dp(12),0,ui.dp(16));content.addView(note);}
        if(!ready||busy){TextView loading=ui.text(R.string.hub_loading,14,false);loading.setPadding(ui.dp(4),ui.dp(6),0,ui.dp(12));content.addView(loading);}
        if(page.equals("hub"))renderHub(content);else if(page.equals("tricky"))renderTricky(content);else if(page.equals("pif"))renderPif(content);else renderGames(content);
        scroll=ui.scroll(content);ui.present(root,scroll);final ScrollView current=scroll;
        current.post(()->current.scrollTo(0,scrollPositions.getOrDefault(page,0)));
    }
    private void rememberPosition(){if(scroll!=null)scrollPositions.put(shownPage,scroll.getScrollY());if(list!=null)targetPosition=list.onSaveInstanceState();}
    private boolean editable(){return ready&&!busy&&SpoofingData.canManage();}
    private void renderHub(LinearLayout page){
        LinearLayout hero=ui.card();art=new LottieAnimationView(this);art.setAnimation(R.raw.hecker_spoofing_hub);art.setRepeatCount(-1);art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        hero.addView(art,new LinearLayout.LayoutParams(-1,ui.dp(132)));TextView intro=ui.text(R.string.hub_intro,14,false);intro.setTextColor(ui.muted);hero.addView(intro);page.addView(hero);play();
        ui.heading(page,R.string.hub_integrity_category);
        ui.switchEntry(page,R.string.hub_tricky,getString(R.string.hub_tricky_summary),trickyEnabled,editable(),()->open("tricky"),value->operation(()->data.trickyEnabled(value)));
        ui.switchEntry(page,R.string.hub_pif,getString(R.string.hub_pif_summary),pifEnabled,editable(),()->open("pif"),value->operation(()->data.pifEnabled(value)));
        ui.heading(page,R.string.hub_app_category);
        ui.entry(page,R.string.hub_games,getString(R.string.hub_games_summary),()->open("games"));
        ui.heading(page,R.string.hub_maintenance);
        ui.switchEntry(page,R.string.hub_auto_refresh,getString(R.string.hub_auto_summary),data.prefs.getBoolean("auto_refresh",false),editable(),null,value->{
            if(!editable())return;data.prefs.edit().putBoolean("auto_refresh",value).apply();SpoofingMaintenance.schedule(this);render();
        });
        TextView note=ui.text(R.string.hub_verdict_note,12,false);note.setTextColor(ui.muted);note.setPadding(ui.dp(4),ui.dp(18),ui.dp(4),0);page.addView(note);
    }
    private void renderTricky(LinearLayout page){
        ui.switchEntry(page,R.string.hub_tricky,getString(keybox==null||keybox.isBlank()?R.string.hub_key_missing:R.string.hub_key_configured,targets.size()),trickyEnabled,editable(),null,value->operation(()->data.trickyEnabled(value)));
        Button fetch=ui.button(R.string.hub_fetch_keybox,()->operation(this::fetchKeybox));
        boolean valid=status.state==KeyboxStatus.State.VALID&&System.currentTimeMillis()-status.checkedAt<86400000L;
        fetch.setEnabled(editable()&&trickyEnabled&&!valid);fetch.setAlpha(fetch.isEnabled()?1f:.45f);page.addView(fetch,new LinearLayout.LayoutParams(-1,-2));
        TextView helper=ui.text(valid?R.string.hub_no_fetch_needed:R.string.hub_fetch_source,13,false);helper.setTextColor(ui.muted);helper.setPadding(ui.dp(12),ui.dp(8),ui.dp(12),ui.dp(14));page.addView(helper);
        ui.buttons(page,new int[]{R.string.hub_import,R.string.hub_delete},new Runnable[]{()->pick(KEYBOX),()->confirm(R.string.hub_delete_keybox,()->operation(()->{data.put(HeckerSpoofConfig.KEYBOX,null);data.prefs.edit().remove("keybox_source").apply();status=new KeyboxStatus(KeyboxStatus.State.MISSING,0,0,0);}))});
        ui.heading(page,R.string.hub_key_management);
        String summary=getString(statusLabel(status.state));
        if(status.checkedAt>0)summary+="\n"+getString(R.string.hub_last_checked,DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(status.checkedAt)));
        LinearLayout statusRow=ui.entry(page,R.string.hub_check_revocation,summary,()->operation(()->{String raw=data.get(HeckerSpoofConfig.KEYBOX);KeyboxStatus result=KeyboxStatus.check(raw,true,data.prefs);if(Objects.equals(raw,data.get(HeckerSpoofConfig.KEYBOX)))status=result;}));
        if(status.state==KeyboxStatus.State.VALID)ui.badge(statusRow,R.string.hub_valid_badge);
        ui.heading(page,R.string.hub_target_configuration);
        ui.entry(page,R.string.hub_manage_targets,getString(R.string.hub_target_count,targets.size()),()->open("targets"));
        ui.entry(page,R.string.hub_import_targets,getString(R.string.hub_import_targets_summary),()->pick(TARGETS));
        ui.entry(page,R.string.hub_verification,modeSummary(),this::bulkMode);
        ui.entry(page,R.string.hub_patch,patch.isBlank()?getString(R.string.hub_not_set):patch,this::editPatch);
        TextView note=ui.text(R.string.hub_tricky_note,13,false);note.setTextColor(ui.muted);note.setPadding(ui.dp(8),ui.dp(22),ui.dp(8),0);page.addView(note);
    }
    private void fetchKeybox() throws Exception{
        String before=data.get(HeckerSpoofConfig.KEYBOX),raw=KeyboxStatus.xml(SpoofingHttp.get(SpoofingHttp.KEYBOX,256*1024));
        KeyboxStatus checked=KeyboxStatus.check(raw,true,data.prefs);
        if(!KeyboxStatus.installable(checked))throw new java.io.IOException(getString(R.string.hub_key_fetch_reason,getString(statusLabel(checked.state))));
        if(!Objects.equals(before,data.get(HeckerSpoofConfig.KEYBOX))||!data.trickyEnabled())throw new IllegalStateException("Configuration changed during fetch");
        data.put(HeckerSpoofConfig.KEYBOX,raw);data.prefs.edit().putString("keybox_source","provider").putLong("keybox_fetch",System.currentTimeMillis()).apply();status=checked;
        // Attestation caches belong to the running processes; match Evolution X’s refresh behavior.
        try{restartGoogle();}catch(Exception unavailable){completedMessage=R.string.hub_restart_needed;}
        keybox=raw; // Keep the live certificate status when the page reloads.
        if(checked.state==KeyboxStatus.State.UNKNOWN)completedMessage=R.string.hub_key_saved_unknown;
    }
    private String modeSummary(){
        EnumMap<SpoofingTargets.Mode,Integer> counts=new EnumMap<>(SpoofingTargets.Mode.class);
        for(SpoofingTargets.Mode mode:targets.values())counts.put(mode,counts.getOrDefault(mode,0)+1);
        List<String> out=new ArrayList<>();for(SpoofingTargets.Mode mode:SpoofingTargets.Mode.values())if(counts.containsKey(mode))out.add(counts.get(mode)+" "+getString(modeLabel(mode)));
        return out.isEmpty()?getString(R.string.hub_no_targets):String.join(" · ",out);
    }
    private void bulkMode(){if(!editable())return;
        String[] labels=Arrays.stream(SpoofingTargets.Mode.values()).map(mode->getString(modeLabel(mode))).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle(R.string.hub_verification).setItems(labels,(dialog,index)->{
            LinkedHashMap<String,SpoofingTargets.Mode> value=new LinkedHashMap<>(targets);value.replaceAll((pkg,mode)->SpoofingTargets.Mode.values()[index]);operation(()->data.targets(value));
        }).setNegativeButton(android.R.string.cancel,null).show();
    }
    private void editPatch(){if(!editable())return;
        EditText input=new EditText(this);input.setSingleLine();input.setHint("YYYY-MM-DD");input.setText(patch.matches("\\d{4}-\\d{2}-\\d{2}")?patch:"");
        new AlertDialog.Builder(this).setTitle(R.string.hub_patch).setView(input).setPositiveButton(android.R.string.ok,(d,w)->operation(()->data.patch(input.getText().toString().trim())))
                .setNeutralButton(R.string.hub_delete,(d,w)->operation(()->data.put(HeckerSpoofConfig.PATCH,null))).setNegativeButton(android.R.string.cancel,null).show();
    }
    private void renderPif(LinearLayout page){
        String name=profile.get("MODEL");ui.switchEntry(page,R.string.hub_spoofing,name==null?getString(R.string.hub_not_configured):getString(R.string.hub_profile_selected,name),pifEnabled,editable(),null,value->operation(()->data.pifEnabled(value)));
        ui.fullButton(page,R.string.hub_fetch_canary,this::choosePixel);
        ui.buttons(page,new int[]{R.string.hub_import,R.string.hub_delete,R.string.hub_reset},new Runnable[]{()->pick(PROFILE),()->confirm(R.string.hub_delete_profile,()->operation(data::deleteProfile)),()->operation(()->{
            Map<String,String> value=data.profile();if(value.isEmpty())return;value.keySet().removeIf(key->key.startsWith("spoof")||key.equals("DEBUG"));data.profile(value,data.prefs.getBoolean("pif_imported",true));
        })});
        ui.heading(page,R.string.hub_active_config);
        if(profile.containsKey("SECURITY_PATCH")){
            String description=profile.get("SECURITY_PATCH");
            try{
                LocalDate date=LocalDate.parse(description);long age=java.time.temporal.ChronoUnit.DAYS.between(date,LocalDate.now());
                if(age>=0)description=getString(R.string.hub_patch_age,description,age);
                if(profile.getOrDefault("FINGERPRINT","").contains(":CANARY/"))description+="\n"+getString(R.string.hub_canary_estimate,java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(),date.plusDays(42)));
            }catch(java.time.DateTimeException invalid){}
            ui.detail(page,R.string.hub_profile_status,description);
        }
        String[] keys={"MODEL","MANUFACTURER","BRAND","PRODUCT","DEVICE","FINGERPRINT","SECURITY_PATCH","DEVICE_INITIAL_SDK_INT"};
        int[] labels={R.string.hub_model,R.string.hub_manufacturer,R.string.hub_brand,R.string.hub_product,R.string.hub_device,R.string.hub_fingerprint,R.string.hub_patch,R.string.hub_initial_sdk};
        if(profile.isEmpty())page.addView(ui.text(R.string.hub_not_configured,15,false));
        for(int i=0;i<keys.length;i++)if(profile.containsKey(keys[i])){
            final String key=keys[i];int label=labels[i];LinearLayout card=ui.column();ui.detail(card,label,profile.get(key));
            card.setFocusable(true);card.setOnClickListener(v->editProfile(key,label));page.addView(card);
        }
        ui.heading(page,R.string.hub_advanced);
        String[] flags={"spoofBuild","spoofProps","spoofProvider","spoofSignature","spoofVendingBuild","spoofVendingSdk"};
        int[] names={R.string.hub_spoof_build,R.string.hub_spoof_props,R.string.hub_spoof_provider,R.string.hub_spoof_signature,R.string.hub_spoof_vending,R.string.hub_spoof_sdk};
        for(int i=0;i<flags.length;i++){
            String flag=flags[i];boolean selected=profile.containsKey(flag)?Set.of("1","true").contains(profile.get(flag)):Set.of("spoofBuild","spoofProps","spoofVendingBuild").contains(flag);
            ui.switchEntry(page,names[i],null,selected,editable()&&!profile.isEmpty(),null,value->operation(()->{
                Map<String,String> fields=data.profile();fields.put(flag,value?"1":"0");data.profile(fields,data.prefs.getBoolean("pif_imported",true));
            }));
        }
        ui.fullButton(page,R.string.hub_restart_google,()->confirm(R.string.hub_restart_confirm,()->operation(this::restartGoogle)));
        TextView note=ui.text(R.string.hub_pif_note,13,false);note.setTextColor(ui.muted);page.addView(note);
    }
    private void editProfile(String key,int label){if(!editable())return;EditText input=new EditText(this);input.setText(profile.get(key));
        new AlertDialog.Builder(this).setTitle(label).setView(input).setPositiveButton(android.R.string.ok,(d,w)->operation(()->{
            Map<String,String> value=data.profile();value.put(key,input.getText().toString().trim());data.profile(value,true);
        })).setNegativeButton(android.R.string.cancel,null).show();
    }
    private void restartGoogle() throws Exception{
        ActivityManager manager=getSystemService(ActivityManager.class);
        for(String pkg:new String[]{"com.google.android.gms","com.android.vending"})try{
            ActivityManager.class.getMethod("forceStopPackageAsUser",String.class,int.class).invoke(manager,pkg,0);
        }catch(java.lang.reflect.InvocationTargetException error){throw new IllegalStateException("Could not restart Google services");}
    }
    private void choosePixel(){
        if(!editable())return;busy=true;render();int request=++generation;
        worker.execute(()->{try{Map<String,String> devices=PixelCanaryProfileFetcher.devices();runOnUiThread(()->{
            if(!alive(request))return;busy=false;render();List<String> codes=new ArrayList<>(devices.keySet());
            new AlertDialog.Builder(this).setTitle(R.string.hub_choose_pixel).setItems(codes.stream().map(code->devices.get(code)).toArray(String[]::new),(dialog,index)->{
                String code=codes.get(index);operation(()->{
                    Map<String,String> fresh=PixelCanaryProfileFetcher.fetch(code,devices.get(code));
                    Map<String,String> existing=data.profile();for(String key:existing.keySet())if(key.startsWith("spoof")||key.equals("DEBUG"))fresh.put(key,existing.get(key));
                    data.profile(fresh,false);
                });
            }).setNegativeButton(android.R.string.cancel,null).show();
        });}catch(Exception error){runOnUiThread(()->{if(alive(request)){busy=false;render();Toast.makeText(this,getString(R.string.hub_error,SpoofingData.error(error)),Toast.LENGTH_LONG).show();}});}});
    }
    private void renderGames(LinearLayout page){
        page.addView(ui.text(R.string.hub_games_summary,15,false));ui.fullButton(page,R.string.hub_import,()->pick(GAMES));
        ui.fullButton(page,R.string.hub_edit,this::editGames);ui.fullButton(page,R.string.hub_delete,()->confirm(R.string.hub_delete_games,()->operation(()->data.put(HeckerSpoofConfig.GAMES,null))));
    }
    private void editGames(){if(!editable())return;
        EditText input=new EditText(this);input.setMinLines(5);input.setText(games==null?"{\"enabled\":true,\"games\":{}}":games);
            new AlertDialog.Builder(this).setTitle(R.string.hub_games).setView(input).setPositiveButton(android.R.string.ok,(d,w)->operation(()->data.put(HeckerSpoofConfig.GAMES,input.getText().toString()))).setNegativeButton(android.R.string.cancel,null).show();
    }
    private void pick(int kind){if(!editable())return;importKind=kind;startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),IMPORT);}
    @Override protected void onActivityResult(int request,int result,Intent intent){
        super.onActivityResult(request,result,intent);if(request!=IMPORT||result!=RESULT_OK||intent==null||intent.getData()==null)return;
        pendingImport=intent.getData();consumeImport();
    }
    private void consumeImport(){
        if(pendingImport==null||!editable())return;
        final int kind=importKind;final android.net.Uri uri=pendingImport;pendingImport=null;operation(()->{
            String raw;try(InputStream input=getContentResolver().openInputStream(uri)){
                if(input==null)throw new java.io.IOException("Could not open the selected file");int limit=kind==PROFILE?65536:256*1024;
                byte[] bytes=input.readNBytes(limit+1);if(bytes.length>limit)throw new IllegalArgumentException("The selected file is too large");raw=new String(bytes,StandardCharsets.UTF_8);
            }
            if(raw.isBlank())throw new IllegalArgumentException("The selected file is empty");
            if(kind==PROFILE)data.profile(SpoofingData.parseProfile(raw),true);
            else if(kind==TARGETS)data.targets(SpoofingTargets.parse(raw));
            else if(kind==GAMES)data.put(HeckerSpoofConfig.GAMES,raw);
            else{String xml=KeyboxStatus.xml(raw);data.put(HeckerSpoofConfig.KEYBOX,xml);data.prefs.edit().putString("keybox_source","user").apply();status=KeyboxStatus.check(xml,false);}
        });
    }
    private void renderTargets(LinearLayout root){
        LinearLayout content=ui.column();content.setPadding(ui.dp(16),ui.dp(8),ui.dp(16),0);
        TextView count=ui.text(getString(R.string.hub_target_count,targets.size()),15,ui.muted,false);content.addView(count);
        EditText input=new EditText(this);input.setSingleLine();input.setText(search);input.setHint(R.string.hub_search_apps);input.setPadding(ui.dp(16),ui.dp(10),ui.dp(16),ui.dp(10));input.setBackground(ui.shape(ui.surface,28));content.addView(input,new LinearLayout.LayoutParams(-1,ui.dp(52)));
        LinearLayout filters=ui.buttons(content,new int[]{R.string.hub_system_apps,R.string.hub_select_all},new Runnable[]{()->{showSystem=!showSystem;render();},()->{
            if(!editable()||adapter==null)return;LinkedHashMap<String,SpoofingTargets.Mode> values=new LinkedHashMap<>(targets);for(App app:adapter.visible)if(app.eligible)values.putIfAbsent(app.pkg,SpoofingTargets.Mode.AUTO);operation(()->data.targets(values));
        }});
        filters.getChildAt(0).setSelected(showSystem);filters.getChildAt(0).setAlpha(showSystem?1f:.65f);
        ui.buttons(content,new int[]{R.string.hub_reset,R.string.hub_add_installed},new Runnable[]{()->confirm(R.string.hub_reset_targets,()->operation(()->data.targets(data.defaults()))),this::addInstalled});
        list=new ListView(this);list.setDivider(null);list.setVerticalScrollBarEnabled(false);adapter=new TargetAdapter();list.setAdapter(adapter);
        if(targetPosition!=null)list.onRestoreInstanceState(targetPosition);
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){search=s.toString();if(adapter!=null)adapter.filter();}public void afterTextChanged(Editable e){}});
        content.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        TextView note=ui.text(R.string.hub_protected_note,12,false);note.setTextColor(ui.muted);note.setPadding(ui.dp(4),ui.dp(8),ui.dp(4),ui.dp(8));content.addView(note);
        ui.present(root,content);
    }
    private void addInstalled(){if(!editable())return;List<App> available=new ArrayList<>();for(App app:apps)if(app.eligible&&!targets.containsKey(app.pkg))available.add(app);
        available.sort(Comparator.comparing(app->app.label.toLowerCase(Locale.ROOT)));
        new AlertDialog.Builder(this).setTitle(R.string.hub_add_installed).setItems(available.stream().map(app->app.label).toArray(String[]::new),(d,index)->{
            LinkedHashMap<String,SpoofingTargets.Mode> values=new LinkedHashMap<>(targets);values.put(available.get(index).pkg,SpoofingTargets.Mode.AUTO);operation(()->data.targets(values));
        }).setNegativeButton(android.R.string.cancel,null).show();
    }
    private final class TargetAdapter extends BaseAdapter {
        List<App> visible=new ArrayList<>();TargetAdapter(){filter();}
        void filter(){visible=new ArrayList<>();String query=search.toLowerCase(Locale.ROOT);
            for(App app:apps)if((showSystem||!app.system||targets.containsKey(app.pkg))&&(app.label.toLowerCase(Locale.ROOT).contains(query)||app.pkg.toLowerCase(Locale.ROOT).contains(query)))visible.add(app);
            visible.sort(Comparator.<App,Boolean>comparing(app->!targets.containsKey(app.pkg)).thenComparing(app->app.label.toLowerCase(Locale.ROOT)));notifyDataSetChanged();
        }
        public int getCount(){return visible.size();}public App getItem(int position){return visible.get(position);}public long getItemId(int position){return position;}
        public View getView(int position,View recycled,android.view.ViewGroup parent){
            App app=getItem(position);LinearLayout shell=ui.card(),row=ui.row();
            ImageView icon=new ImageView(HeckerSpoofingHubActivity.this);if(app.info!=null)icon.setImageDrawable(app.info.loadIcon(getPackageManager()));icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(ui.dp(42),ui.dp(42));ip.rightMargin=ui.dp(14);row.addView(icon,ip);
            LinearLayout copy=ui.column();TextView label=ui.text(app.label,16,ui.foreground,true);copy.addView(label);TextView pkg=ui.text(app.pkg+(app.system?" · SYS":""),12,ui.muted,false);copy.addView(pkg);row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
            Switch selected=new Switch(HeckerSpoofingHubActivity.this);selected.setShowText(false);selected.setChecked(targets.containsKey(app.pkg));selected.setEnabled(editable()&&(app.eligible||targets.containsKey(app.pkg)));selected.setContentDescription(app.label);
            selected.setOnCheckedChangeListener((v,value)->{if(value&&!app.eligible){v.setChecked(false);return;}LinkedHashMap<String,SpoofingTargets.Mode> values=new LinkedHashMap<>(targets);if(value)values.put(app.pkg,SpoofingTargets.Mode.AUTO);else values.remove(app.pkg);operation(()->data.targets(values));});row.addView(selected);shell.addView(row);
            LinearLayout modes=ui.row();SpoofingTargets.Mode current=targets.getOrDefault(app.pkg,SpoofingTargets.Mode.AUTO);
            for(SpoofingTargets.Mode mode:SpoofingTargets.Mode.values()){
                Button chip=ui.button(modeLabel(mode),()->{LinkedHashMap<String,SpoofingTargets.Mode> values=new LinkedHashMap<>(targets);values.put(app.pkg,mode);operation(()->data.targets(values));});
                chip.setTextSize(12);chip.setMinWidth(0);chip.setMinimumWidth(0);chip.setPadding(ui.dp(3),0,ui.dp(3),0);chip.setEnabled(editable()&&app.eligible&&targets.containsKey(app.pkg));
                chip.setSelected(current==mode);chip.setTextColor(current==mode?ui.onAccent:ui.foreground);chip.setBackground(ui.ripple(current==mode?ui.accent:ui.background,24));
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,ui.dp(48),1);cp.setMargins(ui.dp(1),ui.dp(10),ui.dp(1),0);modes.addView(chip,cp);
            }
            shell.addView(modes);return shell;
        }
    }
    private int modeLabel(SpoofingTargets.Mode mode){switch(mode){case LEAF_HACK:return R.string.hub_mode_leaf;case GENERATE:return R.string.hub_mode_cert;case SKIP:return R.string.hub_mode_skip;default:return R.string.hub_mode_auto;}}
    private int statusLabel(KeyboxStatus.State state){switch(state){case VALID:return R.string.hub_key_valid;case EXPIRING:return R.string.hub_key_expiring;case EXPIRED:return R.string.hub_key_expired;case NOT_YET_VALID:return R.string.hub_key_not_yet_valid;case REVOKED:return R.string.hub_key_revoked;case SUSPENDED:return R.string.hub_key_suspended;case INVALID:return R.string.hub_key_invalid;case UNTRUSTED:return R.string.hub_key_untrusted;case MISSING:return R.string.hub_key_missing;default:return R.string.hub_key_unknown;}}
    private void confirm(int title,Runnable action){if(!editable())return;new AlertDialog.Builder(this).setTitle(title).setPositiveButton(android.R.string.ok,(d,w)->action.run()).setNegativeButton(android.R.string.cancel,null).show();}
    private void toast(int message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
}
