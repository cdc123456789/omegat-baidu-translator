package org.cdc.openaitrans;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.omegat.core.Core;
import org.omegat.core.data.SourceTextEntry;
import org.omegat.core.machinetranslators.BaseCachedTranslate;
import org.omegat.core.machinetranslators.MachineTranslateError;
import org.omegat.gui.glossary.GlossaryEntry;
import org.omegat.util.Language;
import org.omegat.util.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared implementation for the default connector and its extra
 * configurations.
 */
abstract class BaseOpenaiTranslate extends BaseCachedTranslate {

    protected static final Logger LOGGER = LoggerFactory.getLogger(BaseOpenaiTranslate.class);

    protected static final String PROPERTY_API_KEY = "openai.api.Key";
    protected static final String PROPERTY_API_URL = "openai.api.url";
    protected static final String PROPERTY_API_MODEL = "openai.api.model";
    protected static final String PROPERTY_API_CUSTOM_MODEL = "openai.api.custom.model";
    protected static final String PROPERTY_USE_CUSTOM_MODEL = "openai.api.use.custom.model";
    protected static final String PROPERTY_API_PROVIDER = "openai.api.format";
    protected static final String PROPERTY_API_PROMPT = "openai.api.prompt";
    protected static final String PROPERTY_API_TEMPERATURE = "openai.api.temperature";
    protected static final String PROPERTY_API_CACHE = "openai.api.enable.cache";
    protected static final String PROPERTY_API_GLOSSARY = "openai.api.enable.glossary";

    private static final String DEFAULT_PROMPT = "Please translate the following text from %s to %s.";
    private static final String DEFAULT_MODEL = "gpt-4o";
    private static final String DEFAULT_PROVIDER = "OpenAI";
    private static final int MAX_TEXT_LENGTH = 5000;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private static final List<String> OPENAI_MODELS = List.of(
            "gpt-5",
            "gpt-5-mini",
            "gpt-5-nano",
            "gpt-4.1",
            "gpt-4.1-mini",
            "gpt-4.1-nano",
            "gpt-4o",
            "gpt-4o-2024-11-20",
            "gpt-4o-2024-08-06",
            "gpt-4o-2024-05-13",
            "gpt-4o-mini",
            "gpt-4",
            "gpt-4-turbo",
            "gpt-3.5-turbo",
            "gpt-3.5",
            "deepseek-chat");

    private static final List<String> CLAUDE_MODELS = List.of(
            "claude-opus-4-1-20250805",
            "claude-opus-4-20250514",
            "claude-sonnet-4-20250514",
            "claude-3-7-sonnet-20250219",
            "claude-3-5-sonnet-20241022",
            "claude-3-5-sonnet-20240620",
            "claude-3-opus-20240229",
            "claude-3-sonnet-20240229",
            "claude-3-5-haiku-20241022",
            "claude-3-haiku-20240307");

    protected static final List<String> PROVIDERS = List.of(
            "default", "OpenAI", "Claude", "DeepSeek", "VolcEngine");

    static String[] allModels() {
        List<String> models = new ArrayList<>(OPENAI_MODELS);
        models.addAll(CLAUDE_MODELS);
        return models.toArray(new String[0]);
    }

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    protected String configId;
    protected String displayName;
    protected final boolean showConfigUI;

    protected BaseOpenaiTranslate(String configId, String displayName, boolean showConfigUI) {
        this.configId = configId;
        this.displayName = displayName;
        this.showConfigUI = showConfigUI;
    }

    @Override
    public String getName() {
        if (displayName == null) {
            ensureConfigInitialized();
        }
        return displayName != null ? displayName : getClass().getName();
    }

    @Override
    public boolean isConfigurable() {
        return showConfigUI;
    }

    @Override
    protected String getPreferenceName() {
        ensureConfigInitialized();
        return "allow_openai_translate_" + (configId == null ? "api" : configId);
    }

    /**
     * Hook for dynamically generated translators that receive their
     * configuration after construction.
     */
    protected void ensureConfigInitialized() {
        // Default connector is fully initialized by its constructor.
    }

    protected String getConfigKey(String baseKey) {
        ensureConfigInitialized();
        return OpenaiTranslate.configKey(configId, baseKey);
    }

    @Override
    protected String translate(Language sLang, Language tLang, String text) throws Exception {
        ensureConfigInitialized();
        return performTranslation(sLang, tLang, text);
    }

    private String performTranslation(Language sLang, Language tLang, String text) throws Exception {
        String apiKey = getCredential(getConfigKey(PROPERTY_API_KEY)).trim();
        String url = preference(getConfigKey(PROPERTY_API_URL), "");
        if (apiKey.trim().isEmpty()) {
            throw new MachineTranslateError(
                    "API key is not configured. Set it in the OpenAI translator settings.");
        }
        if (url.trim().isEmpty()) {
            throw new MachineTranslateError(
                    "API URL is not configured. Set it in the OpenAI translator settings.");
        }

        String model = preference(getConfigKey(PROPERTY_API_MODEL), DEFAULT_MODEL);
        String provider = preference(getConfigKey(PROPERTY_API_PROVIDER), DEFAULT_PROVIDER);
        if (Boolean.parseBoolean(preference(getConfigKey(PROPERTY_USE_CUSTOM_MODEL), "false"))) {
            String customModel = preference(getConfigKey(PROPERTY_API_CUSTOM_MODEL), "");
            if (customModel.isEmpty()) {
                throw new MachineTranslateError("Custom model name cannot be empty.");
            }
            model = customModel;
        } else if (OPENAI_MODELS.contains(model)) {
            if ("default".equals(provider) || "DeepSeek".equals(provider)) {
                provider = "OpenAI";
            }
        } else if (CLAUDE_MODELS.contains(model)) {
            if ("default".equals(provider)) {
                provider = "Claude";
            }
        } else {
            throw new MachineTranslateError("Unsupported model: " + model);
        }

        String endpoint = endpointFor(url, provider);
        String prompt = preference(getConfigKey(PROPERTY_API_PROMPT), "");
        if (prompt.isEmpty()) {
            prompt = String.format(DEFAULT_PROMPT, sLang.getLanguage(), tLang.getLanguage());
        }
        if (Boolean.parseBoolean(preference(getConfigKey(PROPERTY_API_GLOSSARY), "true"))) {
            prompt = appendGlossary(prompt);
        }

        String shortText = truncate(text);
        boolean cacheEnabled = Boolean.parseBoolean(preference(getConfigKey(PROPERTY_API_CACHE), "true"));
        if (cacheEnabled) {
            String cached = getCachedTranslation(sLang, tLang, shortText);
            if (cached != null) {
                return cached;
            }
        }

        double temperature = parseTemperature(preference(getConfigKey(PROPERTY_API_TEMPERATURE), "0"));
        return executeTranslationRequest(endpoint, apiKey, model, temperature, prompt, shortText, provider);
    }

