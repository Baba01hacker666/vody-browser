package org.chromium.chrome.browser.extensions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.chromium.chrome.browser.extensions.manifest.ExtensionManifestParser;
import org.chromium.chrome.browser.extensions.model.Extension;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Unit tests for {@link ExtensionManifestParser}.
 */
@RunWith(RobolectricTestRunner.class)
public class ExtensionManifestParserTest {

    @Rule
    public TemporaryFolder mTempFolder = new TemporaryFolder();

    @Test
    public void testParseManifestV3() throws Exception {
        File dir = mTempFolder.newFolder("test_ext_v3");
        File manifest = new File(dir, "manifest.json");

        String manifestContent = "{\n"
                + "  \"manifest_version\": 3,\n"
                + "  \"name\": \"Vody Adblocker\",\n"
                + "  \"version\": \"1.2.0\",\n"
                + "  \"description\": \"Fast adblocker extension for Vody Browser\",\n"
                + "  \"permissions\": [\"storage\", \"declarativeNetRequest\"],\n"
                + "  \"action\": {\n"
                + "    \"default_title\": \"Vody Adblocker Popup\",\n"
                + "    \"default_popup\": \"popup.html\"\n"
                + "  },\n"
                + "  \"content_scripts\": [{\n"
                + "    \"matches\": [\"*://*.example.com/*\"],\n"
                + "    \"js\": [\"content.js\"],\n"
                + "    \"run_at\": \"document_idle\"\n"
                + "  }]\n"
                + "}";

        try (FileOutputStream fos = new FileOutputStream(manifest)) {
            fos.write(manifestContent.getBytes(StandardCharsets.UTF_8));
        }

        Extension ext = ExtensionManifestParser.parseManifest(dir, "test_vody_id");
        assertNotNull(ext);
        assertEquals("test_vody_id", ext.getId());
        assertEquals("Vody Adblocker", ext.getName());
        assertEquals("1.2.0", ext.getVersion());
        assertEquals(3, ext.getManifestVersion());
        assertTrue(ext.hasPermission("storage"));
        assertTrue(ext.hasPermission("declarativeNetRequest"));
        assertEquals("popup.html", ext.getAction().getDefaultPopup());
        assertEquals(1, ext.getContentScripts().size());
        assertTrue(ext.getContentScripts().get(0).matchesUrl("https://www.example.com/page"));
    }
}
