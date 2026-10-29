package br.edu.utfpr.inventariomaquinas.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import br.edu.utfpr.inventariomaquinas.data.local.AppDatabase;
import br.edu.utfpr.inventariomaquinas.data.local.SyncStatus;
import br.edu.utfpr.inventariomaquinas.data.local.entity.MarcaEntity;
import br.edu.utfpr.inventariomaquinas.data.local.entity.MaquinaEntity;
import br.edu.utfpr.inventariomaquinas.data.local.entity.TipoEntity;

public class MaquinaRepository {

    private final AppDatabase database;

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    public enum Error {
        NOT_FOUND,
        INVALID_TYPE,
        INVALID_BRAND,
        EMPTY_DESCRIPTION,
        INVALID_YEAR,
        INVALID_VALUE,
        EMPTY_OWNER_NAME,
        EMPTY_OWNER_CONTACT,
        INVALID_COMMISSION,
        INVALID_STATUS,
        DATABASE_ERROR
    }

    public interface Callback {
        void onSuccess();

        void onError(Error error);
    }

    public MaquinaRepository(Context context) {
        database = AppDatabase.getInstance(context);
    }

    public LiveData<List<MaquinaEntity>> observeAll() {
        return database.maquinaDao().observeAll();
    }

    public LiveData<MaquinaEntity> observeByLocalId(long localId) {
        return database.maquinaDao().observeByLocalId(localId);
    }

    public LiveData<List<MaquinaEntity>> observeFiltered(
            Long tipoLocalId,
            Long marcaLocalId,
            Long valorDeCentavos,
            Long valorAteCentavos,
            String status
    ) {
        return database.maquinaDao().observeFiltered(
                tipoLocalId,
                marcaLocalId,
                valorDeCentavos,
                valorAteCentavos,
                status
        );
    }

    public void save(MaquinaEntity input, Callback callback) {
        // Copia os dados antes de iniciar a operação assíncrona.
        final MaquinaEntity draft = copyEditableFields(input);
        draft.localId = input.localId;

        AppDatabase.databaseExecutor.execute(() -> {
            try {
                database.runInTransaction((Runnable) () -> {
                    validate(draft);

                    MaquinaEntity target;

                    if (draft.localId == 0) {
                        target = new MaquinaEntity();

                        target.dataInclusao = new SimpleDateFormat(
                                "yyyy-MM-dd HH:mm",
                                Locale.ROOT
                        ).format(new Date());

                        target.syncStatus = SyncStatus.CREATED;
                    } else {
                        target = database.maquinaDao()
                                .getByLocalId(draft.localId);

                        if (target == null
                                || SyncStatus.DELETED.equals(
                                target.syncStatus
                        )) {
                            throw new RuleException(Error.NOT_FOUND);
                        }

                        target.syncStatus = target.serverId == null
                                ? SyncStatus.CREATED
                                : SyncStatus.UPDATED;
                    }

                    target.tipoLocalId = draft.tipoLocalId;
                    target.marcaLocalId = draft.marcaLocalId;
                    target.descricao = draft.descricao.trim();
                    target.anoFabricacao = draft.anoFabricacao;
                    target.valorCentavos = draft.valorCentavos;
                    target.nomeProprietario =
                            draft.nomeProprietario.trim();
                    target.contatoProprietario =
                            draft.contatoProprietario.trim();
                    target.percentualComissaoCentesimos =
                            draft.percentualComissaoCentesimos;
                    target.status = draft.status;

                    if (target.localId == 0) {
                        database.maquinaDao().insert(target);
                    } else {
                        database.maquinaDao().update(target);
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

    public void delete(long localId, Callback callback) {
        AppDatabase.databaseExecutor.execute(() -> {
            try {
                database.runInTransaction((Runnable) () -> {
                    MaquinaEntity maquina = database.maquinaDao()
                            .getByLocalId(localId);

                    if (maquina == null
                            || SyncStatus.DELETED.equals(
                            maquina.syncStatus
                    )) {
                        throw new RuleException(Error.NOT_FOUND);
                    }

                    if (maquina.serverId == null) {
                        database.maquinaDao().delete(maquina);
                    } else {
                        database.maquinaDao().markAsDeleted(localId);
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

    private void validate(MaquinaEntity maquina) {
        TipoEntity tipo = database.tipoDao()
                .getByLocalId(maquina.tipoLocalId);

        if (tipo == null
                || SyncStatus.DELETED.equals(tipo.syncStatus)) {
            throw new RuleException(Error.INVALID_TYPE);
        }

        MarcaEntity marca = database.marcaDao()
                .getByLocalId(maquina.marcaLocalId);

        if (marca == null
                || SyncStatus.DELETED.equals(marca.syncStatus)) {
            throw new RuleException(Error.INVALID_BRAND);
        }

        if (isBlank(maquina.descricao)) {
            throw new RuleException(Error.EMPTY_DESCRIPTION);
        }

        if (maquina.anoFabricacao <= 0) {
            throw new RuleException(Error.INVALID_YEAR);
        }

        if (maquina.valorCentavos < 0) {
            throw new RuleException(Error.INVALID_VALUE);
        }

        if (isBlank(maquina.nomeProprietario)) {
            throw new RuleException(Error.EMPTY_OWNER_NAME);
        }

        if (isBlank(maquina.contatoProprietario)) {
            throw new RuleException(Error.EMPTY_OWNER_CONTACT);
        }

        if (maquina.percentualComissaoCentesimos < 0
                || maquina.percentualComissaoCentesimos > 10000) {
            throw new RuleException(Error.INVALID_COMMISSION);
        }

        if (!"D".equals(maquina.status)
                && !"N".equals(maquina.status)
                && !"R".equals(maquina.status)
                && !"V".equals(maquina.status)) {
            throw new RuleException(Error.INVALID_STATUS);
        }
    }

    private MaquinaEntity copyEditableFields(MaquinaEntity source) {
        MaquinaEntity copy = new MaquinaEntity();

        copy.tipoLocalId = source.tipoLocalId;
        copy.marcaLocalId = source.marcaLocalId;
        copy.descricao = source.descricao;
        copy.anoFabricacao = source.anoFabricacao;
        copy.valorCentavos = source.valorCentavos;
        copy.nomeProprietario = source.nomeProprietario;
        copy.contatoProprietario = source.contatoProprietario;
        copy.percentualComissaoCentesimos =
                source.percentualComissaoCentesimos;
        copy.status = source.status;

        return copy;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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