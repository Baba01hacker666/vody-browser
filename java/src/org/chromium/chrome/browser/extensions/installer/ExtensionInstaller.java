package org.chromium.chrome.browser.extensions.installer;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.chromium.chrome.browser.extensions.manifest.ExtensionManifestParser;
import org.chromium.chrome.browser.extensions.model.Extension;
import org.chromium.chrome.browser.extensions.runtime.ExtensionManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles downloading, extracting, and installing extensions from files, folders, or the Chrome Web Store.
 */
public class ExtensionInstaller {

    private static final String CWS_DOWNLOAD_URL =
            "https://clients2.google.com/service/update2/crx?response=redirect&os=android&arch=arm64&os_arch=arm64&nacl_arch=arm64&prod=chromecrx&prodchannel=unknown&prodversion=135.0.0.0&acceptformat=crx2,crx3&x=id%3D";

    private static final Pattern CWS_ID_PATTERN = Pattern.compile("([a-z]{32})");

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    public interface InstallCallback {
        void onSuccess(Extension extension);
        void onError(String message);
    }

    public void installFromFile(Context context, File packageFile, InstallCallback callback) {
        mExecutor.execute(() -> {
            try {
                File tempDir = new File(context.getCacheDir(), "ext_unpack_" + System.currentTimeMillis());
                CrxExtractor.extractCrxOrZip(packageFile, tempDir);

                Extension extension = ExtensionManifestParser.parseManifest(tempDir, null);
                File finalDir = new File(ExtensionManager.getExtensionsStorageDir(context), extension.getId());

                if (finalDir.exists()) {
                    deleteRecursively(finalDir);
                }
                tempDir.renameTo(finalDir);

                Extension installedExt = ExtensionManifestParser.parseManifest(finalDir, extension.getId());
                ExtensionManager.getInstance(context).registerInstalledExtension(installedExt);

                mMainHandler.post(() -> callback.onSuccess(installedExt));
            } catch (Exception e) {
                mMainHandler.post(() -> callback.onError("Failed to install extension: " + e.getMessage()));
            }
        });
    }

    public void installFromUri(Context context, Uri uri, InstallCallback callback) {
        mExecutor.execute(() -> {
            try {
                File tempFile = File.createTempFile("ext_pkg", ".crx", context.getCacheDir());
                try (InputStream is = context.getContentResolver().openInputStream(uri);
                     FileOutputStream fos = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, count);
                    }
                }
                installFromFile(context, tempFile, callback);
            } catch (Exception e) {
                mMainHandler.post(() -> callback.onError("Failed to read file URI: " + e.getMessage()));
            }
        });
    }

    public void installFromWebStore(Context context, String queryOrId, InstallCallback callback) {
        mExecutor.execute(() -> {
            try {
                String extensionId = extractExtensionId(queryOrId);
                if (extensionId == null) {
                    throw new IllegalArgumentException("Could not extract a valid 32-character Chrome extension ID.");
                }

                String downloadUrl = CWS_DOWNLOAD_URL + extensionId + "%26uc";
                File tempCrx = new File(context.getCacheDir(), extensionId + ".crx");

                HttpURLConnection conn = (HttpURLConnection) new URL(downloadUrl).openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile) Chrome/135.0.0.0");
                conn.connect();

                if (conn.getResponseCode() >= 400) {
                    throw new IllegalStateException("Chrome Web Store returned HTTP " + conn.getResponseCode());
                }

                try (InputStream is = conn.getInputStream();
                     FileOutputStream fos = new FileOutputStream(tempCrx)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, count);
                    }
                }

                File finalDir = new File(ExtensionManager.getExtensionsStorageDir(context), extensionId);
                if (finalDir.exists()) {
                    deleteRecursively(finalDir);
                }
                CrxExtractor.extractCrxOrZip(tempCrx, finalDir);
                tempCrx.delete();

                Extension extension = ExtensionManifestParser.parseManifest(finalDir, extensionId);
                ExtensionManager.getInstance(context).registerInstalledExtension(extension);

                mMainHandler.post(() -> callback.onSuccess(extension));
            } catch (Exception e) {
                mMainHandler.post(() -> callback.onError("Web Store install failed: " + e.getMessage()));
            }
        });
    }

    public static String extractExtensionId(String input) {
        if (input == null) return null;
        Matcher matcher = CWS_ID_PATTERN.matcher(input.toLowerCase());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public static void deleteRecursively(File fileOrDir) {
        if (fileOrDir.isDirectory()) {
            File[] children = fileOrDir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        fileOrDir.delete();
    }
}
