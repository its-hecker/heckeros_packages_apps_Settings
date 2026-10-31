/* SPDX-FileCopyrightText: Evolution X
 * SPDX-FileCopyrightText: 2026 HeckerOS
 * SPDX-License-Identifier: Apache-2.0
 * Native View adaptation of Evolution X SpoofingSharedUi.kt, cnb@84b7ff421f5ce2c0dcdc3f573597f5d4d658f9ba. */
package com.android.settings.hecker;

import android.app.Activity;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import com.android.settings.R;

/** Expressive grouped cards with native touch, focus, switch and accessibility behavior. */
final class SpoofingUi {
    final Activity activity;
    final int background,surface,foreground,muted,accent,onAccent,primary;
    SpoofingUi(Activity activity){
        this.activity=activity;
        boolean night=(activity.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        background=color("colorSurface",night?0xff171c12:0xfff6faef);
        surface=color("colorSurfaceContainerHigh",night?0xff272e20:0xffe6eddd);
        foreground=color("colorOnSurface",night?0xffe3e8da:0xff1a2115);
        muted=color("colorOnSurfaceVariant",night?0xffbac3af:0xff535d49);
        accent=color("colorPrimaryContainer",night?0xffb6d293:0xffd7eeb8);
        onAccent=color("colorOnPrimaryContainer",night?0xff213b0d:0xff253e11);
        primary=color("colorPrimary",night?0xffb6d293:0xff446722);
    }
    private int color(String name,int fallback){
        int id=activity.getResources().getIdentifier(name,"attr",activity.getPackageName());
        TypedValue value=new TypedValue();
        if(id!=0&&activity.getTheme().resolveAttribute(id,value,true)){
            try{return value.resourceId!=0?activity.getColor(value.resourceId):value.data;}catch(Resources.NotFoundException ignored){}
        }
        return fallback;
    }
    int dp(float value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    LinearLayout column(){LinearLayout view=new LinearLayout(activity);view.setOrientation(LinearLayout.VERTICAL);return view;}
    LinearLayout row(){LinearLayout view=new LinearLayout(activity);view.setGravity(Gravity.CENTER_VERTICAL);view.setBaselineAligned(false);return view;}
    TextView text(CharSequence value,int size,int color,boolean bold){
        TextView view=new TextView(activity);view.setText(value);view.setTextSize(size);view.setTextColor(color);
        if(bold)view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return view;
    }
    TextView text(int value,int size,boolean bold){return text(activity.getString(value),size,foreground,bold);}
    GradientDrawable shape(int color,int radius){GradientDrawable value=new GradientDrawable();value.setColor(color);value.setCornerRadius(dp(radius));return value;}
    Drawable ripple(int color,int radius){return new RippleDrawable(ColorStateList.valueOf(0x225f7844),shape(color,radius),null);}
    LinearLayout card(){
        LinearLayout value=column();value.setPadding(dp(20),dp(18),dp(20),dp(18));value.setBackground(shape(surface,30));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);value.setLayoutParams(p);return value;
    }
    void heading(LinearLayout page,int label){
        TextView value=text(label,17,true);value.setTextColor(primary);value.setPadding(dp(4),dp(20),dp(4),dp(10));value.setAccessibilityHeading(true);page.addView(value);
    }
    Button button(int label,Runnable click){
        Button value=new Button(activity);value.setAllCaps(false);value.setText(label);value.setTextSize(14);value.setTextColor(onAccent);
        value.setPadding(dp(14),dp(12),dp(14),dp(12));value.setMinHeight(dp(48));value.setBackground(ripple(accent,28));
        value.setOnClickListener(v->click.run());return value;
    }
    void fullButton(LinearLayout parent,int label,Runnable click){
        Button value=button(label,click);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);parent.addView(value,p);
    }
    LinearLayout buttons(LinearLayout parent,int[] labels,Runnable[] actions){
        LinearLayout row=row();
        for(int i=0;i<labels.length;i++){
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);if(i>0)p.leftMargin=dp(8);
            row.addView(button(labels[i],actions[i]),p);
        }
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);parent.addView(row,p);return row;
    }
    LinearLayout entry(LinearLayout parent,int title,CharSequence summary,Runnable open){
        LinearLayout row=row();row.setPadding(dp(18),dp(17),dp(18),dp(17));row.setMinimumHeight(dp(84));row.setBackground(ripple(surface,28));
        LinearLayout text=column();text.addView(text(title,19,true));
        if(summary!=null){TextView detail=text(summary,14,muted,false);detail.setPadding(0,dp(4),0,0);text.addView(detail);}
        row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=text("›",26,muted,false);arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(arrow);
        row.setFocusable(true);row.setOnClickListener(v->open.run());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(5);parent.addView(row,p);return row;
    }
    void switchEntry(LinearLayout parent,int title,CharSequence summary,boolean checked,boolean enabled,Runnable open,java.util.function.Consumer<Boolean> change){
        LinearLayout row=entry(parent,title,summary,()->{});row.removeViewAt(row.getChildCount()-1);
        View divider=new View(activity);divider.setBackgroundColor(muted);divider.setAlpha(.25f);
        LinearLayout.LayoutParams d=new LinearLayout.LayoutParams(dp(1),dp(54));d.setMargins(dp(12),0,dp(12),0);row.addView(divider,d);
        Switch toggle=new Switch(activity);toggle.setShowText(false);toggle.setSwitchMinWidth(dp(62));toggle.setSplitTrack(false);
        toggle.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{onAccent,muted}));
        toggle.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{accent,background}));
        toggle.setChecked(checked);toggle.setEnabled(enabled);toggle.setContentDescription(activity.getString(title));
        toggle.setOnCheckedChangeListener((v,value)->change.accept(value));row.addView(toggle,new LinearLayout.LayoutParams(dp(66),dp(48)));
        row.setOnClickListener(v->{if(open!=null)open.run();else if(enabled)toggle.toggle();});row.setEnabled(open!=null||enabled);
    }
    void badge(LinearLayout row,int label){
        row.removeViewAt(row.getChildCount()-1);TextView badge=text(label,12,true);
        badge.setTextColor(onAccent);badge.setBackground(shape(accent,20));badge.setPadding(dp(12),dp(6),dp(12),dp(6));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.leftMargin=dp(12);row.addView(badge,p);
    }
    void detail(LinearLayout parent,int title,CharSequence value){
        LinearLayout row=card();TextView label=text(title,13,false);label.setTextColor(muted);row.addView(label);
        TextView content=text(value==null?"—":value,17,foreground,false);content.setTextIsSelectable(true);content.setPadding(0,dp(6),0,0);row.addView(content);parent.addView(row);
    }
    LinearLayout root(int title,Runnable back){
        LinearLayout root=column();root.setBackgroundColor(background);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            root.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
        });
        LinearLayout toolbar=row();toolbar.setPadding(dp(16),dp(8),dp(20),dp(8));
        ImageButton button=new ImageButton(activity);button.setImageResource(R.drawable.hecker_hub_back);
        button.setImageTintList(ColorStateList.valueOf(foreground));button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);button.setPadding(dp(14),dp(14),dp(14),dp(14));
        button.setBackground(ripple(surface,24));button.setContentDescription(activity.getString(R.string.hub_back));button.setOnClickListener(v->back.run());
        toolbar.addView(button,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView heading=text(title,22,true);heading.setAccessibilityHeading(true);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.leftMargin=dp(16);toolbar.addView(heading,p);root.addView(toolbar);
        return root;
    }
    void present(LinearLayout root,View content){
        root.addView(content,new LinearLayout.LayoutParams(-1,0,1));activity.setContentView(root);root.post(root::requestApplyInsets);
    }
    ScrollView scroll(LinearLayout page){
        ScrollView scroll=new ScrollView(activity);scroll.setClipToPadding(false);scroll.setPadding(dp(16),dp(10),dp(16),dp(24));scroll.setVerticalScrollBarEnabled(false);scroll.addView(page);return scroll;
    }
}
