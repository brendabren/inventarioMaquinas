package br.edu.utfpr.inventariomaquinas.ui.maquina;

import android.app.Dialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import br.edu.utfpr.inventariomaquinas.R;
import br.edu.utfpr.inventariomaquinas.data.local.entity.MarcaEntity;
import br.edu.utfpr.inventariomaquinas.data.local.entity.TipoEntity;
import br.edu.utfpr.inventariomaquinas.databinding.DialogMaquinaFilterBinding;
import br.edu.utfpr.inventariomaquinas.ui.viewmodel.MaquinaViewModel;

public class MaquinaFilterDialog extends DialogFragment {

    public static final String TAG = "MaquinaFilterDialog";

    private DialogMaquinaFilterBinding binding;
    private MaquinaViewModel viewModel;

    private final List<TipoEntity> tipos = new ArrayList<>();
    private final List<MarcaEntity> marcas = new ArrayList<>();

    private final String[] statusCodes = {
            null, "D", "N", "R", "V"
    };

    private long selectedTipoId;
    private long selectedMarcaId;

    private boolean tiposLoaded;
    private boolean marcasLoaded;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        binding = DialogMaquinaFilterBinding.inflate(
                getLayoutInflater()
        );

        viewModel = new ViewModelProvider(requireActivity())
                .get(MaquinaViewModel.class);

        setOptions(binding.spinnerStatus, new String[]{
                getString(R.string.option_all),
                getString(R.string.status_available),
                getString(R.string.status_negotiating),
                getString(R.string.status_reserved),
                getString(R.string.status_sold)
        });

        if (savedInstanceState != null) {
            selectedTipoId =
                    savedInstanceState.getLong("tipoId", 0);
            selectedMarcaId =
                    savedInstanceState.getLong("marcaId", 0);

            binding.editValorDe.setText(
                    savedInstanceState.getString("valorDe", "")
            );
            binding.editValorAte.setText(
                    savedInstanceState.getString("valorAte", "")
            );
            binding.spinnerStatus.setSelection(
                    Math.max(0, Math.min(
                            4,
                            savedInstanceState.getInt("status", 0)
                    ))
            );
        } else {
            MaquinaViewModel.Filters filters =
                    viewModel.getFilters().getValue();

            if (filters != null) {
                selectedTipoId = filters.tipoLocalId == null
                        ? 0 : filters.tipoLocalId;

                selectedMarcaId = filters.marcaLocalId == null
                        ? 0 : filters.marcaLocalId;

                binding.editValorDe.setText(
                        formatHundredths(filters.valorDeCentavos)
                );
                binding.editValorAte.setText(
                        formatHundredths(filters.valorAteCentavos)
                );

                for (int i = 1; i < statusCodes.length; i++) {
                    if (statusCodes[i].equals(filters.status)) {
                        binding.spinnerStatus.setSelection(i);
                        break;
                    }
                }
            }
        }

        viewModel.getTipos().observe(this, list -> {
            if (tiposLoaded) {
                selectedTipoId = getSelectedTipoId();
            }

            tipos.clear();
            if (list != null) {
                tipos.addAll(list);
            }

            String[] labels = new String[tipos.size() + 1];
            labels[0] = getString(R.string.option_all);

            for (int i = 0; i < tipos.size(); i++) {
                labels[i + 1] = tipos.get(i).descricao;
            }

            setOptions(binding.spinnerTipo, labels);

            for (int i = 0; i < tipos.size(); i++) {
                if (tipos.get(i).localId == selectedTipoId) {
                    binding.spinnerTipo.setSelection(i + 1);
                    break;
                }
            }

            tiposLoaded = true;
            updateApplyState();
        });

