package com.hexaro.office;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.format.DateFormat;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecentAdapter extends RecyclerView.Adapter<RecentAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(RecentFileManager.Item item);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(RecentFileManager.Item item, View anchor);
    }

    private final List<RecentFileManager.Item> originalItems = new ArrayList<>();
    private final List<RecentFileManager.Item> displayedItems = new ArrayList<>();
    private final OnItemClickListener clickListener;
    private final OnItemLongClickListener longClickListener;

    private String currentCategory = "ALL";
    private String currentQuery = "";

    public RecentAdapter(OnItemClickListener clickListener, OnItemLongClickListener longClickListener) {
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    public void setData(List<RecentFileManager.Item> newItems) {
        originalItems.clear();
        if (newItems != null) {
            originalItems.addAll(newItems);
        }
        applyFilter();
    }

    public void setFilter(String category, String query) {
        this.currentCategory = category != null ? category.toUpperCase(Locale.ROOT) : "ALL";
        this.currentQuery = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
        applyFilter();
    }

    private void applyFilter() {
        displayedItems.clear();
        for (RecentFileManager.Item item : originalItems) {
            if (!matchesQuery(item.name, currentQuery)) {
                continue;
            }
            if (matchesCategory(item.name, currentCategory)) {
                displayedItems.add(item);
            }
        }
        notifyDataSetChanged();
    }

    private boolean matchesQuery(String name, String q) {
        return q.isEmpty() || name.toLowerCase(Locale.ROOT).contains(q);
    }

    private boolean matchesCategory(String name, String cat) {
        if ("ALL".equals(cat)) return true;
        String ext = getExtension(name);
        switch (cat) {
            case "WORD":
                return ext.equals("doc") || ext.equals("docx") || ext.equals("odt") || ext.equals("rtf");
            case "EXCEL":
                return ext.equals("xls") || ext.equals("xlsx") || ext.equals("ods") || ext.equals("csv");
            case "PPT":
                return ext.equals("ppt") || ext.equals("pptx") || ext.equals("odp");
            case "PDF":
                return ext.equals("pdf");
            default:
                return true;
        }
    }

    private static String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot != -1 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recent_document, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RecentFileManager.Item item = displayedItems.get(position);
        holder.bind(item, clickListener, longClickListener);
    }

    @Override
    public int getItemCount() {
        return displayedItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvBadge;
        private final TextView tvFileName;
        private final TextView tvFileInfo;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBadge = itemView.findViewById(R.id.tv_badge);
            tvFileName = itemView.findViewById(R.id.tv_file_name);
            tvFileInfo = itemView.findViewById(R.id.tv_file_info);
        }

        public void bind(RecentFileManager.Item item, OnItemClickListener click, OnItemLongClickListener longClick) {
            tvFileName.setText(item.name);
            
            String ext = getExtension(item.name);
            String badgeText = "FILE";
            int badgeColor = Color.parseColor("#607D8B");

            if (ext.equals("doc") || ext.equals("docx") || ext.equals("odt") || ext.equals("rtf")) {
                badgeText = "DOC";
                badgeColor = Color.parseColor("#1A73E8");
            } else if (ext.equals("xls") || ext.equals("xlsx") || ext.equals("ods") || ext.equals("csv")) {
                badgeText = "XLS";
                badgeColor = Color.parseColor("#1E8E3E");
            } else if (ext.equals("ppt") || ext.equals("pptx") || ext.equals("odp")) {
                badgeText = "PPT";
                badgeColor = Color.parseColor("#E8710A");
            } else if (ext.equals("pdf")) {
                badgeText = "PDF";
                badgeColor = Color.parseColor("#D93025");
            } else if (ext.equals("txt")) {
                badgeText = "TXT";
                badgeColor = Color.parseColor("#5F6368");
            }

            tvBadge.setText(badgeText);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.RECTANGLE);
            shape.setCornerRadius(16f);
            shape.setColor(badgeColor);
            tvBadge.setBackground(shape);

            File f = new File(item.path);
            String sizeStr = f.exists() ? Formatter.formatFileSize(itemView.getContext(), f.length()) : "";
            CharSequence dateStr = DateFormat.format("MMM d, yyyy", item.lastOpened);

            if (!sizeStr.isEmpty()) {
                tvFileInfo.setText(sizeStr + " • " + dateStr);
            } else {
                tvFileInfo.setText(dateStr);
            }

            itemView.setOnClickListener(v -> {
                if (click != null) click.onItemClick(item);
            });

            itemView.setOnLongClickListener(v -> {
                if (longClick != null) {
                    longClick.onItemLongClick(item, itemView);
                    return true;
                }
                return false;
            });
        }
    }
}
