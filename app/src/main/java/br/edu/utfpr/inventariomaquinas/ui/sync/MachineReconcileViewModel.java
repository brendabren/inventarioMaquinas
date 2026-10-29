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
import br.edu.utfpr.inventariomaquinas.data.local.entity.MaquinaEntity;
import br.edu.utfpr.inventariomaquinas.data.local.entity.TipoEntity;
import br.edu.utfpr.inventariomaquinas.data.remote.SyncPayloadMapper;
import br.edu.utfpr.inventariomaquinas.data.remote.api.ApiClient;
import br.edu.utfpr.inventariomaquinas.data.remote.dto.MaquinaDto;
import br.edu.utfpr.inventariomaquinas.data.remote.dto.MaquinaRequest;

import okhttp3.ResponseBody;
import retrofit2.Response;

public class MachineReconcileViewModel extends AndroidViewModel {

    public static final class Candidate {
        public final int id;
        public final String label;

        public Candidate(int id, String label) {
            this.id = id;
            this.label = label;
        }

        @NonNull
        @Override
        public String toString() {
            return "ID " + id + " — " + label;
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

    private long localId;
    private String localName = "";
    private String message = "";
    private boolean busy;

    private List<Candidate> candidates =
            new ArrayList<>();

    public MachineReconcileViewModel(
            @NonNull Application application
    ) {
        super(application);

        database = AppDatabase.getInstance(application);

        journal = application.getSharedPreferences(
                "machine_creation_reconciliation",
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
            if (journal.getBoolean(
                    key() + "_attempted",
                    false
            )) {
                throw new IOException(
                        "A POST attempt already exists. Query candidates."
                );
            }

            MaquinaRequest payload = readCurrentPayload();

            String json = ApiClient.getGson()
                    .toJson(payload);

            boolean saved = journal.edit()
                    .putBoolean(key() + "_attempted", true)
                    .putString(key() + "_payload", json)
                    .commit();

            if (!saved) {
                throw new IOException(
                        "Could not persist the creation attempt"
                );
            }

            Response<ResponseBody> response =
                    ApiClient.getApi()
                            .createMaquina(payload)
                            .execute();

            try {
                if (!response.isSuccessful()
                        || response.code() == 202) {
                    throw new IOException(
                            "POST maquina not confirmed: HTTP "
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
            MaquinaRequest expected = candidatePayload();

            MaquinaDto remote = requireBody(
                    ApiClient.getApi()
                            .getMaquina(selected.id)
                            .execute()
            );

            if (!Objects.equals(remote.id, selected.id)
                    || !matchesCandidate(remote, expected)) {
                throw new IOException(
                        "Remote maquina changed. Query candidates again."
                );
            }

            MaquinaRequest remotePayload =
                    remoteToRequest(remote);

            database.runInTransaction((Runnable) () -> {
                MaquinaEntity current = database.maquinaDao()
                        .getByLocalId(localId);

                if (current == null
                        || current.serverId != null
                        || !SyncStatus.CREATED.equals(
                        current.syncStatus
                )) {
                    throw new IllegalStateException(
                            "Local maquina changed or no longer exists"
                    );
                }

                MaquinaEntity existing =
                        database.maquinaDao()
                                .getByServerId(selected.id);

                if (existing != null) {
                    throw new IllegalStateException(
                            "This server ID is already linked "
                                    + "to another local maquina. "
                                    + "A local merge is required."
                    );
                }

                TipoEntity tipo = database.tipoDao()
                        .getByLocalId(current.tipoLocalId);

                MarcaEntity marca = database.marcaDao()
                        .getByLocalId(current.marcaLocalId);

                MaquinaRequest currentPayload =
                        SyncPayloadMapper.toMaquina(
                                current,
                                tipo,
                                marca
                        );

                boolean sameData = fingerprint(currentPayload)
                        .equals(fingerprint(remotePayload));

                current.serverId = selected.id;

                current.syncStatus = sameData
                        ? SyncStatus.SYNCED
                        : SyncStatus.UPDATED;

                int affected = database.maquinaDao()
                        .update(current);

                if (affected != 1) {
                    throw new IllegalStateException(
                            "Could not link local maquina"
                    );
                }
            });

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

        for (MaquinaEntity item :
                database.maquinaDao().getPendingSync()) {
            if (item.serverId == null
                    && SyncStatus.CREATED.equals(
                    item.syncStatus
            )) {
                localId = item.localId;
                localName = item.descricao;
                break;
            }
        }

        if (localId == 0) {
            message = text(
                    R.string.reconcile_machines_empty
            );
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
        MaquinaRequest expected = candidatePayload();

        List<MaquinaDto> remote = requireBody(
                ApiClient.getApi()
                        .listMaquinas()
                        .execute()
        );

        List<Candidate> found = new ArrayList<>();

        for (MaquinaDto item : remote) {
            if (item != null
                    && item.id != null
                    && item.id > 0
                    && matchesCandidate(item, expected)) {

                String label = item.descricao
                        + " | " + item.anoFabricacao
                        + " | " + item.valor
                        + " | " + item.nomeProprietario;

                found.add(new Candidate(
                        item.id,
                        label
                ));
            }
        }

        candidates = found;

        message = getApplication().getString(
                R.string.reconcile_candidates,
                found.size()
        );
    }

    private boolean matchesCandidate(
            MaquinaDto remote,
            MaquinaRequest expected
    ) {
        return Objects.equals(
                remote.descricao,
                expected.descricao
        ) && Objects.equals(
                remote.idTipo,
                expected.idTipo
        ) && Objects.equals(
                remote.idMarca,
                expected.idMarca
        ) && Objects.equals(
                remote.anoFabricacao,
                expected.anoFabricacao
        );
    }

    private MaquinaRequest readCurrentPayload()
            throws IOException {

        MaquinaEntity item = database.maquinaDao()
                .getByLocalId(localId);

        if (item == null
                || item.serverId != null
                || !SyncStatus.CREATED.equals(
                item.syncStatus
        )) {
            throw new IOException(
                    "Local maquina is no longer eligible"
            );
        }

        TipoEntity tipo = database.tipoDao()
                .getByLocalId(item.tipoLocalId);

        MarcaEntity marca = database.marcaDao()
                .getByLocalId(item.marcaLocalId);

        return SyncPayloadMapper.toMaquina(
                item,
                tipo,
                marca
        );
    }

    private MaquinaRequest candidatePayload()
            throws IOException {

        if (!journal.getBoolean(
                key() + "_attempted",
                false
        )) {
            return readCurrentPayload();
        }

        String json = journal.getString(
                key() + "_payload",
                null
        );

        if (json == null) {
            throw new IOException(
                    "Missing maquina creation journal"
            );
        }

        MaquinaRequest payload = ApiClient.getGson()
                .fromJson(json, MaquinaRequest.class);

        if (payload == null) {
            throw new IOException(
                    "Invalid maquina creation journal"
            );
        }

        return payload;
    }

    private MaquinaRequest remoteToRequest(MaquinaDto dto) {
        MaquinaRequest request = new MaquinaRequest();

        request.idTipo = dto.idTipo;
        request.idMarca = dto.idMarca;
        request.anoFabricacao = dto.anoFabricacao;
        request.contatoProprietario =
                dto.contatoProprietario;
        request.dataInclusao = dto.dataInclusao;
        request.descricao = dto.descricao;
        request.nomeProprietario =
                dto.nomeProprietario;
        request.percentualComissao =
                dto.percentualComissao;
        request.status = dto.status;
        request.valor = dto.valor;

        return request;
    }

    private String fingerprint(MaquinaRequest source) {
        MaquinaRequest copy = ApiClient.getGson()
                .fromJson(
                        ApiClient.getGson().toJson(source),
                        MaquinaRequest.class
                );

        copy.id = null;

        if (copy.valor != null) {
            copy.valor = copy.valor.stripTrailingZeros();
        }

        if (copy.percentualComissao != null) {
            copy.percentualComissao =
                    copy.percentualComissao.stripTrailingZeros();
        }

        return ApiClient.getGson().toJson(copy);
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
        return "maquina_" + localId;
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
                    text(R.string.reconcile_machine),
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
