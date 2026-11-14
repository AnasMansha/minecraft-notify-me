import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scripted fake phone for end-to-end testing of the mod's link server (JDK 11+).
 * Run with the mod's dev jar on the classpath (for Callsign):
 *   java -cp build/libs/notifyme-1.0.0-dev.jar tools/LinkTestClient.java 127.0.0.1 25570 callsign.txt
 * Writes its callsign to callsign.txt, then expects: pending -> welcome -> self-test block -> alert -> acked
 * -> config rename -> test alert -> bye. A driver script must run the matching /notifyme commands.
 */
public class LinkTestClient {

    static final BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
    static int failures = 0;

    public static void main(String[] a) throws Exception {
        String host = a[0];
        int port = Integer.parseInt(a[1]);
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = g.generateKeyPair();
        byte[] pub = kp.getPublic().getEncoded();
        String callsign = com.notifyme.util.Callsign.fromPublicKey(pub);
        Files.writeString(Path.of(a[2]), callsign);
        log("callsign " + callsign);

        WebSocket ws = null;
        long deadline = System.currentTimeMillis() + 300_000;
        while (ws == null) {
            try {
                ws = HttpClient.newHttpClient().newWebSocketBuilder()
                    .buildAsync(URI.create("ws://" + host + ":" + port + "/ws"), new Listener()).join();
            } catch (Exception e) {
                if (System.currentTimeMillis() > deadline) throw e;
                Thread.sleep(2000);
            }
        }
        log("connected");
        ws.sendText("{\"t\":\"hello\",\"v\":1,\"callsign\":\"" + callsign.toLowerCase().replace(" ", "")
            + "\",\"key\":\"" + Base64.getEncoder().encodeToString(pub) + "\",\"device\":\"Test client\"}", true).join();

        String ch = expect("challenge", 10);
        String nonce = field(ch, "nonce"), serverId = field(ch, "serverId");
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(kp.getPrivate());
        s.update(("notifyme-auth|" + nonce + "|" + serverId).getBytes(StandardCharsets.UTF_8));
        ws.sendText("{\"t\":\"auth\",\"sig\":\"" + Base64.getEncoder().encodeToString(s.sign()) + "\"}", true).join();

        expect("pending", 10);
        expect("welcome", 120);
        expect("snapshot", 10);
        String block = expectContaining("\"t\":\"block\"", "Self test", 60);
        String uuid = field(block, "uuid");
        String alert = expect("alert", 60);
        check(alert.contains("\"active\":true"), "alert says active=true");
        check(alert.contains("\"tier\":2"), "alert tier 2 (default Notify)");
        ws.sendText("{\"t\":\"ack\",\"id\":\"" + field(alert, "id") + "\"}", true).join();
        expect("acked", 10);
        expectContaining("\"t\":\"block\"", "\"active\":false", 60);

        ws.sendText("{\"t\":\"config\",\"uuid\":\"" + uuid + "\",\"name\":\"Renamed from phone\",\"tier\":4}", true).join();
        String renamed = expectContaining("\"t\":\"block\"", "Renamed from phone", 10);
        check(renamed.contains("\"tier\":4"), "tier changed to 4 from phone");
        ws.sendText("{\"t\":\"test\",\"uuid\":\"" + uuid + "\"}", true).join();
        String test = expect("alert", 10);
        check(test.contains("\"test\":true") && test.contains("\"tier\":4"), "test alert with new tier");

        expect("bye", 180);
        log(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    static class Listener implements WebSocket.Listener {
        StringBuilder buf = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buf.append(data);
            if (last) {
                inbox.add(buf.toString());
                buf = new StringBuilder();
            }
            ws.request(1);
            return null;
        }
    }

    static String expect(String type, int seconds) throws InterruptedException {
        return expectContaining("\"t\":\"" + type + "\"", "", seconds);
    }

    static String expectContaining(String typeToken, String needle, int seconds) throws InterruptedException {
        long end = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < end) {
            String m = inbox.poll(500, TimeUnit.MILLISECONDS);
            if (m == null) continue;
            if (m.contains(typeToken) && m.contains(needle)) {
                log("OK   got " + m);
                return m;
            }
            log("     (skip) " + m);
        }
        log("FAIL timed out waiting for " + typeToken + " " + needle);
        System.exit(2);
        return null;
    }

    static void check(boolean ok, String what) {
        log((ok ? "OK   " : "FAIL ") + what);
        if (!ok) failures++;
    }

    static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\":\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : "";
    }

    static void log(String s) {
        System.out.println("[client] " + s);
    }
}