    private static String preference(String key, String defaultValue) {
        String value = Preferences.getPreference(key);
        return value == null || value.trim().isEmpty() ? defaultValue : value;
    }

    private static double parseTemperature(String value) {
        try {
            return new java.math.BigDecimal(value).setScale(3, java.math.RoundingMode.DOWN).doubleValue();
        } catch (NumberFormatException ex) {
            LOGGER.warn("Invalid temperature '{}', using 0", value);
            return 0;
        }
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH - 3) + "\u2026" : text;
    }

    private static String endpointFor(String baseUrl, String provider) {
        String url = baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        String path;
        if ("Claude".equals(provider)) {
            path = "/v1/messages";
        } else if ("VolcEngine".equals(provider)) {
            path = "/api/v3/chat/completions";
        } else if ("OpenAI".equals(provider)) {
            path = "/v1/chat/completions";
        } else {
            return url;
        }
        return url.endsWith(path) ? url : url + path;
    }

    private static String appendGlossary(String prompt) {
        try {
            SourceTextEntry entry = Core.getEditor().getCurrentEntry();
            if (entry == null) {
                return prompt;
            }
            List<GlossaryEntry> entries = Core.getGlossaryManager().searchSourceMatches(entry);
            if (entries.isEmpty()) {
                return prompt;
            }
            StringBuilder result = new StringBuilder(prompt);
            result.append("\n\nUse the following glossary when translating:\n");
            for (GlossaryEntry glossaryEntry : entries) {
                result.append("src: ").append(glossaryEntry.getSrcText())
                        .append(", loc: ").append(glossaryEntry.getLocText())
                        .append(", comment: ").append(glossaryEntry.getCommentText())
                        .append('\n');
            }
            return result.toString();
        } catch (RuntimeException ex) {
            LOGGER.warn("Could not load glossary for the current entry", ex);
            return prompt;
        }
    }

    private static String executeTranslationRequest(String url, String apiKey, String model, double temperature,
                                                    String prompt, String text, String provider) throws MachineTranslateError {
        Map<String, Object> body = new LinkedHashMap<>();
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException ex) {
            throw new MachineTranslateError("Invalid API URL: " + url, ex);
        }
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");

        if ("Claude".equals(provider)) {
            body.put("model", model);
            body.put("max_tokens", 4096);
            body.put("system", prompt);
            body.put("messages", List.of(Map.of("role", "user", "content", text)));
            body.put("temperature", temperature);
            requestBuilder.header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01");
        } else {
            body.put("model", model);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", prompt),
                    Map.of("role", "user", "content", text)));
            body.put("temperature", temperature);
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        String requestBody;
        try {
            requestBody = MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new MachineTranslateError("Could not build the API request.", ex);
        }
        requestBuilder.POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));

        LOGGER.debug("Sending {} request to {} with model {}", provider, url, model);
        HttpResponse<String> response;
        try {
            response = HTTP_CLIENT.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MachineTranslateError("The translation request was interrupted.", ex);
        } catch (IOException ex) {
            if (isCausedBy(ex, SSLHandshakeException.class)) {
                throw new MachineTranslateError(
                        "SSL handshake failed. Check your network settings or SSL certificate and try again.", ex);
            }
            if (isCausedBy(ex, SocketException.class)) {
                throw new MachineTranslateError(
                        "Network connection error. Check your network connection and try again.", ex);
            }
            throw new MachineTranslateError("HTTP request failed: " + messageOf(ex), ex);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new MachineTranslateError("HTTP " + response.statusCode() + ": " + abbreviate(response.body()));
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(response.body());
        } catch (JsonProcessingException ex) {
            throw new MachineTranslateError("The API returned an invalid JSON response.", ex);
        }
        if (root == null || !root.isObject()) {
            throw new MachineTranslateError("The API returned an invalid JSON response.");
        }

        JsonNode error = root.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            throw new MachineTranslateError("API error: " + error.path("message").asText(error.toString()));
        }

        String content = "Claude".equals(provider)
                ? root.path("content").path(0).path("text").asText("")
                : root.path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new MachineTranslateError("The API returned an empty translation.");
        }
        return content;
    }

    private static boolean isCausedBy(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private static String messageOf(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isEmpty() ? throwable.getClass().getSimpleName() : message;
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String singleLine = text.replace('\n', ' ').replace('\r', ' ').trim();
        return singleLine.length() > 300 ? singleLine.substring(0, 300) + "..." : singleLine;
    }

    @Override
    protected String getTruncateText(String text) {
        return truncate(text);
    }
}
