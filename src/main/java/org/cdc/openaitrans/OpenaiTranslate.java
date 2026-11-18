/*
 * Copyright (C) 2024 Inertia
 *
 * 这是一个基于 LLM API 的 OmegaT 机器翻译插件
 * This is an OmegaT machine translation plugin based on LLM API
 *
 * 本项目参考和借鉴了以下开源项目：
 * This project references and learns from the following open source projects:
 * - omegat-org/azure-translate-plugin
 * - yoyicue/omegat-tencent-plugin
 * - omegat-org/omegat (YandexTranslate.java)
 * - GoogleTranslateWithoutApiKey
 * - omegat-niutrans-plugin
 *
 * 本程序遵循 GNU 通用公共许可证第3版（GPL v3）
 * This program is licensed under GNU General Public License v3
 */
package org.cdc.openaitrans;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.matcher.ElementMatchers;

import org.omegat.core.Core;
import org.omegat.util.CredentialsManager;
import org.omegat.util.OStrings;
import org.omegat.util.Preferences;
import org.omegat.util.gui.StaticUIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OmegaT machine translation connector for OpenAI-compatible APIs.
 * <p>
 * The connector supports OpenAI, Claude, DeepSeek and VolcEngine API formats,
 * multiple named configurations, optional glossary injection and translation
 * caching. Configuration strings are intentionally not localized.
 */
