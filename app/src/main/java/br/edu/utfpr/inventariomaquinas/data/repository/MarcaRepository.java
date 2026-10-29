package br.edu.utfpr.inventariomaquinas.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import java.util.List;

import br.edu.utfpr.inventariomaquinas.data.local.AppDatabase;
import br.edu.utfpr.inventariomaquinas.data.local.SyncStatus;
import br.edu.utfpr.inventariomaquinas.data.local.dao.MarcaDao;
import br.edu.utfpr.inventariomaquinas.data.local.entity.MarcaEntity;

public class MarcaRepository {

    private final AppDatabase database;
    private final MarcaDao marcaDao;

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    public enum Error {
        EMPTY_NAME,
        NOT_FOUND,
        HAS_LINKED_MACHINES,
        HAS_PENDING_MACHINE_DELETIONS,
        DATABASE_ERROR
    }

    public interface Callback {
        void onSuccess();

        void onError(Error error);
    }

    public MarcaRepository(Context context) {
        database = AppDatabase.getInstance(context);
        marcaDao = database.marcaDao();
    }

    public LiveData<List<MarcaEntity>> observeAll() {
        return marcaDao.observeAll();
    }

    public void insert(String nome, Callback callback) {
        final String normalizedName = normalize(nome);

        if (normalizedName.isEmpty()) {
            notifyError(callback, Error.EMPTY_NAME);
            return;
        }

        AppDatabase.databaseExecutor.execute(() -> {
            try {
                MarcaEntity marca = new MarcaEntity();
                marca.nome = normalizedName;
                marca.syncStatus = SyncStatus.CREATED;

                marcaDao.insert(marca);

                notifySuccess(callback);
            } catch (Exception exception) {
                notifyError(callback, Error.DATABASE_ERROR);
            }
        });
    }

    public void update(
            long localId,
            String nome,
            Callback callback
    ) {
        final String normalizedName = normalize(nome);

        if (normalizedName.isEmpty()) {
            notifyError(callback, Error.EMPTY_NAME);
            return;
        }

        AppDatabase.databaseExecutor.execute(() -> {
            try {
                database.runInTransaction((Runnable) () -> {
                    MarcaEntity marca = marcaDao.getByLocalId(localId);

                    if (marca == null
                            || SyncStatus.DELETED.equals(marca.syncStatus)) {
                        throw new RuleException(Error.NOT_FOUND);
                    }

                    marca.nome = normalizedName;

                    if (marca.serverId == null) {
                        marca.syncStatus = SyncStatus.CREATED;
                    } else {
                        marca.syncStatus = SyncStatus.UPDATED;
                    }

                    marcaDao.update(marca);
                });

                notifySuccess(callback);
            } catch (RuleException exception) {
                notifyError(callback, exception.error);
            } catch (Exception exception) {
                notifyError(callback, Error.DATABASE_ERROR);
            }
        });
    }

    public void delete(long localId, Callback callback) {
        AppDatabase.databaseExecutor.execute(() -> {
            try {
                database.runInTransaction((Runnable) () -> {
                    MarcaEntity marca = marcaDao.getByLocalId(localId);

                    if (marca == null
                            || SyncStatus.DELETED.equals(marca.syncStatus)) {
                        throw new RuleException(Error.NOT_FOUND);
                    }

                    if (marcaDao.countActiveMachines(localId) > 0) {
                        throw new RuleException(
                                Error.HAS_LINKED_MACHINES
                        );
                    }

                    if (marca.serverId == null) {
                        if (marcaDao.countAllMachines(localId) > 0) {
                            throw new RuleException(
                                    Error.HAS_PENDING_MACHINE_DELETIONS
                            );
                        }

                        marcaDao.delete(marca);
                    } else {
                        marcaDao.markAsDeleted(localId);
                    }
                });

                notifySuccess(callback);
            } catch (RuleException exception) {
                notifyError(callback, exception.error);
            } catch (Exception exception) {
                notifyError(callback, Error.DATABASE_ERROR);
            }
        });
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private void notifySuccess(Callback callback) {
        mainHandler.post(callback::onSuccess);
    }

    private void notifyError(Callback callback, Error error) {
        mainHandler.post(() -> callback.onError(error));
    }

    private static class RuleException extends RuntimeException {

        private final Error error;

        private RuleException(Error error) {
            super(error.name());
            this.error = error;
        }
    }
}