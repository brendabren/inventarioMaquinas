package br.edu.utfpr.inventariomaquinas.data.remote;

import android.os.Looper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.math.BigDecimal;

import br.edu.utfpr.inventariomaquinas.data.remote.api.ApiClient;
import br.edu.utfpr.inventariomaquinas.data.remote.api.MaquinasApi;
import br.edu.utfpr.inventariomaquinas.data.remote.dto.MarcaDto;
import br.edu.utfpr.inventariomaquinas.data.remote.dto.MaquinaRequest;
import br.edu.utfpr.inventariomaquinas.data.remote.dto.TipoDto;

import okhttp3.ResponseBody;
import okhttp3.HttpUrl;
import android.util.Log;
import retrofit2.Call;
import retrofit2.Response;

public final class RemoteWriter {

    private final MaquinasApi api;

    private boolean lenient;

    public RemoteWriter() {
        this(ApiClient.getApi());
    }

    public RemoteWriter(MaquinasApi api) {
        if (api == null) {
            throw new IllegalArgumentException(
                    "MaquinasApi must not be null"
            );
        }

        this.api = api;
    }

    public RemoteWriter allowMissingId() {
        lenient = true;

        return this;
    }

    public int createTipo(TipoDto dto) throws IOException {
        if (dto == null) {
            throw new IllegalArgumentException("Missing tipo");
        }

        requireNewRecord(dto.id);

        return executeCreate(
                api.createTipo(dto),
                "POST tipo"
        );
    }

    public void updateTipo(TipoDto dto) throws IOException {
        if (dto == null) {
            throw new IllegalArgumentException("Missing tipo");
        }

        int id = requireId(dto.id);

        executeChange(
                api.updateTipo(id, dto),
                "PUT tipo/" + id
        );
    }

    public void deleteTipo(int id) throws IOException {
        requireId(id);

        executeChange(
                api.deleteTipo(id),
                "DELETE tipo/" + id
        );
    }

    public int createMarca(MarcaDto dto) throws IOException {
        if (dto == null) {
            throw new IllegalArgumentException("Missing marca");
        }

        requireNewRecord(dto.id);

        return executeCreate(
                api.createMarca(dto),
                "POST marca"
        );
    }

    public void updateMarca(MarcaDto dto) throws IOException {
        if (dto == null) {
            throw new IllegalArgumentException("Missing marca");
        }

        int id = requireId(dto.id);

        executeChange(
                api.updateMarca(id, dto),
                "PUT marca/" + id
        );
    }

    public void deleteMarca(int id) throws IOException {
        requireId(id);

        executeChange(
                api.deleteMarca(id),
                "DELETE marca/" + id
        );
    }

    public int createMaquina(
            MaquinaRequest request
    ) throws IOException {
        if (request == null) {
            throw new IllegalArgumentException("Missing maquina");
        }

        requireNewRecord(request.id);
        requireId(request.idTipo);
        requireId(request.idMarca);

        return executeCreate(
                api.createMaquina(request),
                "POST maquina"
        );
    }

    public void updateMaquina(
            MaquinaRequest request
    ) throws IOException {
        if (request == null) {
            throw new IllegalArgumentException("Missing maquina");
        }

        int id = requireId(request.id);

        requireId(request.idTipo);
        requireId(request.idMarca);

        executeChange(
                api.updateMaquina(id, request),
                "PUT maquina/" + id
        );
    }

    public void deleteMaquina(int id) throws IOException {
        requireId(id);

        executeChange(
                api.deleteMaquina(id),
                "DELETE maquina/" + id
        );
    }

