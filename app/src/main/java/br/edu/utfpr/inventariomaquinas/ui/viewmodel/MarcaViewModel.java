package br.edu.utfpr.inventariomaquinas.ui.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

import br.edu.utfpr.inventariomaquinas.data.local.entity.MarcaEntity;
import br.edu.utfpr.inventariomaquinas.data.repository.MarcaRepository;

public class MarcaViewModel extends AndroidViewModel {

    private final MarcaRepository repository;

    private final LiveData<List<MarcaEntity>> marcas;

    private final MutableLiveData<Boolean> saving =
            new MutableLiveData<>(false);

    private final MutableLiveData<Boolean> operationSucceeded =
            new MutableLiveData<>(false);

    private final MutableLiveData<MarcaRepository.Error> error =
            new MutableLiveData<>();

    public MarcaViewModel(@NonNull Application application) {
        super(application);

        repository = new MarcaRepository(application);
        marcas = repository.observeAll();
    }

    public LiveData<List<MarcaEntity>> getMarcas() {
        return marcas;
    }

    public LiveData<Boolean> getSaving() {
        return saving;
    }

    public LiveData<Boolean> getOperationSucceeded() {
        return operationSucceeded;
    }

    public LiveData<MarcaRepository.Error> getError() {
        return error;
    }

    public void save(long localId, String nome) {
        if (Boolean.TRUE.equals(saving.getValue())) {
            return;
        }

        startOperation();

        if (localId == 0) {
            repository.insert(nome, createCallback());
        } else {
            repository.update(localId, nome, createCallback());
        }
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

    private MarcaRepository.Callback createCallback() {
        return new MarcaRepository.Callback() {

            @Override
            public void onSuccess() {
                saving.setValue(false);
                operationSucceeded.setValue(true);
            }

            @Override
            public void onError(MarcaRepository.Error operationError) {
                saving.setValue(false);
                error.setValue(operationError);
            }
        };
    }
}