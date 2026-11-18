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

import java.awt.Window;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import javax.swing.SwingUtilities;

import org.omegat.core.machinetranslators.BaseCachedTranslate;
import org.omegat.core.machinetranslators.MachineTranslateError;
import org.omegat.util.Language;

/**
 * OmegaT machine translation connector for Baidu Translate.
 * <p>
 * This class is both the plugin entry point and the MT connector. OmegaT calls
 * during startup; that method registers this class with
 * the machine translation manager, which then creates an instance through its
 * public no-argument constructor.
 * <p>
 * Credentials are stored through OmegaT's credential manager, so they can be
 * protected by the user's master password. The connector uses the cache layer
 * provided by {@link BaseCachedTranslate}.
 */
public class BaiduTranslator extends BaseCachedTranslate {

    static final String NAME = "Baidu Translate";
    static final String APP_ID_KEY = "org.cdc.baidu.translator.appId";
    static final String SECRET_KEY = "org.cdc.baidu.translator.secretKey";
    static final String ENABLED_PREFERENCE = "baidu.translate.enabled";

    /** Maximum size of the {@code q} parameter accepted by Baidu. */
    static final int MAX_QUERY_BYTES = 6000;

    private static final String TRUNCATION_MARKER = "...";

    private final BaiduTranslateClient client;

    /**
     * No-argument constructor required by OmegaT's plugin loader.
     */
    public BaiduTranslator() {
        this(new BaiduTranslateClient());
    }

    BaiduTranslator(BaiduTranslateClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    protected String getPreferenceName() {
        return ENABLED_PREFERENCE;
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public void showConfigurationUI(Window parent) {
        Runnable show = () -> new BaiduTranslatorConfigDialog(parent, this).show();
        if (SwingUtilities.isEventDispatchThread()) {
            show.run();
        } else {
            SwingUtilities.invokeLater(show);
        }
    }

    @Override
    protected String translate(Language sLang, Language tLang, String text) throws Exception {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        String appId = getCredential(APP_ID_KEY).trim();
        String secretKey = getCredential(SECRET_KEY).trim();
        if (appId.isEmpty() || secretKey.isEmpty()) {
            throw new MachineTranslateError(
                    "Baidu Translate is not configured. Set the APP ID and secret key in the machine translation options.");
        }

        String from = BaiduLanguages.toBaiduCode(sLang, true);
        String to = BaiduLanguages.toBaiduCode(tLang, false);
        if (to == null) {
            throw new MachineTranslateError("Baidu Translate does not support the language pair "
                    + (sLang == null ? "?" : sLang.getDisplayName()) + " -> "
                    + (tLang == null ? "?" : tLang.getDisplayName()) + ".");
        }

        try {
            String translation = client.translate(appId, secretKey, from, to, text);
            if (translation == null || translation.isEmpty()) {
                return null;
            }
            return cleanSpacesAroundTags(translation, text);
        } catch (BaiduTranslateException ex) {
            throw new MachineTranslateError(ex.getMessage(), ex);
        }
    }

    @Override
    protected String getTruncateText(String text) {
        return truncateToMaxBytes(text);
    }

    static String truncateToMaxBytes(String text) {
        if (text == null) {
            return null;
        }
        if (text.getBytes(StandardCharsets.UTF_8).length <= MAX_QUERY_BYTES) {
            return text;
        }

        int byteLimit = MAX_QUERY_BYTES - TRUNCATION_MARKER.length();
        StringBuilder truncated = new StringBuilder(text.length());
        int byteCount = 0;
        for (int index = 0; index < text.length();) {
            int codePoint = text.codePointAt(index);
            int codePointBytes = utf8Length(codePoint);
            if (byteCount + codePointBytes > byteLimit) {
                break;
            }
            truncated.appendCodePoint(codePoint);
            byteCount += codePointBytes;
            index += Character.charCount(codePoint);
        }
        return truncated.append(TRUNCATION_MARKER).toString();
    }

    @Override
    protected int getMaxTextBytes() {
        return MAX_QUERY_BYTES;
    }

    String getAppId() {
        return getCredential(APP_ID_KEY);
    }

    String getSecretKey() {
        return getCredential(SECRET_KEY);
    }

    boolean isCredentialStoredTemporarily() {
        return isCredentialStoredTemporarily(APP_ID_KEY) || isCredentialStoredTemporarily(SECRET_KEY);
    }

    void saveCredentials(String appId, String secretKey, boolean temporary) {
        setCredential(APP_ID_KEY, appId == null ? "" : appId, temporary);
        setCredential(SECRET_KEY, secretKey == null ? "" : secretKey, temporary);
        clearCache();
    }

    private static int utf8Length(int codePoint) {
        if (codePoint <= 0x7F) {
            return 1;
        }
        if (codePoint <= 0x7FF) {
            return 2;
        }
        if (codePoint <= 0xFFFF) {
            return 3;
        }
        return 4;
    }
}