public class OpenaiTranslate extends BaseOpenaiTranslate {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenaiTranslate.class);

    static final String PROPERTY_CONFIG_LIST = "openai.config.list";
    static final String PROPERTY_CONFIG_NAME = "openai.%s.config.name";
    static final String PROPERTY_CONFIG_ENABLED = "openai.%s.config.enabled";
    static final String PROPERTY_CONFIG_CREATED = "openai.%s.config.created";

    private static final String DEFAULT_CONFIG_ID = "api";
    private static final String DEFAULT_CONFIG_NAME = "Default Configuration";
    private static final String CONFIG_NAME_PREFIX = "Configuration ";
    private static final String DEFAULT_MODEL = "gpt-4o";
    private static final String DEFAULT_PROVIDER = "OpenAI";

    /**
     * Configurations registered for dynamically generated translator classes.
     * The class is the key because OmegaT instantiates every registered class
     * through its no-argument constructor.
     */
    static final Map<Class<?>, ConfigInfo> translatorConfigs = new ConcurrentHashMap<>();

    public OpenaiTranslate() {
        super(DEFAULT_CONFIG_ID, "Openai Translate", true);
    }

    /**
     * OmegaT plugin entry point. The plugin's main class registers this
     * connector explicitly, so this method only exists for compatibility.
     */
    public static void loadPlugins() {
        LOGGER.debug("OpenaiTranslate plugin loaded");
    }

    /**
     * Registers one translator per enabled extra configuration.
     */
    public static void registerAdditionalTranslators() {
        try {
            for (String configId : getConfigList()) {
                if (DEFAULT_CONFIG_ID.equals(configId)) {
                    continue;
                }
                if (!Boolean.parseBoolean(preference(configTemplate(PROPERTY_CONFIG_ENABLED, configId), "true"))) {
                    continue;
                }
                String configName = preference(configTemplate(PROPERTY_CONFIG_NAME, configId),
                        CONFIG_NAME_PREFIX + configId);
                registerConfigSpecificTranslator(configId, configName);
            }
        } catch (RuntimeException ex) {
            // OmegaT initializes Preferences before loading plugins. Keep the
            // default connector usable even if preferences are not available.
            LOGGER.warn("Could not register extra OpenAI translators", ex);
        }
    }

    private static void registerConfigSpecificTranslator(String configId, String configName) {
        try {
            Class<? extends ConfigurableOpenaiTranslate> dynamicClass = new ByteBuddy()
                    .subclass(ConfigurableOpenaiTranslate.class)
                    .name("xyz.inertia.machinetranslators.openaitrans.DynamicTranslator_" + configId)
                    .method(ElementMatchers.named("getName"))
                    .intercept(FixedValue.value(configName))
                    .make()
                    .load(OpenaiTranslate.class.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                    .getLoaded();

            translatorConfigs.put(dynamicClass, new ConfigInfo(configId, configName));
            Core.registerMachineTranslationClass(dynamicClass);
            LOGGER.debug("Registered OpenAI translator '{}' (config {})", configName, configId);
        } catch (Exception ex) {
            LOGGER.error("Failed to register OpenAI translator '{}' (config {})", configName, configId, ex);
        }
    }

    @Override
    public String getName() {
        return "Openai Translate";
    }

    @Override
    protected String getPreferenceName() {
        return "allow_openai_translate";
    }

    @Override
    public void showConfigurationUI(Window parent) {
        Runnable show = () -> {
            List<ConfigTabData> configs = loadAllConfigs();
            ConfigDialog dialog = new ConfigDialog(parent, getName(), configs);
            if (dialog.showDialog()) {
                saveAllConfigurations(dialog.getConfigs());
            }
        };
        runOnEventDispatchThread(show);
    }

    private static void runOnEventDispatchThread(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }

    // ------------------------------------------------------------------
    // Configuration persistence
    // ------------------------------------------------------------------

    private static List<ConfigTabData> loadAllConfigs() {
        List<ConfigTabData> configs = new ArrayList<>();
        configs.add(loadConfigData(DEFAULT_CONFIG_ID));
        for (String configId : getConfigList()) {
            if (!DEFAULT_CONFIG_ID.equals(configId)) {
                configs.add(loadConfigData(configId));
            }
        }
        return configs;
    }

    private void saveAllConfigurations(List<ConfigTabData> configs) {
        List<String> previousIds = getConfigList();
        List<String> currentIds = new ArrayList<>();

        for (ConfigTabData config : configs) {
            saveConfigData(config);
            if (!DEFAULT_CONFIG_ID.equals(config.configId)) {
                currentIds.add(config.configId);
            }
        }
        saveConfigList(currentIds);

        for (String previousId : previousIds) {
            if (!currentIds.contains(previousId)) {
                deleteConfig(previousId);
            }
        }
        // Prompt, URL or model changes must not keep returning old results.
        clearCache();
    }

    private static ConfigTabData loadConfigData(String configId) {
        String defaultName = DEFAULT_CONFIG_ID.equals(configId)
                ? DEFAULT_CONFIG_NAME
                : CONFIG_NAME_PREFIX + configId;
        String name = preference(configTemplate(PROPERTY_CONFIG_NAME, configId), defaultName);
        ConfigTabData data = new ConfigTabData(configId, name);

        data.enabled = Boolean.parseBoolean(preference(configTemplate(PROPERTY_CONFIG_ENABLED, configId), "true"));
        data.createdTime = preference(configTemplate(PROPERTY_CONFIG_CREATED, configId),
                String.valueOf(System.currentTimeMillis()));
        data.apiKey = credential(configKey(configId, BaseOpenaiTranslate.PROPERTY_API_KEY));
        data.apiUrl = preference(configKey(configId, BaseOpenaiTranslate.PROPERTY_API_URL), "");
        data.prompt = preference(configKey(configId, BaseOpenaiTranslate.PROPERTY_API_PROMPT), "");
        data.temperature = preference(configKey(configId, BaseOpenaiTranslate.PROPERTY_API_TEMPERATURE), "0");
        data.useCustomModel = Boolean.parseBoolean(
                preference(configKey(configId, BaseOpenaiTranslate.PROPERTY_USE_CUSTOM_MODEL), "false"));
        data.customModel = preference(configKey(configId, BaseOpenaiTranslate.PROPERTY_API_CUSTOM_MODEL), "");
        data.model = preference(configKey(configId, BaseOpenaiTranslate.PROPERTY_API_MODEL), DEFAULT_MODEL);
        data.apiProvider = preference(configKey(configId, BaseOpenaiTranslate.PROPERTY_API_PROVIDER), DEFAULT_PROVIDER);
        data.enableCache = Boolean.parseBoolean(
                preference(configKey(configId, BaseOpenaiTranslate.PROPERTY_API_CACHE), "true"));
        data.enableGlossary = Boolean.parseBoolean(
                preference(configKey(configId, BaseOpenaiTranslate.PROPERTY_API_GLOSSARY), "true"));
        data.temporaryCredential = false;
        return data;
    }

    private static void saveConfigData(ConfigTabData data) {
        Preferences.setPreference(configTemplate(PROPERTY_CONFIG_NAME, data.configId), data.name);
        Preferences.setPreference(configTemplate(PROPERTY_CONFIG_ENABLED, data.configId),
                String.valueOf(data.enabled));
        Preferences.setPreference(configTemplate(PROPERTY_CONFIG_CREATED, data.configId), data.createdTime);

        storeCredential(configKey(data.configId, BaseOpenaiTranslate.PROPERTY_API_KEY), data.apiKey,
                data.temporaryCredential);        Preferences.setPreference(configKey(data.configId, BaseOpenaiTranslate.PROPERTY_API_URL), data.apiUrl);
        Preferences.setPreference(configKey(data.configId, BaseOpenaiTranslate.PROPERTY_API_PROMPT), data.prompt);
        Preferences.setPreference(configKey(data.configId, BaseOpenaiTranslate.PROPERTY_API_TEMPERATURE),
                data.temperature);
        Preferences.setPreference(configKey(data.configId, BaseOpenaiTranslate.PROPERTY_USE_CUSTOM_MODEL),
                String.valueOf(data.useCustomModel));
        Preferences.setPreference(configKey(data.configId, BaseOpenaiTranslate.PROPERTY_API_CUSTOM_MODEL),
                data.customModel);
        Preferences.setPreference(configKey(data.configId, BaseOpenaiTranslate.PROPERTY_API_MODEL), data.model);
        Preferences.setPreference(configKey(data.configId, BaseOpenaiTranslate.PROPERTY_API_PROVIDER),
                data.apiProvider);
        Preferences.setPreference(configKey(data.configId, BaseOpenaiTranslate.PROPERTY_API_CACHE),
                String.valueOf(data.enableCache));
        Preferences.setPreference(configKey(data.configId, BaseOpenaiTranslate.PROPERTY_API_GLOSSARY),
                String.valueOf(data.enableGlossary));
    }

    private static void deleteConfig(String configId) {
        if (DEFAULT_CONFIG_ID.equals(configId)) {
            return;
        }
        Preferences.setPreference(configTemplate(PROPERTY_CONFIG_NAME, configId), "");
        Preferences.setPreference(configTemplate(PROPERTY_CONFIG_ENABLED, configId), "");
        Preferences.setPreference(configTemplate(PROPERTY_CONFIG_CREATED, configId), "");
        for (String baseKey : new String[] {
                BaseOpenaiTranslate.PROPERTY_API_URL,
                BaseOpenaiTranslate.PROPERTY_API_MODEL,
                BaseOpenaiTranslate.PROPERTY_API_CUSTOM_MODEL,
                BaseOpenaiTranslate.PROPERTY_USE_CUSTOM_MODEL,
                BaseOpenaiTranslate.PROPERTY_API_PROVIDER,
                BaseOpenaiTranslate.PROPERTY_API_PROMPT,
                BaseOpenaiTranslate.PROPERTY_API_TEMPERATURE,
                BaseOpenaiTranslate.PROPERTY_API_CACHE,
                BaseOpenaiTranslate.PROPERTY_API_GLOSSARY
        }) {
            Preferences.setPreference(configKey(configId, baseKey), "");
        }
        storeCredential(configKey(configId, BaseOpenaiTranslate.PROPERTY_API_KEY), "", false);
    }

    private static String credential(String key) {
        String property = System.getProperty(key);
        if (property != null) {
            return property;
        }
        return CredentialsManager.getInstance().retrieve(key).orElse("");
    }

    private static void storeCredential(String key, String value, boolean temporary) {
        System.setProperty(key, value);
        CredentialsManager.getInstance().store(key, temporary ? "" : value);
    }

    private static List<String> getConfigList() {
        String raw = Preferences.getPreference(PROPERTY_CONFIG_LIST);
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<>();
        for (String id : raw.split(",")) {
            String trimmed = id.trim();
            if (!trimmed.isEmpty()) {
                ids.add(trimmed);
            }
        }
        return ids;
    }

    private static void saveConfigList(List<String> configIds) {
        Preferences.setPreference(PROPERTY_CONFIG_LIST, String.join(",", configIds));
    }

    private static String configTemplate(String template, String configId) {
        return String.format(template, configId);
    }

    static String configKey(String configId, String baseKey) {
        if (DEFAULT_CONFIG_ID.equals(configId)) {
            return baseKey;
        }
        return "openai." + configId + baseKey.substring("openai".length());
    }

    private static String preference(String key, String defaultValue) {
        String value = Preferences.getPreference(key);
        return value == null || value.trim().isEmpty() ? defaultValue : value;
    }

    /**
     * Configuration data shown in one tab of the settings dialog.
     */
    static class ConfigTabData {
        String configId;
        String name;
        boolean enabled;
        String createdTime;
        String apiKey;
        String apiUrl;
        String prompt;
        String temperature;
        String model;
        String customModel;
        boolean useCustomModel;
        String apiProvider;
        boolean enableCache;
        boolean enableGlossary;
        boolean temporaryCredential;

        ConfigTabData(String name) {
            this(generateConfigId(), name);
        }

        ConfigTabData(String configId, String name) {
            this.configId = configId;
            this.name = name;
            this.enabled = true;
            this.createdTime = String.valueOf(System.currentTimeMillis());
            this.apiKey = "";
            this.apiUrl = "";
            this.prompt = "";
            this.temperature = "0";
            this.model = DEFAULT_MODEL;
            this.customModel = "";
            this.useCustomModel = false;
            this.apiProvider = DEFAULT_PROVIDER;
            this.enableCache = true;
            this.enableGlossary = true;
            this.temporaryCredential = false;
        }

        ConfigTabData copy() {
            ConfigTabData copy = new ConfigTabData(name);
            copy.enabled = enabled;
            copy.apiKey = apiKey;
            copy.apiUrl = apiUrl;
            copy.prompt = prompt;
            copy.temperature = temperature;
            copy.model = model;
            copy.customModel = customModel;
            copy.useCustomModel = useCustomModel;
            copy.apiProvider = apiProvider;
            copy.enableCache = enableCache;
            copy.enableGlossary = enableGlossary;
            copy.temporaryCredential = temporaryCredential;
            return copy;
        }

        private static String generateConfigId() {
            String alphabet = "abcdefghijklmnopqrstuvwxyz";
            StringBuilder id = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                id.append(alphabet.charAt(ThreadLocalRandom.current().nextInt(alphabet.length())));
            }
            return id.toString();
        }
    }

    static final class ConfigInfo {
        final String configId;
        final String displayName;

        ConfigInfo(String configId, String displayName) {
            this.configId = configId;
            this.displayName = displayName;
        }
    }

    // ------------------------------------------------------------------
    // Settings UI
    // ------------------------------------------------------------------

    private static final class ConfigDialog extends JDialog {

        private static final long serialVersionUID = 1L;

        private final JTabbedPane tabs = new JTabbedPane();
        private final List<ConfigEditorPanel> editors = new ArrayList<>();
        private final JButton removeButton = new JButton("Remove configuration");
        private boolean confirmed;
        private int newConfigCounter = 1;

        ConfigDialog(Window parent, String title, List<ConfigTabData> configs) {
            super(parent, title, Dialog.ModalityType.APPLICATION_MODAL);
            setLayout(new BorderLayout());

            JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton addButton = new JButton("Add configuration");
            addButton.addActionListener(event -> addConfig(new ConfigTabData(
                    CONFIG_NAME_PREFIX + newConfigCounter++)));
            removeButton.addActionListener(event -> removeCurrentConfig());
            toolbar.add(addButton);
            toolbar.add(removeButton);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton okButton = new JButton(OStrings.getString("BUTTON_OK"));
            JButton cancelButton = new JButton(OStrings.getString("BUTTON_CANCEL"));
            okButton.addActionListener(event -> {
                confirmed = true;
                dispose();
            });
            cancelButton.addActionListener(event -> dispose());
            buttons.add(okButton);
            buttons.add(cancelButton);

            add(toolbar, BorderLayout.NORTH);
            add(tabs, BorderLayout.CENTER);
            add(buttons, BorderLayout.SOUTH);

            for (ConfigTabData config : configs) {
                addConfig(config);
            }
            if (editors.isEmpty()) {
                addConfig(new ConfigTabData(DEFAULT_CONFIG_NAME));
            }

            StaticUIUtils.setEscapeClosable(this);
            getRootPane().setDefaultButton(okButton);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        }

        private void addConfig(ConfigTabData data) {
            ConfigEditorPanel editor = new ConfigEditorPanel(data);
            editors.add(editor);
            tabs.addTab(tabTitle(editor), editor);
            tabs.setSelectedComponent(editor);

            editor.nameField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent event) {
                    refreshTabTitle(editor);
                }

                @Override
                public void removeUpdate(DocumentEvent event) {
                    refreshTabTitle(editor);
                }

                @Override
                public void changedUpdate(DocumentEvent event) {
                    refreshTabTitle(editor);
                }
            });
            editor.enabledCheck.addActionListener(event -> refreshTabTitle(editor));
            updateRemoveButton();
        }

        private void removeCurrentConfig() {
            int index = tabs.getSelectedIndex();
            if (index < 0 || editors.size() <= 1) {
                return;
            }
            editors.remove(index);
            tabs.removeTabAt(index);
            if (!editors.isEmpty()) {
                tabs.setSelectedIndex(Math.max(0, index - 1));
            }
            updateRemoveButton();
        }

        private void updateRemoveButton() {
            removeButton.setEnabled(editors.size() > 1);
        }

        private void refreshTabTitle(ConfigEditorPanel editor) {
            int index = tabs.indexOfComponent(editor);
            if (index >= 0) {
                tabs.setTitleAt(index, tabTitle(editor));
            }
        }

        private String tabTitle(ConfigEditorPanel editor) {
            ConfigTabData data = editor.getData();
            return data.enabled ? data.name : data.name + " (Disabled)";
        }

        List<ConfigTabData> getConfigs() {
            List<ConfigTabData> configs = new ArrayList<>();
            for (ConfigEditorPanel editor : editors) {
                editor.updateData();
                configs.add(editor.getData());
            }
            return configs;
        }

        boolean showDialog() {
            setPreferredSize(new Dimension(700, 600));
            pack();
            setLocationRelativeTo(getOwner());
            setVisible(true);
            return confirmed;
        }
    }

    private static final class ConfigEditorPanel extends JPanel {

        private static final long serialVersionUID = 1L;

        private final ConfigTabData data;
        private final JCheckBox enabledCheck = new JCheckBox("Enable this configuration");
        private final JTextField nameField = new JTextField();
        private final JTextArea apiKeyField = textArea(2);
        private final JTextField urlField = new JTextField();
        private final JTextArea promptField = textArea(3);
        private final JTextField temperatureField = new JTextField();
        private final JComboBox<String> modelCombo = new JComboBox<>();
        private final JTextField customModelField = new JTextField();
        private final JCheckBox useCustomModelCheck = new JCheckBox("Use custom model");
        private final JComboBox<String> providerCombo = new JComboBox<>();
        private final JCheckBox temporaryCheck = new JCheckBox(OStrings.getString("PREFS_CREDENTIAL_TEMPORARY_LABEL"));
        private final JCheckBox cacheCheck = new JCheckBox("Enable caching");
        private final JCheckBox glossaryCheck = new JCheckBox("Enable glossary");

        ConfigEditorPanel(ConfigTabData data) {
            this.data = data;
            buildUi();
            loadData();
        }

        private void buildUi() {
            setLayout(new BorderLayout());
            JPanel form = new JPanel(new GridBagLayout());
            form.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            int row = 0;
            addFullWidthRow(form, enabledCheck, row++);
            addRow(form, "Configuration name", nameField, row++);
            addRow(form, "API key", scroll(apiKeyField), row++);
            addRow(form, "API URL (e.g. https://api.openai.com)", urlField, row++);
            addRow(form, "Prompt", scroll(promptField), row++);
            addRow(form, "Temperature (default 0)", temperatureField, row++);

            modelCombo.setModel(new DefaultComboBoxModel<>(BaseOpenaiTranslate.allModels()));
            addRow(form, "Model", modelCombo, row++);
            addRow(form, "Custom model", customModelField, row++);
            addFullWidthRow(form, useCustomModelCheck, row++);

            providerCombo.setModel(new DefaultComboBoxModel<>(
                    BaseOpenaiTranslate.PROVIDERS.toArray(new String[0])));
            addRow(form, "API format", providerCombo, row++);
            addFullWidthRow(form, cacheCheck, row++);
            addFullWidthRow(form, glossaryCheck, row++);
            addFullWidthRow(form, temporaryCheck, row);

            useCustomModelCheck.addActionListener(event -> updateCustomModelState());
            add(new JScrollPane(form), BorderLayout.CENTER);
        }

        private void loadData() {
            enabledCheck.setSelected(data.enabled);
            nameField.setText(data.name);
            apiKeyField.setText(data.apiKey);
            urlField.setText(data.apiUrl);
            promptField.setText(data.prompt);
            temperatureField.setText(data.temperature);
            useCustomModelCheck.setSelected(data.useCustomModel);
            customModelField.setText(data.customModel);
            providerCombo.setSelectedItem(data.apiProvider);
            cacheCheck.setSelected(data.enableCache);
            glossaryCheck.setSelected(data.enableGlossary);
            temporaryCheck.setSelected(data.temporaryCredential);

            if (!data.useCustomModel) {
                modelCombo.setSelectedItem("custom".equals(data.model) ? DEFAULT_MODEL : data.model);
            }
            updateCustomModelState();
        }

        private void updateCustomModelState() {
            boolean custom = useCustomModelCheck.isSelected();
            modelCombo.setEnabled(!custom);
            customModelField.setEnabled(custom);
        }

        void updateData() {
            data.enabled = enabledCheck.isSelected();
            data.name = nameField.getText().trim();
            data.apiKey = apiKeyField.getText().trim();
            data.apiUrl = urlField.getText().trim();
            data.prompt = promptField.getText().trim();
            data.temperature = temperatureField.getText().trim();
            data.useCustomModel = useCustomModelCheck.isSelected();
            data.customModel = customModelField.getText().trim();
            String selectedModel = (String) modelCombo.getSelectedItem();
            data.model = data.useCustomModel ? "custom"
                    : (selectedModel == null ? DEFAULT_MODEL : selectedModel);
            String selectedProvider = (String) providerCombo.getSelectedItem();
            data.apiProvider = selectedProvider == null ? DEFAULT_PROVIDER : selectedProvider;
            data.enableCache = cacheCheck.isSelected();
            data.enableGlossary = glossaryCheck.isSelected();
            data.temporaryCredential = temporaryCheck.isSelected();
        }

        ConfigTabData getData() {
            updateData();
            return data;
        }

        private static JTextArea textArea(int rows) {
            JTextArea area = new JTextArea(rows, 40);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            return area;
        }

        private static JScrollPane scroll(JTextArea area) {
            JScrollPane scrollPane = new JScrollPane(area);
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            return scrollPane;
        }

        private static void addRow(JPanel panel, String label, JComponent field, int row) {
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(4, 4, 4, 4);
            constraints.gridx = 0;
            constraints.gridy = row;
            constraints.anchor = GridBagConstraints.WEST;
            panel.add(new JLabel(label), constraints);

            constraints.gridx = 1;
            constraints.weightx = 1.0;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            panel.add(field, constraints);
        }

        private static void addFullWidthRow(JPanel panel, JComponent component, int row) {
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(4, 4, 4, 4);
            constraints.gridx = 0;
            constraints.gridy = row;
            constraints.gridwidth = 2;
            constraints.anchor = GridBagConstraints.WEST;
            panel.add(component, constraints);
        }
    }
}

