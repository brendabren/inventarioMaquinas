package br.edu.utfpr.inventariomaquinas.ui.marca;

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
import br.edu.utfpr.inventariomaquinas.data.local.entity.MarcaEntity;
import br.edu.utfpr.inventariomaquinas.data.repository.MarcaRepository;
import br.edu.utfpr.inventariomaquinas.databinding.ActivityMarcaListBinding;
import br.edu.utfpr.inventariomaquinas.ui.adapter.MarcaAdapter;
import br.edu.utfpr.inventariomaquinas.ui.viewmodel.MarcaViewModel;

public class MarcaListActivity extends AppCompatActivity {

    private ActivityMarcaListBinding binding;
    private MarcaViewModel viewModel;
    private AlertDialog formDialog;

    private EditText formInput;
    private long editingLocalId;
    private boolean formOpen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMarcaListBinding.inflate(getLayoutInflater());
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
                .get(MarcaViewModel.class);

        MarcaAdapter adapter = new MarcaAdapter(this::showItemMenu);

        binding.recyclerMarcas.setLayoutManager(
                new LinearLayoutManager(this)
        );

        binding.recyclerMarcas.setAdapter(adapter);

        binding.toolbar.inflateMenu(R.menu.menu_marca_list);

        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_add) {
                if (!isSaving()) {
                    showForm(0, "");
                }
                return true;
            }

            return false;
        });

        if (savedInstanceState != null
                && savedInstanceState.getBoolean("formOpen", false)) {

            showForm(
                    savedInstanceState.getLong("editingLocalId", 0),
                    savedInstanceState.getString("formText", "")
            );
        }

        viewModel.getMarcas().observe(this, marcas -> {
            adapter.submitList(marcas);

            boolean empty = marcas == null || marcas.isEmpty();

            binding.textEmpty.setVisibility(
                    empty ? View.VISIBLE : View.GONE
            );

            binding.recyclerMarcas.setVisibility(
                    empty ? View.GONE : View.VISIBLE
            );
        });

        viewModel.getSaving().observe(this, saving -> {
            boolean busy = Boolean.TRUE.equals(saving);

            binding.toolbar.getMenu()
                    .findItem(R.id.action_add)
                    .setEnabled(!busy);

            updateFormBusyState();
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

    private void showItemMenu(MarcaEntity marca) {
        if (isSaving()) {
            return;
        }

        PopupMenu popup = new PopupMenu(
                this,
                binding.recyclerMarcas
        );

        popup.inflate(R.menu.menu_marca_item);

        popup.setOnMenuItemClickListener(item -> {
            if (isSaving()) {
                return true;
            }

            if (item.getItemId() == R.id.action_edit) {
                showForm(marca.localId, marca.nome);
                return true;
            }

            if (item.getItemId() == R.id.action_delete) {
                confirmDelete(marca);
                return true;
            }

            return false;
        });

        popup.show();
    }

    private void showForm(long localId, String nome) {
        if (formDialog != null && formDialog.isShowing()) {
            return;
        }

        editingLocalId = localId;

        formInput = new EditText(this);
        formInput.setHint(R.string.label_name);
        formInput.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_WORDS
        );
        formInput.setSingleLine(true);
        formInput.setText(nome);
        formInput.setSelection(formInput.length());

        int padding = Math.round(
                24 * getResources().getDisplayMetrics().density
        );

        FrameLayout container = new FrameLayout(this);
        container.setPadding(padding, padding / 2, padding, 0);

        container.addView(
                formInput,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                )
        );

        formDialog = new AlertDialog.Builder(this)
                .setTitle(
                        localId == 0
                                ? R.string.title_add_brand
                                : R.string.title_edit_brand
                )
                .setView(container)
                .setPositiveButton(R.string.action_save, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();

        formDialog.setOnShowListener(dialog -> {
            formDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> {
                        String value =
                                formInput.getText().toString().trim();

                        if (value.isEmpty()) {
                            formInput.setError(
                                    getString(R.string.error_empty_name)
                            );
                            return;
                        }

                        viewModel.save(editingLocalId, value);
                    });

            updateFormBusyState();
        });

        formDialog.setOnDismissListener(dialog -> formOpen = false);

        formOpen = true;
        formDialog.show();
    }

    private void updateFormBusyState() {
        if (formDialog == null || !formDialog.isShowing()) {
            return;
        }

        boolean busy = isSaving();

        formDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setEnabled(!busy);

        formDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setEnabled(!busy);

        formInput.setEnabled(!busy);
        formDialog.setCancelable(!busy);
        formDialog.setCanceledOnTouchOutside(!busy);
    }

    private void confirmDelete(MarcaEntity marca) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_title)
                .setMessage(
                        getString(
                                R.string.dialog_delete_message,
                                marca.nome
                        )
                )
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(
                        R.string.action_delete,
                        (dialog, which) -> {
                            if (!isSaving()) {
                                viewModel.delete(marca.localId);
                            }
                        }
                )
                .show();
    }

    private int getErrorMessage(MarcaRepository.Error error) {
        switch (error) {
            case EMPTY_NAME:
                return R.string.error_empty_name;

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
            outState.putString(
                    "formText",
                    formInput.getText().toString()
            );
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