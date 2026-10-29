package br.edu.utfpr.inventariomaquinas.ui.maquina;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.core.content.ContextCompat;

import br.edu.utfpr.inventariomaquinas.R;
import br.edu.utfpr.inventariomaquinas.data.local.entity.MaquinaEntity;
import br.edu.utfpr.inventariomaquinas.data.repository.MaquinaRepository;
import br.edu.utfpr.inventariomaquinas.databinding.ActivityMaquinaListBinding;
import br.edu.utfpr.inventariomaquinas.ui.adapter.MaquinaAdapter;
import br.edu.utfpr.inventariomaquinas.ui.viewmodel.MaquinaViewModel;
import br.edu.utfpr.inventariomaquinas.service.DownloadDataService;
import br.edu.utfpr.inventariomaquinas.ui.sync.ReconcileActivity;
import br.edu.utfpr.inventariomaquinas.ui.sync.MachineReconcileActivity;


public class MaquinaListActivity extends AppCompatActivity {

    private ActivityMaquinaListBinding binding;
    private MaquinaViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMaquinaListBinding.inflate(
                getLayoutInflater()
        );
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        ViewCompat.setOnApplyWindowInsetsListener(
                binding.root,
                (view, insets) -> {
                    Insets bars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                    );

                    view.setPadding(
                            bars.left, bars.top, bars.right, bars.bottom
                    );

                    return insets;
                }
        );

        ViewCompat.requestApplyInsets(binding.root);

        viewModel = new ViewModelProvider(this)
                .get(MaquinaViewModel.class);

        MaquinaAdapter adapter =
                new MaquinaAdapter(this::showItemMenu);

        binding.recyclerMaquinas.setLayoutManager(
                new LinearLayoutManager(this)
        );
        binding.recyclerMaquinas.setAdapter(adapter);

        binding.toolbar.inflateMenu(R.menu.menu_maquina_list);

        binding.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();

            if (id == R.id.action_add) {
                if (!isSaving()) {
                    openForm(0);
                }

                return true;
            }

            if (id == R.id.action_filter) {
                if (!isSaving()
                        && getSupportFragmentManager()
                        .findFragmentByTag(
                                MaquinaFilterDialog.TAG
                        ) == null) {

                    new MaquinaFilterDialog().show(
                            getSupportFragmentManager(),
                            MaquinaFilterDialog.TAG
                    );
                }

                return true;
            }

            if (id == R.id.action_clear_filters) {
                viewModel.clearFilters();
                return true;
            }

            if (id == R.id.action_download_data
                    || id == R.id.action_sync_catalogs) {

                if (!isSaving()) {
                    try {
                        Intent intent = new Intent(
                                MaquinaListActivity.this,
                                DownloadDataService.class
                        );

                        if (id == R.id.action_sync_catalogs) {
                            intent.setAction(
                                    DownloadDataService
                                            .ACTION_SYNC_CATALOGS
                            );
                        }

                        ContextCompat.startForegroundService(
                                MaquinaListActivity.this,
                                intent
                        );

                    } catch (RuntimeException exception) {
                        Toast.makeText(
                                MaquinaListActivity.this,
                                R.string.download_start_error,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }

                return true;
            }
            if (id == R.id.action_reconcile) {
                if (!isSaving()) {
                    startActivity(new Intent(
                            MaquinaListActivity.this,
                            ReconcileActivity.class
                    ));
                }

                return true;
            }
            if (id == R.id.action_reconcile_machines) {
                if (!isSaving()) {
                    startActivity(new Intent(
                            MaquinaListActivity.this,
                            MachineReconcileActivity.class
                    ));
                }

                return true;
            }

            return false;
        });

        viewModel.getTipos().observe(
                this,
                adapter::submitTipos
        );

        viewModel.getMarcas().observe(
                this,
                adapter::submitMarcas
        );

        viewModel.getMaquinas().observe(this, maquinas -> {
            adapter.submitList(maquinas);

            boolean empty =
                    maquinas == null || maquinas.isEmpty();

            binding.textEmpty.setVisibility(
                    empty ? View.VISIBLE : View.GONE
            );

            binding.recyclerMaquinas.setVisibility(
                    empty ? View.GONE : View.VISIBLE
            );
        });

        viewModel.getSaving().observe(this, saving -> {
            binding.toolbar.getMenu()
                    .findItem(R.id.action_add)
                    .setEnabled(!Boolean.TRUE.equals(saving));
        });

        viewModel.getOperationSucceeded().observe(this, success -> {
            if (Boolean.TRUE.equals(success)) {
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
                int message =
                        error == MaquinaRepository.Error.NOT_FOUND
                                ? R.string.error_not_found
                                : R.string.error_database;

                Toast.makeText(
                        this,
                        message,
                        Toast.LENGTH_LONG
                ).show();

                viewModel.clearOperationResult();
            }
        });
    }

    private boolean isSaving() {
        return Boolean.TRUE.equals(
                viewModel.getSaving().getValue()
        );
    }

    private void openForm(long localId) {
        Intent intent = new Intent(
                this,
                MaquinaFormActivity.class
        );

        intent.putExtra(
                MaquinaFormActivity.EXTRA_LOCAL_ID,
                localId
        );

        startActivity(intent);
    }

    private void showItemMenu(MaquinaEntity maquina) {
        if (isSaving()) {
            return;
        }

        PopupMenu popup = new PopupMenu(
                this,
                binding.recyclerMaquinas
        );

        popup.inflate(R.menu.menu_maquina_item);

        popup.setOnMenuItemClickListener(item -> {
            if (isSaving()) {
                return true;
            }

            if (item.getItemId() == R.id.action_edit) {
                openForm(maquina.localId);
                return true;
            }

            if (item.getItemId() == R.id.action_delete) {
                confirmDelete(maquina);
                return true;
            }

            return false;
        });

        popup.show();
    }

    private void confirmDelete(MaquinaEntity maquina) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_delete_title)
                .setMessage(
                        getString(
                                R.string.dialog_delete_message,
                                maquina.descricao
                        )
                )
                .setNegativeButton(
                        R.string.action_cancel,
                        null
                )
                .setPositiveButton(
                        R.string.action_delete,
                        (dialog, which) -> {
                            if (!isSaving()) {
                                viewModel.delete(maquina.localId);
                            }
                        }
                )
                .show();
    }
}