    private int executeCreate(
            Call<ResponseBody> call,
            String operation
    ) throws IOException {
        ensureBackgroundThread();

        Response<ResponseBody> response = call.execute();

        try {
            requireSuccess(response, operation);

            ResponseBody body = response.body();
            String json = body == null ? "" : body.string();
            Integer bodyId = null;
            IOException bodyFailure = null;
            if (!json.trim().isEmpty()) {
                try {
                    bodyId = parseCreatedId(json, operation);
                } catch (IOException exception) {
                    bodyFailure = exception;
                }
            }

            Integer locationId = createdIdFromLocation(response);
            if (bodyId != null && locationId != null && !bodyId.equals(locationId)) {
                throw new IOException(operation +
                        " returned conflicting IDs in body and Location. Do not repeat POST.");
            }
            if (bodyId != null) return bodyId;
            if (locationId != null) return locationId;

            String location = response.headers().get("Location");
            Log.e("RemoteWriter", operation + " HTTP " + response.code()
                    + "; emptyBody=" + json.trim().isEmpty()
                    + "; LocationPresent=" + (location != null)
                    + "; usableLocationId=false");
            if (bodyFailure != null) throw bodyFailure;
            if (lenient) return 0;
            throw missingCreatedId(operation);

        } finally {
            closeBodies(response);
        }
    }

    private Integer createdIdFromLocation(Response<?> response) {
        String location = response.headers().get("Location");
        if (location == null || location.trim().isEmpty()) return null;
        HttpUrl requestUrl = response.raw().request().url();
        HttpUrl createdUrl = requestUrl.resolve(location.trim());
        if (createdUrl == null
                || !requestUrl.scheme().equals(createdUrl.scheme())
                || !requestUrl.host().equals(createdUrl.host())
                || requestUrl.port() != createdUrl.port()) return null;
        String collection = requestUrl.encodedPath();
        while (collection.endsWith("/")) {
            collection = collection.substring(0, collection.length() - 1);
        }
        String prefix = collection + "/";
        String path = createdUrl.encodedPath();
        if (!path.startsWith(prefix)) return null;
        String idPart = path.substring(prefix.length());
        if (idPart.endsWith("/")) idPart = idPart.substring(0, idPart.length() - 1);
        if (!idPart.matches("[0-9]+")) return null;
        try {
            int id = Integer.parseInt(idPart);
            return id > 0 ? id : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void executeChange(
            Call<ResponseBody> call,
            String operation
    ) throws IOException {
        ensureBackgroundThread();

        Response<ResponseBody> response = call.execute();

        try {
            requireSuccess(response, operation);
        } finally {
            closeBodies(response);
        }
    }

    private int parseCreatedId(
            String json,
            String operation
    ) throws IOException {
        try {
            JsonElement root = JsonParser.parseString(json);
            JsonElement idElement;

            if (root.isJsonObject()) {
                JsonObject object = root.getAsJsonObject();
                idElement = object.get("id");
            } else {
                idElement = root;
            }

            if (idElement == null
                    || !idElement.isJsonPrimitive()) {
                throw missingCreatedId(operation);
            }

            int id = new BigDecimal(
                    idElement.getAsString()
            ).intValueExact();

            if (id <= 0) {
                throw missingCreatedId(operation);
            }

            return id;

        } catch (RuntimeException exception) {
            throw new IOException(
                    operation
                            + " succeeded, but its returned ID "
                            + "could not be interpreted. "
                            + "Do not automatically repeat this POST.",
                    exception
            );
        }
    }

    private void requireSuccess(
            Response<?> response,
            String operation
    ) throws IOException {
        if (!response.isSuccessful()) {
            throw new IOException(
                    operation
                            + " failed: HTTP "
                            + response.code()
            );
        }

        if (response.code() == 202) {
            throw new IOException(
                    operation
                            + " was accepted, but completion "
                            + "has not been confirmed"
            );
        }
    }

    private void closeBodies(Response<ResponseBody> response) {
        if (response.body() != null) {
            response.body().close();
        }

        if (response.errorBody() != null) {
            response.errorBody().close();
        }
    }

    private IOException missingCreatedId(String operation) {
        return new IOException(
                operation
                        + " succeeded, but no usable ID was returned. "
                        + "Check the server before repeating this POST."
        );
    }

    private void requireNewRecord(Integer id) {
        if (id != null) {
            throw new IllegalArgumentException(
                    "POST must not include an ID"
            );
        }
    }

    private int requireId(Integer id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "A positive server ID is required"
            );
        }

        return id;
    }

    private void ensureBackgroundThread() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException(
                    "Remote writes must run on a background thread"
            );
        }
    }
}
