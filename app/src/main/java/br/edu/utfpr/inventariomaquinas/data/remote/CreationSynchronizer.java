package br.edu.utfpr.inventariomaquinas.data.remote;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import com.google.gson.JsonObject;
import br.edu.utfpr.inventariomaquinas.R;
import br.edu.utfpr.inventariomaquinas.data.local.AppDatabase;
import br.edu.utfpr.inventariomaquinas.data.local.SyncStatus;
import br.edu.utfpr.inventariomaquinas.data.local.entity.*;
import br.edu.utfpr.inventariomaquinas.data.remote.api.ApiClient;
import br.edu.utfpr.inventariomaquinas.data.remote.dto.*;
import retrofit2.Response;

public final class CreationSynchronizer {
    private final AppDatabase db;
    private final Context context;
    private final SharedPreferences catalogs;
    private final SharedPreferences machines;
    private final RemoteWriter writer = new RemoteWriter().allowMissingId();

    public CreationSynchronizer(Context context, AppDatabase db) {
        this.context = context.getApplicationContext();
        this.db = db;
        catalogs = this.context.getSharedPreferences("creation_reconciliation", Context.MODE_PRIVATE);
        machines = this.context.getSharedPreferences("machine_creation_reconciliation", Context.MODE_PRIVATE);
    }

