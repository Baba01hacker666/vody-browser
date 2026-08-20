package org.chromium.chrome.browser.extensions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.chromium.chrome.browser.extensions.installer.CrxExtractor;
import org.chromium.chrome.browser.extensions.installer.ExtensionInstaller;
import org.chromium.chrome.browser.extensions.manifest.ExtensionManifestParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Unit tests for {@link CrxExtractor} and {@link ExtensionInstaller}.
 */
@RunWith(RobolectricTestRunner.class)
public class CrxExtractorTest {

    @Rule
    public TemporaryFolder mTempFolder = new TemporaryFolder();

    @Test
    public void testChrome32CharIdGeneration() {
        String id1 = ExtensionManifestParser.generateExtensionId("uBlock Origin");
        assertNotNull(id1);
        assertEquals(32, id1.length());
        assertTrue(id1.matches("^[a-p]{32}$"));

        String id2 = ExtensionManifestParser.generateExtensionId("Dark Reader");
        assertEquals(32, id2.length());
        assertTrue(id2.matches("^[a-p]{32}$"));
    }

    @Test
    public void testCwsUrlExtraction() {
        String url1 = "https://chromewebstore.google.com/detail/ublock-origin/cjpalhdlnbpafiamejdnhcphjbkeiagm";
        assertEquals("cjpalhdlnbpafiamejdnhcphjbkeiagm", ExtensionInstaller.extractExtensionId(url1));

        String url2 = "https://chrome.google.com/webstore/detail/eimadpbcbfnmbkopoojfekhnkhdbieeh";
        assertEquals("eimadpbcbfnmbkopoojfekhnkhdbieeh", ExtensionInstaller.extractExtensionId(url2));

        String directId = "cjpalhdlnbpafiamejdnhcphjbkeiagm";
        assertEquals("cjpalhdlnbpafiamejdnhcphjbkeiagm", ExtensionInstaller.extractExtensionId(directId));
    }

    @Test
    public void testExtractZipPackage() throws Exception {
        File zipFile = mTempFolder.newFile("test_package.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write("{\"name\":\"Test\",\"version\":\"1.0\",\"manifest_version\":2}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("content.js"));
            zos.write("console.log('hello');".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        File targetDir = mTempFolder.newFolder("extracted");
        CrxExtractor.extractCrxOrZip(zipFile, targetDir);

        assertTrue(new File(targetDir, "manifest.json").exists());
        assertTrue(new File(targetDir, "content.js").exists());
    }

    private static void assertNotNull(Object o) {
        assertTrue(o != null);
    }
}
