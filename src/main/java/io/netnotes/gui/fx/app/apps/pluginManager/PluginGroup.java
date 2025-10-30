package io.netnotes.gui.fx.app.apps.pluginManager;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

import io.netnotes.engine.AppDataInterface;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteFiles.NoteFile;
import io.netnotes.engine.plugins.OSGiPluginInformation;
import io.netnotes.engine.plugins.OSGiPluginMetaData;
import io.netnotes.engine.plugins.OSGiPluginRelease;
import io.netnotes.engine.plugins.OSGiPluginReleaseFetcher;
import io.netnotes.engine.utils.github.GitHubAPI;
import io.netnotes.engine.utils.github.GitHubInfo;
import io.netnotes.engine.utils.streams.StreamUtils;
import io.netnotes.engine.utils.streams.UrlStreamHelpers;
import javafx.scene.image.Image;

/**
 * Represents a group of plugin versions for the same application.
 * Handles image caching, README fetching, and version management for UI display.
 */
public class PluginGroup {
    private final String m_appName;
    private final OSGiPluginInformation m_pluginInfo;
    private final List<OSGiPluginMetaData> m_installedVersions;
    
    // Cached resources (lazy-loaded)
    private CompletableFuture<Image> m_smallIcon;
    private CompletableFuture<Image> m_fullIcon;
    private CompletableFuture<String> m_readme;
    
    public PluginGroup(String appName, OSGiPluginInformation pluginInfo) {
        m_appName = appName;
        m_pluginInfo = pluginInfo;
        m_installedVersions = new ArrayList<>();
    }
    
    /**
     * Add an installed version to this group.
     */
    public void addVersion(OSGiPluginMetaData metadata) {
        if (!m_installedVersions.contains(metadata)) {
            m_installedVersions.add(metadata);
        }
    }
    
    /**
     * Get all installed versions of this plugin.
     */
    public List<OSGiPluginMetaData> getInstalledVersions() {
        return new ArrayList<>(m_installedVersions);
    }
    
