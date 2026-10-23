package com.hexaro.office;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.libreoffice.androidlib.LOActivity;

import java.io.File;
import java.util.List;

public class FileListActivity extends AppCompatActivity {

    private RecyclerView rvRecents;
    private LinearLayout llEmptyState;
    private RecentAdapter adapter;
    private TextView btnClearRecents;

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        handleIncomingUri(uri);
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_list);

        initViews();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRecents();
    }

    private void initViews() {
        View cardOpen = findViewById(R.id.card_open_document);
        cardOpen.setOnClickListener(v -> openSystemFilePicker());

        btnClearRecents = findViewById(R.id.btn_clear_recents);
        btnClearRecents.setOnClickListener(v -> {
            RecentFileManager.clearRecents(this);
            refreshRecents();
        });

        llEmptyState = findViewById(R.id.ll_empty_state);
        rvRecents = findViewById(R.id.rv_recents);
        rvRecents.setLayoutManager(new LinearLayoutManager(this));

        adapter = new RecentAdapter(item -> {
            File file = new File(item.path);
            if (file.exists()) {
                openEditor(item.path, item.name);
            } else {
                Toast.makeText(this, R.string.file_not_found, Toast.LENGTH_SHORT).show();
                refreshRecents();
            }
        });
        rvRecents.setAdapter(adapter);
    }

    private void refreshRecents() {
        List<RecentFileManager.Item> recents = RecentFileManager.getRecents(this);
        if (recents.isEmpty()) {
            llEmptyState.setVisibility(View.VISIBLE);
            rvRecents.setVisibility(View.GONE);
            btnClearRecents.setVisibility(View.GONE);
        } else {
            llEmptyState.setVisibility(View.GONE);
            rvRecents.setVisibility(View.VISIBLE);
            btnClearRecents.setVisibility(View.VISIBLE);
            adapter.setData(recents);
        }
    }

    private void openSystemFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.oasis.opendocument.text",
                "application/vnd.oasis.opendocument.spreadsheet",
                "application/vnd.oasis.opendocument.presentation",
                "text/plain",
                "text/csv"
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        filePickerLauncher.launch(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_EDIT.equals(action)) {
            Uri uri = intent.getData();
            if (uri != null) {
                handleIncomingUri(uri);
            }
        } else if (Intent.ACTION_SEND.equals(action)) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) {
                handleIncomingUri(uri);
            }
        }
    }

    private void handleIncomingUri(Uri uri) {
        String fileName = FileUtils.getFileName(this, uri);
        String localPath = FileUtils.copyUriToInternalStorage(this, uri);
        if (localPath != null) {
            openEditor(localPath, fileName);
        } else {
            Toast.makeText(this, R.string.file_not_found, Toast.LENGTH_SHORT).show();
        }
    }

    private void openEditor(String filePath, String fileName) {
        RecentFileManager.addRecent(this, filePath, fileName);
        Intent intent = new Intent(this, LOActivity.class);
        intent.putExtra("filePath", filePath);
        startActivity(intent);
    }
}
