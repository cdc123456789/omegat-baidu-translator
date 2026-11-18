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

/**
 * Signals a problem while talking to the Baidu Translate API.
 */
class BaiduTranslateException extends Exception {

    private static final long serialVersionUID = 1L;

    BaiduTranslateException(String message) {
        super(message);
    }

    BaiduTranslateException(String message, Throwable cause) {
        super(message, cause);
    }
}
