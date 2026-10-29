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

public final class TypeDeletionSynchronizer {

    private final AppDatabase database;

    private final RemoteWriter writer =
            new RemoteWriter();

    public TypeDeletionSynchronizer(AppDatabase database) {
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

        int[] deleted = {0};

        for (TipoEntity candidate : pending) {
            TipoEntity snapshot = database.tipoDao()
                    .getByLocalId(candidate.localId);

            if (snapshot == null
                    || !SyncStatus.DELETED.equals(
                    snapshot.syncStatus
            )
                    || snapshot.serverId == null) {
                continue;
            }

            if (snapshot.serverId <= 0) {
                throw new IOException(
                        "Invalid server ID for tipo "
                                + snapshot.localId
                );
            }

            if (database.tipoDao().countAllMachines(
                    snapshot.localId
            ) > 0) {
                throw new IOException(
                        "Tipo "
                                + snapshot.serverId
                                + " still has local machine references"
                );
            }

            boolean existsBefore =
                    existsOnServer(snapshot.serverId);

            if (existsBefore) {
                writer.deleteTipo(snapshot.serverId);

                if (existsOnServer(snapshot.serverId)) {
                    throw new IOException(
                            "Tipo "
                                    + snapshot.serverId
                                    + " still appears on the server"
                    );
                }
            }

            database.runInTransaction((Runnable) () -> {
                TipoEntity current = database.tipoDao()
                        .getByLocalId(snapshot.localId);

                if (current == null) {
                    return;
                }

                boolean sameServerId = Objects.equals(
                        current.serverId,
                        snapshot.serverId
                );

                boolean stillDeleted =
                        SyncStatus.DELETED.equals(
                                current.syncStatus
                        );

                if (!sameServerId || !stillDeleted) {
                    return;
                }

                if (database.tipoDao().countAllMachines(
                        current.localId
                ) > 0) {
                    throw new IllegalStateException(
                            "Local machine references prevent deletion"
                    );
                }

                int affected = database.tipoDao()
                        .delete(current);

                if (affected != 1) {
                    throw new IllegalStateException(
                            "Could not remove local tipo"
                    );
                }

                deleted[0]++;
            });
        }

        return deleted[0];
    }

    private boolean existsOnServer(int serverId)
            throws IOException {

        Response<List<TipoDto>> response =
                ApiClient.getApi()
                        .listTipos()
                        .execute();

        try {
            if (!response.isSuccessful()) {
                throw new IOException(
                        "GET tipo failed: HTTP "
                                + response.code()
                );
            }

            List<TipoDto> tipos = response.body();

            if (tipos == null) {
                throw new IOException(
                        "GET tipo returned no list"
                );
            }

            boolean found = false;

            for (TipoDto tipo : tipos) {
                if (tipo == null
                        || tipo.id == null
                        || tipo.id <= 0) {
                    throw new IOException(
                            "GET tipo returned an invalid item"
                    );
                }

                if (tipo.id == serverId) {
                    found = true;
                }
            }

            return found;

        } finally {
            if (response.errorBody() != null) {
                response.errorBody().close();
            }
        }
    }
}