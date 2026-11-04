package service.firebase;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Lê a configuração Firebase que o Gradle embute no pacote durante a compilação. */
public final class FirebaseBuildConfig {
    private static final Properties CONFIG = load();

    private FirebaseBuildConfig() {}

    public static String apiKey() {
        return requiredValue("apiKey", "FIREBASE_API_KEY");
    }

    public static String databaseUrl() {
        return requiredValue("databaseUrl", "FIREBASE_DATABASE_URL");
    }

    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream input = FirebaseBuildConfig.class.getResourceAsStream("/firebase.properties")) {
            if (input == null) {
                return properties;
            }
            properties.load(input);
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível ler a configuração Firebase embutida.", exception);
        }
    }

    private static String requiredValue(String key, String variableName) {
        String value = CONFIG.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(variableName + " não foi informada durante a compilação.");
        }
        return value;
    }
}
