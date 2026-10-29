package br.edu.utfpr.inventariomaquinas.data.remote;

import android.os.Looper;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import br.edu.utfpr.inventariomaquinas.data.local.AppDatabase;
import br.edu.utfpr.inventariomaquinas.data.local.SyncStatus;
import br.edu.utfpr.inventariomaquinas.data.local.entity.TipoEntity;
import br.edu.utfpr.inventariomaquinas.data.remote.api.ApiClient;
import br.edu.utfpr.inventariomaquinas.data.remote.dto.TipoDto;

import retrofit2.Response;

public final class TypeUpdateSynchronizer {

    private final AppDatabase database;

    private final RemoteWriter writer =
            new RemoteWriter();

    public TypeUpdateSynchronizer(AppDatabase database) {
        this.database = database;
    }

    public int synchronize() throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException(
                    "Synchronization must run on a background thread"
            );
        }

        List<TipoEntity> pending =
                database.tipoDao().getPendingSync();

        int[] confirmed = {0};

        for (TipoEntity candidate : pending) {
            TipoEntity snapshot = database.tipoDao()
                    .getByLocalId(candidate.localId);

            if (snapshot == null
                    || !SyncStatus.UPDATED.equals(
                    snapshot.syncStatus
            )
                    || snapshot.serverId == null) {
                continue;
            }

            TipoDto payload =
                    SyncPayloadMapper.toTipo(snapshot);

            writer.updateTipo(payload);

            verifyRemote(payload);

            database.runInTransaction((Runnable) () -> {
                TipoEntity current = database.tipoDao()
                        .getByLocalId(snapshot.localId);

                if (current == null) {
                    return;
                }

                boolean sameRecord = Objects.equals(
                        current.serverId,
                        snapshot.serverId
                );

                boolean sameDescription = Objects.equals(
                        current.descricao,
                        snapshot.descricao
                );

                boolean stillUpdated =
                        SyncStatus.UPDATED.equals(
                                current.syncStatus
                        );

                if (sameRecord
                        && sameDescription
                        && stillUpdated) {
                    current.syncStatus = SyncStatus.SYNCED;

                    int affected = database.tipoDao()
                            .update(current);

                    if (affected != 1) {
                        throw new IllegalStateException(
                                "Could not confirm local tipo update"
                        );
                    }

                    confirmed[0]++;
                }
            });
        }

        return confirmed[0];
    }

    private void verifyRemote(TipoDto expected)
            throws IOException {

        Response<TipoDto> response = ApiClient.getApi()
                .getTipo(expected.id)
                .execute();

        try {
            if (!response.isSuccessful()) {
                throw new IOException(
                        "GET tipo/"
                                + expected.id
                                + " failed: HTTP "
                                + response.code()
                );
            }

            TipoDto actual = response.body();

            if (actual == null
                    || !Objects.equals(actual.id, expected.id)
                    || !Objects.equals(
                    actual.descricao,
                    expected.descricao
            )) {
                throw new IOException(
                        "Server verification failed for tipo "
                                + expected.id
                );
            }

        } finally {
            if (response.errorBody() != null) {
                response.errorBody().close();
            }
        }
    }
}