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

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.omegat.util.Language;

/**
 * Maps OmegaT language codes to the language codes expected by the Baidu
 * Translate API.
 * <p>
 * Baidu uses a few non-ISO codes, for example {@code jp} for Japanese,
 * {@code kor} for Korean and {@code cht} for Traditional Chinese. The list
 * below follows the language table of the Baidu general translation API.
 */
final class BaiduLanguages {

    /** Language code used to let Baidu detect the source language. */
    static final String AUTO = "auto";

    private static final Map<String, String> LANGUAGE_CODES;

    static {
        Map<String, String> codes = new HashMap<>();
        codes.put("en", "en");
        codes.put("ja", "jp");
        codes.put("ko", "kor");
        codes.put("fr", "fra");
        codes.put("es", "spa");
        codes.put("th", "th");
        codes.put("ar", "ara");
        codes.put("ru", "ru");
        codes.put("pt", "pt");
        codes.put("de", "de");
        codes.put("it", "it");
        codes.put("el", "el");
        codes.put("nl", "nl");
        codes.put("pl", "pl");
        codes.put("bg", "bul");
        codes.put("et", "est");
        codes.put("da", "dan");
        codes.put("fi", "fin");
        codes.put("cs", "cs");
        codes.put("ro", "rom");
        codes.put("sl", "slo");
        codes.put("sv", "swe");
        codes.put("hu", "hu");
        codes.put("vi", "vie");
        codes.put("yue", "yue");
        LANGUAGE_CODES = Collections.unmodifiableMap(codes);
    }

    private BaiduLanguages() {
    }

    /**
     * Convert an OmegaT language to a Baidu language code.
     *
     * @param language
     *            OmegaT language, may be {@code null}.
     * @param source
     *            {@code true} when the language is the source language. If the
     *            language is unknown for a source language, {@code auto} is
     *            returned; for a target language {@code null} is returned.
     * @return the Baidu language code, or {@code null} for an unsupported
     *         target language.
     */
    static String toBaiduCode(Language language, boolean source) {
        if (language == null) {
            return source ? AUTO : null;
        }
        String languageCode = language.getLanguageCode();
        if (languageCode == null || languageCode.isEmpty()) {
            return source ? AUTO : null;
        }
        languageCode = languageCode.toLowerCase(Locale.ROOT);

        if ("zh".equals(languageCode)) {
            String scriptCode = language.getLocale().getScript();
            if ("Hant".equalsIgnoreCase(scriptCode)) {
                return "cht";
            }
            String countryCode = language.getCountryCode();
            if (countryCode != null) {
                countryCode = countryCode.toUpperCase(Locale.ROOT);
                if ("TW".equals(countryCode) || "HK".equals(countryCode) || "MO".equals(countryCode)) {
                    return "cht";
                }
            }
            return "zh";
        }

        String mapped = LANGUAGE_CODES.get(languageCode);
        if (mapped != null) {
            return mapped;
        }
        return source ? AUTO : null;
    }
}
