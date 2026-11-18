package org.cdc;

import org.cdc.baidu.translator.BaiduTranslator;
import org.omegat.core.Core;
import org.cdc.openaitrans.OpenaiTranslate;
import org.cdc.search.SearchSelectedTextPlugin;

import static org.cdc.openaitrans.OpenaiTranslate.registerAdditionalTranslators;

public class PluginMain {
    /**
     * OmegaT plugin entry point. Called before the GUI is initialized.
     */
    public static void loadPlugins() {
        Core.registerMachineTranslationClass(BaiduTranslator.class);
        // 注册默认插件
        Core.registerMachineTranslationClass(OpenaiTranslate.class);

        // 注册额外的翻译插件实例
        registerAdditionalTranslators();

        // 编辑器右键菜单：搜索选中文本
        SearchSelectedTextPlugin.install();
    }

    /**
     * OmegaT plugin unload hook. The connector has no global resources.
     */
    public static void unloadPlugins() {
        SearchSelectedTextPlugin.uninstall();
    }
}
