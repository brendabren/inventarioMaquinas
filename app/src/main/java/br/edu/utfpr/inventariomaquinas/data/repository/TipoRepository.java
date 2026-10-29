package br.edu.utfpr.inventariomaquinas.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import java.util.List;

import br.edu.utfpr.inventariomaquinas.data.local.AppDatabase;
import br.edu.utfpr.inventariomaquinas.data.local.SyncStatus;
import br.edu.utfpr.inventariomaquinas.data.local.dao.TipoDao;
import br.edu.utfpr.inventariomaquinas.data.local.entity.TipoEntity;

public class TipoRepository {

    private final AppDatabase database;
    private final TipoDao tipoDao;

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    public enum Error {
        EMPTY_DESCRIPTION,
        NOT_FOUND,
        HAS_LINKED_MACHINES,
        HAS_PENDING_MACHINE_DELETIONS,
        DATABASE_ERROR
    }

    public interface Callback {
        void onSuccess();

        void onError(Error error);
    }

    public TipoRepository(Context context) {
        database = AppDatabase.getInstance(context);
        tipoDao = database.tipoDao();
    }

    public LiveData<List<TipoEntity>> observeAll() {
        return tipoDao.observeAll();
    }

    public void insert(String descricao, Callback callback) {
        final String normalizedDescription = normalize(descricao);

        if (normalizedDescription.isEmpty()) {
            notifyError(callback, Error.EMPTY_DESCRIPTION);
            return;
        }

        AppDatabase.databaseExecutor.execute(() -> {
            try {
                TipoEntity tipo = new TipoEntity();
                tipo.descricao = normalizedDescription;
                tipo.syncStatus = SyncStatus.CREATED;

                tipoDao.insert(tipo);

                notifySuccess(callback);
            } catch (Exception exception) {
                notifyError(callback, Error.DATABASE_ERROR);
            }
        });
    }

    public void update(
            long localId,
            String descricao,
            Callback callback
    ) {
        final String normalizedDescription = normalize(descricao);

        if (normalizedDescription.isEmpty()) {
            notifyError(callback, Error.EMPTY_DESCRIPTION);
            return;
        }

        AppDatabase.databaseExecutor.execute(() -> {
            try {
                database.runInTransaction((Runnable) () -> {
                    TipoEntity tipo = tipoDao.getByLocalId(localId);

                    if (tipo == null
                            || SyncStatus.DELETED.equals(tipo.syncStatus)) {
                        throw new RuleException(Error.NOT_FOUND);
                    }

                    tipo.descricao = normalizedDescription;

                    if (tipo.serverId == null) {
                        tipo.syncStatus = SyncStatus.CREATED;
                    } else {
                        tipo.syncStatus = SyncStatus.UPDATED;
                    }

                    tipoDao.update(tipo);
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
                    TipoEntity tipo = tipoDao.getByLocalId(localId);

                    if (tipo == null
                            || SyncStatus.DELETED.equals(tipo.syncStatus)) {
                        throw new RuleException(Error.NOT_FOUND);
                    }

                    if (tipoDao.countActiveMachines(localId) > 0) {
                        throw new RuleException(
                                Error.HAS_LINKED_MACHINES
                        );
                    }

                    if (tipo.serverId == null) {
                        if (tipoDao.countAllMachines(localId) > 0) {
                            throw new RuleException(
                                    Error.HAS_PENDING_MACHINE_DELETIONS
                            );
                        }

                        tipoDao.delete(tipo);
                    } else {
                        tipoDao.markAsDeleted(localId);
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