    /**
     * Get the currently enabled version, if any.
     */
    public OSGiPluginMetaData getEnabledVersion() {
        return m_installedVersions.stream()
            .filter(OSGiPluginMetaData::isEnabled)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Check if this plugin has any installed versions.
     */
    public boolean hasInstalledVersions() {
        return !m_installedVersions.isEmpty();
    }
    
    /**
     * Check if this plugin is currently installed and enabled.
     */
    public boolean isEnabled() {
        return getEnabledVersion() != null;
    }
    
    public String getAppName() {
        return m_appName;
    }
    
    public OSGiPluginInformation getPluginInfo() {
        return m_pluginInfo;
    }
    
    /**
     * Get the small icon, loading from cache or downloading if necessary.
     */
    public CompletableFuture<Image> getSmallIcon(AppDataInterface appData, ExecutorService execService) {
        if (m_smallIcon == null) {
            m_smallIcon = loadImage(
                m_pluginInfo.getSmallIconUrl(),
                m_pluginInfo.getSmallImageNotePath(),
                appData,
                execService
            );
        }
        return m_smallIcon;
    }
    
    /**
     * Get the full icon, loading from cache or downloading if necessary.
     */
    public CompletableFuture<Image> getFullIcon(AppDataInterface appData, ExecutorService execService) {
        if (m_fullIcon == null) {
            m_fullIcon = loadImage(
                m_pluginInfo.getIconUrl(),
                m_pluginInfo.getImageNotePath(),
                appData,
                execService
            );
        }
        return m_fullIcon;
    }
    
    /**
     * Load an image from cache or download if not cached.
     */
    private CompletableFuture<Image> loadImage(
        String imageUrl,
        NoteStringArrayReadOnly notePath,
        AppDataInterface appData,
        ExecutorService execService
    ) {
        return appData.getNoteFile(notePath).thenCompose(noteFile -> {
            if (noteFile.isFile()) {
                // Image is cached, read from NoteFile
                return readImageFromNoteFile(noteFile, execService);
            } else {
                // Image not cached, download and cache
                return downloadAndCacheImage(imageUrl, noteFile, execService);
            }
        });
    }
    
    /**
     * Download an image from URL, cache it to NoteFile, and return it for display.
     */
    private CompletableFuture<Image> downloadAndCacheImage(
        String imageUrl,
        NoteFile noteFile,
        ExecutorService execService
    ) {
        PipedOutputStream cacheStream = new PipedOutputStream();
        PipedOutputStream displayStream = new PipedOutputStream();
        
        // Download and duplicate stream to both cache and display
        CompletableFuture<Void> downloadFuture = 
            UrlStreamHelpers.duplicateUrlStream(imageUrl, cacheStream, displayStream, null, execService);
        
        // Write one stream to NoteFile for caching
        CompletableFuture<NoteBytesObject> writeFuture = 
            noteFile.writeOnly(cacheStream);
        
        // Read other stream to create Image for display
        CompletableFuture<Image> imageFuture = 
            readImageStream(displayStream, execService);
        
        // Wait for all operations to complete
        return CompletableFuture.allOf(downloadFuture, writeFuture, imageFuture)
            .thenCompose(_ -> imageFuture);
    }
    
    /**
     * Read an image from a cached NoteFile.
     */
    private CompletableFuture<Image> readImageFromNoteFile(NoteFile noteFile, ExecutorService execService) {
        PipedOutputStream outputStream = new PipedOutputStream();
        
        CompletableFuture<NoteBytesObject> readFuture = noteFile.readOnly(outputStream);
        CompletableFuture<Image> imageFuture = readImageStream(outputStream, execService);
        
        return CompletableFuture.allOf(readFuture, imageFuture)
            .thenCompose(_ -> imageFuture);
    }
    
    /**
     * Read an Image from a PipedOutputStream.
     */
    private CompletableFuture<Image> readImageStream(PipedOutputStream outputStream, ExecutorService execService) {
        return CompletableFuture.supplyAsync(() -> {
            try (PipedInputStream inputStream = new PipedInputStream(outputStream, StreamUtils.PIPE_BUFFER_SIZE)) {
                return new Image(inputStream);
            } catch (IOException e) {
                throw new CompletionException("Failed to read image stream", e);
            }
        }, execService);
    }
    
    /**
     * Get the README for this plugin, fetching from GitHub if not cached.
     * Note: README is NOT persisted, only cached in memory.
     */
    public CompletableFuture<String> getReadme(ExecutorService execService) {
        if (m_readme == null) {
            m_readme = fetchReadme(execService);
        }
        return m_readme;
    }
    
    /**
     * Fetch README.md from the GitHub repository.
     */
    private CompletableFuture<String> fetchReadme(ExecutorService execService) {
        GitHubInfo githubInfo = m_pluginInfo.getGitHubJar().getGitHubInfo();
        String readmeUrl = GitHubAPI.getUrlUserContentPath(githubInfo, m_pluginInfo.getBranch(), GitHubAPI.README_FILE);
        
        return UrlStreamHelpers.getUrlContentAsString(readmeUrl, execService);
    }
    
    /**
     * Clear cached resources (for refresh operations).
     */
    public void clearCache() {
        m_smallIcon = null;
        m_fullIcon = null;
        m_readme = null;
    }
    
    /**
     * Check if there's a newer version available than the currently enabled one.
     * Returns the latest release if an update is available, null otherwise.
     */
    public CompletableFuture<OSGiPluginRelease> checkForUpdate(
        OSGiPluginReleaseFetcher fetcher,
        ExecutorService execService
    ) {
        OSGiPluginMetaData enabledVersion = getEnabledVersion();
        if (enabledVersion == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        return fetcher.fetchReleasesForApp(false, m_pluginInfo)
            .thenApply(releases -> {
                if (releases.isEmpty()) {
                    return null;
                }
                
                // Get the latest release (first in list)
                OSGiPluginRelease latest = releases.get(0);
                
                // Check if it's different from enabled version
                String enabledTag = enabledVersion.getRelease().getTagName();
                String latestTag = latest.getTagName();
                
                if (!enabledTag.equals(latestTag)) {
                    return latest;
                }
                
                return null;
            });
    }
}