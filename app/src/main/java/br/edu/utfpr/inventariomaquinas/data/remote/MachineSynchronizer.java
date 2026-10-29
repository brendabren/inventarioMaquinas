package br.edu.utfpr.inventariomaquinas.data.remote;

import android.os.Looper;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import br.edu.utfpr.inventariomaquinas.data.local.AppDatabase;
import br.edu.utfpr.inventariomaquinas.data.local.SyncStatus;
import br.edu.utfpr.inventariomaquinas.data.local.entity.MarcaEntity;
import br.edu.utfpr.inventariomaquinas.data.local.entity.MaquinaEntity;
import br.edu.utfpr.inventariomaquinas.data.local.entity.TipoEntity;
import br.edu.utfpr.inventariomaquinas.data.remote.api.ApiClient;
import br.edu.utfpr.inventariomaquinas.data.remote.dto.MaquinaDto;
import br.edu.utfpr.inventariomaquinas.data.remote.dto.MaquinaRequest;

import retrofit2.Response;

public final class MachineSynchronizer {

    public static final class Result {
        public final int updated;
        public final int deleted;

        Result(int updated, int deleted) {
            this.updated = updated;
            this.deleted = deleted;
        }
    }

    private final AppDatabase database;

    private final RemoteWriter writer =
            new RemoteWriter();

    public MachineSynchronizer(AppDatabase database) {
        this.database = database;
    }

    public Result synchronize() throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException(
                    "Synchronization must run on a background thread"
            );
        }

        int updated = synchronizeUpdates();
        int deleted = synchronizeDeletions();

        return new Result(updated, deleted);
    }

    private int synchronizeUpdates() throws IOException {
        List<MaquinaEntity> pending =
                database.maquinaDao().getPendingSync();

        int[] confirmed = {0};

        for (MaquinaEntity candidate : pending) {
            MaquinaEntity snapshot = database.maquinaDao()
                    .getByLocalId(candidate.localId);

            if (snapshot == null
                    || snapshot.serverId == null
                    || !SyncStatus.UPDATED.equals(
                    snapshot.syncStatus
            )) {
                continue;
            }

            TipoEntity tipo = database.tipoDao()
                    .getByLocalId(snapshot.tipoLocalId);

            MarcaEntity marca = database.marcaDao()
                    .getByLocalId(snapshot.marcaLocalId);

            MaquinaRequest payload =
                    SyncPayloadMapper.toMaquina(
                            snapshot,
                            tipo,
                            marca
                    );

            String sentLocalSnapshot =
                    ApiClient.getGson().toJson(snapshot);

            writer.updateMaquina(payload);

            MaquinaDto remote = requireBody(
                    ApiClient.getApi()
                            .getMaquina(payload.id)
                            .execute()
            );

            if (!sameRemoteData(remote, payload)) {
                throw new IOException(
                        "Server verification failed for maquina "
                                + payload.id
                );
            }

            database.runInTransaction((Runnable) () -> {
                MaquinaEntity current = database.maquinaDao()
                        .getByLocalId(snapshot.localId);

                if (current == null) {
                    return;
                }

                String currentSnapshot =
                        ApiClient.getGson().toJson(current);

                if (!sentLocalSnapshot.equals(
                        currentSnapshot
                )) {
                    return;
                }

                current.syncStatus = SyncStatus.SYNCED;

                int affected = database.maquinaDao()
                        .update(current);

                if (affected != 1) {
                    throw new IllegalStateException(
                            "Could not confirm local maquina update"
                    );
                }

                confirmed[0]++;
            });
        }

        return confirmed[0];
    }

    private int synchronizeDeletions() throws IOException {
        List<MaquinaEntity> pending =
                database.maquinaDao().getPendingSync();

        int[] deleted = {0};

        for (MaquinaEntity candidate : pending) {
            MaquinaEntity snapshot = database.maquinaDao()
                    .getByLocalId(candidate.localId);

            if (snapshot == null
                    || snapshot.serverId == null
                    || !SyncStatus.DELETED.equals(
                    snapshot.syncStatus
            )) {
                continue;
            }

            if (snapshot.serverId <= 0) {
                throw new IOException(
                        "Invalid server ID for maquina "
                                + snapshot.localId
                );
            }

            if (existsOnServer(snapshot.serverId)) {
                writer.deleteMaquina(snapshot.serverId);

                if (existsOnServer(snapshot.serverId)) {
                    throw new IOException(
                            "Maquina "
                                    + snapshot.serverId
                                    + " still appears on the server"
                    );
                }
            }

            database.runInTransaction((Runnable) () -> {
                MaquinaEntity current = database.maquinaDao()
                        .getByLocalId(snapshot.localId);

                if (current == null) {
                    return;
                }

                boolean sameId = Objects.equals(
                        current.serverId,
                        snapshot.serverId
                );

                boolean stillDeleted =
                        SyncStatus.DELETED.equals(
                                current.syncStatus
                        );

                if (!sameId || !stillDeleted) {
                    return;
                }

                int affected = database.maquinaDao()
                        .delete(current);

                if (affected != 1) {
                    throw new IllegalStateException(
                            "Could not remove local maquina"
                    );
                }

                deleted[0]++;
            });
        }

        return deleted[0];
    }

    private boolean existsOnServer(int serverId)
            throws IOException {

        List<MaquinaDto> remote = requireBody(
                ApiClient.getApi()
                        .listMaquinas()
                        .execute()
        );

        boolean found = false;

        for (MaquinaDto item : remote) {
            if (item == null
                    || item.id == null
                    || item.id <= 0) {
                throw new IOException(
                        "GET maquina returned an invalid item"
                );
            }

            if (item.id == serverId) {
                found = true;
            }
        }

        return found;
    }

    private boolean sameRemoteData(
            MaquinaDto remote,
            MaquinaRequest expected
    ) {
        return Objects.equals(remote.id, expected.id)
                && Objects.equals(
                remote.idTipo,
                expected.idTipo
        )
                && Objects.equals(
                remote.idMarca,
                expected.idMarca
        )
                && Objects.equals(
                remote.anoFabricacao,
                expected.anoFabricacao
        )
                && Objects.equals(
                remote.dataInclusao,
                expected.dataInclusao
        )
                && Objects.equals(
                remote.descricao,
                expected.descricao
        )
                && Objects.equals(
                remote.nomeProprietario,
                expected.nomeProprietario
        )
                && Objects.equals(
                remote.contatoProprietario,
                expected.contatoProprietario
        )
                && Objects.equals(
                remote.status,
                expected.status
        )
                && sameDecimal(
                remote.valor,
                expected.valor
        )
                && sameDecimal(
                remote.percentualComissao,
                expected.percentualComissao
        );
    }

    private boolean sameDecimal(
            BigDecimal first,
            BigDecimal second
    ) {
        return first != null
                && second != null
                && first.compareTo(second) == 0;
    }

    private <T> T requireBody(Response<T> response)
            throws IOException {

        try {
            if (!response.isSuccessful()
                    || response.body() == null) {
                throw new IOException(
                        "GET failed or returned no body: HTTP "
                                + response.code()
                );
            }

            return response.body();

        } finally {
            if (response.errorBody() != null) {
                response.errorBody().close();
            }
        }
    }
}