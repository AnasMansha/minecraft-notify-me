package com.notifyme.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phone callsigns, e.g. "Odd Deer 68".
 * <p>
 * Derived from SHA-256(public key X.509 bytes): adjective = h[0], noun = h[1], number = h[2] % 100.
 * MUST stay identical to the Android implementation (app/.../Callsign.kt).
 */
public final class Callsign {

    private static List<String> adjectives;
    private static List<String> nouns;
    private static Map<String, Integer> adjIndex;
    private static Map<String, Integer> nounIndex;

    private Callsign() {}

    private static synchronized void load() {
        if (adjectives != null) return;
        adjectives = readList("/assets/notifyme/callsign_adjectives.txt");
        nouns = readList("/assets/notifyme/callsign_nouns.txt");
        adjIndex = new HashMap<>();
        nounIndex = new HashMap<>();
        for (int i = 0; i < adjectives.size(); i++) adjIndex.put(
            adjectives.get(i)
                .toLowerCase(Locale.ROOT),
            i);
        for (int i = 0; i < nouns.size(); i++) nounIndex.put(
            nouns.get(i)
                .toLowerCase(Locale.ROOT),
            i);
    }

    private static List<String> readList(String path) {
        List<String> out = new ArrayList<>();
        try (InputStream in = Callsign.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing " + path);
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) out.add(line);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load callsign word list " + path, e);
        }
        if (out.size() != 256) throw new IllegalStateException(path + " must have 256 words, has " + out.size());
        return out;
    }

    private static String format(int adj, int noun, int num) {
        load();
        return adjectives.get(adj) + " " + nouns.get(noun) + " " + String.format(Locale.ROOT, "%02d", num);
    }

    /** Callsign for a public key (X.509 encoded bytes): adjective = h[0], noun = h[1], number = h[2] % 100. */
    public static String fromPublicKey(byte[] encodedKey) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256")
                .digest(encodedKey);
            return format(h[0] & 0xFF, h[1] & 0xFF, (h[2] & 0xFF) % 100);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Parses user input ("odd deer 68", "OddDeer68", ...) into canonical form ("Odd Deer 68"),
     * or returns null if it is not a valid callsign.
     */
    public static String normalize(String input) {
        if (input == null) return null;
        load();
        String s = input.replaceAll("[^A-Za-z0-9]", "")
            .toLowerCase(Locale.ROOT);
        if (s.length() < 4) return null;
        String digits = s.substring(s.length() - 2);
        String words = s.substring(0, s.length() - 2);
        if (!digits.matches("[0-9]{2}") || !words.matches("[a-z]+")) return null;
        int num = Integer.parseInt(digits);
        for (int split = 1; split < words.length(); split++) {
            Integer a = adjIndex.get(words.substring(0, split));
            Integer n = nounIndex.get(words.substring(split));
            if (a != null && n != null) return format(a, n, num);
        }
        return null;
    }
}
