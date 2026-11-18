package org.cdc.search;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.text.JTextComponent;

import org.omegat.core.Core;
import org.omegat.core.CoreEvents;
import org.omegat.core.events.IApplicationEventListener;
import org.omegat.gui.editor.IEditor;
import org.omegat.gui.editor.IPopupMenuConstructor;
import org.omegat.gui.editor.SegmentBuilder;
import org.omegat.util.Log;

/**
 * Adds search-engine entries for the selected text to OmegaT's editor context
 * menu.
 * <p>
 * The popup constructor is registered during application startup, after the
 * editor has been created. Clicking an entry opens the default browser with
 * the selected text as the search query.
 */
public final class SearchSelectedTextPlugin {

    private static final int POPUP_PRIORITY = 900;

    private static IApplicationEventListener applicationListener;

    private SearchSelectedTextPlugin() {
    }

    /**
     * Registers the application startup listener. Safe to call more than once.
     */
    public static synchronized void install() {
        if (applicationListener != null) {
            return;
        }
        applicationListener = new IApplicationEventListener() {
            @Override
            public void onApplicationStartup() {
                IEditor editor = Core.getEditor();
                editor.registerPopupMenuConstructors(POPUP_PRIORITY, new SearchPopup());
            }

            @Override
            public void onApplicationShutdown() {
                // The popup constructor cannot be unregistered from the editor.
            }
        };
        CoreEvents.registerApplicationEventListener(applicationListener);
    }

    /**
     * Removes the application startup listener.
     */
    public static synchronized void uninstall() {
        if (applicationListener != null) {
            CoreEvents.unregisterApplicationEventListener(applicationListener);
            applicationListener = null;
        }
    }

    /**
     * Supported search engines. More engines can be added without changing the
     * popup code.
     */
    enum Engine {
        BAIDU("Baidu", "https://www.baidu.com/s?wd="),
        GOOGLE("Google", "https://www.google.com/search?q="),
        BING("Bing", "https://www.bing.com/search?q="),
        DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=");

        private final String label;
        private final String baseUrl;

        Engine(String label, String baseUrl) {
            this.label = label;
            this.baseUrl = baseUrl;
        }

        String searchUrl(String query) {
            return baseUrl + URLEncoder.encode(query, StandardCharsets.UTF_8);
        }
    }

    static final class SearchPopup implements IPopupMenuConstructor {

        @Override
        public void addItems(JPopupMenu menu, JTextComponent component, int mousepos,
                boolean isInActiveEntry, boolean isInActiveTranslation, SegmentBuilder segmentBuilder) {
            String selection = component.getSelectedText();
            if (selection == null || selection.trim().isEmpty()) {
                return;
            }
            final String query = selection.trim();

            JMenu searchMenu = new JMenu("Search selected text");
            for (Engine engine : Engine.values()) {
                JMenuItem item = new JMenuItem(engine.label);
                item.addActionListener(event -> openInBrowser(engine.searchUrl(query)));
                searchMenu.add(item);
            }

            menu.add(searchMenu);
        }
    }

    private static void openInBrowser(String url) {
        try {
            if (GraphicsEnvironment.isHeadless() || !Desktop.isDesktopSupported()) {
                throw new IllegalStateException("Desktop browsing is not supported");
            }
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                throw new IllegalStateException("Browsing is not supported");
            }
            desktop.browse(URI.create(url));
        } catch (Exception ex) {
            Log.log(ex);
            String message = "Could not open the search URL:\n" + url;
            if (Core.getMainWindow() != null) {
                Core.getMainWindow().showMessageDialog(message);
            } else {
                JOptionPane.showMessageDialog(null, message);
            }
        }
    }
}
