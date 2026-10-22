package app.doudou.ime.people;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import app.doudou.ime.design.Ui;
import app.doudou.ime.memory.MemoryStore;
import java.util.*;

public final class RelationshipGraph extends View {
  public interface PersonTap {
    void select(long id);
  }

  final List<MemoryStore.Item> people;
  final PersonTap callback;
  final Paint paint = new Paint(3);
  final List<PointF> points = new ArrayList<>();

  public RelationshipGraph(Context c, List<MemoryStore.Item> p, PersonTap cb) {
    super(c);
    people = p;
    callback = cb;
    setMinimumHeight(Ui.dp(c, 220));
    setContentDescription("人物关系图，下面的列表提供全部人物入口");
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    points.clear();
    float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(getWidth(), getHeight()) * .34f;
    paint.setTextAlign(Paint.Align.CENTER);
    paint.setStrokeWidth(Ui.dp(getContext(), 1));
    int n = Math.min(8, people.size());
    for (int i = 0; i < n; i++) {
      double angle = -Math.PI / 2 + 2 * Math.PI * i / n;
      float x = cx + (float) Math.cos(angle) * r, y = cy + (float) Math.sin(angle) * r;
      points.add(new PointF(x, y));
      paint.setColor(Ui.LINE);
    }
    for (int i = 0; i < n; i++) {
      try {
        org.json.JSONArray relations =
            new org.json.JSONObject(people.get(i).aux).getJSONArray("relations");
        for (int j = 0; j < relations.length(); j++) {
          org.json.JSONObject relation = relations.getJSONObject(j);
          String target = relation.getString("targetId");
          PointF end = null;
          if (target.equals("self")) end = new PointF(cx, cy);
          else
            for (int k = 0; k < n; k++)
              if (people.get(k).globalId.equals(target)) end = points.get(k);
          if (end != null) {
            PointF start = points.get(i);
            paint.setColor(Ui.LINE);
            c.drawLine(start.x, start.y, end.x, end.y, paint);
          }
        }
      } catch (org.json.JSONException ignored) {
      }
    }
    node(c, cx, cy, "我", true);
    for (int i = 0; i < n; i++) {
      PointF p = points.get(i);
      String name = people.get(i).title;
      node(c, p.x, p.y, name.length() > 4 ? name.substring(0, 4) : name, false);
    }
  }

  void node(Canvas c, float x, float y, String name, boolean me) {
    paint.setColor(me ? Ui.ACCENT : Ui.KEY);
    c.drawCircle(x, y, Ui.dp(getContext(), 29), paint);
    paint.setTextSize(Ui.dp(getContext(), 13));
    paint.setColor(me ? Ui.BG : Ui.INK);
    c.drawText(name, x, y + Ui.dp(getContext(), 5), paint);
  }

  @Override
  public boolean onTouchEvent(android.view.MotionEvent e) {
    if (e.getAction() == MotionEvent.ACTION_UP) {
      for (int i = 0; i < points.size(); i++)
        if (Math.hypot(e.getX() - points.get(i).x, e.getY() - points.get(i).y)
            < Ui.dp(getContext(), 34)) {
          performClick();
          callback.select(people.get(i).id);
          break;
        }
      return true;
    }
    return true;
  }

  @Override
  public boolean performClick() {
    return super.performClick();
  }
}
