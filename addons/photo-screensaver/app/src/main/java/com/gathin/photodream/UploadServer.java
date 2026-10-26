package com.gathin.photodream;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * A tiny web server for the "Send pictures from your phone" screen, running only while that screen is open.
 * GET /PIN/ serves the upload page; PUT /PIN/upload?name=x.jpg stores one picture in the screensaver's folder.
 * Anything without the code gets 404, and after MAX_FAILURES such requests the server stops. The page turns every photo into a JPEG of at most 3840 px on the phone first,
 * so iPhone HEIC photos work and stay under the 10 MB limit.
 */
final class UploadServer {

    interface Listener {
        void received(String name);

        /** Too many requests with a wrong code: the server has shut itself down. */
        void locked();
    }

    /** Wrong-code requests tolerated before the server stops; the QR code carries the right one. */
    static final int MAX_FAILURES = 5;
    private int failures;

    private final File folder;
    private final String pin;
    private final String page;
    private final Listener listener;
    private ServerSocket socket;
    private Thread thread;

    UploadServer(File folder, String pin, String page, Listener listener) {
        this.folder = folder;
        this.pin = pin;
        this.page = page;
        this.listener = listener;
    }

    /** Binds the first free port from 8080 and returns it. */
    int start() throws IOException {
        IOException last = null;
        for (int port = 8080; port < 8090; port++) {
            try {
                socket = new ServerSocket(port);
                break;
            } catch (IOException e) {
                last = e;
            }
        }
        if (socket == null) throw last;
        thread = new Thread(this::serve, "upload");
        thread.start();
        return socket.getLocalPort();
    }

    void stop() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
        socket = null;
    }

    private void serve() {
        ServerSocket s = socket;
        while (s != null && !s.isClosed()) {
            try (Socket c = s.accept()) {
                c.setSoTimeout(30_000);
                handle(c);
            } catch (IOException e) {
                // a dropped request or the socket closing; carry on until stopped
            }
        }
    }

    private void handle(Socket c) throws IOException {
        InputStream in = c.getInputStream();
        String request = line(in);
        if (request == null) return;
        long length = -1;
        String h;
        while ((h = line(in)) != null && !h.isEmpty()) {
            if (h.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                try {
                    length = Long.parseLong(h.substring(15).trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        String[] parts = request.split(" ");
        if (parts.length < 2) return;
        String method = parts[0], target = parts[1];
        OutputStream out = new BufferedOutputStream(c.getOutputStream());
        String base = "/" + pin + "/";
        if (method.equals("GET") && (target.equals(base) || target.equals("/" + pin))) {
            reply(out, 200, "text/html; charset=utf-8", page.getBytes(StandardCharsets.UTF_8));
        } else if (method.equals("PUT") && target.startsWith(base + "upload?name=")) {
            String name = URLDecoder.decode(target.substring((base + "upload?name=").length()), "UTF-8");
            String stored = store(in, name, length);
            if (stored == null) reply(out, 400, "text/plain", "no".getBytes(StandardCharsets.UTF_8));
            else {
                reply(out, 200, "text/plain", "ok".getBytes(StandardCharsets.UTF_8));
                listener.received(stored);
            }
        } else {
            reply(out, 404, "text/plain", "404".getBytes(StandardCharsets.UTF_8));
            // Browsers ask for these by themselves; anything else is someone guessing the code.
            if (!target.startsWith("/favicon") && !target.equals("/robots.txt") && ++failures >= MAX_FAILURES) {
                out.flush();
                stop();
                listener.locked();
                return;
            }
        }
        out.flush();
    }

    /** A code for the link: 10 characters from a set without look-alikes (about 50 bits). */
    static String newCode(java.util.Random random) {
        String set = "abcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 10; i++) b.append(set.charAt(random.nextInt(set.length())));
        return b.toString();
    }

    /** Writes the body to a hidden file, then renames it into place; null when the name, size or count is wrong. */
    private String store(InputStream in, String name, long length) throws IOException {
        if (!Media.image(name) || length <= 0 || length > Media.IMAGE_MAX) return null;
        File[] existing = folder.listFiles();
        if (existing != null && existing.length >= Media.MAX_FILES) return null;
        //noinspection ResultOfMethodCallIgnored
        folder.mkdirs();
        File tmp = new File(folder, ".upload-" + name);
        try (OutputStream f = new FileOutputStream(tmp)) {
            byte[] buf = new byte[65536];
            long left = length;
            while (left > 0) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                if (n < 0) throw new IOException("short body");
                f.write(buf, 0, n);
                left -= n;
            }
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw e;
        }
        File dest = new File(folder, name);
        if (!tmp.renameTo(dest)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return null;
        }
        return name;
    }

    private static void reply(OutputStream out, int code, String type, byte[] body) throws IOException {
        String status = code == 200 ? "OK" : code == 400 ? "Bad Request" : "Not Found";
        String head = "HTTP/1.1 " + code + " " + status + "\r\nContent-Type: " + type + "\r\nContent-Length: "
                + body.length + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
    }

    /** One header line without CRLF, or null at the end of the stream. Lines are capped at 8 KB. */
    private static String line(InputStream in) throws IOException {
        StringBuilder b = new StringBuilder();
        int ch;
        while ((ch = in.read()) >= 0) {
            if (ch == '\n') break;
            if (ch != '\r' && b.length() < 8192) b.append((char) ch);
        }
        return ch < 0 && b.length() == 0 ? null : b.toString();
    }
}