    public int synchronize() throws IOException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("Creation must run on a background thread");
        }
        synchronized (TransferCoordinator.LOCK) {
            int count = createTypes();
            count += createBrands();
            count += createMachines();
            return count;
        }
    }

    private IOException pending(String key) {
        return new IOException(context.getString(R.string.creation_needs_review, key));
    }

    private void persistAttempt(SharedPreferences journal, String key, String json, String name) throws IOException {
        SharedPreferences.Editor editor = journal.edit()
                .putBoolean(key + "_attempted", true)
                .putString(key + "_payload", json);
        if (name != null) editor.putString(key + "_name", name);
        if (!editor.commit()) throw new IOException(context.getString(R.string.creation_journal_error));
    }

    private void persistId(SharedPreferences journal, String key, int id) throws IOException {
        if (id <= 0 || !journal.edit().putInt(key + "_serverId", id).commit()) {
            throw pending(key);
        }
    }

    private void complete(SharedPreferences journal, String key) throws IOException {
        if (!journal.edit().putBoolean(key + "_done", true).commit()) {
            throw new IOException(context.getString(R.string.creation_journal_error));
        }
    }

    private <T> T body(Response<T> response) throws IOException {
        try {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("GET: HTTP " + response.code());
            }
            return response.body();
        } finally {
            if (response.errorBody() != null) response.errorBody().close();
        }
    }

    private String fingerprint(Object value) {
        JsonObject json = ApiClient.getGson().toJsonTree(value).getAsJsonObject();
        json.remove("id");
        for (String field : new String[]{"valor", "percentualComissao"}) {
            if (json.has(field) && !json.get(field).isJsonNull()) {
                json.addProperty(field, json.get(field).getAsBigDecimal().stripTrailingZeros());
            }
        }
        return json.toString();
    }

    private int resolveTipoId(TipoDto payload) throws IOException {
        Integer found = null;
        for (TipoDto c : body(ApiClient.getApi().listTipos().execute())) {
            if (c != null && c.id != null && c.id > 0
                    && Objects.equals(c.descricao, payload.descricao)
                    && db.tipoDao().getByServerId(c.id) == null) {
                if (found != null) return 0;
                found = c.id;
            }
        }
        return found == null ? 0 : found;
    }

    private int resolveMarcaId(MarcaDto payload) throws IOException {
        Integer found = null;
        for (MarcaDto c : body(ApiClient.getApi().listMarcas().execute())) {
            if (c != null && c.id != null && c.id > 0
                    && Objects.equals(c.nome, payload.nome)
                    && db.marcaDao().getByServerId(c.id) == null) {
                if (found != null) return 0;
                found = c.id;
            }
        }
        return found == null ? 0 : found;
    }

    private int resolveMaquinaId(MaquinaRequest payload) throws IOException {
        Integer found = null;
        for (MaquinaDto c : body(ApiClient.getApi().listMaquinas().execute())) {
            if (c != null && c.id != null && c.id > 0
                    && Objects.equals(c.descricao, payload.descricao)
                    && Objects.equals(c.anoFabricacao, payload.anoFabricacao)
                    && Objects.equals(c.idTipo, payload.idTipo)
                    && Objects.equals(c.idMarca, payload.idMarca)
                    && db.maquinaDao().getByServerId(c.id) == null) {
                if (found != null) return 0;
                found = c.id;
            }
        }
        return found == null ? 0 : found;
    }

    private int createTypes() throws IOException {
        int count = 0;
        for (TipoEntity item : db.tipoDao().getPendingSync()) {
            TipoEntity snapshot = db.tipoDao().getByLocalId(item.localId);
            if (snapshot == null || snapshot.serverId != null
                    || !SyncStatus.CREATED.equals(snapshot.syncStatus)) continue;
            String key = "tipo_" + snapshot.localId;
            TipoDto payload;
            int id;
            if (catalogs.getBoolean(key + "_attempted", false)) {
                id = catalogs.getInt(key + "_serverId", 0);
                String stored = catalogs.getString(key + "_payload", null);
                if (stored == null) throw pending(key);
                payload = ApiClient.getGson().fromJson(stored, TipoDto.class);
                if (payload == null) throw pending(key);
                if (id <= 0) {
                    id = resolveTipoId(payload);
                    persistId(catalogs, key, id);
                }
            } else {
                payload = SyncPayloadMapper.toTipo(snapshot);
                for (TipoDto candidate : body(ApiClient.getApi().listTipos().execute())) {
                    if (candidate != null && candidate.id != null && candidate.id > 0
                            && (Objects.equals(candidate.descricao, payload.descricao))) throw pending(key);
                }
                persistAttempt(catalogs, key, ApiClient.getGson().toJson(payload), payload.descricao);
                try {
                    id = writer.createTipo(payload);
                    if (id <= 0) id = resolveTipoId(payload);
                    persistId(catalogs, key, id);
                } catch (IOException exception) {
                    throw new IOException(context.getString(R.string.creation_needs_review, key)
                            + " " + exception.getMessage(), exception);
                }
            }
            TipoDto remote = body(ApiClient.getApi().getTipo(id).execute());
            if (!Objects.equals(remote.id, id)
                    || !fingerprint(payload).equals(fingerprint(remote))) throw pending(key);
            final int serverId = id;
            db.runInTransaction((Runnable) () -> {
                TipoEntity current = db.tipoDao().getByLocalId(snapshot.localId);
                if (current == null || current.serverId != null
                        || !SyncStatus.CREATED.equals(current.syncStatus)) {
                    throw new IllegalStateException("Local record changed: " + key);
                }
                if (db.tipoDao().getByServerId(serverId) != null) {
                    throw new IllegalStateException("Server ID already linked; manual merge required: " + key);
                }
                current.serverId = serverId;
                current.syncStatus = SyncStatus.UPDATED;
                if (db.tipoDao().update(current) != 1) {
                    throw new IllegalStateException("Could not link: " + key);
                }
            });
            complete(catalogs, key);
            count++;
        }
        return count;
    }

    private int createBrands() throws IOException {
        int count = 0;
        for (MarcaEntity item : db.marcaDao().getPendingSync()) {
            MarcaEntity snapshot = db.marcaDao().getByLocalId(item.localId);
            if (snapshot == null || snapshot.serverId != null
                    || !SyncStatus.CREATED.equals(snapshot.syncStatus)) continue;
            String key = "marca_" + snapshot.localId;
            MarcaDto payload;
            int id;
            if (catalogs.getBoolean(key + "_attempted", false)) {
                id = catalogs.getInt(key + "_serverId", 0);
                String stored = catalogs.getString(key + "_payload", null);
                if (stored == null) throw pending(key);
                payload = ApiClient.getGson().fromJson(stored, MarcaDto.class);
                if (payload == null) throw pending(key);
                if (id <= 0) {
                    id = resolveMarcaId(payload);
                    persistId(catalogs, key, id);
                }
            } else {
                payload = SyncPayloadMapper.toMarca(snapshot);
                for (MarcaDto candidate : body(ApiClient.getApi().listMarcas().execute())) {
                    if (candidate != null && candidate.id != null && candidate.id > 0
                            && (Objects.equals(candidate.nome, payload.nome))) throw pending(key);
                }
                persistAttempt(catalogs, key, ApiClient.getGson().toJson(payload), payload.nome);
                try {
                    id = writer.createMarca(payload);
                    if (id <= 0) id = resolveMarcaId(payload);
                    persistId(catalogs, key, id);
                } catch (IOException exception) {
                    throw new IOException(context.getString(R.string.creation_needs_review, key)
                            + " " + exception.getMessage(), exception);
                }
            }
            MarcaDto remote = body(ApiClient.getApi().getMarca(id).execute());
            if (!Objects.equals(remote.id, id)
                    || !fingerprint(payload).equals(fingerprint(remote))) throw pending(key);
            final int serverId = id;
            db.runInTransaction((Runnable) () -> {
                MarcaEntity current = db.marcaDao().getByLocalId(snapshot.localId);
                if (current == null || current.serverId != null
                        || !SyncStatus.CREATED.equals(current.syncStatus)) {
                    throw new IllegalStateException("Local record changed: " + key);
                }
                if (db.marcaDao().getByServerId(serverId) != null) {
                    throw new IllegalStateException("Server ID already linked; manual merge required: " + key);
                }
                current.serverId = serverId;
                current.syncStatus = SyncStatus.UPDATED;
                if (db.marcaDao().update(current) != 1) {
                    throw new IllegalStateException("Could not link: " + key);
                }
            });
            complete(catalogs, key);
            count++;
        }
        return count;
    }

    private int createMachines() throws IOException {
        int count = 0;
        for (MaquinaEntity item : db.maquinaDao().getPendingSync()) {
            MaquinaEntity snapshot = db.maquinaDao().getByLocalId(item.localId);
            if (snapshot == null || snapshot.serverId != null
                    || !SyncStatus.CREATED.equals(snapshot.syncStatus)) continue;
            String key = "maquina_" + snapshot.localId;
            MaquinaRequest payload;
            int id;
            if (machines.getBoolean(key + "_attempted", false)) {
                id = machines.getInt(key + "_serverId", 0);
                String stored = machines.getString(key + "_payload", null);
                if (stored == null) throw pending(key);
                payload = ApiClient.getGson().fromJson(stored, MaquinaRequest.class);
                if (payload == null) throw pending(key);
                if (id <= 0) {
                    id = resolveMaquinaId(payload);
                    persistId(machines, key, id);
                }
            } else {
                payload = SyncPayloadMapper.toMaquina(snapshot, db.tipoDao().getByLocalId(snapshot.tipoLocalId), db.marcaDao().getByLocalId(snapshot.marcaLocalId));
                for (MaquinaDto candidate : body(ApiClient.getApi().listMaquinas().execute())) {
                    if (candidate != null && candidate.id != null && candidate.id > 0
                            && (Objects.equals(candidate.descricao, payload.descricao) && Objects.equals(candidate.anoFabricacao, payload.anoFabricacao) && Objects.equals(candidate.idTipo, payload.idTipo) && Objects.equals(candidate.idMarca, payload.idMarca))) throw pending(key);
                }
                persistAttempt(machines, key, ApiClient.getGson().toJson(payload), null);
                try {
                    id = writer.createMaquina(payload);
                    if (id <= 0) id = resolveMaquinaId(payload);
                    persistId(machines, key, id);
                } catch (IOException exception) {
                    throw new IOException(context.getString(R.string.creation_needs_review, key)
                            + " " + exception.getMessage(), exception);
                }
            }
            MaquinaDto remote = body(ApiClient.getApi().getMaquina(id).execute());
            if (!Objects.equals(remote.id, id)
                    || !fingerprint(payload).equals(fingerprint(ApiClient.getGson().fromJson(ApiClient.getGson().toJson(remote), MaquinaRequest.class)))) throw pending(key);
            final int serverId = id;
            db.runInTransaction((Runnable) () -> {
                MaquinaEntity current = db.maquinaDao().getByLocalId(snapshot.localId);
                if (current == null || current.serverId != null
                        || !SyncStatus.CREATED.equals(current.syncStatus)) {
                    throw new IllegalStateException("Local record changed: " + key);
                }
                if (db.maquinaDao().getByServerId(serverId) != null) {
                    throw new IllegalStateException("Server ID already linked; manual merge required: " + key);
                }
                current.serverId = serverId;
                current.syncStatus = SyncStatus.UPDATED;
                if (db.maquinaDao().update(current) != 1) {
                    throw new IllegalStateException("Could not link: " + key);
                }
            });
            complete(machines, key);
            count++;
        }
        return count;
    }

    public void ensureDownloadAllowed() throws IOException {
        for (TipoEntity item : db.tipoDao().getPendingSync()) {
            if (item.serverId == null) throw new IOException(context.getString(R.string.download_new_pending));
        }
        for (MarcaEntity item : db.marcaDao().getPendingSync()) {
            if (item.serverId == null) throw new IOException(context.getString(R.string.download_new_pending));
        }
        for (MaquinaEntity item : db.maquinaDao().getPendingSync()) {
            if (item.serverId == null) throw new IOException(context.getString(R.string.download_new_pending));
        }
        checkJournal(catalogs);
        checkJournal(machines);
    }

    private void checkJournal(SharedPreferences journal) throws IOException {
        for (Map.Entry<String, ?> entry : journal.getAll().entrySet()) {
            String name = entry.getKey();
            if (!name.endsWith("_attempted") || !Boolean.TRUE.equals(entry.getValue())) continue;
            String key = name.substring(0, name.length() - "_attempted".length());
            if (journal.getBoolean(key + "_done", false)) continue;
            int split = key.lastIndexOf('_');
            if (split < 0) throw pending(key);
            long localId;
            try { localId = Long.parseLong(key.substring(split + 1)); }
            catch (NumberFormatException e) { throw pending(key); }
            Integer serverId = null;
            if (key.startsWith("tipo_")) {
                TipoEntity item = db.tipoDao().getByLocalId(localId);
                if (item != null) serverId = item.serverId;
            } else if (key.startsWith("marca_")) {
                MarcaEntity item = db.marcaDao().getByLocalId(localId);
                if (item != null) serverId = item.serverId;
            } else if (key.startsWith("maquina_")) {
                MaquinaEntity item = db.maquinaDao().getByLocalId(localId);
                if (item != null) serverId = item.serverId;
            }
            if (serverId == null || serverId <= 0) throw pending(key);
        }
    }
}
