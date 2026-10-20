package app.personalkeyboard.demo;

import android.inputmethodservice.InputMethodService;
import android.content.Intent;
import android.provider.Settings;
import android.view.*;
import android.view.inputmethod.InputConnection;
import android.widget.*;

public class KeyboardService extends InputMethodService {
    private LinearLayout root;private int generation=0,selected=0;private final int[] variants={0,0,0};
    private final String[] labels={"自然接话","温柔关心","直接明确"};
    private final String[][] replies={{"这周有点忙，咱们约下周？找个不用赶时间的下午～","这周排满了，下周一起去逛逛吧。"},{"想和你一起去，不过这周抽不开身。咱们改到下周，好不好？","当然想去呀。留到下周，咱们慢慢看。"},{"这周不方便，我们约下周吧。你哪天有空？","我想改到下周，具体时间再一起定。"}};
    @Override public View onCreateInputView(){root=Ui.column(this);root.setBackgroundColor(Ui.BG);normal();return root;}
    @Override public boolean onEvaluateFullscreenMode(){return false;}
    @Override public void onStartInput(android.view.inputmethod.EditorInfo info,boolean restarting){super.onStartInput(info,restarting);generation++;if(root!=null)normal();}
    @Override public void onFinishInput(){generation++;if(CaptureService.current!=null)CaptureService.current.clearCapture();super.onFinishInput();}
    private void height(int dp){root.removeAllViews();root.setLayoutParams(new android.widget.FrameLayout.LayoutParams(-1,Ui.dp(this,dp)));Ui.pad(this,root,6,5);root.requestLayout();}
    private void commit(String text){InputConnection c=getCurrentInputConnection();if(c!=null)c.commitText(text,1);}
    private ImageButton icon(int resource,String label,Runnable action){ImageButton b=new ImageButton(this);b.setImageResource(resource);b.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));b.setContentDescription(label);b.setPadding(Ui.dp(this,10),Ui.dp(this,10),Ui.dp(this,10),Ui.dp(this,10));b.setBackground(Ui.bg(this,Ui.BG,8));b.setOnClickListener(v->action.run());return b;}
    private void normal(){
        height(260);LinearLayout candidates=Ui.row(this);TextView hint=Ui.text(this,"交互演示",11,Ui.SUB);candidates.addView(hint,new LinearLayout.LayoutParams(0,Ui.dp(this,40),1));
        for(String s:new String[]{"下周","好呀"})candidates.addView(Ui.button(this,s,()->commit(s)),new LinearLayout.LayoutParams(Ui.dp(this,51),Ui.dp(this,38)));
        candidates.addView(icon(android.R.drawable.ic_btn_speak_now,"语音",()->voice()),new LinearLayout.LayoutParams(Ui.dp(this,42),Ui.dp(this,42)));candidates.addView(icon(android.R.drawable.ic_menu_compass,"军师",()->capture()),new LinearLayout.LayoutParams(Ui.dp(this,42),Ui.dp(this,42)));root.addView(candidates);
        String[] rows={"QWERTYUIOP","ASDFGHJKL","ZXCVBNM"};
        for(int i=0;i<rows.length;i++){LinearLayout row=Ui.row(this);if(i==1)Ui.pad(this,row,13,0);if(i==2)row.addView(Ui.button(this,"⇧",()->{}),Ui.weighted(this,46));for(char ch:rows[i].toCharArray()){String s=String.valueOf(ch);Button key=Ui.button(this,s,()->commit(s.toLowerCase()));key.setTextColor(Ui.INK);key.setBackground(Ui.bg(this,Ui.PANEL,7));LinearLayout.LayoutParams p=Ui.weighted(this,46);p.setMargins(Ui.dp(this,2),Ui.dp(this,3),Ui.dp(this,2),0);row.addView(key,p);}if(i==2)row.addView(Ui.button(this,"⌫",()->{InputConnection c=getCurrentInputConnection();if(c!=null)c.deleteSurroundingText(1,0);}),Ui.weighted(this,46));root.addView(row);}
        LinearLayout last=Ui.row(this);last.addView(Ui.button(this,"123",()->commit("1")),Ui.weighted(this,45));last.addView(Ui.button(this,"，",()->commit("，")),Ui.weighted(this,45));LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(0,Ui.dp(this,45),4);last.addView(Ui.button(this,"空格",()->commit(" ")),space);last.addView(Ui.button(this,"。",()->commit("。")),Ui.weighted(this,45));last.addView(Ui.button(this,"↵",()->commit("\n")),Ui.weighted(this,45));Ui.pad(this,last,0,5);root.addView(last);
    }
    private void capture(){
        final int request=++generation;height(76);LinearLayout bar=Ui.row(this);TextView t=Ui.text(this,"正在读取当前聊天…",13,Ui.SUB);bar.addView(t,new LinearLayout.LayoutParams(0,Ui.dp(this,44),1));bar.addView(Ui.button(this,"取消",()->{generation++;normal();}),new LinearLayout.LayoutParams(Ui.dp(this,60),Ui.dp(this,40)));root.addView(bar);
        if(CaptureService.current==null){root.postDelayed(()->{if(request!=generation)return;permission();},150);return;}
        // Two frames after the input view has relaid out; the app's inset resize is allowed to settle.
        root.post(()->Choreographer.getInstance().postFrameCallback(frame->Choreographer.getInstance().postFrameCallback(next->root.postDelayed(()->{
            if(request!=generation)return;
            CaptureService service=CaptureService.current;if(service==null){permission();return;}
            int[] position=new int[2];root.getLocationOnScreen(position);
            service.capture(position[1],(ok,message)->{if(request!=generation){service.clearCapture();return;}if(ok)suggestions("已截取聊天 · "+message);else error(message);});
        },180))));
    }
    private void permission(){height(170);TextView t=Ui.text(this,"需要开启读屏，才能自动截取当前聊天",14,Ui.INK);Ui.pad(this,t,12,14);root.addView(t);Button b=Ui.button(this,"开启演示读屏权限",()->{Intent i=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);});root.addView(b,new LinearLayout.LayoutParams(-1,Ui.dp(this,42)));root.addView(Ui.button(this,"返回键盘",()->normal()),new LinearLayout.LayoutParams(-1,Ui.dp(this,40)));}
    private void error(String message){height(150);TextView t=Ui.text(this,message+"，草稿仍保留",14,Ui.INK);Ui.pad(this,t,12,15);root.addView(t);root.addView(Ui.button(this,"重试",()->capture()),new LinearLayout.LayoutParams(-1,Ui.dp(this,40)));root.addView(Ui.button(this,"返回键盘",()->normal()),new LinearLayout.LayoutParams(-1,Ui.dp(this,40)));}
    private void suggestions(String status){
        height(284);TextView title=Ui.text(this,"林然 · "+status,10,Ui.SUB);Ui.pad(this,title,7,2);root.addView(title);
        for(int i=0;i<3;i++){final int index=i;LinearLayout row=Ui.row(this);row.setBackground(Ui.bg(this,i==selected?Ui.SOFT:Ui.PANEL,11));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,0,1);rp.setMargins(0,Ui.dp(this,5),0,0);Ui.pad(this,row,10,5);LinearLayout copy=Ui.column(this);copy.addView(Ui.text(this,labels[i],10,Ui.SUB));TextView reply=Ui.text(this,replies[i][variants[i]%2],13,Ui.INK);reply.setMaxLines(2);copy.addView(reply);row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));row.addView(Ui.button(this,"填入",()->{selected=index;InputConnection c=getCurrentInputConnection();if(c!=null){android.view.inputmethod.ExtractedTextRequest r=new android.view.inputmethod.ExtractedTextRequest();android.view.inputmethod.ExtractedText e=c.getExtractedText(r,0);if(e!=null&&e.text!=null){c.setSelection(e.startOffset,e.startOffset+e.text.length());}c.commitText(replies[index][variants[index]%2],1);}suggestions(status);}),new LinearLayout.LayoutParams(Ui.dp(this,52),Ui.dp(this,40)));row.setOnClickListener(v->{selected=index;suggestions(status);});root.addView(row,rp);}
        LinearLayout actions=Ui.row(this);actions.addView(Ui.button(this,"返回键盘",()->normal()),Ui.weighted(this,40));actions.addView(Ui.button(this,"更多："+labels[selected],()->{variants[selected]++;suggestions("同一风格的新示例");}),Ui.weighted(this,40));root.addView(actions);TextView note=Ui.text(this,"固定演示回复 · 未调用模型、ASR 或 OCR",10,Ui.SUB);note.setGravity(Gravity.CENTER);root.addView(note);
    }
    private void voice(){height(260);LinearLayout box=Ui.column(this);box.setGravity(Gravity.CENTER);root.addView(box,new LinearLayout.LayoutParams(-1,-1));box.addView(Ui.text(this,"说出意思，按你的习惯整理",17,Ui.INK));TextView note=Ui.text(this,"此处仅演示上屏，未开启麦克风",12,Ui.SUB);Ui.pad(this,note,5,12);box.addView(note);box.addView(Ui.button(this,"试说一句",()->{commit("这周事情有点多，咱们下周再约，好不好？");normal();}),new LinearLayout.LayoutParams(Ui.dp(this,200),Ui.dp(this,48)));box.addView(Ui.button(this,"返回键盘",()->normal()),new LinearLayout.LayoutParams(Ui.dp(this,200),Ui.dp(this,40)));}
}
