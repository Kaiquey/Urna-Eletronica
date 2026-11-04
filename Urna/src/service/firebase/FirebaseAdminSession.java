package service.firebase;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Autentica um usuário do Firebase Authentication e fornece operações REST
 * para o Realtime Database durante a sessão local.
 *
 * A autorização de leitura e escrita é aplicada pelas regras do Firebase.
 * Nenhuma interface gráfica ou senha é armazenada por esta classe.
 *
 * Exemplo de integração com uma interface:
 * <pre>
 * try (FirebaseAdminSession firebase = new FirebaseAdminSession(apiKey, databaseUrl)) {
 *     firebase.signInAdmin(email, passwordField.getPassword());
 *     JsonElement candidatos = firebase.readJson("candidatos");
 * }
 * </pre>
 */
public final class FirebaseAdminSession implements AutoCloseable {
    private static final Gson GSON = new Gson();
    private static final String SIGN_IN_URL =
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=";
    private static final String REFRESH_URL =
            "https://securetoken.googleapis.com/v1/token?key=";
    private static final String INVALID_FIREBASE_KEY_CHARS = ".#$[]/";
    private static final long TOKEN_REFRESH_MARGIN_MILLIS = 60_000;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final String apiKey;
    private final String databaseUrl;
    private final HttpClient httpClient;
    private Session session;

    /** Cria a sessão usando a API Key e URL embutidas no pacote pelo Gradle. */
    public FirebaseAdminSession() {
        this(FirebaseBuildConfig.apiKey(), FirebaseBuildConfig.databaseUrl());
    }

    /**
     * @param apiKey Firebase Web API Key do projeto
     * @param databaseUrl URL base do Realtime Database, sem caminho ou query
     */
    public FirebaseAdminSession(String apiKey, String databaseUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("A Firebase API Key é obrigatória.");
        }
        this.apiKey = apiKey.trim();
        this.databaseUrl = validateDatabaseUrl(databaseUrl);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Entra com uma conta do Firebase Authentication. A senha recebida é
     * apagada do array fornecido antes do retorno deste método.
     */
    public synchronized AuthenticatedUser signInAdmin(String email, char[] password)
            throws IOException, InterruptedException {
        return authenticate(email, password, SIGN_IN_URL, "Login de administrador");
    }

