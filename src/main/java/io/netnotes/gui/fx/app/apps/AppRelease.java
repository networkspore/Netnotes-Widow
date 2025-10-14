package io.netnotes.gui.fx.app.apps;

import io.netnotes.engine.utils.github.GitHubAsset;

public class AppRelease {
    private final String m_version;
    private final String m_tagName;
    private final GitHubAsset m_asset;
    private final AppInformation m_appInfo;
    private final long m_publishedDate;
    
    public AppRelease(AppInformation appInfo, GitHubAsset asset, String version, 
                     String tagName, long publishedDate) {
        m_appInfo = appInfo;
        m_asset = asset;
        m_version = version;
        m_tagName = tagName;
        m_publishedDate = publishedDate;
    }
    
    public AppInformation getAppInfo() {
        return m_appInfo;
    }
    
    public GitHubAsset getAsset() {
        return m_asset;
    }
    
    public String getVersion() {
        return m_version;
    }
    
    public String getTagName() {
        return m_tagName;
    }
    
    public long getPublishedDate() {
        return m_publishedDate;
    }
    
    public String getDownloadUrl() {
        return m_asset.getUrl();
    }
    
    public long getSize() {
        return m_asset.getSize();
    }
}