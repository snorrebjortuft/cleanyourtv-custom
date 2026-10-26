package com.gathin.photodream;

import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UploadServerTest {

    private static String request(int port, String text, byte[] body) throws Exception {
        try (Socket s = new Socket("127.0.0.1", port)) {
            OutputStream out = s.getOutputStream();
            out.write(text.getBytes(StandardCharsets.US_ASCII));
            if (body != null) out.write(body);
            out.flush();
            InputStream in = s.getInputStream();
            byte[] buf = new byte[256];
            int n = in.read(buf);
            return n <= 0 ? "" : new String(buf, 0, n, StandardCharsets.US_ASCII);
        }
    }

    private static UploadServer server(File dir, AtomicBoolean locked) {
        return new UploadServer(dir, "abcdefghjk", "<html>page</html>", new UploadServer.Listener() {
            @Override public void received(String name) { }
            @Override public void locked() { locked.set(true); }
        });
    }

    @Test public void theRightCodeGetsThePageAndCanUpload() throws Exception {
        File dir = Files.createTempDirectory("upload").toFile();
        AtomicBoolean locked = new AtomicBoolean();
        UploadServer s = server(dir, locked);
        int port = s.start();
        try {
            assertTrue(request(port, "GET /abcdefghjk/ HTTP/1.1\r\n\r\n", null).startsWith("HTTP/1.1 200"));
            byte[] jpeg = {1, 2, 3, 4};
            String r = request(port, "PUT /abcdefghjk/upload?name=a.jpg HTTP/1.1\r\nContent-Length: 4\r\n\r\n", jpeg);
            assertTrue(r.startsWith("HTTP/1.1 200"));
            assertEquals(4, new File(dir, "a.jpg").length());
            // a name that could climb out of the folder is refused
            r = request(port, "PUT /abcdefghjk/upload?name=..%2Fx.jpg HTTP/1.1\r\nContent-Length: 4\r\n\r\n", jpeg);
            assertTrue(r.startsWith("HTTP/1.1 400"));
        } finally {
            s.stop();
        }
        assertFalse(locked.get());
    }

    @Test public void wrongCodesShutTheServerDown() throws Exception {
        File dir = Files.createTempDirectory("upload").toFile();
        AtomicBoolean locked = new AtomicBoolean();
        int port = server(dir, locked).start();
        request(port, "GET /favicon.ico HTTP/1.1\r\n\r\n", null);   // browsers do this; not counted
        for (int i = 0; i < UploadServer.MAX_FAILURES; i++) {
            assertTrue(request(port, "GET /guess" + i + "/ HTTP/1.1\r\n\r\n", null).startsWith("HTTP/1.1 404"));
        }
        assertTrue(locked.get());
        boolean refused;
        try (Socket ignored = new Socket("127.0.0.1", port)) {
            refused = false;
        } catch (java.io.IOException e) {
            refused = true;
        }
        assertTrue("port still open after lock-out", refused);
    }

    @Test public void codesAreLongAndFromTheSafeSet() {
        String c = UploadServer.newCode(new Random(1));
        assertEquals(10, c.length());
        assertTrue(c.matches("[a-km-np-z2-9]{10}"));
    }
}
