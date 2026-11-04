import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Lê output_candidatos/<pasta>/data.json e grava cada registro em
 * Realtime Database em FIREBASE_CANDIDATES_PATH/<nome-da-pasta>.
 *
 * Execute a partir da raiz do repositório:
 *   javac scripts/FirebaseCandidateImporter.java
 *   java -cp scripts FirebaseCandidateImporter
 */
public final class FirebaseCandidateImporter {
    private static final Pattern ID_TOKEN_PATTERN = Pattern.compile("\\\"idToken\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"");
    private static final Pattern ERROR_MESSAGE_PATTERN = Pattern.compile("\\\"message\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
    private static final String INVALID_FIREBASE_KEY_CHARS = ".#$[]/";

    private FirebaseCandidateImporter() {}

    public static void main(String[] args) {
        try {
            run(Path.of("").toAbsolutePath().normalize());
        } catch (Exception exception) {
            System.err.println("Falha ao importar candidatos: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static void run(Path projectRoot) throws IOException, InterruptedException {
        Map<String, String> envFile = readEnvFile(projectRoot.resolve(".env"));
        String apiKey = requiredValue("FIREBASE_API_KEY", envFile);
        String databaseUrl = requiredValue("FIREBASE_DATABASE_URL", envFile);
        String email = requiredValue("FIREBASE_EMAIL", envFile);
        String password = requiredValue("FIREBASE_PASSWORD", envFile);
        String candidatesPath = value("FIREBASE_CANDIDATES_PATH", envFile, "candidatos");
        String candidatesDirValue = value("CANDIDATES_DIR", envFile, "output_candidatos");

        URI databaseUri = validateDatabaseUrl(databaseUrl);
        validateFirebasePath(candidatesPath, "FIREBASE_CANDIDATES_PATH");
        Path candidatesDir = Path.of(candidatesDirValue);
        if (!candidatesDir.isAbsolute()) {
            candidatesDir = projectRoot.resolve(candidatesDir).normalize();
        }

        Map<String, String> candidateJson = loadCandidates(candidatesDir);
        String idToken = signIn(apiKey, email, password);
        updateDatabase(databaseUri, candidatesPath, idToken, candidateJson);
        System.out.printf("Importação concluída: %d candidatos gravados em %s/%s.%n",
                candidateJson.size(), databaseUri, candidatesPath);
    }

    private static Map<String, String> readEnvFile(Path envPath) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        if (!Files.exists(envPath)) {
            return values;
        }

        int lineNumber = 0;
        for (String rawLine : Files.readAllLines(envPath, StandardCharsets.UTF_8)) {
            lineNumber++;
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).trim();
            }

            int separator = line.indexOf('=');
            if (separator <= 0) {
                throw new IOException("Formato inválido no .env, linha " + lineNumber + ". Use CHAVE=VALOR.");
            }

            String key = line.substring(0, separator).trim();
            String value = unquote(line.substring(separator + 1).trim());
            values.put(key, value);
        }
        return values;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                value = value.substring(1, value.length() - 1);
                if (first == '"') {
                    value = value.replace("\\n", "\n").replace("\\r", "\r").replace("\\\"", "\"").replace("\\\\", "\\");
                }
                return value;
            }
        }

