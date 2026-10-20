package app.personalkeyboard.demo;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.view.*;
import android.widget.*;

public class PeopleActivity extends Activity {
    TextView detail;
    @Override public void onCreate(Bundle saved){super.onCreate(saved);LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.BG);Ui.pad(this,root,20,18);root.addView(Ui.button(this,"‹ 返回聊天",()->finish()),new LinearLayout.LayoutParams(Ui.dp(this,115),Ui.dp(this,40)));TextView title=Ui.text(this,"关系与记忆",26,Ui.INK);Ui.pad(this,title,0,15);root.addView(title);root.addView(Ui.text(this,"演示人物 · 点节点查看资料",12,Ui.SUB));root.addView(new Graph(this),new LinearLayout.LayoutParams(-1,Ui.dp(this,260)));detail=Ui.text(this,"林然 · 朋友\n\n想去看展\n依据：周一聊天\n\n本周六白天加班\n依据：本次聊天，有时间范围\n\n推测与已知事实分别保存，可修订与删除。",15,Ui.INK);detail.setBackground(Ui.bg(this,Ui.PANEL,15));Ui.pad(this,detail,18,18);root.addView(detail);Ui.content(this,root);}
    class Graph extends View {
        Paint p=new Paint(3);String[] names={"我","林然","陈乔","周同学"};float[] xs={.5f,.18f,.82f,.55f},ys={.48f,.2f,.24f,.83f};
        Graph(Activity a){super(a);setContentDescription("我与林然、陈乔、周同学的关系图");}
        @Override protected void onDraw(Canvas c){for(int i=1;i<4;i++){p.setColor(Ui.LINE);p.setStrokeWidth(Ui.dp(getContext(),1.5f));c.drawLine(getWidth()*xs[0],getHeight()*ys[0],getWidth()*xs[i],getHeight()*ys[i],p);}for(int i=0;i<4;i++){float x=getWidth()*xs[i],y=getHeight()*ys[i];p.setColor(i==0?Ui.ACCENT:Ui.SOFT);c.drawRoundRect(x-Ui.dp(getContext(),33),y-Ui.dp(getContext(),23),x+Ui.dp(getContext(),33),y+Ui.dp(getContext(),23),Ui.dp(getContext(),16),Ui.dp(getContext(),16),p);p.setColor(i==0?Ui.PANEL:Ui.INK);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(Ui.dp(getContext(),14));c.drawText(names[i],x,y+Ui.dp(getContext(),5),p);}}
        @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()!=MotionEvent.ACTION_UP)return true;for(int i=1;i<4;i++)if(Math.hypot(e.getX()-getWidth()*xs[i],e.getY()-getHeight()*ys[i])<Ui.dp(getContext(),45)){if(i==1)detail.setText("林然 · 朋友\n\n想去看展\n依据：周一聊天\n\n本周六白天加班\n依据：本次聊天，有时间范围");else if(i==2)detail.setText("陈乔 · 同事\n\n下周一起评审方案\n依据：周三聊天\n\n先给结论，再解释\n依据：用户手动记录");else detail.setText("周同学 · 老同学\n\n计划月底聚一次\n依据：周二聊天\n\n同名人物不自动合并，资料可修订。");performClick();return true;}return true;}
        @Override public boolean performClick(){return super.performClick();}
    }
}
