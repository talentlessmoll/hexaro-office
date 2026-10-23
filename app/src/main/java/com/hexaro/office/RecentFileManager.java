package com.hexaro.office;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RecentFileManager {
    private static final String PREF_NAME = "hexaro_recents_pref";
    private static final String KEY_RECENTS = "recent_documents_list";
    private static final int MAX_RECENTS = 50;

    public static class Item {
        public String name;
        public String path;
        public long lastOpened;

        public Item(String name, String path, long lastOpened) {
            this.name = name;
            this.path = path;
            this.lastOpened = lastOpened;
        }
    }

    public static List<Item> getRecents(Context context) {
        List<Item> list = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String jsonStr = prefs.getString(KEY_RECENTS, "[]");
        try {
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String path = obj.optString("path");
                File f = new File(path);
                if (f.exists()) {
                    list.add(new Item(obj.optString("name", f.getName()), path, obj.optLong("time", System.currentTimeMillis())));
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static void addRecent(Context context, String path, String name) {
        if (path == null) return;
        List<Item> current = getRecents(context);
        
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i).path.equals(path)) {
                current.remove(i);
                break;
            }
        }

        current.add(0, new Item(name, path, System.currentTimeMillis()));

        while (current.size() > MAX_RECENTS) {
            current.remove(current.size() - 1);
        }

        saveRecents(context, current);
    }

    public static void removeRecent(Context context, String path) {
        if (path == null) return;
        List<Item> current = getRecents(context);
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i).path.equals(path)) {
                current.remove(i);
                break;
            }
        }
        saveRecents(context, current);
    }

    public static void clearRecents(Context context) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();
        editor.remove(KEY_RECENTS);
        editor.apply();
    }

    private static void saveRecents(Context context, List<Item> items) {
        try {
            JSONArray arr = new JSONArray();
            for (Item item : items) {
                JSONObject obj = new JSONObject();
                obj.put("name", item.name);
                obj.put("path", item.path);
                obj.put("time", item.lastOpened);
                arr.put(obj);
            }
            SharedPreferences.Editor editor = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();
            editor.putString(KEY_RECENTS, arr.toString());
            editor.apply();
        } catch (Exception ignored) {}
    }
}
