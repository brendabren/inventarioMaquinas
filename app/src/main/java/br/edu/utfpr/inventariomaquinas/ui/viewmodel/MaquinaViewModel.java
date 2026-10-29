package br.edu.utfpr.inventariomaquinas.ui.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import java.util.List;

import br.edu.utfpr.inventariomaquinas.data.local.entity.MarcaEntity;
import br.edu.utfpr.inventariomaquinas.data.local.entity.MaquinaEntity;
import br.edu.utfpr.inventariomaquinas.data.local.entity.TipoEntity;
import br.edu.utfpr.inventariomaquinas.data.repository.MarcaRepository;
import br.edu.utfpr.inventariomaquinas.data.repository.MaquinaRepository;
import br.edu.utfpr.inventariomaquinas.data.repository.TipoRepository;

public class MaquinaViewModel extends AndroidViewModel {

    private final MaquinaRepository repository;

    private final LiveData<List<MaquinaEntity>> maquinas;
    private final LiveData<List<TipoEntity>> tipos;
    private final LiveData<List<MarcaEntity>> marcas;

    private final MutableLiveData<Filters> filters =
            new MutableLiveData<>(new Filters(
                    null, null, null, null, null
            ));

    private final MutableLiveData<Boolean> saving =
            new MutableLiveData<>(false);

    private final MutableLiveData<Boolean> operationSucceeded =
            new MutableLiveData<>(false);

    private final MutableLiveData<MaquinaRepository.Error> error =
            new MutableLiveData<>();

    public MaquinaViewModel(@NonNull Application application) {
        super(application);

        repository = new MaquinaRepository(application);

        TipoRepository tipoRepository =
                new TipoRepository(application);

        MarcaRepository marcaRepository =
                new MarcaRepository(application);

        tipos = tipoRepository.observeAll();
        marcas = marcaRepository.observeAll();

        maquinas = Transformations.switchMap(
                filters,
                currentFilters -> repository.observeFiltered(
                        currentFilters.tipoLocalId,
                        currentFilters.marcaLocalId,
                        currentFilters.valorDeCentavos,
                        currentFilters.valorAteCentavos,
                        currentFilters.status
                )
        );
    }

    public LiveData<List<MaquinaEntity>> getMaquinas() {
        return maquinas;
    }

    public LiveData<MaquinaEntity> getMaquina(long localId) {
        return repository.observeByLocalId(localId);
    }

    public LiveData<List<TipoEntity>> getTipos() {
        return tipos;
    }

    public LiveData<List<MarcaEntity>> getMarcas() {
        return marcas;
    }

    public LiveData<Filters> getFilters() {
        return filters;
    }

    public LiveData<Boolean> getSaving() {
        return saving;
    }

    public LiveData<Boolean> getOperationSucceeded() {
        return operationSucceeded;
    }

    public LiveData<MaquinaRepository.Error> getError() {
        return error;
    }

    public void applyFilters(
            Long tipoLocalId,
            Long marcaLocalId,
            Long valorDeCentavos,
            Long valorAteCentavos,
            String status
    ) {
        filters.setValue(new Filters(
                tipoLocalId,
                marcaLocalId,
                valorDeCentavos,
                valorAteCentavos,
                status
        ));
    }

    public void clearFilters() {
        applyFilters(null, null, null, null, null);
    }

    public void save(MaquinaEntity maquina) {
        if (Boolean.TRUE.equals(saving.getValue())) {
            return;
        }

        startOperation();

        repository.save(maquina, createCallback());
    }

    public void delete(long localId) {
        if (Boolean.TRUE.equals(saving.getValue())) {
            return;
        }

        startOperation();

        repository.delete(localId, createCallback());
    }

    public void clearOperationResult() {
        operationSucceeded.setValue(false);
        error.setValue(null);
    }

    private void startOperation() {
        clearOperationResult();
        saving.setValue(true);
    }

    private MaquinaRepository.Callback createCallback() {
        return new MaquinaRepository.Callback() {

            @Override
            public void onSuccess() {
                saving.setValue(false);
                operationSucceeded.setValue(true);
            }

            @Override
            public void onError(
                    MaquinaRepository.Error operationError
            ) {
                saving.setValue(false);
                error.setValue(operationError);
            }
        };
    }

    public static final class Filters {

        public final Long tipoLocalId;
        public final Long marcaLocalId;
        public final Long valorDeCentavos;
        public final Long valorAteCentavos;
        public final String status;

        public Filters(
                Long tipoLocalId,
                Long marcaLocalId,
                Long valorDeCentavos,
                Long valorAteCentavos,
                String status
        ) {
            this.tipoLocalId = tipoLocalId;
            this.marcaLocalId = marcaLocalId;
            this.valorDeCentavos = valorDeCentavos;
            this.valorAteCentavos = valorAteCentavos;
            this.status = status;
        }
    }
}