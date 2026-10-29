package br.edu.utfpr.inventariomaquinas.ui.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

import br.edu.utfpr.inventariomaquinas.data.local.entity.TipoEntity;
import br.edu.utfpr.inventariomaquinas.data.repository.TipoRepository;

public class TipoViewModel extends AndroidViewModel {

    private final TipoRepository repository;

    private final LiveData<List<TipoEntity>> tipos;

    private final MutableLiveData<Boolean> saving =
            new MutableLiveData<>(false);

    private final MutableLiveData<Boolean> operationSucceeded =
            new MutableLiveData<>(false);

    private final MutableLiveData<TipoRepository.Error> error =
            new MutableLiveData<>();

    public TipoViewModel(@NonNull Application application) {
        super(application);

        repository = new TipoRepository(application);
        tipos = repository.observeAll();
    }

    public LiveData<List<TipoEntity>> getTipos() {
        return tipos;
    }

    public LiveData<Boolean> getSaving() {
        return saving;
    }

    public LiveData<Boolean> getOperationSucceeded() {
        return operationSucceeded;
    }

    public LiveData<TipoRepository.Error> getError() {
        return error;
    }

    public void save(long localId, String descricao) {
        if (Boolean.TRUE.equals(saving.getValue())) {
            return;
        }

        startOperation();

        if (localId == 0) {
            repository.insert(descricao, createCallback());
        } else {
            repository.update(localId, descricao, createCallback());
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

    private TipoRepository.Callback createCallback() {
        return new TipoRepository.Callback() {

            @Override
            public void onSuccess() {
                saving.setValue(false);
                operationSucceeded.setValue(true);
            }

            @Override
            public void onError(TipoRepository.Error operationError) {
                saving.setValue(false);
                error.setValue(operationError);
            }
        };
    }
}