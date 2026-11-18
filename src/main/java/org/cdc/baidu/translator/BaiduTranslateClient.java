/*
 * OmegaT Baidu Translate plugin
 *
 * This file is part of the OmegaT Baidu Translate plugin.
 *
 * The plugin is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */

package org.cdc.baidu.translator;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Client for the Baidu general text translation API.
 * <p>
 * The API expects a POST request with form encoded parameters. The request
 * signature is the MD5 of {@code appid + q + salt + secretKey}, where {@code q}
 * is the unencoded query text. The response is JSON containing either a
 * {@code trans_result} array or an {@code error_code}.
 *
 * @see <a href="https://fanyi-api.baidu.com/doc/21">Baidu Translate API
 *      documentation</a>
 */
final class BaiduTranslateClient {

    static final String DEFAULT_ENDPOINT = "https://fanyi-api.baidu.com/api/trans/vip/translate";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded; charset=UTF-8";
    private static final String USER_AGENT = "OmegaT-BaiduTranslator/1.0";

    private final String endpoint;
    private final HttpClient httpClient;

    BaiduTranslateClient() {
        this(DEFAULT_ENDPOINT);
    }

    /**
     * Constructor used by tests to point the client at a local server.
     */
    BaiduTranslateClient(String endpoint) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    String translate(String appId, String secretKey, String from, String to, String text)
            throws BaiduTranslateException {
        String salt = Integer.toString(ThreadLocalRandom.current().nextInt(32768, 65536));
        String sign = sign(appId, text, salt, secretKey);
        String body = buildFormBody(appId, from, to, text, salt, sign);

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", FORM_CONTENT_TYPE)
                .header("Accept", "application/json")
                .header("Accept-Encoding", "identity")
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BaiduTranslateException("The Baidu Translate request was interrupted.", ex);
        } catch (IOException ex) {
            throw new BaiduTranslateException("Could not connect to Baidu Translate: " + ex.getMessage(), ex);
        }

        if (response.statusCode() != 200) {
            throw new BaiduTranslateException("Baidu Translate returned HTTP status " + response.statusCode() + ".");
        }
        return parseResponse(response.body());
    }

    /**
     * Build the form encoded request body. Package visibility for tests.
     */
    static String buildFormBody(String appId, String from, String to, String text, String salt, String sign) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("q", text);
        parameters.put("from", from);
        parameters.put("to", to);
        parameters.put("appid", appId);
        parameters.put("salt", salt);
        parameters.put("sign", sign);

        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> parameter : parameters.entrySet()) {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(URLEncoder.encode(parameter.getKey(), StandardCharsets.UTF_8));
            body.append('=');
            body.append(URLEncoder.encode(parameter.getValue(), StandardCharsets.UTF_8));
        }
        return body.toString();
    }

    /**
     * Compute the Baidu request signature. Package visibility for tests.
     *
     * @param appId
     *            Baidu application id.
     * @param text
     *            query text, not URL encoded.
     * @param salt
     *            random salt.
     * @param secretKey
     *            Baidu application secret key.
     * @return 32 character lower case MD5 signature.
     */
    static String sign(String appId, String text, String salt, String secretKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest((appId + text + salt + secretKey).getBytes(StandardCharsets.UTF_8));
            StringBuilder signature = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                signature.append(Character.forDigit((value >> 4) & 0x0F, 16));
                signature.append(Character.forDigit(value & 0x0F, 16));
            }
            return signature.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("MD5 is not available", ex);
        }
    }

    /**
     * Parse a Baidu API response. Package visibility for tests.
     *
     * @param body
     *            response body.
     * @return the translated text. Multiple result rows are joined with a
     *         newline, matching Baidu's line based request format.
     * @throws BaiduTranslateException
     *             if the response contains an API error or is malformed.
     */
    static String parseResponse(String body) throws BaiduTranslateException {
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (JsonProcessingException ex) {
            throw new BaiduTranslateException("Baidu Translate returned an invalid response.", ex);
        }
        if (root == null || !root.isObject()) {
            throw new BaiduTranslateException("Baidu Translate returned an invalid response.");
        }

        JsonNode errorCode = root.get("error_code");
        if (errorCode != null && !errorCode.isNull() && !"0".equals(errorCode.asText())) {
            String code = errorCode.asText();
            String reason = root.path("error_msg").asText("");
            if (reason.isEmpty()) {
                reason = root.path("reason").asText("");
            }
            if (reason.isEmpty()) {
                reason = "unknown error";
            }
            throw new BaiduTranslateException("Baidu Translate error " + code + ": " + reason);
        }

        JsonNode result = root.get("trans_result");
        if (result == null || !result.isArray()) {
            throw new BaiduTranslateException("Baidu Translate returned an invalid response.");
        }

        StringBuilder translation = new StringBuilder();
        for (JsonNode item : result) {
            String destination = item.path("dst").asText("");
            if (destination.isEmpty()) {
                continue;
            }
            if (translation.length() > 0) {
                translation.append('\n');
            }
            translation.append(destination);
        }

        if (translation.length() == 0) {
            throw new BaiduTranslateException("Baidu Translate returned an empty translation.");
        }
        return translation.toString();
    }
}
