package com.hexaro.office;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
            if ("file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null) {
                File directFile = new File(uri.getPath());
                if (directFile.exists() && directFile.canRead()) {
                    return directFile.getAbsolutePath();
                }
            }

            ContentResolver resolver = context.getContentResolver();
            String fileName = getFileName(context, uri);
            
            File cacheFolder = new File(context.getFilesDir(), "opened_docs");
            if (!cacheFolder.exists()) {
                cacheFolder.mkdirs();
            }

            File destFile = new File(cacheFolder, fileName);
            try (InputStream in = resolver.openInputStream(uri);
                 OutputStream out = new FileOutputStream(destFile)) {
                if (in == null) return null;
                byte[] buffer = new byte[65536];
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

    public static String createNewDocument(Context context, String format) {
        try {
            String assetName;
            String prefix;
            String ext;

            switch (format.toLowerCase()) {
                case "sheet":
                case "xlsx":
                case "ods":
                    assetName = "templates/untitled.ods";
                    prefix = "Spreadsheet";
                    ext = ".ods";
                    break;
                case "presentation":
                case "pptx":
                case "odp":
                    assetName = "templates/untitled.odp";
                    prefix = "Presentation";
                    ext = ".odp";
                    break;
                case "doc":
                case "docx":
                case "odt":
                default:
                    assetName = "templates/untitled.odt";
                    prefix = "Document";
                    ext = ".odt";
                    break;
            }

            File folder = new File(context.getFilesDir(), "my_documents");
            if (!folder.exists()) {
                folder.mkdirs();
            }

            String dateSuffix = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String newFileName = prefix + "_" + dateSuffix + ext;
            File dest = new File(folder, newFileName);

            AssetManager am = context.getAssets();
            try (InputStream in = am.open(assetName);
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[4096];
                int r;
                while ((r = in.read(buf)) != -1) {
                    out.write(buf, 0, r);
                }
                out.flush();
            }

            return dest.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Error creating new document template", e);
            return null;
        }
    }

    public static void shareDocument(Context context, String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) return;

            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(intent, "Share " + file.getName()));
        } catch (Exception e) {
            Log.e(TAG, "Error sharing file", e);
        }
    }
}
