package app.personalkeyboard.demo;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.*;

final class Ui {
    static final int BG=Color.rgb(245,247,242), PANEL=Color.WHITE, INK=Color.rgb(35,49,40), SUB=Color.rgb(106,120,108), ACCENT=Color.rgb(64,103,73), SOFT=Color.rgb(226,237,221), LINE=Color.rgb(220,227,216);
    static int dp(Context c,float n){return (int)(n*c.getResources().getDisplayMetrics().density+.5f);}
    static GradientDrawable bg(Context c,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    static TextView text(Context c,String s,int size,int color){TextView v=new TextView(c);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setFontFeatureSettings("kern");return v;}
    static LinearLayout column(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}
    static LinearLayout row(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(android.view.Gravity.CENTER_VERTICAL);return v;}
    static Button button(Context c,String s,Runnable click){Button b=new Button(c);b.setText(s);b.setTextSize(13);b.setTextColor(ACCENT);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(c,10),0,dp(c,10),0);b.setBackground(bg(c,SOFT,10));b.setOnClickListener(v->click.run());return b;}
    static void pad(Context c,View v,int x,int y){v.setPadding(dp(c,x),dp(c,y),dp(c,x),dp(c,y));}
    static LinearLayout.LayoutParams weighted(Context c,int height){return new LinearLayout.LayoutParams(0,dp(c,height),1);}
    static void content(android.app.Activity activity,View root){
        activity.getWindow().setDecorFitsSystemWindows(false);
        final int left=root.getPaddingLeft(),top=root.getPaddingTop(),right=root.getPaddingRight(),bottom=root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((view,insets)->{
            android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());
            android.graphics.Insets ime=insets.getInsets(android.view.WindowInsets.Type.ime());
            view.setPadding(left+bars.left,top+bars.top,right+bars.right,bottom+Math.max(bars.bottom,ime.bottom));
            return insets;
        });
        activity.setContentView(root);root.requestApplyInsets();
    }
}
