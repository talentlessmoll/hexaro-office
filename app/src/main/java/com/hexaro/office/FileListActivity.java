package com.hexaro.office;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.libreoffice.androidlib.LOActivity;

import java.io.File;
import java.util.List;

public class FileListActivity extends AppCompatActivity {

    private RecyclerView rvRecents;
    private LinearLayout llEmptyState;
    private RecentAdapter adapter;
    private TextView btnClearRecents;
    private EditText etSearch;
    private ChipGroup chipGroupFilter;

    private String currentCategory = "ALL";
    private String currentSearchText = "";

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
        findViewById(R.id.card_open_document).setOnClickListener(v -> openSystemFilePicker());
        findViewById(R.id.card_new_document).setOnClickListener(v -> showNewDocumentDialog());

        FloatingActionButton fabNew = findViewById(R.id.fab_new);
        fabNew.setOnClickListener(v -> showNewDocumentDialog());

        btnClearRecents = findViewById(R.id.btn_clear_recents);
        btnClearRecents.setOnClickListener(v -> confirmClearRecents());

        llEmptyState = findViewById(R.id.ll_empty_state);
        rvRecents = findViewById(R.id.rv_recents);
        rvRecents.setLayoutManager(new LinearLayoutManager(this));

        adapter = new RecentAdapter(
                item -> {
                    File file = new File(item.path);
                    if (file.exists()) {
                        openEditor(item.path, item.name);
                    } else {
                        Toast.makeText(this, R.string.file_not_found, Toast.LENGTH_SHORT).show();
                        refreshRecents();
                    }
                },
                (item, anchor) -> showDocumentOptionsDialog(item)
        );
        rvRecents.setAdapter(adapter);

        // Search text watcher
        etSearch = findViewById(R.id.et_search);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchText = s != null ? s.toString() : "";
                adapter.setFilter(currentCategory, currentSearchText);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Filter chips
        chipGroupFilter = findViewById(R.id.chip_group_filter);
        chipGroupFilter.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chip_word) {
                currentCategory = "WORD";
            } else if (checkedId == R.id.chip_excel) {
                currentCategory = "EXCEL";
            } else if (checkedId == R.id.chip_ppt) {
                currentCategory = "PPT";
            } else if (checkedId == R.id.chip_pdf) {
                currentCategory = "PDF";
            } else {
                currentCategory = "ALL";
            }
            adapter.setFilter(currentCategory, currentSearchText);
        });
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
            adapter.setFilter(currentCategory, currentSearchText);
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

    private void showNewDocumentDialog() {
        String[] options = {
                "📄  Word Document (.odt / .docx)",
                "📊  Excel Spreadsheet (.ods / .xlsx)",
                "📽️  PowerPoint Presentation (.odp / .pptx)"
        };

        new MaterialAlertDialogBuilder(this)
                .setTitle("Create New Document")
                .setItems(options, (dialog, which) -> {
                    String format = "doc";
                    if (which == 1) format = "sheet";
                    else if (which == 2) format = "presentation";

                    String newFilePath = FileUtils.createNewDocument(this, format);
                    if (newFilePath != null) {
                        File f = new File(newFilePath);
                        openEditor(newFilePath, f.getName());
                    } else {
                        Toast.makeText(this, "Could not create document template", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void showDocumentOptionsDialog(RecentFileManager.Item item) {
        String[] actions = {"Open", "Share File", "Remove from Recents", "Delete File"};

        new MaterialAlertDialogBuilder(this)
                .setTitle(item.name)
                .setItems(actions, (dialog, which) -> {
                    switch (which) {
                        case 0: // Open
                            openEditor(item.path, item.name);
                            break;
                        case 1: // Share
                            FileUtils.shareDocument(this, item.path);
                            break;
                        case 2: // Remove from recents
                            RecentFileManager.removeRecent(this, item.path);
                            refreshRecents();
                            break;
                        case 3: // Delete
                            confirmDeleteFile(item);
                            break;
                    }
                })
                .show();
    }

    private void confirmDeleteFile(RecentFileManager.Item item) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Document?")
                .setMessage("Are you sure you want to permanently delete \"" + item.name + "\" from your device?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    File file = new File(item.path);
                    if (file.exists()) {
                        file.delete();
                    }
                    RecentFileManager.removeRecent(this, item.path);
                    refreshRecents();
                    Toast.makeText(this, "File deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClearRecents() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Clear History")
                .setMessage("Clear all recent documents from history? (Files on disk will remain untouched)")
                .setPositiveButton("Clear", (dialog, which) -> {
                    RecentFileManager.clearRecents(this);
                    refreshRecents();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_EDIT.equals(action)) {
            Uri uri = intent.getData();
            if (uri != null) {
                // Clear the intent action so subsequent resumes or task switches never re-open
                setIntent(new Intent(this, FileListActivity.class).setAction(Intent.ACTION_MAIN));
                handleIncomingUri(uri);
            }
        } else if (Intent.ACTION_SEND.equals(action)) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) {
                setIntent(new Intent(this, FileListActivity.class).setAction(Intent.ACTION_MAIN));
                handleIncomingUri(uri);
            }
        }
    }

    private void handleIncomingUri(Uri uri) {
        // Fast direct path if it's already a local file scheme
        if ("file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null && new File(uri.getPath()).exists()) {
            openEditor(uri.getPath(), new File(uri.getPath()).getName());
            return;
        }

        AlertDialog loadingDialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Opening Document")
                .setMessage("Loading file...")
                .setCancelable(false)
                .show();

        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            String fileName = FileUtils.getFileName(this, uri);
            String localPath = FileUtils.copyUriToInternalStorage(this, uri);
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    loadingDialog.dismiss();
                }
                if (localPath != null) {
                    openEditor(localPath, fileName);
                } else {
                    Toast.makeText(this, R.string.file_not_found, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void openEditor(String filePath, String fileName) {
        RecentFileManager.addRecent(this, filePath, fileName);
        Intent intent = new Intent(this, LOActivity.class);
        intent.putExtra("filePath", filePath);
        startActivity(intent);
    }
}
