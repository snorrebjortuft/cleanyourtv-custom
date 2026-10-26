package no.kongsnorre.fotball;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Club logos from NIFS, loaded off the main thread and kept in memory while the app runs. */
final class Logos {

    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(60);
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void into(ImageView view, String url) {
        view.setTag(url);
        if (url == null) {
            view.setImageDrawable(null);
            return;
        }
        Bitmap hit = CACHE.get(url);
        if (hit != null) {
            view.setImageBitmap(hit);
            return;
        }
        view.setImageDrawable(null);
        POOL.execute(() -> {
            Bitmap b;
            try {
                byte[] bytes = Nifs.fetch(url);
                b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            } catch (Exception e) {
                return;
            }
            if (b == null) return;
            CACHE.put(url, b);
            MAIN.post(() -> {
                if (url.equals(view.getTag())) view.setImageBitmap(b);
            });
        });
    }
}
