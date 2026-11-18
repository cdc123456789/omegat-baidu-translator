package org.cdc.openaitrans;

/**
 * Translator used for extra configurations. OmegaT creates it through the
 * no-argument constructor, so the configuration is looked up by class.
 */
class ConfigurableOpenaiTranslate extends BaseOpenaiTranslate {

    ConfigurableOpenaiTranslate() {
        super(null, null, false);
    }

    @Override
    protected void ensureConfigInitialized() {
        if (configId == null) {
            OpenaiTranslate.ConfigInfo info = OpenaiTranslate.translatorConfigs.get(getClass());
            if (info == null) {
                throw new IllegalStateException("No configuration registered for " + getClass().getName());
            }
            configId = info.configId;
            displayName = info.displayName;
        }
    }
}
