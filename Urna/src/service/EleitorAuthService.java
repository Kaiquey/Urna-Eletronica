package service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import service.firebase.FirebaseAdminSession;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Base64;

/** Cadastro e login locais de eleitores usando a sessão Firebase do administrador. */
public final class EleitorAuthService {
    private static final String ELEITORES_PATH = "eleitores";
    private static final int HASH_LENGTH_BITS = 256;
    private static final int PBKDF2_ITERATIONS = 210_000;

    private final FirebaseAdminSession firebase;
    private final SecureRandom secureRandom = new SecureRandom();
    private EleitorAccount currentEleitor;

    public EleitorAuthService(FirebaseAdminSession firebase) {
        if (firebase == null) {
            throw new IllegalArgumentException("A sessão Firebase é obrigatória.");
        }
        this.firebase = firebase;
    }

    /** Registra um eleitor no banco; requer que o administrador já tenha aberto a urna. */
    public synchronized EleitorAccount signUp(String nome, char[] password)
            throws IOException, InterruptedException {
        currentEleitor = null;
        try {
            requireAdminSession();
            String normalizedName = normalizeName(nome);
            validatePassword(password);
            String displayName = Normalizer.normalize(nome, Normalizer.Form.NFKC);
            String key = voterKey(normalizedName);
            JsonObject profile = new JsonObject();
            profile.addProperty("nome", displayName);
            profile.addProperty("senhaHash", encodePasswordHash(password));
            profile.addProperty("votou_P", false);
            profile.addProperty("votou_G", false);
            firebase.writeJsonIfAbsent(ELEITORES_PATH + "/" + key, profile);

            currentEleitor = new EleitorAccount(key, displayName);
            return currentEleitor;
        } finally {
            clear(password);
        }
    }

    /** Autentica o eleitor pelo nome e senha armazenados no banco. */
    public synchronized EleitorAccount signIn(String nome, char[] password)
            throws IOException, InterruptedException {
        currentEleitor = null;
        try {
            requireAdminSession();
            String normalizedName = normalizeName(nome);
            validatePassword(password);
            String key = voterKey(normalizedName);
            JsonElement result = firebase.readJson(ELEITORES_PATH + "/" + key);
            if (result == null || !result.isJsonObject()) {
                throw invalidCredentials();
            }

            JsonObject profile = result.getAsJsonObject();
            String passwordHash = readString(profile, "senhaHash");
            if (!passwordMatches(password, passwordHash)) {
                throw invalidCredentials();
            }

            String displayName = readName(profile);
            readBoolean(profile, "votou_P");
            readBoolean(profile, "votou_G");
            currentEleitor = new EleitorAccount(key, displayName);
            return currentEleitor;
        } finally {
            clear(password);
        }
    }

    public synchronized void signOut() {
        currentEleitor = null;
    }

    /** Marca o voto da categoria uma única vez, com proteção contra chamadas concorrentes. */
    public synchronized boolean marcarVoto(TipoVoto tipoVoto) throws IOException, InterruptedException {
        requireAdminSession();
        if (currentEleitor == null) {
            throw new IllegalStateException("O eleitor precisa estar logado para votar.");
        }
        if (tipoVoto == null) {
            throw new IllegalArgumentException("A categoria do voto é obrigatória.");
        }
        return firebase.setJsonBooleanIfFalse(
                ELEITORES_PATH + "/" + currentEleitor.id(), tipoVoto.campoVotacao);
    }

    /** Consulta se o eleitor atual já votou na categoria informada. */
    public synchronized boolean jaVotou(TipoVoto tipoVoto) throws IOException, InterruptedException {
        requireAdminSession();
        if (currentEleitor == null) throw new IllegalStateException("O eleitor precisa estar logado.");
        JsonElement result = firebase.readJson(ELEITORES_PATH + "/" + currentEleitor.id());
        if (result == null || !result.isJsonObject()) throw invalidCredentials();
        return readBoolean(result.getAsJsonObject(), tipoVoto.campoVotacao);
    }

    public synchronized boolean isEleitorSignedIn() {
        return currentEleitor != null && firebase.isAdminSignedIn();
    }

    public synchronized java.util.Optional<EleitorAccount> getCurrentEleitor() {
        return isEleitorSignedIn() ? java.util.Optional.of(currentEleitor) : java.util.Optional.empty();
    }

    private void requireAdminSession() {
        if (!firebase.isAdminSignedIn()) {
            currentEleitor = null;
            throw new IllegalStateException("O administrador precisa abrir a sessão da urna antes do login do eleitor.");
        }
    }

    private static String normalizeName(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome é obrigatório.");
        }
        String normalized = Normalizer.normalize(nome, Normalizer.Form.NFKC);
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                throw new IllegalArgumentException("O nome não pode conter espaços.");
            }
            offset += Character.charCount(codePoint);
        }
        return normalized.toLowerCase(java.util.Locale.ROOT);
    }

    private static void validatePassword(char[] password) {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("A senha é obrigatória.");
        }
    }

    private static String voterKey(String normalizedName) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalizedName.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 não está disponível.", exception);
        }
    }

    private static byte[] derivePasswordHash(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, HASH_LENGTH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException("Não foi possível proteger a senha do eleitor.", exception);
        } finally {
            spec.clearPassword();
        }
    }

    private static String readString(JsonObject profile, String field) throws IOException {
        JsonElement value = profile.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()) {
            throw invalidCredentials();
        }
        return value.getAsString();
    }

    private static boolean readBoolean(JsonObject profile, String field) throws IOException {
        JsonElement value = profile.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw invalidCredentials();
        }
        return value.getAsBoolean();
    }

    private static String readName(JsonObject profile) throws IOException {
        JsonElement value = profile.get("nome");
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()) {
            throw invalidCredentials();
        }
        return value.getAsString();
    }

    private static IOException invalidCredentials() {
        return new IOException("Nome ou senha inválidos.");
    }

    private String encodePasswordHash(char[] password) {
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        byte[] hash = derivePasswordHash(password, salt);
        return "pbkdf2-sha256$" + PBKDF2_ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    private static boolean passwordMatches(char[] password, String encodedHash) throws IOException {
        String[] parts = encodedHash.split("\\$", -1);
        if (parts.length != 4 || !"pbkdf2-sha256".equals(parts[0])) {
            throw invalidCredentials();
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            if (iterations != PBKDF2_ITERATIONS) {
                throw invalidCredentials();
            }
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[3]);
            byte[] actualHash = derivePasswordHash(password, salt);
            return MessageDigest.isEqual(expectedHash, actualHash);
        } catch (IllegalArgumentException exception) {
            throw invalidCredentials();
        }
    }

    private static void clear(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }

    public record EleitorAccount(String id, String nome) {}

    public enum TipoVoto {
        PRESIDENTE("votou_P"),
        GOVERNADOR("votou_G");

        private final String campoVotacao;

        TipoVoto(String campoVotacao) {
            this.campoVotacao = campoVotacao;
        }
    }
}
