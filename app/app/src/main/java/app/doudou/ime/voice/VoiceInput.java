package app.doudou.ime.voice;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.speech.*;
import java.util.*;

/** Device-provided ASR is separate from future paid ASR. Online system ASR requires opt-in. */
public final class VoiceInput implements RecognitionListener {
  public interface Listener {
    void text(String value, boolean finalResult);

    void status(String value);

    void error(String value);

    default void level(float rmsDb) {}

    default void processing() {}
  }

  private final Context context;
  private SpeechRecognizer recognizer;
  private Listener listener;
  private boolean cancelled;
  private int epoch;

  public VoiceInput(Context c) {
    context = c;
  }

  public void start(boolean allowOnline, Listener l) {
    cancel();
    listener = l;
    cancelled = false;
    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        != PackageManager.PERMISSION_GRANTED) {
      l.error("请先在设置中允许麦克风权限");
      return;
    }
    boolean offline =
        Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context);
    if (!offline && !allowOnline) {
      l.error("这台手机没有本地语音服务。可在设置中自愿启用系统在线识别，或稍后接入专用 ASR");
      return;
    }
    if (!SpeechRecognizer.isRecognitionAvailable(context) && !offline) {
      l.error("系统没有可用语音识别服务；专用 ASR 尚未配置");
      return;
    }
    try {
      recognizer =
          offline
              ? SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
              : SpeechRecognizer.createSpeechRecognizer(context);
      final int request = epoch;
      recognizer.setRecognitionListener(
          new RecognitionListener() {
            boolean valid() {
              return request == epoch && !cancelled;
            }

            public void onReadyForSpeech(Bundle b) {
              if (valid()) VoiceInput.this.onReadyForSpeech(b);
            }

            public void onBeginningOfSpeech() {
              if (valid()) VoiceInput.this.onBeginningOfSpeech();
            }

            public void onRmsChanged(float f) {
              if (valid()) VoiceInput.this.onRmsChanged(f);
            }

            public void onBufferReceived(byte[] a) {}

            public void onEndOfSpeech() {
              if (valid()) VoiceInput.this.onEndOfSpeech();
            }

            public void onError(int e) {
              if (valid()) VoiceInput.this.onError(e);
            }

            public void onResults(Bundle b) {
              if (valid()) VoiceInput.this.onResults(b);
            }

            public void onPartialResults(Bundle b) {
              if (valid()) VoiceInput.this.onPartialResults(b);
            }

            public void onEvent(int t, Bundle b) {}
          });
      Intent intent =
          new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
              .putExtra(
                  RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
              .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
              .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
              .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, offline);
      l.status(offline ? "本机识别 · 正在听" : "系统在线识别 · 正在听");
      recognizer.startListening(intent);
    } catch (Exception e) {
      cancel();
      l.error("系统语音启动失败，请检查系统服务和权限");
    }
  }

  public void stop() {
    if (recognizer != null) recognizer.stopListening();
  }

  public void cancel() {
    epoch++;
    cancelled = true;
    if (recognizer != null) {
      recognizer.cancel();
      recognizer.destroy();
      recognizer = null;
    }
    listener = null;
  }

  private void result(Bundle b, boolean fin) {
    if (cancelled || listener == null) return;
    ArrayList<String> texts = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
    if (texts != null && !texts.isEmpty()) listener.text(texts.get(0), fin);
  }

  @Override
  public void onReadyForSpeech(Bundle b) {}

  @Override
  public void onBeginningOfSpeech() {
    if (listener != null) listener.status("正在听，说完点停止");
  }

  @Override
  public void onRmsChanged(float f) {
    if (!cancelled && listener != null) listener.level(f);
  }

  @Override
  public void onBufferReceived(byte[] a) {}

  @Override
  public void onEndOfSpeech() {
    if (!cancelled && listener != null) {
      listener.status("正在识别…");
      listener.processing();
    }
  }

  @Override
  public void onError(int e) {
    if (!cancelled && listener != null)
      listener.error(
          e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
              ? "没有听清，请再说一次"
              : e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                  ? "麦克风权限已撤销"
                  : "系统语音暂不可用（" + e + "），可以重试");
  }

  @Override
  public void onResults(Bundle b) {
    result(b, true);
  }

  @Override
  public void onPartialResults(Bundle b) {
    result(b, false);
  }

  @Override
  public void onEvent(int t, Bundle b) {}
}
