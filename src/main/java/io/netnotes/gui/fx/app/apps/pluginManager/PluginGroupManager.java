package io.netnotes.gui.fx.app.apps.pluginManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import io.netnotes.engine.plugins.OSGiPluginInformation;
import io.netnotes.engine.plugins.OSGiPluginMetaData;
import io.netnotes.engine.plugins.OSGiPluginRegistry;

/**
 * Manages the grouped view of plugins for the UI.
 * Creates PluginGroup instances from the flat registry structure,
 * grouping multiple versions of the same plugin together.
 */
public class PluginGroupManager {
    private final Map<String, PluginGroup> m_groups;
    
    public PluginGroupManager() {
        m_groups = new ConcurrentHashMap<>();
    }
    
    /**
     * Build the grouped view from the registry and available plugins list.
     * This should be called whenever the registry or available apps list changes.
     * 
     * @param registry The plugin registry containing installed plugins
     * @param availablePlugins List of all available plugins
     */
    public void buildFromRegistry(
        OSGiPluginRegistry registry,
        List<OSGiPluginInformation> availablePlugins
    ) {
        m_groups.clear();
        
        // Create groups from available plugins
        for (OSGiPluginInformation pluginInfo : availablePlugins) {
            String appName = pluginInfo.getName();
            if (!m_groups.containsKey(appName)) {
                m_groups.put(appName, new PluginGroup(appName, pluginInfo));
            }
        }
        
        // Add installed versions to their respective groups
        for (OSGiPluginMetaData metadata : registry.getAllPlugins()) {
            String appName = metadata.getName();
            PluginGroup group = m_groups.get(appName);
            
            if (group != null) {
                group.addVersion(metadata);
            } else {
                // Plugin is installed but not in available list
                // This could happen if the plugin was removed from the available list
                // Create a group for it anyway so it shows in installed tab
                System.out.println("Warning: Installed plugin '" + appName + 
                    "' not found in available plugins list");
            }
        }
    }
    
    /**
     * Get all plugin groups (both installed and available).
     */
    public List<PluginGroup> getAllGroups() {
        return new ArrayList<>(m_groups.values());
    }
    
    /**
     * Get all plugin groups for the Browse tab.
     * Returns all available plugins regardless of installation status.
     */
    public List<PluginGroup> getBrowseGroups() {
        return new ArrayList<>(m_groups.values());
    }
    
    /**
     * Get only plugin groups that have installed versions.
     * Used for the Installed tab.
     */
    public List<PluginGroup> getInstalledGroups() {
        return m_groups.values().stream()
            .filter(PluginGroup::hasInstalledVersions)
            .collect(Collectors.toList());
    }
    
    /**
     * Get a specific plugin group by app name.
     */
    public PluginGroup getGroup(String appName) {
        return m_groups.get(appName);
    }
    
    /**
     * Check if a plugin is installed.
     */
    public boolean isPluginInstalled(String appName) {
        PluginGroup group = m_groups.get(appName);
        return group != null && group.hasInstalledVersions();
    }
    
    /**
     * Get the number of available plugins.
     */
    public int getAvailableCount() {
        return m_groups.size();
    }
    
    /**
     * Get the number of installed plugins.
     */
    public int getInstalledCount() {
        return (int) m_groups.values().stream()
            .filter(PluginGroup::hasInstalledVersions)
            .count();
    }
    
    /**
     * Get the number of enabled plugins.
     */
    public int getEnabledCount() {
        return (int) m_groups.values().stream()
            .filter(PluginGroup::isEnabled)
            .count();
    }
    
    /**
     * Clear all groups.
     */
    public void clear() {
        m_groups.clear();
    }
}