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

public final class BrandDeletionSynchronizer {

    private final AppDatabase database;

    private final RemoteWriter writer =
            new RemoteWriter();

    public BrandDeletionSynchronizer(AppDatabase database) {
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

        int[] deleted = {0};

        for (MarcaEntity candidate : pending) {
            MarcaEntity snapshot = database.marcaDao()
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
                        "Invalid server ID for marca "
                                + snapshot.localId
                );
            }

            if (database.marcaDao().countAllMachines(
                    snapshot.localId
            ) > 0) {
                throw new IOException(
                        "Marca "
                                + snapshot.serverId
                                + " still has local machine references"
                );
            }

            if (existsOnServer(snapshot.serverId)) {
                writer.deleteMarca(snapshot.serverId);

                if (existsOnServer(snapshot.serverId)) {
                    throw new IOException(
                            "Marca "
                                    + snapshot.serverId
                                    + " still appears on the server"
                    );
                }
            }

            database.runInTransaction((Runnable) () -> {
                MarcaEntity current = database.marcaDao()
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

                if (database.marcaDao().countAllMachines(
                        current.localId
                ) > 0) {
                    throw new IllegalStateException(
                            "Local machine references prevent deletion"
                    );
                }

                int affected = database.marcaDao()
                        .delete(current);

                if (affected != 1) {
                    throw new IllegalStateException(
                            "Could not remove local marca"
                    );
                }

                deleted[0]++;
            });
        }

        return deleted[0];
    }

    private boolean existsOnServer(int serverId)
            throws IOException {

        Response<List<MarcaDto>> response =
                ApiClient.getApi()
                        .listMarcas()
                        .execute();

        try {
            if (!response.isSuccessful()) {
                throw new IOException(
                        "GET marca failed: HTTP "
                                + response.code()
                );
            }

            List<MarcaDto> marcas = response.body();

            if (marcas == null) {
                throw new IOException(
                        "GET marca returned no list"
                );
            }

            boolean found = false;

            for (MarcaDto marca : marcas) {
                if (marca == null
                        || marca.id == null
                        || marca.id <= 0) {
                    throw new IOException(
                            "GET marca returned an invalid item"
                    );
                }

                if (marca.id == serverId) {
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