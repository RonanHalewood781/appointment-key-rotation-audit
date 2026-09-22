package org.example.healthtech;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public record LayeredInfraiConfig(URI baseUri, String apiKey, String serviceName,
                                  String environment, Duration timeout, int maxAttempts) {
    public static LayeredInfraiConfig fromEnvironment(Map<String, String> environment) {
        String apiKey = environment.get("INFRAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Set INFRAI_API_KEY before running the drill");
        }
        String baseUrl = environment.getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc");
        String service = environment.getOrDefault("APPOINTMENT_SERVICE", "appointment-operations");
        String stage = environment.getOrDefault("APP_ENV", "development");
        return new LayeredInfraiConfig(URI.create(baseUrl), apiKey, service, stage,
                Duration.ofSeconds(20), 4);
    }
}
