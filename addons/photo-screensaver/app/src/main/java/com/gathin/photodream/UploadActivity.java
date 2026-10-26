package com.gathin.photodream;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Locale;

/**
 * "Send pictures from your phone": while this screen is open, a QR code leads the phone to a page on the TV
 * (UploadServer) where photos are picked and sent straight into the screensaver's folder. Closing the screen stops
 * the server; a fresh PIN each time keeps other devices on the network out.
 */
public final class UploadActivity extends Activity {

    private UploadServer server;
    private TextView status;
    private int received;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        float d = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF101418);
        setContentView(root);

        ImageView qr = new ImageView(this);
        root.addView(qr, new LinearLayout.LayoutParams((int) (300 * d), (int) (300 * d)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams((int) (520 * d), -2);
        tl.setMarginStart((int) (48 * d));
        root.addView(texts, tl);
        text(texts, getString(R.string.upload_title), 34, Color.WHITE);
        TextView how = text(texts, getString(R.string.upload_how), 18, 0xCCFFFFFF);
        TextView url = text(texts, "", 20, 0xFFFFC93C);
        status = text(texts, "", 18, 0x99FFFFFF);

        String ip = ipv4();
        File folder = new File(getExternalFilesDir(null), Media.PICTURES);
        String pin = UploadServer.newCode(new SecureRandom());
        server = new UploadServer(folder, pin, page(), new UploadServer.Listener() {
            @Override
            public void received(String name) {
                runOnUiThread(() -> {
                    received++;
                    status.setText(getString(R.string.upload_received, received));
                });
            }

            @Override
            public void locked() {
                runOnUiThread(() -> {
                    qr.setImageDrawable(null);
                    url.setText("");
                    status.setText(R.string.upload_locked);
                });
            }
        });
        if (ip == null) {
            how.setText(R.string.upload_no_network);
            return;
        }
        try {
            int port = server.start();
            String link = "http://" + ip + ":" + port + "/" + pin + "/";
            url.setText(link);
            qr.setImageBitmap(qr(link, (int) (300 * d)));
            status.setText(R.string.upload_waiting);
        } catch (IOException | WriterException e) {
            how.setText(R.string.upload_no_network);
        }
    }

    @Override
    protected void onDestroy() {
        if (server != null) server.stop();
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        super.onStop();
        finish();   // the server lives only while the screen is on
    }

    private TextView text(LinearLayout parent, String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setPadding(0, (int) (8 * getResources().getDisplayMetrics().density), 0, 0);
        parent.addView(t, new LinearLayout.LayoutParams(-1, -2));
        return t;
    }

    /** The TV's own IPv4 address on the home network, read from the interfaces (no permission needed). */
    private static String ipv4() {
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                for (InetAddress a : Collections.list(nif.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch (SocketException ignored) {
        }
        return null;
    }

    private static Bitmap qr(String text, int size) throws WriterException {
        BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size);
        int[] px = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) px[y * size + x] = m.get(x, y) ? Color.BLACK : Color.WHITE;
        }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.RGB_565);
    }

    /** The phone's page: pick photos, each is shrunk to a JPEG and sent one at a time, with progress. */
    private static String page() {
        return "<!doctype html><html lang=no><meta charset=utf-8>"
                + "<meta name=viewport content='width=device-width,initial-scale=1'><title>Send bilder til TV-en</title>"
                + "<style>body{font-family:system-ui,sans-serif;background:#101418;color:#fff;margin:0;padding:24px}"
                + "h1{font-weight:300}label{display:block;background:#5aa9e6;color:#000;text-align:center;padding:18px;"
                + "border-radius:14px;font-size:20px;font-weight:600}input{display:none}#log{margin-top:20px;line-height:1.6;"
                + "color:#cfd6dd}</style><h1>Send bilder til TV-en</h1>"
                + "<label>Velg bilder<input id=f type=file accept='image/*' multiple></label><div id=log></div><script>"
                + "const log=t=>{document.getElementById('log').innerHTML+=t+'<br>'};"
                + "async function jpeg(file){const img=await createImageBitmap(file);const s=Math.min(1,3840/Math.max(img.width,img.height));"
                + "const c=document.createElement('canvas');c.width=Math.round(img.width*s);c.height=Math.round(img.height*s);"
                + "c.getContext('2d').drawImage(img,0,0,c.width,c.height);return await new Promise(r=>c.toBlob(r,'image/jpeg',0.88));}"
                + "document.getElementById('f').onchange=async e=>{const files=[...e.target.files];let ok=0;"
                + "for(let i=0;i<files.length;i++){const name='mobil-'+Date.now()+'-'+i+'.jpg';"
                + "try{const blob=await jpeg(files[i]);const r=await fetch('upload?name='+name,{method:'PUT',body:blob});"
                + "if(r.ok){ok++;log('✔ '+files[i].name)}else log('✖ '+files[i].name+' (for stort eller fullt)')}"
                + "catch(x){log('✖ '+files[i].name)}}log('<b>'+ok+' av '+files.length+' sendt.</b>')};</script></html>";
    }
}