    private AuthenticatedUser authenticate(String email, char[] password, String endpoint, String operation)
            throws IOException, InterruptedException {
        clearSession();
        try {
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("O email é obrigatório.");
            }
            if (password == null || password.length == 0) {
                throw new IllegalArgumentException("A senha é obrigatória.");
            }

            JsonObject payload = new JsonObject();
            payload.addProperty("email", email.trim());
            payload.addProperty("password", new String(password));
            payload.addProperty("returnSecureToken", true);
            HttpResponse<String> response = sendJson(
                    endpoint + urlEncode(apiKey), "POST", GSON.toJson(payload));
            requireSuccess(response, operation);

            JsonObject responseJson = parseJsonObject(response.body(), operation);
            String idToken = requiredJsonString(responseJson, "idToken");
            String refreshToken = requiredJsonString(responseJson, "refreshToken");
            String uid = requiredJsonString(responseJson, "localId");
            long expiresInSeconds = requiredJsonLong(responseJson, "expiresIn");
            if (expiresInSeconds <= 0) {
                throw new IOException("O Firebase retornou um prazo de token inválido.");
            }
            AuthenticatedUser user = new AuthenticatedUser(uid, email.trim());
            session = new Session(idToken, refreshToken, expirationTime(expiresInSeconds), user);
            return user;
        } finally {
            if (password != null) {
                java.util.Arrays.fill(password, '\0');
            }
        }
    }

    /** Indica se há uma sessão autenticada nesta instância. */
    public synchronized boolean isSignedIn() {
        return session != null;
    }

    public synchronized boolean isAdminSignedIn() {
        return session != null;
    }

    /** Retorna o usuário atual, ou vazio quando não há sessão. */
    public synchronized Optional<AuthenticatedUser> getCurrentUser() {
        return session == null ? Optional.empty() : Optional.of(session.user);
    }

    /** Lê um nó do Realtime Database como Gson JsonElement. Use "" para a raiz. */
    public JsonElement readJson(String databasePath) throws IOException, InterruptedException {
        requireAdminSession();
        return sendDatabaseRequest(databasePath, "GET", null);
    }

    /** Substitui o conteúdo do nó informado (HTTP PUT). */
    public JsonElement writeJson(String databasePath, JsonElement value) throws IOException, InterruptedException {
        requireAdminSession();
        return sendDatabaseRequest(databasePath, "PUT", requireJson(value));
    }

    /** Cria um registro somente se ainda não existir no caminho indicado. */
    public JsonElement writeJsonIfAbsent(String databasePath, JsonElement value)
            throws IOException, InterruptedException {
        requireAdminSession();
        String normalizedPath = normalizeDatabasePath(databasePath);
        String token = currentIdToken();
        String endpoint = databaseUrl + "/" + encodePath(normalizedPath)
                + ".json?auth=" + urlEncode(token);
        HttpRequest getRequest = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("X-Firebase-ETag", "true")
                .GET().build();
        HttpResponse<String> current = httpClient.send(getRequest,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        requireSuccess(current, "Verificação de cadastro");
        JsonElement existing = parseJsonResponse(current.body(), "Verificação de cadastro");
        if (existing != null && !existing.isJsonNull()) {
            throw new IllegalArgumentException("Já existe um cadastro com esse nome.");
        }
        String etag = current.headers().firstValue("ETag")
                .orElseThrow(() -> new IOException("O Firebase não retornou ETag para o cadastro."));
        HttpRequest putRequest = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json; charset=utf-8")
                .header("If-Match", etag)
                .PUT(HttpRequest.BodyPublishers.ofString(GSON.toJson(requireJson(value)), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> created = httpClient.send(putRequest,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (created.statusCode() == 412) {
            throw new IllegalArgumentException("Já existe um cadastro com esse nome.");
        }
        requireSuccess(created, "Cadastro de eleitor");
        return parseJsonResponse(created.body(), "Cadastro de eleitor");
    }

    /** Atualiza somente os campos enviados no objeto JSON (HTTP PATCH). */
    public JsonElement patchJson(String databasePath, JsonElement value) throws IOException, InterruptedException {
        requireAdminSession();
        return sendDatabaseRequest(databasePath, "PATCH", requireJson(value));
    }

    /** Incremento genérico reservado à administração. */
    public JsonElement incrementJsonNumber(String databasePath, String field)
            throws IOException, InterruptedException {
        requireAdminSession();
        return incrementJsonNumberInternal(databasePath, field);
    }

    /** Marca um booleano falso como verdadeiro de forma atômica; retorna false se já era true. */
    public boolean setJsonBooleanIfFalse(String databasePath, String field)
            throws IOException, InterruptedException {
        requireAdminSession();
        String normalizedPath = normalizeDatabasePath(databasePath);
        validateFirebaseKey(field);
        for (int attempt = 0; attempt < 20; attempt++) {
            String token = currentIdToken();
            String endpoint = databaseUrl + "/" + encodePath(normalizedPath)
                    + ".json?auth=" + urlEncode(token);
            HttpRequest getRequest = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("X-Firebase-ETag", "true")
                    .GET().build();
            HttpResponse<String> current = httpClient.send(getRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            requireSuccess(current, "Leitura do cadastro do eleitor");
            String etag = current.headers().firstValue("ETag")
                    .orElseThrow(() -> new IOException("O Firebase não retornou ETag para o cadastro do eleitor."));
            JsonElement parsed = parseJsonResponse(current.body(), "Cadastro do eleitor");
            if (parsed == null || !parsed.isJsonObject()) {
                throw new IOException("O cadastro do eleitor não existe ou está inválido.");
            }
            JsonObject profile = parsed.getAsJsonObject();
            JsonElement currentValue = profile.get(field);
            if (currentValue == null || !currentValue.isJsonPrimitive()
                    || !currentValue.getAsJsonPrimitive().isBoolean()) {
                throw new IOException("O campo " + field + " do cadastro do eleitor não é booleano.");
            }
            if (currentValue.getAsBoolean()) {
                return false;
            }
            profile.addProperty(field, true);

            HttpRequest putRequest = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("If-Match", etag)
                    .PUT(HttpRequest.BodyPublishers.ofString(GSON.toJson(profile), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> updated = httpClient.send(putRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (updated.statusCode() == 412) {
                continue;
            }
            requireSuccess(updated, "Marcação de voto");
            return true;
        }
        throw new IOException("Não foi possível atualizar o cadastro após alterações concorrentes.");
    }

    /** Incrementa um campo numérico com controle otimista de concorrência via ETag. */
    private JsonElement incrementJsonNumberInternal(String databasePath, String field)
            throws IOException, InterruptedException {
        String normalizedPath = normalizeDatabasePath(databasePath);
        validateFirebaseKey(field);
        for (int attempt = 0; attempt < 20; attempt++) {
            String token = currentIdToken();
            String endpoint = databaseUrl + "/" + encodePath(normalizedPath)
                    + ".json?auth=" + urlEncode(token);
            HttpRequest getRequest = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("X-Firebase-ETag", "true")
                    .GET().build();
            HttpResponse<String> current = httpClient.send(getRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            requireSuccess(current, "Leitura para incremento");
            String etag = current.headers().firstValue("ETag")
                    .orElseThrow(() -> new IOException("O Firebase não retornou ETag para o incremento."));
            JsonElement parsed = parseJsonResponse(current.body(), "Leitura para incremento");
            if (parsed == null || !parsed.isJsonObject()) {
                throw new IOException("O registro do candidato não existe ou não é um objeto.");
            }
            JsonObject object = parsed.getAsJsonObject();
            JsonElement number = object.get(field);
            long count = 0;
            if (number != null && !number.isJsonNull()) {
                try {
                    if (!number.isJsonPrimitive() || !number.getAsJsonPrimitive().isNumber()) {
                        throw new NumberFormatException();
                    }
                    count = number.getAsLong();
                } catch (NumberFormatException | UnsupportedOperationException exception) {
                    throw new IOException("O campo " + field + " não contém uma contagem numérica válida.", exception);
                }
            }
            if (count < 0 || count == Long.MAX_VALUE) {
                throw new IOException("A contagem de " + field + " está fora do intervalo permitido.");
            }
            object.addProperty(field, count + 1);

            HttpRequest putRequest = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("If-Match", etag)
                    .PUT(HttpRequest.BodyPublishers.ofString(GSON.toJson(object), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> updated = httpClient.send(putRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (updated.statusCode() == 412) {
                continue;
            }
            requireSuccess(updated, "Incremento");
            return parseJsonResponse(updated.body(), "Incremento");
        }
        throw new IOException("Não foi possível incrementar após várias alterações concorrentes. Tente novamente.");
    }

    /** Apaga o nó informado (HTTP DELETE). */
    public JsonElement delete(String databasePath) throws IOException, InterruptedException {
        requireAdminSession();
        return sendDatabaseRequest(databasePath, "DELETE", null);
    }

    /**
     * Encerra a sessão desta instância e remove os tokens mantidos em memória.
     * Não persiste credenciais nem tokens em disco. O Firebase ainda pode
     * aceitar um ID token já emitido até ele expirar.
     */
    public synchronized void signOut() {
        clearSession();
    }

    @Override
    public void close() {
        signOut();
    }

    private JsonElement sendDatabaseRequest(String databasePath, String method, JsonElement body)
            throws IOException, InterruptedException {
        String token = currentIdToken();
        String normalizedPath = normalizeDatabasePath(databasePath);
        String endpoint = databaseUrl + "/" + encodePath(normalizedPath)
                + ".json?auth=" + urlEncode(token);
        HttpResponse<String> response = sendJson(endpoint, method, body == null ? null : GSON.toJson(body));
        requireSuccess(response, "Realtime Database");
        return parseJsonResponse(response.body(), "Realtime Database");
    }

    private synchronized String currentIdToken() throws IOException, InterruptedException {
        if (session == null) {
            throw new IllegalStateException("Faça login antes de acessar o Realtime Database.");
        }
        if (System.currentTimeMillis() + TOKEN_REFRESH_MARGIN_MILLIS < session.expiresAtMillis) {
            return session.idToken;
        }

        String body = "grant_type=refresh_token&refresh_token=" + urlEncode(session.refreshToken);
        HttpRequest request = HttpRequest.newBuilder(URI.create(REFRESH_URL + urlEncode(apiKey)))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            if (response.statusCode() == 400 || response.statusCode() == 401) {
                clearSession();
            }
            throw firebaseException(response, "Renovação da sessão");
        }

        JsonObject responseJson = parseJsonObject(response.body(), "Renovação da sessão");
        String idToken = requiredJsonString(responseJson, "id_token");
        String refreshToken = requiredJsonString(responseJson, "refresh_token");
        long expiresInSeconds = requiredJsonLong(responseJson, "expires_in");
        if (expiresInSeconds <= 0) {
            clearSession();
            throw new IOException("O Firebase retornou um prazo de token inválido.");
        }
        session = new Session(idToken, refreshToken, expirationTime(expiresInSeconds), session.user);
        return idToken;
    }

    private HttpResponse<String> sendJson(String endpoint, String method, String body)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json");
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json; charset=utf-8")
                    .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String validateDatabaseUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A URL do Firebase Realtime Database é obrigatória.");
        }
        URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("A URL do Realtime Database é inválida.", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) {
            throw new IllegalArgumentException(
                    "A URL deve ser HTTPS e apontar para a raiz do Realtime Database.");
        }
        return value.trim().replaceAll("/+$", "");
    }

    private static String normalizeDatabasePath(String path) {
        if (path == null || path.isBlank() || "/".equals(path.trim())) {
            return "";
        }
        String normalized = path.trim().replaceAll("^/+|/+$", "");
        for (String part : normalized.split("/", -1)) {
            validateFirebaseKey(part);
        }
        return normalized;
    }

    private static void validateFirebaseKey(String key) {
        if (key.isEmpty() || key.getBytes(StandardCharsets.UTF_8).length > 768) {
            throw new IllegalArgumentException("O caminho contém uma chave inválida para o Firebase.");
        }
        for (int index = 0; index < key.length(); index++) {
            char character = key.charAt(index);
            if (character < 0x20 || character == 0x7f || INVALID_FIREBASE_KEY_CHARS.indexOf(character) >= 0) {
                throw new IllegalArgumentException("O caminho contém caractere não permitido pelo Firebase.");
            }
        }
    }

    private static String encodePath(String path) {
        if (path.isEmpty()) {
            return "";
        }
        List<String> encodedParts = new ArrayList<>();
        for (String part : path.split("/")) {
            encodedParts.add(urlEncode(part).replace("+", "%20"));
        }
        return String.join("/", encodedParts);
    }

    private static JsonElement requireJson(JsonElement value) {
        if (value == null) {
            throw new IllegalArgumentException("O conteúdo JSON não pode ser nulo.");
        }
        return value;
    }

    private static void requireSuccess(HttpResponse<String> response, String operation) throws IOException {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw firebaseException(response, operation);
        }
    }

    private static IOException firebaseException(HttpResponse<String> response, String operation) {
        String detail = null;
        try {
            JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
            if (body.has("error") && body.get("error").isJsonObject()) {
                JsonElement message = body.getAsJsonObject("error").get("message");
                if (message != null && message.isJsonPrimitive()) {
                    detail = message.getAsString();
                }
            }
        } catch (JsonParseException | IllegalStateException ignored) {
            // Alguns endpoints podem responder com corpo vazio ou não JSON.
        }
        if (detail == null || detail.isBlank()) {
            detail = "verifique as credenciais, a URL e as regras de segurança do Firebase.";
        }
        return new IOException(operation + " falhou (HTTP " + response.statusCode() + "): " + detail);
    }

    private static JsonObject parseJsonObject(String json, String source) throws IOException {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new IOException(source + " retornou um JSON que não é um objeto.");
            }
            return parsed.getAsJsonObject();
        } catch (JsonParseException exception) {
            throw new IOException(source + " retornou JSON inválido.", exception);
        }
    }

    private static JsonElement parseJsonResponse(String json, String source) throws IOException {
        if (json == null || json.isBlank()) {
            return JsonNull.INSTANCE;
        }
        try {
            return JsonParser.parseString(json);
        } catch (JsonParseException exception) {
            throw new IOException(source + " retornou JSON inválido.", exception);
        }
    }

    private static String requiredJsonString(JsonObject json, String field) throws IOException {
        JsonElement element = json.get(field);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()
                || element.getAsString().isBlank()) {
            throw new IOException("A resposta do Firebase não contém o campo " + field + ".");
        }
        return element.getAsString();
    }

    private static long requiredJsonLong(JsonObject json, String field) throws IOException {
        JsonElement element = json.get(field);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()
                && !element.getAsJsonPrimitive().isString()) {
            throw new IOException("A resposta do Firebase não contém o campo " + field + ".");
        }
        try {
            return Long.parseLong(element.getAsString());
        } catch (NumberFormatException | UnsupportedOperationException exception) {
            throw new IOException("O Firebase retornou um campo numérico inválido: " + field + ".", exception);
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static long expirationTime(long expiresInSeconds) {
        return System.currentTimeMillis() + expiresInSeconds * 1_000;
    }

    private synchronized void clearSession() {
        session = null;
    }

    private synchronized void requireAdminSession() {
        if (session == null) {
            throw new IllegalStateException("É necessário entrar como administrador para acessar essa operação.");
        }
    }

    /** Identidade sem credenciais ou tokens. */
    public record AuthenticatedUser(String uid, String email) {}

    private record Session(String idToken, String refreshToken, long expiresAtMillis, AuthenticatedUser user) {}
}
