package service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import service.firebase.FirebaseAdminSession;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Leitura de candidatos e contabilização de votos no Realtime Database. */
public final class CandidatoFirebaseService {
    private static final String VOTE_COUNT_FIELD = "quantidade_votos";

    private final FirebaseAdminSession firebase;
    private final EleitorAuthService eleitorAuth;
    private final String candidatesPath;

    public CandidatoFirebaseService(FirebaseAdminSession firebase, EleitorAuthService eleitorAuth,
                                    String candidatesPath) {
        if (firebase == null) {
            throw new IllegalArgumentException("A sessão Firebase é obrigatória.");
        }
        if (eleitorAuth == null) {
            throw new IllegalArgumentException("O serviço de autenticação do eleitor é obrigatório.");
        }
        if (candidatesPath == null || candidatesPath.isBlank()) {
            throw new IllegalArgumentException("O caminho dos candidatos é obrigatório.");
        }
        this.firebase = firebase;
        this.eleitorAuth = eleitorAuth;
        this.candidatesPath = candidatesPath.trim().replaceAll("^/+|/+$", "");
    }

    /** Retorna os candidatos e suas contagens atuais, sem depender de interface. */
    public List<CandidatoData> listarCandidatos() throws IOException, InterruptedException {
        requireVotingSession();
        JsonElement result = firebase.readJson(candidatesPath);
        if (result == null || result.isJsonNull()) {
            return List.of();
        }
        if (!result.isJsonObject()) {
            throw new IOException("O nó de candidatos no Firebase não é um objeto.");
        }
        List<CandidatoData> candidatos = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : result.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                throw new IOException("O registro do candidato " + entry.getKey() + " não é um objeto.");
            }
            JsonObject candidate = entry.getValue().getAsJsonObject();
            candidatos.add(new CandidatoData(
                    stringValue(candidate, "id", entry.getKey()),
                    requiredString(candidate, "nome", entry.getKey()),
                    stringValue(candidate, "cargo", ""),
                    intValue(candidate, "numero", 0, entry.getKey()),
                    stringValue(candidate, "description", ""),
                    intValue(candidate, "quantidade_informacoes", 0, entry.getKey()),
                    longValue(candidate, VOTE_COUNT_FIELD, 0, entry.getKey()),
                    stringValue(candidate, "partido", "")
            ));
        }
        return List.copyOf(candidatos);
    }

    /** Incrementa a contagem apenas durante uma sessão de eleitor autenticada. */
    public long incrementarVoto(String candidateKey, EleitorAuthService.TipoVoto tipoVoto)
            throws IOException, InterruptedException {
        requireVotingSession();
        if (candidateKey == null || candidateKey.isBlank() || candidateKey.contains("/")) {
            throw new IllegalArgumentException("A chave do candidato é obrigatória.");
        }
        if (tipoVoto == null) {
            throw new IllegalArgumentException("A categoria do voto é obrigatória.");
        }
        String candidatePath = candidatesPath + "/" + candidateKey.trim();
        JsonElement candidateData = firebase.readJson(candidatePath);
        if (candidateData == null || !candidateData.isJsonObject()) {
            throw new IOException("O candidato não foi encontrado.");
        }
        String cargo = requiredString(candidateData.getAsJsonObject(), "cargo", candidateKey);
        if (!cargoMatches(tipoVoto, cargo)) {
            throw new IllegalArgumentException("O candidato não corresponde à categoria de voto selecionada.");
        }
        if (!eleitorAuth.marcarVoto(tipoVoto)) {
            throw new IllegalStateException("O eleitor já registrou voto nesta categoria.");
        }

        JsonElement updated = firebase.incrementJsonNumber(
                candidatePath, VOTE_COUNT_FIELD);
        if (updated == null || !updated.isJsonObject()) {
            throw new IOException("O Firebase não retornou o candidato atualizado.");
        }
        return longValue(updated.getAsJsonObject(), VOTE_COUNT_FIELD, -1, candidateKey);
    }

    private static boolean cargoMatches(EleitorAuthService.TipoVoto tipoVoto, String cargo) {
        String normalized = Normalizer.normalize(cargo, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(java.util.Locale.ROOT);
        return switch (tipoVoto) {
            case PRESIDENTE -> normalized.startsWith("president");
            case GOVERNADOR -> normalized.startsWith("governador");
        };
    }

    private void requireVotingSession() {
        if (!firebase.isAdminSignedIn() || !eleitorAuth.isEleitorSignedIn()) {
            throw new IllegalStateException(
                    "O administrador precisa manter a urna aberta e o eleitor precisa estar logado.");
        }
    }

    private static String requiredString(JsonObject object, String field, String candidateKey) throws IOException {
        String value = stringValue(object, field, null);
        if (value == null || value.isBlank()) {
            throw new IOException("O candidato " + candidateKey + " não contém " + field + ".");
        }
        return value;
    }

    private static String stringValue(JsonObject object, String field, String defaultValue) {
        JsonElement value = object.get(field);
        return value == null || value.isJsonNull() || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString() ? defaultValue : value.getAsString();
    }

    private static int intValue(JsonObject object, String field, int defaultValue, String candidateKey)
            throws IOException {
        long value = longValue(object, field, defaultValue, candidateKey);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IOException("O campo " + field + " do candidato " + candidateKey + " excede o intervalo inteiro.");
        }
        return (int) value;
    }

    private static long longValue(JsonObject object, String field, long defaultValue, String candidateKey)
            throws IOException {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            return defaultValue;
        }
        try {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                throw new NumberFormatException();
            }
            return value.getAsLong();
        } catch (NumberFormatException | UnsupportedOperationException exception) {
            throw new IOException("O campo " + field + " do candidato " + candidateKey
                    + " não contém um número válido.", exception);
        }
    }

    public record CandidatoData(String id, String nome, String cargo, int numero, String descricao,
                                int quantidadeInformacoes, long quantidadeVotos, String partido) {}
}
