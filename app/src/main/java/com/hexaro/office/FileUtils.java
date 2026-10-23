package com.hexaro.office;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class FileUtils {
    private static final String TAG = "HexaroFileUtils";

    public static String getFileName(Context context, Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting file name from content URI", e);
            }
        }
        if (result == null) {
            String path = uri.getPath();
            if (path != null) {
                int cut = path.lastIndexOf('/');
                if (cut != -1) {
                    result = path.substring(cut + 1);
                }
            }
        }
        return result != null ? result : "document";
    }

    public static String copyUriToInternalStorage(Context context, Uri uri) {
        try {
            ContentResolver resolver = context.getContentResolver();
            String fileName = getFileName(context, uri);
            
            File cacheFolder = new File(context.getFilesDir(), "opened_docs");
            if (!cacheFolder.exists()) {
                cacheFolder.mkdirs();
            }

            File destFile = new File(cacheFolder, fileName);
            // If file already exists with same name and non-zero size, we can overwrite or reuse
            try (InputStream in = resolver.openInputStream(uri);
                 OutputStream out = new FileOutputStream(destFile)) {
                if (in == null) return null;
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            }
            return destFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Failed to copy URI to internal storage", e);
            return null;
        }
    }
}
