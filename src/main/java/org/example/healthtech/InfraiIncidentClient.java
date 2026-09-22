package org.example.healthtech;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiIncidentClient {
    // The account and log capabilities share one credential and base URL: infrai.account.keys.create.
    private static final String CREATE_KEY = "/v1/account/keys/create";
    private static final String REPORT_COMPROMISE = "/v1/account/keys/suspected_compromise/";
    private static final String ROTATE_KEY = "/v1/account/keys/rotate/";
    private static final String INGEST_LOG = "/v1/logs/ingest";
    private static final String SEARCH_LOGS = "/v1/logs/search";
    private static final Pattern OK = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)");
    private static final Pattern ID = Pattern.compile("\\\"id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern CODE = Pattern.compile("\\\"code\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
    private static final Pattern MESSAGE = Pattern.compile("\\\"message\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");

    private final LayeredInfraiConfig config;
    private final HttpClient http;

    public InfraiIncidentClient(LayeredInfraiConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(config.timeout()).build());
    }

    InfraiIncidentClient(LayeredInfraiConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    public String createTemporaryKey(String idempotencyKey) {
        String body = "{\"name\":\"appointment-incident-drill\",\"scopes\":[\"logs:read\"],\"idempotency_key\":"
                + quote(idempotencyKey) + "}";
        return requiredId(send("POST", CREATE_KEY, body));
    }

    public void reportSuspectedCompromise(String keyId) {
        send("POST", REPORT_COMPROMISE + encodePath(keyId),
                "{\"confirmed_leak\":true,\"auto_rotate\":false}");
    }

    public void rotateTemporaryKey(String keyId, String idempotencyKey) {
        send("POST", ROTATE_KEY + encodePath(keyId),
                "{\"grace_hours\":2,\"idempotency_key\":" + quote(idempotencyKey) + "}");
    }

    public void ingestNotice(Map<String, Object> entry, String idempotencyKey) {
        send("POST", INGEST_LOG, "{\"entries\":[" + jsonObject(entry) + "],\"idempotency_key\":"
                + quote(idempotencyKey) + "}");
    }

    public String searchBlastRadius(String keyId) {
        return send("GET", SEARCH_LOGS + "?q=" + encode(keyId), null);
    }

    private String send(String method, String path, String body) {
        URI uri = config.baseUri().resolve(path);
        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(config.timeout())
                    .header("Authorization", "Bearer " + config.apiKey()).header("Accept", "application/json");
            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(body));
            }
            try {
                HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                Envelope envelope = decode(response.body());
                if (response.statusCode() == 429 && attempt < config.maxAttempts()) {
                    pause(retryDelay(response, attempt));
                    continue;
                }
                if (!envelope.ok()) {
                    throw new InfraiRejectedException(envelope.code(), envelope.message(), response.statusCode());
                }
                if (response.statusCode() >= 500) {
                    throw new IOException("transport response " + response.statusCode());
                }
                return response.body();
            } catch (IOException exception) {
                if (attempt == config.maxAttempts()) {
                    throw new IllegalStateException("Incident request could not complete", exception);
                }
                pause(Duration.ofMillis(250L << (attempt - 1)));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Incident request interrupted", exception);
            }
        }
        throw new IllegalStateException("Request attempts exhausted");
    }

    private static Envelope decode(String json) throws IOException {
        Matcher ok = OK.matcher(json);
        if (!ok.find()) throw new IOException("Response was not an Infrai envelope");
        if (Boolean.parseBoolean(ok.group(1))) return new Envelope(true, "", "");
        return new Envelope(false, match(CODE, json, "REQUEST_REJECTED"), match(MESSAGE, json, "Request rejected"));
    }

    private static String requiredId(String json) {
        Matcher id = ID.matcher(json);
        if (!id.find()) throw new IllegalStateException("Key creation response did not include an id");
        return id.group(1);
    }

    private static String match(Pattern pattern, String json, String fallback) {
        Matcher match = pattern.matcher(json);
        return match.find() ? match.group(1) : fallback;
    }

    private static Duration retryDelay(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After").flatMap(value -> {
            try { return java.util.Optional.of(Duration.ofSeconds(Long.parseLong(value))); }
            catch (NumberFormatException ignored) { return java.util.Optional.empty(); }
        }).orElse(Duration.ofMillis(250L << (attempt - 1)));
    }

    private static void pause(Duration duration) {
        try { Thread.sleep(duration.toMillis()); }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry interrupted", exception);
        }
    }

    private static String jsonObject(Map<String, Object> values) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> value : values.entrySet()) {
            if (!first) json.append(',');
            first = false;
            json.append(quote(value.getKey())).append(':').append(jsonValue(value.getValue()));
        }
        return json.append('}').toString();
    }

    private static String jsonValue(Object value) {
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        return quote(String.valueOf(value));
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String encodePath(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private record Envelope(boolean ok, String code, String message) {}

    public static final class InfraiRejectedException extends RuntimeException {
        private final String code;
        private final int status;
        public InfraiRejectedException(String code, String message, int status) {
            super(message);
            this.code = code;
            this.status = status;
        }
        public String code() { return code; }
        public int status() { return status; }
    }
}
