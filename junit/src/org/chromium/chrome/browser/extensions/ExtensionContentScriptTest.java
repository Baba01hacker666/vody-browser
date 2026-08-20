package org.chromium.chrome.browser.extensions;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.chromium.chrome.browser.extensions.model.ExtensionContentScript;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;

/**
 * Unit tests for {@link ExtensionContentScript}.
 */
@RunWith(RobolectricTestRunner.class)
public class ExtensionContentScriptTest {

    @Test
    public void testUrlMatchingAllUrls() {
        ExtensionContentScript script = new ExtensionContentScript(
                Collections.singletonList("<all_urls>"),
                Collections.singletonList("script.js"),
                Collections.emptyList(),
                ExtensionContentScript.RunAt.DOCUMENT_END,
                false,
                false);

        assertTrue(script.matchesUrl("https://google.com/search?q=test"));
        assertTrue(script.matchesUrl("http://example.org/path/to/page.html"));
        // Internal chrome schemes should not match
        assertFalse(script.matchesUrl("chrome://settings"));
        assertFalse(script.matchesUrl("chrome-extension://xyz/popup.html"));
    }

    @Test
    public void testUrlMatchingSpecificDomain() {
        ExtensionContentScript script = new ExtensionContentScript(
                Arrays.asList("*://*.github.com/*", "https://reddit.com/*"),
                Collections.singletonList("script.js"),
                Collections.emptyList(),
                ExtensionContentScript.RunAt.DOCUMENT_IDLE,
                false,
                false);

        assertTrue(script.matchesUrl("https://github.com/chromium/chromium"));
        assertTrue(script.matchesUrl("https://api.github.com/repos"));
        assertTrue(script.matchesUrl("https://reddit.com/r/android"));
        assertFalse(script.matchesUrl("https://youtube.com/watch"));
    }
}
