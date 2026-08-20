package org.vody.browser;

/** A single browser tab: a title, URL and its GeckoView session. */
public class Tab {
    private String mTitle = "";
    private String mUrl = "";
    private final org.mozilla.geckoview.GeckoSession mSession;

    public Tab(org.mozilla.geckoview.GeckoSession session) {
        mSession = session;
    }

    public org.mozilla.geckoview.GeckoSession getSession() {
        return mSession;
    }

    public String getTitle() {
        return mTitle == null ? "" : mTitle;
    }

    public void setTitle(String title) {
        mTitle = title;
    }

    public String getUrl() {
        return mUrl == null ? "" : mUrl;
    }

    public void setUrl(String url) {
        mUrl = url;
    }
}
