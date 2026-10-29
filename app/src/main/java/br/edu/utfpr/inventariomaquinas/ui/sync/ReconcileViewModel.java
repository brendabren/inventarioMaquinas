package br.edu.utfpr.inventariomaquinas.ui.sync;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import br.edu.utfpr.inventariomaquinas.R;
import br.edu.utfpr.inventariomaquinas.data.local.AppDatabase;
import br.edu.utfpr.inventariomaquinas.data.local.SyncStatus;
import br.edu.utfpr.inventariomaquinas.data.remote.TransferCoordinator;
import br.edu.utfpr.inventariomaquinas.data.local.entity.MarcaEntity;
import br.edu.utfpr.inventariomaquinas.data.local.entity.TipoEntity;
import br.edu.utfpr.inventariomaquinas.data.remote.api.ApiClient;
import br.edu.utfpr.inventariomaquinas.data.remote.dto.MarcaDto;
import br.edu.utfpr.inventariomaquinas.data.remote.dto.TipoDto;

import okhttp3.ResponseBody;
import retrofit2.Response;

public class ReconcileViewModel extends AndroidViewModel {

    public static final class Candidate {
        public final int id;
        public final String name;

        public Candidate(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @NonNull
        @Override
        public String toString() {
            return "ID " + id + " — " + name;
        }
    }

    public static final class State {
        public final String record;
        public final String message;
        public final List<Candidate> candidates;
        public final boolean busy;
        public final boolean hasItem;
        public final boolean canCreate;

        State(
                String record,
                String message,
                List<Candidate> candidates,
                boolean busy,
                boolean hasItem,
                boolean canCreate
        ) {
            this.record = record;
            this.message = message;
            this.candidates = new ArrayList<>(candidates);
            this.busy = busy;
            this.hasItem = hasItem;
            this.canCreate = canCreate;
        }
    }

    private interface Task {
        void run() throws Exception;
    }

    private final AppDatabase database;
    private final SharedPreferences journal;

    private final Handler main =
            new Handler(Looper.getMainLooper());

    private final MutableLiveData<State> state =
            new MutableLiveData<>();

    private boolean type;
    private long localId;
    private String localName = "";
    private String message = "";

    private boolean busy;

    private List<Candidate> candidates =
            new ArrayList<>();

    public ReconcileViewModel(
            @NonNull Application application
    ) {
        super(application);

        database = AppDatabase.getInstance(application);

        journal = application.getSharedPreferences(
                "creation_reconciliation",
                Application.MODE_PRIVATE
        );

        runTask(this::loadNext);
    }

    public LiveData<State> getState() {
        return state;
    }

    public void queryCandidates() {
        if (busy || localId == 0) {
            return;
        }

        runTask(this::fetchCandidates);
    }

    public void createRemote() {
        if (busy || localId == 0) {
            return;
        }

        runTask(() -> {
            if (journal.getBoolean(key() + "_attempted", false)) {
                throw new IOException(
                        "A POST attempt already exists. Query candidates."
                );
            }

            String submittedName = readCurrentName();

            boolean saved = journal.edit()
                    .putBoolean(key() + "_attempted", true)
                    .putString(key() + "_name", submittedName)
                    .commit();

            if (!saved) {
                throw new IOException(
                        "Could not persist the creation attempt"
                );
            }

            Response<ResponseBody> response;

            if (type) {
                TipoDto dto = new TipoDto();
                dto.descricao = submittedName;

                response = ApiClient.getApi()
                        .createTipo(dto)
                        .execute();
            } else {
                MarcaDto dto = new MarcaDto();
                dto.nome = submittedName;

                response = ApiClient.getApi()
                        .createMarca(dto)
                        .execute();
            }

            try {
                if (!response.isSuccessful()
                        || response.code() == 202) {
                    throw new IOException(
                            "POST not confirmed: HTTP "
                                    + response.code()
                                    + ". Query candidates before retrying."
                    );
                }
            } finally {
                if (response.body() != null) {
                    response.body().close();
                }

                if (response.errorBody() != null) {
                    response.errorBody().close();
                }
            }

            fetchCandidates();
        });
    }

    public void link(Candidate selected) {
        if (busy || localId == 0 || selected == null) {
            return;
        }

        runTask(() -> {
            String expectedName = candidateName();

            if (type) {
                TipoDto remote = requireBody(
                        ApiClient.getApi()
                                .getTipo(selected.id)
                                .execute()
                );

                if (!Objects.equals(remote.id, selected.id)
                        || !Objects.equals(
                        remote.descricao,
                        expectedName
                )) {
                    throw new IOException(
                            "Remote tipo changed. Query candidates again."
                    );
                }

                database.runInTransaction((Runnable) () -> {
                    TipoEntity current =
                            database.tipoDao()
                                    .getByLocalId(localId);

                    if (current == null
                            || current.serverId != null
                            || !SyncStatus.CREATED.equals(
                            current.syncStatus
                    )) {
                        throw new IllegalStateException(
                                "Local tipo changed or no longer exists"
                        );
                    }

                    TipoEntity existing =
                            database.tipoDao()
                                    .getByServerId(selected.id);

                    if (existing != null) {
                        throw new IllegalStateException(
                                "This server ID is already linked "
                                        + "to another local tipo. "
                                        + "A local merge is required."
                        );
                    }

                    current.serverId = selected.id;

                    current.syncStatus = Objects.equals(
                            current.descricao,
                            remote.descricao
                    ) ? SyncStatus.SYNCED : SyncStatus.UPDATED;

                    database.tipoDao().update(current);
                });

            } else {
                MarcaDto remote = requireBody(
                        ApiClient.getApi()
                                .getMarca(selected.id)
                                .execute()
                );

                if (!Objects.equals(remote.id, selected.id)
                        || !Objects.equals(
                        remote.nome,
                        expectedName
                )) {
                    throw new IOException(
                            "Remote marca changed. Query candidates again."
                    );
                }

                database.runInTransaction((Runnable) () -> {
                    MarcaEntity current =
                            database.marcaDao()
                                    .getByLocalId(localId);

                    if (current == null
                            || current.serverId != null
                            || !SyncStatus.CREATED.equals(
                            current.syncStatus
                    )) {
                        throw new IllegalStateException(
                                "Local marca changed or no longer exists"
                        );
                    }

                    MarcaEntity existing =
                            database.marcaDao()
                                    .getByServerId(selected.id);

                    if (existing != null) {
                        throw new IllegalStateException(
                                "This server ID is already linked "
                                        + "to another local marca. "
                                        + "A local merge is required."
                        );
                    }

                    current.serverId = selected.id;

                    current.syncStatus = Objects.equals(
                            current.nome,
                            remote.nome
                    ) ? SyncStatus.SYNCED : SyncStatus.UPDATED;

                    database.marcaDao().update(current);
                });
            }

            if (!journal.edit().putBoolean(key() + "_done", true).commit()) {
                throw new IOException("Link saved, but journal completion could not be persisted");
            }
            loadNext();
        });
    }