        int inlineComment = value.indexOf(" #");
        return inlineComment >= 0 ? value.substring(0, inlineComment).trim() : value;
    }

    private static String requiredValue(String key, Map<String, String> envFile) {
        String value = value(key, envFile, "");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Preencha " + key + " no arquivo .env.");
        }
        return value;
    }

    private static String value(String key, Map<String, String> envFile, String defaultValue) {
        String processValue = System.getenv(key);
        if (processValue != null && !processValue.isBlank()) {
            return processValue.trim();
        }
        String fileValue = envFile.get(key);
        return fileValue == null || fileValue.isBlank() ? defaultValue : fileValue.trim();
    }

    private static URI validateDatabaseUrl(String databaseUrl) {
        URI uri = URI.create(databaseUrl.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) {
            throw new IllegalArgumentException(
                    "FIREBASE_DATABASE_URL deve ser a URL HTTPS base do Realtime Database, sem caminho ou parâmetros.");
        }
        return URI.create(databaseUrl.trim().replaceAll("/+$", ""));
    }

    private static Map<String, String> loadCandidates(Path candidatesDir) throws IOException {
        if (!Files.isDirectory(candidatesDir)) {
            throw new IOException("Diretório de candidatos não encontrado: " + candidatesDir);
        }

        List<Path> folders;
        try (Stream<Path> entries = Files.list(candidatesDir)) {
            folders = entries.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
        if (folders.isEmpty()) {
            throw new IOException("Nenhuma pasta de candidato encontrada em " + candidatesDir);
        }

        Map<String, String> candidates = new LinkedHashMap<>();
        for (Path folder : folders) {
            String folderName = folder.getFileName().toString();
            validateFirebaseKey(folderName, "nome da pasta " + folderName);
            Path dataFile = folder.resolve("data.json");
            if (!Files.isRegularFile(dataFile)) {
                throw new IOException("Arquivo ausente: " + dataFile);
            }

            String json = Files.readString(dataFile, StandardCharsets.UTF_8).trim();
            if (json.isEmpty() || !new JsonValidator(json).isValidObject()) {
                throw new IOException("JSON inválido ou não é um objeto: " + dataFile);
            }
            candidates.put(folderName, json);
        }
        return candidates;
    }

    private static String signIn(String apiKey, String email, String password) throws IOException, InterruptedException {
        String endpoint = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key="
                + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        String body = "{\"email\":" + jsonString(email)
                + ",\"password\":" + jsonString(password)
                + ",\"returnSecureToken\":true}";

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient().send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Login Firebase recusado (HTTP " + response.statusCode() + "): "
                    + firebaseError(response.body()));
        }

        Matcher matcher = ID_TOKEN_PATTERN.matcher(response.body());
        if (!matcher.find()) {
            throw new IOException("Resposta de login do Firebase não contém idToken.");
        }
        return matcher.group(1);
    }

    private static void updateDatabase(URI databaseUri, String candidatesPath, String idToken,
                                       Map<String, String> candidates) throws IOException, InterruptedException {
        String encodedPath = encodePath(candidatesPath);
        URI endpoint = URI.create(databaseUri + "/" + encodedPath + ".json?auth="
                + URLEncoder.encode(idToken, StandardCharsets.UTF_8));

        StringBuilder body = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> candidate : candidates.entrySet()) {
            if (!first) {
                body.append(',');
            }
            first = false;
            body.append(jsonString(candidate.getKey())).append(':').append(candidate.getValue());
        }
        body.append('}');

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json; charset=utf-8")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient().send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Gravação no Realtime Database recusada (HTTP " + response.statusCode() + "): "
                    + firebaseError(response.body()));
        }
    }

    private static HttpClient httpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    private static String encodePath(String path) {
        String[] parts = path.split("/");
        List<String> encoded = new ArrayList<>(parts.length);
        for (String part : parts) {
            encoded.add(URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return String.join("/", encoded);
    }

    private static void validateFirebasePath(String path, String description) {
        if (path.isBlank()) {
            throw new IllegalArgumentException(description + " não pode ficar vazio.");
        }
        for (String part : path.split("/", -1)) {
            validateFirebaseKey(part, description);
        }
    }

    private static void validateFirebaseKey(String key, String description) {
        if (key.isBlank() || key.getBytes(StandardCharsets.UTF_8).length > 768) {
            throw new IllegalArgumentException("Chave inválida para o Firebase em " + description + ".");
        }
        for (int index = 0; index < key.length(); index++) {
            char character = key.charAt(index);
            if (character < 0x20 || character == 0x7f || INVALID_FIREBASE_KEY_CHARS.indexOf(character) >= 0) {
                throw new IllegalArgumentException("Chave contém caractere não permitido pelo Firebase em "
                        + description + ": " + key);
            }
        }
    }

    private static String jsonString(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 0x20) {
                        result.append(String.format("\\u%04x", (int) character));
                    } else {
                        result.append(character);
                    }
                }
            }
        }
        return result.append('"').toString();
    }

    private static String firebaseError(String responseBody) {
        Matcher matcher = ERROR_MESSAGE_PATTERN.matcher(responseBody);
        return matcher.find() ? matcher.group(1) : "verifique a configuração e as regras do banco.";
    }

    private static final class JsonValidator {
        private final String input;
        private int position;

        private JsonValidator(String input) {
            this.input = input;
        }

        private boolean isValidObject() {
            skipWhitespace();
            if (!parseObject()) {
                return false;
            }
            skipWhitespace();
            return position == input.length();
        }

        private boolean parseValue() {
            skipWhitespace();
            if (position >= input.length()) {
                return false;
            }
            return switch (input.charAt(position)) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true");
                case 'f' -> parseLiteral("false");
                case 'n' -> parseLiteral("null");
                default -> parseNumber();
            };
        }

        private boolean parseObject() {
            if (!consume('{')) return false;
            skipWhitespace();
            if (consume('}')) return true;
            while (true) {
                skipWhitespace();
                if (!parseString()) return false;
                skipWhitespace();
                if (!consume(':') || !parseValue()) return false;
                skipWhitespace();
                if (consume('}')) return true;
                if (!consume(',')) return false;
            }
        }

        private boolean parseArray() {
            if (!consume('[')) return false;
            skipWhitespace();
            if (consume(']')) return true;
            while (true) {
                if (!parseValue()) return false;
                skipWhitespace();
                if (consume(']')) return true;
                if (!consume(',')) return false;
            }
        }

        private boolean parseString() {
            if (!consume('"')) return false;
            while (position < input.length()) {
                char character = input.charAt(position++);
                if (character == '"') return true;
                if (character < 0x20) return false;
                if (character == '\\') {
                    if (position >= input.length()) return false;
                    char escape = input.charAt(position++);
                    if (escape == 'u') {
                        for (int count = 0; count < 4; count++) {
                            if (position >= input.length() || Character.digit(input.charAt(position++), 16) < 0) {
                                return false;
                            }
                        }
                    } else if ("\"\\/bfnrt".indexOf(escape) < 0) {
                        return false;
                    }
                }
            }
            return false;
        }

        private boolean parseNumber() {
            int start = position;
            consume('-');
            if (consume('0')) {
                if (position < input.length() && Character.isDigit(input.charAt(position))) return false;
            } else if (!consumeDigits()) {
                return false;
            }
            if (consume('.')) {
                if (!consumeDigits()) return false;
            }
            if (consume('e') || consume('E')) {
                if (!consume('+')) consume('-');
                if (!consumeDigits()) return false;
            }
            return position > start;
        }

        private boolean consumeDigits() {
            int start = position;
            while (position < input.length() && Character.isDigit(input.charAt(position))) position++;
            return position > start;
        }

        private boolean parseLiteral(String literal) {
            if (!input.startsWith(literal, position)) return false;
            position += literal.length();
            return true;
        }

        private boolean consume(char expected) {
            if (position < input.length() && input.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (position < input.length() && Character.isWhitespace(input.charAt(position))) position++;
        }
    }
}
