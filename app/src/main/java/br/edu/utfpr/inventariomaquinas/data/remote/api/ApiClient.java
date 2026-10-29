package br.edu.utfpr.inventariomaquinas.data.remote.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiClient {

    public static final String BASE_URL =
            "https://argo.td.utfpr.edu.br/maquinas/ws/";

    private static final Gson GSON =
            new GsonBuilder().create();

    private static final OkHttpClient HTTP_CLIENT =
            new OkHttpClient.Builder()
                    .retryOnConnectionFailure(false)
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();

    private static final Retrofit RETROFIT =
            new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(HTTP_CLIENT)
                    .addConverterFactory(
                            GsonConverterFactory.create(GSON)
                    )
                    .build();

    private static final MaquinasApi API =
            RETROFIT.create(MaquinasApi.class);

    private ApiClient() {
    }

    public static MaquinasApi getApi() {
        return API;
    }

    public static Gson getGson() {
        return GSON;
    }
}
