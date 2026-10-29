package br.edu.utfpr.inventariomaquinas.ui.maquina;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import br.edu.utfpr.inventariomaquinas.R;
import br.edu.utfpr.inventariomaquinas.data.local.entity.MarcaEntity;
import br.edu.utfpr.inventariomaquinas.data.local.entity.MaquinaEntity;
import br.edu.utfpr.inventariomaquinas.data.local.entity.TipoEntity;
import br.edu.utfpr.inventariomaquinas.data.repository.MaquinaRepository;
import br.edu.utfpr.inventariomaquinas.databinding.ActivityMaquinaFormBinding;
import br.edu.utfpr.inventariomaquinas.ui.viewmodel.MaquinaViewModel;

public class MaquinaFormActivity extends AppCompatActivity {

    public static final String EXTRA_LOCAL_ID = "maquinaLocalId";

    private ActivityMaquinaFormBinding binding;
    private MaquinaViewModel viewModel;

    private final List<TipoEntity> tipos = new ArrayList<>();
    private final List<MarcaEntity> marcas = new ArrayList<>();

    private final String[] statusCodes = {"D", "N", "R", "V"};

    private long localId;
    private long selectedTipoId;
    private long selectedMarcaId;

    private boolean initialized;
    private boolean tiposLoaded;
    private boolean marcasLoaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMaquinaFormBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        ViewCompat.setOnApplyWindowInsetsListener(
                binding.root,
                (view, insets) -> {
                    Insets bars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                                    | WindowInsetsCompat.Type.ime()
                    );

                    view.setPadding(
                            bars.left, bars.top, bars.right, bars.bottom
                    );

                    return insets;
                }
        );

        ViewCompat.requestApplyInsets(binding.root);

        localId = getIntent().getLongExtra(EXTRA_LOCAL_ID, 0);

        binding.toolbar.setTitle(
                localId == 0
                        ? R.string.title_add_machine
                        : R.string.title_edit_machine
        );

        binding.toolbar.inflateMenu(R.menu.menu_maquina_form);

        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_save) {
                saveMachine();
                return true;
            }
            return false;
        });

        setOptions(binding.spinnerStatus, new String[]{
                getString(R.string.status_available),
                getString(R.string.status_negotiating),
                getString(R.string.status_reserved),
                getString(R.string.status_sold)
        });

        binding.textDataInclusao.setText(
                R.string.date_generated_on_save
        );

        viewModel = new ViewModelProvider(this)
                .get(MaquinaViewModel.class);

        if (savedInstanceState != null
                && savedInstanceState.getBoolean("initialized", false)) {
            restoreDraft(savedInstanceState);
            initialized = true;
        } else if (localId == 0) {
            initialized = true;
        }

        // Evita edição antes do carregamento inicial.
        binding.root.getChildAt(1).setVisibility(
                initialized ? View.VISIBLE : View.INVISIBLE
        );

        viewModel.getTipos().observe(this, list -> {
            if (tiposLoaded) {
                selectedTipoId = getSelectedTipoId();
            }

            tipos.clear();
            if (list != null) {
                tipos.addAll(list);
            }

            String[] labels = new String[tipos.size() + 1];
            labels[0] = getString(R.string.option_select);

            for (int i = 0; i < tipos.size(); i++) {
                labels[i + 1] = tipos.get(i).descricao;
            }

            setOptions(binding.spinnerTipo, labels);
            selectTipo();
            tiposLoaded = true;
            updateSaveState();
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
            labels[0] = getString(R.string.option_select);

            for (int i = 0; i < marcas.size(); i++) {
                labels[i + 1] = marcas.get(i).nome;
            }

            setOptions(binding.spinnerMarca, labels);
            selectMarca();
            marcasLoaded = true;
            updateSaveState();
        });

        if (!initialized) {
            viewModel.getMaquina(localId).observe(this, maquina -> {
                if (initialized) {
                    return;
                }

                if (maquina == null) {
                    Toast.makeText(
                            this,
                            R.string.error_not_found,
                            Toast.LENGTH_LONG
                    ).show();
                    finish();
                    return;
                }

                fillForm(maquina);
                initialized = true;

                binding.root.getChildAt(1)
                        .setVisibility(View.VISIBLE);

                updateSaveState();
            });
        }

        viewModel.getSaving().observe(
                this,
                saving -> updateSaveState()
        );

        viewModel.getOperationSucceeded().observe(this, success -> {
            if (Boolean.TRUE.equals(success)) {
                viewModel.clearOperationResult();

                Toast.makeText(
                        this,
                        R.string.message_operation_success,
                        Toast.LENGTH_SHORT
                ).show();

                finish();
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

        updateSaveState();
    }

    private void setOptions(Spinner spinner, String[] labels) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
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

    private void selectTipo() {
        binding.spinnerTipo.setSelection(0);

        for (int i = 0; i < tipos.size(); i++) {
            if (tipos.get(i).localId == selectedTipoId) {
                binding.spinnerTipo.setSelection(i + 1);
                break;
            }
        }
    }

    private void selectMarca() {
        binding.spinnerMarca.setSelection(0);

        for (int i = 0; i < marcas.size(); i++) {
            if (marcas.get(i).localId == selectedMarcaId) {
                binding.spinnerMarca.setSelection(i + 1);
                break;
            }
        }
    }

    private void fillForm(MaquinaEntity maquina) {
        selectedTipoId = maquina.tipoLocalId;
        selectedMarcaId = maquina.marcaLocalId;

        binding.editDescricao.setText(maquina.descricao);
        binding.editAnoFabricacao.setText(
                String.valueOf(maquina.anoFabricacao)
        );
        binding.editValor.setText(
                BigDecimal.valueOf(maquina.valorCentavos, 2)
                        .toPlainString()
        );
        binding.editNomeProprietario.setText(
                maquina.nomeProprietario
        );
        binding.editContatoProprietario.setText(
                maquina.contatoProprietario
        );
        binding.editPercentualComissao.setText(
                BigDecimal.valueOf(
                        maquina.percentualComissaoCentesimos, 2
                ).toPlainString()
        );
        binding.textDataInclusao.setText(maquina.dataInclusao);

        for (int i = 0; i < statusCodes.length; i++) {
            if (statusCodes[i].equals(maquina.status)) {
                binding.spinnerStatus.setSelection(i);
                break;
            }
        }

        selectTipo();
        selectMarca();
    }

    private void updateSaveState() {
        boolean ready = initialized
                && tiposLoaded
                && marcasLoaded
                && !Boolean.TRUE.equals(
                viewModel.getSaving().getValue()
        );

        binding.toolbar.getMenu()
                .findItem(R.id.action_save)
                .setEnabled(ready);
    }

    private void saveMachine() {
        if (!initialized || !tiposLoaded || !marcasLoaded
                || Boolean.TRUE.equals(
                viewModel.getSaving().getValue()
        )) {
            return;
        }

        MaquinaEntity maquina = new MaquinaEntity();

        maquina.localId = localId;
        maquina.tipoLocalId = getSelectedTipoId();
        maquina.marcaLocalId = getSelectedMarcaId();

        maquina.descricao =
                binding.editDescricao.getText().toString();

        maquina.nomeProprietario =
                binding.editNomeProprietario.getText().toString();

        maquina.contatoProprietario =
                binding.editContatoProprietario.getText().toString();

        try {
            maquina.anoFabricacao = Integer.parseInt(
                    binding.editAnoFabricacao.getText()
                            .toString().trim()
            );
        } catch (NumberFormatException exception) {
            showFieldError(
                    binding.editAnoFabricacao,
                    R.string.error_invalid_year
            );
            return;
        }

        try {
            maquina.valorCentavos = parseHundredths(
                    binding.editValor
            );
        } catch (IllegalArgumentException | ArithmeticException exception) {
            showFieldError(
                    binding.editValor,
                    R.string.error_invalid_value
            );
            return;
        }

        try {
            long commission = parseHundredths(
                    binding.editPercentualComissao
            );

            if (commission > 10000) {
                throw new IllegalArgumentException();
            }

            maquina.percentualComissaoCentesimos =
                    (int) commission;

        } catch (IllegalArgumentException | ArithmeticException exception) {
            showFieldError(
                    binding.editPercentualComissao,
                    R.string.error_invalid_commission
            );
            return;
        }

        int statusPosition =
                binding.spinnerStatus.getSelectedItemPosition();

        if (statusPosition < 0
                || statusPosition >= statusCodes.length) {
            Toast.makeText(
                    this,
                    R.string.error_invalid_status,
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        maquina.status = statusCodes[statusPosition];

        viewModel.save(maquina);
    }

    private long parseHundredths(EditText field) {
        String text = field.getText().toString().trim();

        if (!text.matches("[0-9]+([.,][0-9]{1,2})?")) {
            throw new IllegalArgumentException();
        }

        return new BigDecimal(text.replace(',', '.'))
                .movePointRight(2)
                .longValueExact();
    }

    private void showFieldError(EditText field, int message) {
        field.setError(getString(message));
        field.requestFocus();
    }

    private int getErrorMessage(MaquinaRepository.Error error) {
        switch (error) {
            case NOT_FOUND:
                return R.string.error_not_found;
            case INVALID_TYPE:
                return R.string.error_invalid_type;
            case INVALID_BRAND:
                return R.string.error_invalid_brand;
            case EMPTY_DESCRIPTION:
                return R.string.error_empty_description;
            case INVALID_YEAR:
                return R.string.error_invalid_year;
            case INVALID_VALUE:
                return R.string.error_invalid_value;
            case EMPTY_OWNER_NAME:
                return R.string.error_empty_owner_name;
            case EMPTY_OWNER_CONTACT:
                return R.string.error_empty_owner_contact;
            case INVALID_COMMISSION:
                return R.string.error_invalid_commission;
            case INVALID_STATUS:
                return R.string.error_invalid_status;
            case DATABASE_ERROR:
            default:
                return R.string.error_database;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("initialized", initialized);

        if (initialized) {
            outState.putLong(
                    "tipoId",
                    tiposLoaded ? getSelectedTipoId() : selectedTipoId
            );
            outState.putLong(
                    "marcaId",
                    marcasLoaded ? getSelectedMarcaId() : selectedMarcaId
            );

            outState.putString(
                    "descricao",
                    binding.editDescricao.getText().toString()
            );
            outState.putString(
                    "ano",
                    binding.editAnoFabricacao.getText().toString()
            );
            outState.putString(
                    "valor",
                    binding.editValor.getText().toString()
            );
            outState.putString(
                    "nome",
                    binding.editNomeProprietario.getText().toString()
            );
            outState.putString(
                    "contato",
                    binding.editContatoProprietario.getText().toString()
            );
            outState.putString(
                    "comissao",
                    binding.editPercentualComissao.getText().toString()
            );
            outState.putString(
                    "data",
                    binding.textDataInclusao.getText().toString()
            );
            outState.putInt(
                    "statusPosition",
                    binding.spinnerStatus.getSelectedItemPosition()
            );
        }

        super.onSaveInstanceState(outState);
    }

    private void restoreDraft(Bundle state) {
        selectedTipoId = state.getLong("tipoId", 0);
        selectedMarcaId = state.getLong("marcaId", 0);

        binding.editDescricao.setText(state.getString("descricao", ""));
        binding.editAnoFabricacao.setText(state.getString("ano", ""));
        binding.editValor.setText(state.getString("valor", ""));
        binding.editNomeProprietario.setText(state.getString("nome", ""));
        binding.editContatoProprietario.setText(
                state.getString("contato", "")
        );
        binding.editPercentualComissao.setText(
                state.getString("comissao", "")
        );
        binding.textDataInclusao.setText(state.getString("data", ""));

        binding.spinnerStatus.setSelection(
                Math.max(0, Math.min(
                        3, state.getInt("statusPosition", 0)
                ))
        );
    }
}