package app.doudou.ime.advisor;

import android.content.Context;
import android.graphics.*;
import android.widget.SeekBar;
import app.doudou.ime.design.Skin;
import app.doudou.ime.design.Ui;

/** Native range semantics, generous touch height, visible detents and a small tactile thumb. */
final class PreferenceSlider extends SeekBar {
  private Runnable explicitSelection;
  private boolean follows;
  private String[] levels = {};
  private final Paint railPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  PreferenceSlider(Context context) {
    super(context);
    setFocusable(true);
    setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null);
  }

  void inherited(boolean value) { follows = value; invalidate(); }

  void setExplicitSelection(Runnable action) { explicitSelection = action; }
  void setLevels(String[] names) { levels = names.clone(); invalidate(); }

  @Override protected synchronized void onDraw(Canvas canvas) {
    float unit = getResources().getDisplayMetrics().density;
    float x = 18*unit, top = 20 * unit, bottom = getHeight() - 20 * unit;
    float half = 15*unit;
    railPaint.setShader(null); railPaint.setColor(Skin.choose(0xff424753, 0xffe1e4eb));
    canvas.drawRoundRect(x-half, 3*unit, x+half, getHeight()-3*unit, half, half, railPaint);
    railPaint.setStrokeWidth(2 * unit); railPaint.setStrokeCap(Paint.Cap.ROUND);
    for (int i = 0; i <= getMax(); i++) {
      float y = bottom - (bottom-top) * i / Math.max(1, getMax());
      railPaint.setColor(Skin.choose(0xffadb5c4, 0xff748093));
      canvas.drawLine(x-3*unit, y, x+3*unit, y, railPaint);
    }
    float y = bottom - (bottom-top) * getProgress() / Math.max(1, getMax());
    float radius = 13*unit;
    railPaint.setColor(Color.WHITE);
    railPaint.setShadowLayer(4*unit,0,2*unit,Skin.choose(0x65000000,0x300d2349));
    railPaint.setShader(new LinearGradient(0,y-radius,0,y+radius,
        Skin.choose(0xff788397, 0xffffffff), Skin.choose(0xff475468, 0xffe9effb), Shader.TileMode.CLAMP));
    canvas.drawCircle(x, y, radius, railPaint); railPaint.setShader(null); railPaint.clearShadowLayer();
    railPaint.setColor(follows ? Ui.sub() : Ui.appAccent());
    canvas.drawRoundRect(x-5*unit,y-unit,x+5*unit,y+unit,unit,unit,railPaint);
    railPaint.setTextSize(11 * getResources().getDisplayMetrics().scaledDensity);
    float left=38*unit, width=Math.max(1,getWidth()-left-2*unit);
    for(int i=0;i<levels.length;i++) {
      boolean selected=!follows&&i==getProgress();
      railPaint.setTypeface(Typeface.create(Typeface.DEFAULT,selected?Typeface.BOLD:Typeface.NORMAL));
      railPaint.setColor(selected?Ui.appAccent():Ui.sub());
      java.util.List<String> lines=new java.util.ArrayList<>();String value=levels[i];int start=0;
      while(start<value.length()) {
        int count=railPaint.breakText(value,start,value.length(),true,width,null);
        if(count==0)count=Character.charCount(value.codePointAt(start));
        lines.add(value.substring(start,start+count));start+=count;
      }
      float center=bottom-(bottom-top)*i/Math.max(1,getMax());Paint.FontMetrics metrics=railPaint.getFontMetrics();
      float baseline=center-(metrics.ascent+metrics.descent)/2-(lines.size()-1)*railPaint.getFontSpacing()/2;
      for(String line:lines){canvas.drawText(line,left,baseline,railPaint);baseline+=railPaint.getFontSpacing();}
    }
    railPaint.setTypeface(Typeface.DEFAULT);
  }

  @Override public boolean onTouchEvent(android.view.MotionEvent event) {
    if (!isEnabled()) return false;
    int action = event.getActionMasked();
    if (action == android.view.MotionEvent.ACTION_DOWN) {
      getParent().requestDisallowInterceptTouchEvent(true); setPressed(true);
    }
    if (action == android.view.MotionEvent.ACTION_DOWN || action == android.view.MotionEvent.ACTION_MOVE
        || action == android.view.MotionEvent.ACTION_UP) {
      float inset = Ui.dp(getContext(),20), span = Math.max(1,getHeight()-2*inset);
      int value = Math.round(Math.max(0,Math.min(1,(getHeight()-inset-event.getY())/span))*getMax());
      boolean changed = value != getProgress(); setProgress(value);
      if (explicitSelection != null) explicitSelection.run();
      if (changed) performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
      if (action == android.view.MotionEvent.ACTION_UP) {
        setPressed(false); getParent().requestDisallowInterceptTouchEvent(false); performClick();
      }
      return true;
    }
    if (action == android.view.MotionEvent.ACTION_CANCEL) {
      setPressed(false); getParent().requestDisallowInterceptTouchEvent(false); return true;
    }
    return false;
  }

  @Override public boolean performClick() { super.performClick(); return true; }

  @Override public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
    if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP || keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
      setProgress(Math.max(0,Math.min(getMax(),getProgress()+(keyCode==android.view.KeyEvent.KEYCODE_DPAD_UP?1:-1))));
      if (explicitSelection != null) explicitSelection.run(); return true;
    }
    return super.onKeyDown(keyCode,event);
  }

  @Override public boolean performAccessibilityAction(int action, android.os.Bundle arguments) {
    if (isEnabled() && action == android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId()
        && arguments != null && arguments.containsKey(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)) {
      float value = arguments.getFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE);
      if (Float.isFinite(value) && value >= 0 && value <= getMax()) {
        setProgress(Math.round(value));
        if (explicitSelection != null) explicitSelection.run();
        sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SELECTED);
        return true;
      }
    }
    boolean handled = super.performAccessibilityAction(action, arguments);
    if (handled && (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        || action == android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        && explicitSelection != null) explicitSelection.run();
    return handled;
  }

}
