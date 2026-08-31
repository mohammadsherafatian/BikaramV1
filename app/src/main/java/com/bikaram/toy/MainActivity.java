package com.bikaram.toy;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import java.io.OutputStream;

public class MainActivity extends Activity implements BoredomView.Host {
  private BoredomView gameView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    gameView = new BoredomView(this, this);
    setContentView(gameView);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (gameView != null) gameView.onHostResume();
  }

  @Override
  protected void onPause() {
    if (gameView != null) gameView.onHostPause();
    super.onPause();
  }

  @Override
  @SuppressWarnings("deprecation")
  public void onBackPressed() {
    if (gameView == null || !gameView.navigateBack()) super.onBackPressed();
  }

  @Override
  protected void onDestroy() {
    if (gameView != null) gameView.release();
    super.onDestroy();
  }

  @Override
  public void shareSnapshot(String text) {
    try {
      if (gameView.getWidth() <= 0 || gameView.getHeight() <= 0) return;
      Bitmap bitmap =
          Bitmap.createBitmap(gameView.getWidth(), gameView.getHeight(), Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(bitmap);
      gameView.draw(canvas);

      ContentValues values = new ContentValues();
      values.put(
          MediaStore.Images.Media.DISPLAY_NAME, "bikaram-" + System.currentTimeMillis() + ".png");
      values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
      values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Bikaram");
      Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
      if (uri != null) {
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
          if (os == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 95, os)) {
            getContentResolver().delete(uri, null, null);
            bitmap.recycle();
            return;
          }
        }
        bitmap.recycle();
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("image/png");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.putExtra(Intent.EXTRA_TEXT, text);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, "رکورد بیکاری رو بفرست"));
      }
    } catch (Exception ignored) {
      Intent send = new Intent(Intent.ACTION_SEND);
      send.setType("text/plain");
      send.putExtra(Intent.EXTRA_TEXT, text);
      startActivity(Intent.createChooser(send, "اشتراک‌گذاری"));
    }
  }
}
