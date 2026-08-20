package org.chromium.chrome.browser.extensions.installer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts Chrome CRX extension packages (CRX2, CRX3, and ZIP).
 */
public class CrxExtractor {

    public static void extractCrxOrZip(File packageFile, File targetDir) throws Exception {
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        try (InputStream rawIs = new FileInputStream(packageFile);
             BufferedInputStream bis = new BufferedInputStream(rawIs)) {

            byte[] magic = new byte[4];
            bis.mark(16);
            int read = bis.read(magic);
            if (read < 4) {
                throw new IllegalArgumentException("File too small to be a CRX/ZIP package");
            }

            // Check if standard ZIP format (PK..)
            if (magic[0] == 0x50 && magic[1] == 0x4B && magic[2] == 0x03 && magic[3] == 0x04) {
                bis.reset();
                unzipStream(bis, targetDir);
                return;
            }

            // Check CRX Magic "Cr24" (0x43, 0x72, 0x32, 0x34)
            if (magic[0] == 0x43 && magic[1] == 0x72 && magic[2] == 0x32 && magic[3] == 0x34) {
                byte[] verBytes = new byte[4];
                bis.read(verBytes);
                int version = ByteBuffer.wrap(verBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();

                if (version == 2) {
                    // CRX2: 4-byte pubkey len, 4-byte sig len
                    byte[] headerLenBytes = new byte[8];
                    bis.read(headerLenBytes);
                    ByteBuffer bb = ByteBuffer.wrap(headerLenBytes).order(ByteOrder.LITTLE_ENDIAN);
                    int pubKeyLen = bb.getInt();
                    int sigLen = bb.getInt();
                    long skipBytes = (long) pubKeyLen + (long) sigLen;
                    long skipped = 0;
                    while (skipped < skipBytes) {
                        skipped += bis.skip(skipBytes - skipped);
                    }
                } else if (version == 3) {
                    // CRX3: 4-byte header length followed by protobuf header
                    byte[] headerLenBytes = new byte[4];
                    bis.read(headerLenBytes);
                    int headerLen = ByteBuffer.wrap(headerLenBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
                    long skipped = 0;
                    while (skipped < headerLen) {
                        skipped += bis.skip(headerLen - skipped);
                    }
                } else {
                    throw new UnsupportedOperationException("Unsupported CRX version: " + version);
                }

                // The remainder of the stream is standard ZIP
                unzipStream(bis, targetDir);
                return;
            }

            // Fallback: Attempt standard unzip
            bis.reset();
            unzipStream(bis, targetDir);
        }
    }

    private static void unzipStream(InputStream is, File targetDir) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                File file = new File(targetDir, entry.getName());
                // Prevent Zip Slip vulnerability
                if (!file.getCanonicalPath().startsWith(targetDir.getCanonicalPath())) {
                    throw new SecurityException("Zip traversal attempt: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    File parent = file.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        int count;
                        while ((count = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, count);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }
}
