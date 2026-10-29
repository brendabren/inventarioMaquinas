package br.edu.utfpr.inventariomaquinas.ui.tipo;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import br.edu.utfpr.inventariomaquinas.R;
import br.edu.utfpr.inventariomaquinas.data.local.entity.TipoEntity;
import br.edu.utfpr.inventariomaquinas.data.repository.TipoRepository;
import br.edu.utfpr.inventariomaquinas.databinding.ActivityTipoListBinding;
import br.edu.utfpr.inventariomaquinas.ui.adapter.TipoAdapter;
import br.edu.utfpr.inventariomaquinas.ui.viewmodel.TipoViewModel;

public class TipoListActivity extends AppCompatActivity {

    private ActivityTipoListBinding binding;
    private TipoViewModel viewModel;
    private AlertDialog formDialog;
    private EditText formInput;
    private long editingLocalId;
    private boolean formOpen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityTipoListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        ViewCompat.setOnApplyWindowInsetsListener(
                binding.root,
                (view, windowInsets) -> {
                    Insets bars = windowInsets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                                    | WindowInsetsCompat.Type.ime()
                    );

                    view.setPadding(
                            bars.left,
                            bars.top,
                            bars.right,
                            bars.bottom
                    );

                    return windowInsets;
                }
        );

        ViewCompat.requestApplyInsets(binding.root);

        viewModel = new ViewModelProvider(this)
                .get(TipoViewModel.class);

        TipoAdapter adapter = new TipoAdapter(this::showItemMenu);

        binding.recyclerTipos.setLayoutManager(
                new LinearLayoutManager(this)
        );

        binding.recyclerTipos.setAdapter(adapter);

        binding.toolbar.inflateMenu(R.menu.menu_tipo_list);

        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_add) {
                if (!isSaving()) {
                    showForm(null);
                }
                return true;
            }

            return false;
        });

        if (savedInstanceState != null
                && savedInstanceState.getBoolean("formOpen", false)) {
            TipoEntity draft = new TipoEntity();
            draft.localId = savedInstanceState.getLong("editingLocalId", 0);
            draft.descricao = savedInstanceState.getString("formText", "");
            showForm(draft);
        }

        viewModel.getTipos().observe(this, tipos -> {
            adapter.submitList(tipos);

            boolean empty = tipos == null || tipos.isEmpty();

            binding.textEmpty.setVisibility(
                    empty ? View.VISIBLE : View.GONE
            );

            binding.recyclerTipos.setVisibility(
                    empty ? View.GONE : View.VISIBLE
            );
        });

        viewModel.getSaving().observe(this, saving -> {
            boolean busy = Boolean.TRUE.equals(saving);

            binding.toolbar.getMenu()
                    .findItem(R.id.action_add)
                    .setEnabled(!busy);

            if (formDialog != null && formDialog.isShowing()) {
                formDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setEnabled(!busy);

                formDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                        .setEnabled(!busy);

                formDialog.setCancelable(!busy);
                formDialog.setCanceledOnTouchOutside(!busy);
                if (formInput != null) {
                    formInput.setEnabled(!busy);
                }
            }
        });

        viewModel.getOperationSucceeded().observe(this, succeeded -> {
            if (Boolean.TRUE.equals(succeeded)) {
                if (formDialog != null) {
                    formDialog.dismiss();
                }

                Toast.makeText(
                        this,
                        R.string.message_operation_success,
                        Toast.LENGTH_SHORT
                ).show();

                viewModel.clearOperationResult();
            }
        });

        viewModel.getError().observe(this, error -> {
            if (error != null) {
                Toast.makeText(
                        this,
                        getErrorMessage(error),
                        Toast.LENGTH_LONG
                ).show();

                viewModel.clearOperationResult();
            }
        });
    }

    private boolean isSaving() {
        return Boolean.TRUE.equals(viewModel.getSaving().getValue());
    }

    private void showItemMenu(TipoEntity tipo) {
        if (isSaving()) {
            return;
        }

        PopupMenu popup = new PopupMenu(
                this,
                binding.recyclerTipos
        );

        popup.inflate(R.menu.menu_tipo_item);

        popup.setOnMenuItemClickListener(item -> {
            if (isSaving()) {
                return true;
            }

            if (item.getItemId() == R.id.action_edit) {
                showForm(tipo);
                return true;
            }

            if (item.getItemId() == R.id.action_delete) {
                confirmDelete(tipo);
                return true;
            }

            return false;
        });

        popup.show();
    }

    private void showForm(TipoEntity tipo) {
        if (formDialog != null && formDialog.isShowing()) {
            return;
        }
        editingLocalId = tipo == null ? 0 : tipo.localId;
        boolean editing = editingLocalId != 0;

        EditText input = new EditText(this);
        formInput = input;
        input.setHint(R.string.label_description);
        input.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );
        input.setSingleLine(true);

        if (tipo != null) {
            input.setText(tipo.descricao);
            input.setSelection(input.length());
        }

        int padding = Math.round(
                24 * getResources().getDisplayMetrics().density
        );

        FrameLayout container = new FrameLayout(this);
        container.setPadding(padding, padding / 2, padding, 0);

        container.addView(
                input,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                )
        );

        formDialog = new AlertDialog.Builder(this)
                .setTitle(
                        editing
                                ? R.string.title_edit_type
                                : R.string.title_add_type
                )
                .setView(container)
                .setPositiveButton(R.string.action_save, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();

        formDialog.setOnShowListener(dialog -> {
            formDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> {
                        String descricao =
                                input.getText().toString().trim();

                        if (descricao.isEmpty()) {
                            input.setError(
                                    getString(
                                            R.string.error_empty_description
                                    )
                            );
                            return;
                        }

                        viewModel.save(
                                editingLocalId,
                                descricao
                        );
                    });
        });

        formDialog.setOnDismissListener(dialog -> formOpen = false);
        formOpen = true;
        formDialog.show();
        boolean busy = isSaving();
        input.setEnabled(!busy);
        formDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!busy);
        formDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(!busy);
        formDialog.setCancelable(!busy);
        formDialog.setCanceledOnTouchOutside(!busy);
    }

    private void confirmDelete(TipoEntity tipo) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_title)
                .setMessage(
                        getString(
                                R.string.dialog_delete_message,
                                tipo.descricao
                        )
                )
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(
                        R.string.action_delete,
                        (dialog, which) ->
                                viewModel.delete(tipo.localId)
                )
                .show();
    }

    private int getErrorMessage(TipoRepository.Error error) {
        switch (error) {
            case EMPTY_DESCRIPTION:
                return R.string.error_empty_description;

            case NOT_FOUND:
                return R.string.error_not_found;

            case HAS_LINKED_MACHINES:
                return R.string.error_linked_machines;

            case HAS_PENDING_MACHINE_DELETIONS:
                return R.string.error_pending_machine_deletions;

            case DATABASE_ERROR:
            default:
                return R.string.error_database;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("formOpen", formOpen);
        outState.putLong("editingLocalId", editingLocalId);
        if (formOpen && formInput != null) {
            outState.putString("formText", formInput.getText().toString());
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (formDialog != null) {
            formDialog.dismiss();
        }

        super.onDestroy();
    }
}

