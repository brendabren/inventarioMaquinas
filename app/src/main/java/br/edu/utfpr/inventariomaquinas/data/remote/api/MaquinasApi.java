package br.edu.utfpr.inventariomaquinas.data.remote.api;

import java.math.BigDecimal;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

import br.edu.utfpr.inventariomaquinas.data.remote.dto.MarcaDto;
import br.edu.utfpr.inventariomaquinas.data.remote.dto.MaquinaDto;
import br.edu.utfpr.inventariomaquinas.data.remote.dto.MaquinaRequest;
import br.edu.utfpr.inventariomaquinas.data.remote.dto.TipoDto;

public interface MaquinasApi {

    @GET("tipo")
    Call<List<TipoDto>> listTipos();

    @GET("tipo/{id}")
    Call<TipoDto> getTipo(@Path("id") int id);

    @POST("tipo")
    Call<ResponseBody> createTipo(@Body TipoDto tipo);

    @PUT("tipo/{id}")
    Call<ResponseBody> updateTipo(
            @Path("id") int id,
            @Body TipoDto tipo
    );

    @DELETE("tipo/{id}")
    Call<ResponseBody> deleteTipo(@Path("id") int id);


    @GET("marca")
    Call<List<MarcaDto>> listMarcas();

    @GET("marca/{id}")
    Call<MarcaDto> getMarca(@Path("id") int id);

    @POST("marca")
    Call<ResponseBody> createMarca(@Body MarcaDto marca);

    @PUT("marca/{id}")
    Call<ResponseBody> updateMarca(
            @Path("id") int id,
            @Body MarcaDto marca
    );

    @DELETE("marca/{id}")
    Call<ResponseBody> deleteMarca(@Path("id") int id);
    
    @GET("maquina")
    Call<List<MaquinaDto>> listMaquinas();

    @GET("maquina")
    Call<List<MaquinaDto>> filterMaquinas(
            @Query("valorDe") BigDecimal valorDe,
            @Query("valorAte") BigDecimal valorAte,
            @Query("status") String status,
            @Query("idTipo") Integer idTipo,
            @Query("idMarca") Integer idMarca
    );

    @GET("maquina/{id}")
    Call<MaquinaDto> getMaquina(@Path("id") int id);

    @POST("maquina")
    Call<ResponseBody> createMaquina(
            @Body MaquinaRequest maquina
    );

    @PUT("maquina/{id}")
    Call<ResponseBody> updateMaquina(
            @Path("id") int id,
            @Body MaquinaRequest maquina
    );

    @DELETE("maquina/{id}")
    Call<ResponseBody> deleteMaquina(@Path("id") int id);
}