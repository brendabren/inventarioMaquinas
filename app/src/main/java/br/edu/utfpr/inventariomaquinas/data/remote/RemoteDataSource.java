package br.edu.utfpr.inventariomaquinas.data.remote;

import android.os.Looper;

import java.io.IOException;
import java.util.List;

import br.edu.utfpr.inventariomaquinas.data.remote.api.ApiClient;
import br.edu.utfpr.inventariomaquinas.data.remote.api.MaquinasApi;
import br.edu.utfpr.inventariomaquinas.data.remote.dto.MarcaDto;
import br.edu.utfpr.inventariomaquinas.data.remote.dto.MaquinaDto;
import br.edu.utfpr.inventariomaquinas.data.remote.dto.RemoteSnapshot;
import br.edu.utfpr.inventariomaquinas.data.remote.dto.TipoDto;

import retrofit2.Call;
import retrofit2.Response;

public final class RemoteDataSource {

    private final MaquinasApi api;

    public RemoteDataSource() {
        this(ApiClient.getApi());
    }

    public RemoteDataSource(MaquinasApi api) {
        if (api == null) {
            throw new IllegalArgumentException(
                    "MaquinasApi must not be null"
            );
        }

        this.api = api;
    }

    public RemoteSnapshot downloadAll() throws IOException {
        ensureBackgroundThread();

        List<TipoDto> tipos = executeList(
                api.listTipos(),
                "GET tipo"
        );

        List<MarcaDto> marcas = executeList(
                api.listMarcas(),
                "GET marca"
        );

        List<MaquinaDto> maquinas = executeList(
                api.listMaquinas(),
                "GET maquina"
        );

        return new RemoteSnapshot(
                tipos,
                marcas,
                maquinas
        );
    }

    private <T> List<T> executeList(
            Call<List<T>> call,
            String operation
    ) throws IOException {
        Response<List<T>> response = call.execute();

        if (!response.isSuccessful()) {
            if (response.errorBody() != null) {
                response.errorBody().close();
            }

            throw new IOException(
                    operation
                            + " failed: HTTP "
                            + response.code()
            );
        }

        List<T> body = response.body();

        if (body == null) {
            throw new IOException(
                    operation
                            + " returned no list: HTTP "
                            + response.code()
            );
        }

        for (T item : body) {
            if (item == null) {
                throw new IOException(
                        operation
                                + " returned a null list item"
                );
            }
        }

        return body;
    }

    private void ensureBackgroundThread() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException(
                    "downloadAll must run on a background thread"
            );
        }
    }
}