        viewModel.getMarcas().observe(this, list -> {
            if (marcasLoaded) {
                selectedMarcaId = getSelectedMarcaId();
            }

            marcas.clear();
            if (list != null) {
                marcas.addAll(list);
            }

            String[] labels = new String[marcas.size() + 1];
            labels[0] = getString(R.string.option_all);

            for (int i = 0; i < marcas.size(); i++) {
                labels[i + 1] = marcas.get(i).nome;
            }

            setOptions(binding.spinnerMarca, labels);

            for (int i = 0; i < marcas.size(); i++) {
                if (marcas.get(i).localId == selectedMarcaId) {
                    binding.spinnerMarca.setSelection(i + 1);
                    break;
                }
            }

            marcasLoaded = true;
            updateApplyState();
        });

        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.action_filter)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.action_filter, null)
                .setNegativeButton(R.string.action_cancel, null)
                .setNeutralButton(
                        R.string.action_clear_filters,
                        (dialog, which) -> viewModel.clearFilters()
                )
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();

        AlertDialog dialog = (AlertDialog) requireDialog();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> applyFilters());

        updateApplyState();
    }

    private void setOptions(Spinner spinner, String[] labels) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                labels
        );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spinner.setAdapter(adapter);
    }

    private long getSelectedTipoId() {
        int position = binding.spinnerTipo.getSelectedItemPosition();

        return position > 0 && position <= tipos.size()
                ? tipos.get(position - 1).localId : 0;
    }

    private long getSelectedMarcaId() {
        int position = binding.spinnerMarca.getSelectedItemPosition();

        return position > 0 && position <= marcas.size()
                ? marcas.get(position - 1).localId : 0;
    }

    private void updateApplyState() {
        Dialog dialog = getDialog();

        if (dialog instanceof AlertDialog) {
            AlertDialog alert = (AlertDialog) dialog;

            if (alert.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                alert.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setEnabled(tiposLoaded && marcasLoaded);
            }
        }
    }

    private void applyFilters() {
        if (!tiposLoaded || !marcasLoaded) {
            return;
        }

        binding.editValorDe.setError(null);
        binding.editValorAte.setError(null);

        Long valorDe;
        Long valorAte;

        try {
            valorDe = parseOptionalHundredths(binding.editValorDe);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            showFieldError(
                    binding.editValorDe,
                    R.string.error_invalid_value
            );
            return;
        }

        try {
            valorAte = parseOptionalHundredths(binding.editValorAte);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            showFieldError(
                    binding.editValorAte,
                    R.string.error_invalid_value
            );
            return;
        }

        if (valorDe != null && valorAte != null
                && valorDe > valorAte) {
            showFieldError(
                    binding.editValorAte,
                    R.string.error_invalid_value_range
            );
            return;
        }

        long tipoId = getSelectedTipoId();
        long marcaId = getSelectedMarcaId();

        int position = binding.spinnerStatus.getSelectedItemPosition();

        String status = position > 0
                && position < statusCodes.length
                ? statusCodes[position] : null;

        viewModel.applyFilters(
                tipoId == 0 ? null : Long.valueOf(tipoId),
                marcaId == 0 ? null : Long.valueOf(marcaId),
                valorDe,
                valorAte,
                status
        );

        dismiss();
    }

    private Long parseOptionalHundredths(EditText field) {
        String text = field.getText().toString().trim();

        if (text.isEmpty()) {
            return null;
        }

        if (!text.matches("[0-9]+([.,][0-9]{1,2})?")) {
            throw new IllegalArgumentException();
        }

        return new BigDecimal(text.replace(',', '.'))
                .movePointRight(2)
                .longValueExact();
    }

    private String formatHundredths(Long value) {
        return value == null
                ? ""
                : BigDecimal.valueOf(value, 2).toPlainString();
    }

    private void showFieldError(EditText field, int message) {
        field.setError(getString(message));
        field.requestFocus();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putLong(
                "tipoId",
                tiposLoaded ? getSelectedTipoId() : selectedTipoId
        );
        outState.putLong(
                "marcaId",
                marcasLoaded ? getSelectedMarcaId() : selectedMarcaId
        );
        outState.putString(
                "valorDe",
                binding.editValorDe.getText().toString()
        );
        outState.putString(
                "valorAte",
                binding.editValorAte.getText().toString()
        );
        outState.putInt(
                "status",
                binding.spinnerStatus.getSelectedItemPosition()
        );

        super.onSaveInstanceState(outState);
    }
}