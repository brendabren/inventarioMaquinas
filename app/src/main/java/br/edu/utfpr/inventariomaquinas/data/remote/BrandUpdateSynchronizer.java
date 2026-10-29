package br.edu.utfpr.inventariomaquinas.data.remote;

import android.os.Looper;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import br.edu.utfpr.inventariomaquinas.data.local.AppDatabase;
import br.edu.utfpr.inventariomaquinas.data.local.SyncStatus;
import br.edu.utfpr.inventariomaquinas.data.local.entity.MarcaEntity;
import br.edu.utfpr.inventariomaquinas.data.remote.api.ApiClient;
import br.edu.utfpr.inventariomaquinas.data.remote.dto.MarcaDto;

import retrofit2.Response;

public final class BrandUpdateSynchronizer {

    private final AppDatabase database;

    private final RemoteWriter writer =
            new RemoteWriter();

    public BrandUpdateSynchronizer(AppDatabase database) {
        this.database = database;
    }

    public int synchronize() throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException(
                    "Synchronization must run on a background thread"
            );
        }

        List<MarcaEntity> pending =
                database.marcaDao().getPendingSync();

        int[] confirmed = {0};

        for (MarcaEntity candidate : pending) {
            MarcaEntity snapshot = database.marcaDao()
                    .getByLocalId(candidate.localId);

            if (snapshot == null
                    || !SyncStatus.UPDATED.equals(
                    snapshot.syncStatus
            )
                    || snapshot.serverId == null) {
                continue;
            }

            MarcaDto payload =
                    SyncPayloadMapper.toMarca(snapshot);

            writer.updateMarca(payload);

            verifyRemote(payload);

            database.runInTransaction((Runnable) () -> {
                MarcaEntity current = database.marcaDao()
                        .getByLocalId(snapshot.localId);

                if (current == null) {
                    return;
                }

                boolean sameRecord = Objects.equals(
                        current.serverId,
                        snapshot.serverId
                );

                boolean sameName = Objects.equals(
                        current.nome,
                        snapshot.nome
                );

                boolean stillUpdated =
                        SyncStatus.UPDATED.equals(
                                current.syncStatus
                        );

                if (sameRecord
                        && sameName
                        && stillUpdated) {
                    current.syncStatus = SyncStatus.SYNCED;

                    int affected = database.marcaDao()
                            .update(current);

                    if (affected != 1) {
                        throw new IllegalStateException(
                                "Could not confirm local marca update"
                        );
                    }

                    confirmed[0]++;
                }
            });
        }

        return confirmed[0];
    }

    private void verifyRemote(MarcaDto expected)
            throws IOException {

        Response<MarcaDto> response = ApiClient.getApi()
                .getMarca(expected.id)
                .execute();

        try {
            if (!response.isSuccessful()) {
                throw new IOException(
                        "GET marca/"
                                + expected.id
                                + " failed: HTTP "
                                + response.code()
                );
            }

            MarcaDto actual = response.body();

            if (actual == null
                    || !Objects.equals(actual.id, expected.id)
                    || !Objects.equals(
                    actual.nome,
                    expected.nome
            )) {
                throw new IOException(
                        "Server verification failed for marca "
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