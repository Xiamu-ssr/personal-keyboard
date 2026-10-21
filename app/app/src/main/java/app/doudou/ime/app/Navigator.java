package app.doudou.ime.app;

public interface Navigator {
  void open(String page, long person);

  void refresh();

  void notice(String text, Runnable undo);
}
