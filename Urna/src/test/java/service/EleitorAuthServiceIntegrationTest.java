package service;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Assumptions;
import service.firebase.FirebaseAdminSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EleitorAuthServiceIntegrationTest {
    @Test
    @Tag("integration")
    void signsUpLogsInAndRemovesTemporaryVoter() throws Exception {
        Map<String, String> config = loadConfig();
        String apiKey = config.get("FIREBASE_API_KEY");
        String databaseUrl = config.get("FIREBASE_DATABASE_URL");
        String adminEmail = config.get("FIREBASE_EMAIL");
        String adminPassword = config.get("FIREBASE_PASSWORD");
        Assumptions.assumeTrue(present(apiKey) && present(databaseUrl)
                        && present(adminEmail) && present(adminPassword),
                "Firebase integration credentials are not configured.");

        String testName = "eleitor" + UUID.randomUUID().toString().replace("-", "");
        String voterKey = voterKey(testName);
        char[] voterPassword = "Senha-temporaria-742!".toCharArray();
        FirebaseAdminSession firebase = new FirebaseAdminSession(apiKey, databaseUrl);
        try {
            firebase.signInAdmin(adminEmail, adminPassword.toCharArray());
            EleitorAuthService auth = new EleitorAuthService(firebase);

            EleitorAuthService.EleitorAccount created = auth.signUp(testName, voterPassword);
            assertEquals(testName, created.nome());
            assertTrue(auth.isEleitorSignedIn());
            assertTrue(allFalseFlags(firebase.readJson("eleitores/" + voterKey).getAsJsonObject()));

            JsonObject stored = firebase.readJson("eleitores/" + voterKey).getAsJsonObject();
            assertEquals(Set.of("nome", "senhaHash", "votou_P", "votou_G"), stored.keySet());
            assertTrue(stored.get("senhaHash").getAsString().startsWith("pbkdf2-sha256$"));

            auth.signOut();
            assertFalse(auth.isEleitorSignedIn());

            EleitorAuthService.EleitorAccount loggedIn = auth.signIn(testName, "Senha-temporaria-742!".toCharArray());
            assertEquals(created.id(), loggedIn.id());
            assertEquals(created.nome(), loggedIn.nome());
            assertTrue(auth.isEleitorSignedIn());

            auth.signOut();
            assertThrows(IOException.class,
                    () -> auth.signIn(testName, "senha-incorreta".toCharArray()));
            assertFalse(auth.isEleitorSignedIn());
            assertThrows(IllegalArgumentException.class,
                    () -> auth.signUp("nome com espaço", "Senha-temporaria-742!".toCharArray()));
            assertThrows(IllegalArgumentException.class,
                    () -> auth.signUp(testName.toUpperCase(java.util.Locale.ROOT),
                            "Outra-senha-743!".toCharArray()));
        } finally {
            try {
                if (firebase.isAdminSignedIn()) {
                    firebase.delete("eleitores/" + voterKey);
                }
            } finally {
                firebase.close();
            }
        }
    }

    private static boolean allFalseFlags(JsonObject profile) {
        return profile.has("votou_P") && profile.has("votou_G")
                && !profile.get("votou_P").getAsBoolean() && !profile.get("votou_G").getAsBoolean();
    }

    private static String voterKey(String name) throws Exception {
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFKC)
                .toLowerCase(java.util.Locale.ROOT);
        return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8)));
    }

    private static Map<String, String> loadConfig() throws IOException {
        Map<String, String> values = new HashMap<>();
        Path envFile = Path.of(".env");
        if (Files.isRegularFile(envFile)) {
            for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int separator = trimmed.indexOf('=');
                if (separator < 1) {
                    continue;
                }
                String key = trimmed.substring(0, separator).trim();
                String value = trimmed.substring(separator + 1).trim();
                if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                values.putIfAbsent(key, value);
            }
        }
        for (String key : Set.of("FIREBASE_API_KEY", "FIREBASE_DATABASE_URL", "FIREBASE_EMAIL", "FIREBASE_PASSWORD")) {
            String environmentValue = System.getenv(key);
            if (environmentValue != null && !environmentValue.isBlank()) {
                values.put(key, environmentValue);
            }
        }
        return values;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
