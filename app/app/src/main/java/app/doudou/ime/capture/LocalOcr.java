package app.doudou.ime.capture;

import android.graphics.Bitmap;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

public final class LocalOcr {
  public interface Result {
    void complete(String text, String error);
  }

  public static void read(Bitmap image, Result result) {
    TextRecognizer recognizer =
        TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
    try {
      recognizer
          .process(InputImage.fromBitmap(image, 0))
          .addOnCompleteListener(
              task -> {
                image.recycle();
                recognizer.close();
                if (task.isSuccessful()) result.complete(task.getResult().getText(), null);
                else result.complete(null, "本地识字失败，请重试或手动补充文字");
              });
    } catch (Exception e) {
      image.recycle();
      recognizer.close();
      result.complete(null, "本地识字不可用");
    }
  }
}
