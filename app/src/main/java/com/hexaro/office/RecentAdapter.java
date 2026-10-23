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

public class RecentAdapter extends RecyclerView.Adapter<RecentAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(RecentFileManager.Item item);
    }

    private final List<RecentFileManager.Item> items = new ArrayList<>();
    private final OnItemClickListener listener;

    public RecentAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<RecentFileManager.Item> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recent_document, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RecentFileManager.Item item = items.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
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

        public void bind(RecentFileManager.Item item, OnItemClickListener listener) {
            tvFileName.setText(item.name);
            
            String ext = "";
            int dot = item.name.lastIndexOf('.');
            if (dot != -1) {
                ext = item.name.substring(dot + 1).toLowerCase();
            }

            String badgeText = "FILE";
            int badgeColor = Color.parseColor("#607D8B");

            if (ext.equals("doc") || ext.equals("docx") || ext.equals("odt") || ext.equals("rtf")) {
                badgeText = "DOC";
                badgeColor = Color.parseColor("#2B579A");
            } else if (ext.equals("xls") || ext.equals("xlsx") || ext.equals("ods") || ext.equals("csv")) {
                badgeText = "XLS";
                badgeColor = Color.parseColor("#217346");
            } else if (ext.equals("ppt") || ext.equals("pptx") || ext.equals("odp")) {
                badgeText = "PPT";
                badgeColor = Color.parseColor("#D24726");
            } else if (ext.equals("pdf")) {
                badgeText = "PDF";
                badgeColor = Color.parseColor("#D32F2F");
            } else if (ext.equals("txt")) {
                badgeText = "TXT";
                badgeColor = Color.parseColor("#546E7A");
            }

            tvBadge.setText(badgeText);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.RECTANGLE);
            shape.setCornerRadius(14f);
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
                if (listener != null) listener.onItemClick(item);
            });
        }
    }
}
