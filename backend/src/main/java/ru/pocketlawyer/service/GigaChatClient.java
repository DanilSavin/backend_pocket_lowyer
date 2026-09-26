package ru.pocketlawyer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.pocketlawyer.config.GigaChatProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** REST client for GigaChat. The auth key is read exclusively from an environment variable. */
@Component
public class GigaChatClient {
    private static final Logger log = LoggerFactory.getLogger(GigaChatClient.class);
    private static final URI TOKEN_URI = URI.create("https://ngw.devices.sberbank.ru:9443/api/v2/oauth");
    private static final URI API_URI = URI.create("https://api.giga.chat/v1/");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final ObjectMapper json;
    private final GigaChatProperties properties;
    private volatile AccessToken cachedToken;

    public GigaChatClient(ObjectMapper json, GigaChatProperties properties) {
        this.json = json;
        this.properties = properties;
    }

    public String analyzeContract(MultipartFile file, String prompt) {
        long startedAt = System.nanoTime();
        String token = token();
        String uploadedFileId = upload(file, token);
        try {
            String answer = completion(uploadedFileId, prompt, token);
            log.info("GigaChat завершил анализ документа name={} за {} ms", file.getOriginalFilename(), elapsedMs(startedAt));
            return answer;
        } finally {
            deleteFileQuietly(uploadedFileId, token);
        }
    }

    private String token() {
        AccessToken current = cachedToken;
        if (current != null && current.valid()) return current.value();
        synchronized (this) {
            if (cachedToken != null && cachedToken.valid()) return cachedToken.value();
            if (properties.authKey() == null || properties.authKey().isBlank()) {
                throw new GigaChatException("GigaChat не настроен. Укажите переменную окружения GIGACHAT_AUTH_KEY.");
            }
            log.debug("Запрашиваю новый OAuth-токен GigaChat, scope={}", properties.scope());
            HttpRequest request = HttpRequest.newBuilder(TOKEN_URI)
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .header("RqUID", UUID.randomUUID().toString())
                    .header("Authorization", "Basic " + properties.authKey())
                    .POST(HttpRequest.BodyPublishers.ofString("scope=" + properties.scope()))
                    .build();
            JsonNode body = sendJson(request, "получить токен доступа");
            String accessToken = body.path("access_token").asText();
            if (accessToken.isBlank()) throw new GigaChatException("GigaChat не вернул токен доступа.");
            cachedToken = new AccessToken(accessToken, System.currentTimeMillis() + Duration.ofMinutes(25).toMillis());
            log.debug("OAuth-токен GigaChat успешно получен и закеширован.");
            return accessToken;
        }
    }

    private String upload(MultipartFile file, String token) {
        try {
            String boundary = "----PocketLawyer" + UUID.randomUUID();
            String contentType = file.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType();
            String filename = safeFileName(file.getOriginalFilename());
            byte[] prefix = ("--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n" +
                    "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
            byte[] purpose = ("\r\n--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"purpose\"\r\n\r\n" +
                    "general\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(API_URI.resolve("files"))
                    .timeout(Duration.ofMinutes(2))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArrays(List.of(prefix, file.getBytes(), purpose)))
                    .build();
            log.debug("Отправляю документ в приватное хранилище GigaChat: name={}, sizeBytes={}", filename, file.getSize());
            String id = sendJson(request, "загрузить документ в GigaChat").path("id").asText();
            if (id.isBlank()) throw new GigaChatException("GigaChat не вернул идентификатор загруженного файла.");
            log.debug("Документ загружен в GigaChat; получен внутренний идентификатор файла.");
            return id;
        } catch (IOException e) {
            throw new GigaChatException("Не удалось прочитать загруженный файл.", e);
        }
    }

    private String completion(String fileId, String prompt, String token) {
        try {
            String payload = json.writeValueAsString(java.util.Map.of(
                    "model", properties.model(), "temperature", 0.1, "function_call", "auto",
                    "messages", List.of(java.util.Map.of("role", "user", "content", prompt, "attachments", List.of(fileId)))));
            HttpRequest request = HttpRequest.newBuilder(API_URI.resolve("chat/completions"))
                    .timeout(Duration.ofMinutes(2))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            log.debug("Запрашиваю анализ договора у модели {}.", properties.model());
            return sendJson(request, "получить юридический анализ").path("choices").path(0).path("message").path("content").asText();
        } catch (IOException e) {
            throw new GigaChatException("Не удалось подготовить запрос к GigaChat.", e);
        }
    }

    private JsonNode sendJson(HttpRequest request, String action) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                String details = errorDetails(response.body());
                log.warn("GigaChat вернул HTTP {} при действии {}: {}", response.statusCode(), action, details);
                throw new GigaChatException("Не удалось " + action + ": GigaChat вернул HTTP " + response.statusCode() + ". " + details);
            }
            return json.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GigaChatException("Запрос к GigaChat был прерван.", e);
        } catch (IOException e) {
            log.warn("Ошибка соединения с GigaChat при действии {}: {}: {}", action,
                    e.getClass().getSimpleName(), e.getMessage());
            throw new GigaChatException("Ошибка соединения с GigaChat при попытке " + action +
                    ". Техническая причина: " + e.getMessage(), e);
        }
    }

    private void deleteFileQuietly(String fileId, String token) {
        try {
            HttpRequest request = HttpRequest.newBuilder(API_URI.resolve("files/" + fileId + "/delete"))
                    .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.noBody()).build();
            http.send(request, HttpResponse.BodyHandlers.discarding());
            log.debug("Временный файл удалён из хранилища GigaChat.");
        } catch (Exception e) {
            log.warn("Не удалось удалить временный файл из GigaChat: {}", e.getClass().getSimpleName());
        }
    }

    private String safeFileName(String name) { return (name == null ? "contract" : name).replaceAll("[\\r\\n\\\"]", "_"); }
    private long elapsedMs(long startedAt) { return Duration.ofNanos(System.nanoTime() - startedAt).toMillis(); }
    private String errorDetails(String body) {
        try {
            JsonNode response = json.readTree(body);
            String message = response.path("error_description").asText();
            if (message.isBlank()) message = response.path("message").asText();
            if (message.isBlank()) message = response.path("error").asText();
            if (!message.isBlank()) return "Причина: " + message.substring(0, Math.min(message.length(), 300));
        } catch (Exception ignored) { }
        return "GigaChat не передал описание ошибки.";
    }
    private record AccessToken(String value, long refreshAt) { boolean valid() { return System.currentTimeMillis() < refreshAt; } }
}