    private void loadNext() {
        localId = 0;
        localName = "";
        candidates = new ArrayList<>();

        for (TipoEntity item :
                database.tipoDao().getPendingSync()) {
            if (item.serverId == null
                    && SyncStatus.CREATED.equals(item.syncStatus)) {
                type = true;
                localId = item.localId;
                localName = item.descricao;
                break;
            }
        }

        if (localId == 0) {
            for (MarcaEntity item :
                    database.marcaDao().getPendingSync()) {
                if (item.serverId == null
                        && SyncStatus.CREATED.equals(
                        item.syncStatus
                )) {
                    type = false;
                    localId = item.localId;
                    localName = item.nome;
                    break;
                }
            }
        }

        if (localId == 0) {
            message = text(R.string.reconcile_empty);
        } else if (journal.getBoolean(
                key() + "_attempted",
                false
        )) {
            message = text(R.string.reconcile_attempted);
        } else {
            message = text(R.string.reconcile_ready);
        }
    }

    private void fetchCandidates() throws IOException {
        String expected = candidateName();

        List<Candidate> found = new ArrayList<>();

        if (type) {
            List<TipoDto> remote = requireBody(
                    ApiClient.getApi().listTipos().execute()
            );

            for (TipoDto item : remote) {
                if (item != null
                        && item.id != null
                        && item.id > 0
                        && Objects.equals(
                        item.descricao,
                        expected
                )) {
                    found.add(new Candidate(
                            item.id,
                            item.descricao
                    ));
                }
            }
        } else {
            List<MarcaDto> remote = requireBody(
                    ApiClient.getApi().listMarcas().execute()
            );

            for (MarcaDto item : remote) {
                if (item != null
                        && item.id != null
                        && item.id > 0
                        && Objects.equals(
                        item.nome,
                        expected
                )) {
                    found.add(new Candidate(
                            item.id,
                            item.nome
                    ));
                }
            }
        }

        candidates = found;

        message = getApplication().getString(
                R.string.reconcile_candidates,
                found.size()
        );
    }

    private String readCurrentName() throws IOException {
        String name;

        if (type) {
            TipoEntity item = database.tipoDao()
                    .getByLocalId(localId);

            if (item == null
                    || item.serverId != null
                    || !SyncStatus.CREATED.equals(item.syncStatus)) {
                throw new IOException("Local tipo is no longer eligible");
            }

            name = item.descricao;
        } else {
            MarcaEntity item = database.marcaDao()
                    .getByLocalId(localId);

            if (item == null
                    || item.serverId != null
                    || !SyncStatus.CREATED.equals(item.syncStatus)) {
                throw new IOException("Local marca is no longer eligible");
            }

            name = item.nome;
        }

        if (name == null || name.trim().isEmpty()) {
            throw new IOException("Empty local name");
        }

        return name;
    }

    private String candidateName() throws IOException {
        if (journal.getBoolean(key() + "_attempted", false)) {
            String submitted = journal.getString(
                    key() + "_name",
                    null
            );

            if (submitted == null) {
                throw new IOException("Missing creation journal");
            }

            return submitted;
        }

        return readCurrentName();
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

    private String key() {
        return (type ? "tipo_" : "marca_") + localId;
    }

    private String text(int id) {
        return getApplication().getString(id);
    }

    private void runTask(Task task) {
        if (busy) {
            return;
        }

        busy = true;
        publish();

        AppDatabase.databaseExecutor.execute(() -> {
            try {
                synchronized (TransferCoordinator.LOCK) {
                    task.run();
                }
            } catch (Exception exception) {
                message = getApplication().getString(
                        R.string.reconcile_error,
                        exception.toString()
                );
            }

            main.post(() -> {
                busy = false;
                publish();
            });
        });
    }

    private void publish() {
        String record = "";

        if (localId != 0) {
            record = getApplication().getString(
                    R.string.reconcile_item,
                    text(
                            type
                                    ? R.string.reconcile_type
                                    : R.string.reconcile_brand
                    ),
                    localId,
                    localName
            );
        }

        state.setValue(new State(
                record,
                message,
                candidates,
                busy,
                localId != 0,
                localId != 0
                        && !journal.getBoolean(
                        key() + "_attempted",
                        false
                )
        ));
    }
}
