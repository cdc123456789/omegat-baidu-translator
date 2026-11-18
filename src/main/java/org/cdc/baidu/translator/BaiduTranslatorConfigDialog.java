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

import org.omegat.gui.exttrans.MTConfigDialog;

/**
 * Configuration dialog for the Baidu Translate connector. It reuses OmegaT's
 * standard MT configuration panel, which provides two credential fields and a
 * "store for this session only" checkbox.
 */
final class BaiduTranslatorConfigDialog extends MTConfigDialog {

    private final BaiduTranslator translator;

    BaiduTranslatorConfigDialog(Window parent, BaiduTranslator translator) {
        super(parent, "Baidu Translate Settings");
        this.translator = translator;
        configurePanel();
    }

    private void configurePanel() {
        panel.descriptionTextArea.setText("Enter the APP ID and secret key of your Baidu Translate API application. "
                + "You can create or manage applications at https://fanyi-api.baidu.com/.");
        panel.descriptionTextArea.setRows(3);
        panel.descriptionTextArea.setColumns(45);

        panel.valueLabel1.setText("APP ID");
        panel.valueField1.setText(translator.getAppId());
        panel.valueField1.setColumns(30);

        panel.valueLabel2.setText("Secret key");
        panel.valueField2.setText(translator.getSecretKey());
        panel.valueField2.setColumns(30);

        panel.temporaryCheckBox.setSelected(translator.isCredentialStoredTemporarily());
    }

    @Override
    protected void onConfirm() {
        translator.saveCredentials(panel.valueField1.getText().trim(), panel.valueField2.getText().trim(),
                panel.temporaryCheckBox.isSelected());
    }
}
