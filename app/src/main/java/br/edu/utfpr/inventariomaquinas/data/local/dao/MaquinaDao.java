package br.edu.utfpr.inventariomaquinas.data.local.dao;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import br.edu.utfpr.inventariomaquinas.data.local.entity.MaquinaEntity;

@Dao
public interface MaquinaDao {

    @Insert
    long insert(MaquinaEntity maquina);

    @Update
    int update(MaquinaEntity maquina);

    @Delete
    int delete(MaquinaEntity maquina);

    @Query(
            "SELECT * FROM maquinas " +
                    "WHERE syncStatus != 'DELETED' " +
                    "ORDER BY dataInclusao DESC, localId DESC"
    )
    LiveData<List<MaquinaEntity>> observeAll();

    @Query(
            "SELECT * FROM maquinas " +
                    "WHERE syncStatus != 'DELETED' " +
                    "AND (:tipoLocalId IS NULL OR tipoLocalId = :tipoLocalId) " +
                    "AND (:marcaLocalId IS NULL OR marcaLocalId = :marcaLocalId) " +
                    "AND (:valorDeCentavos IS NULL OR valorCentavos >= :valorDeCentavos) " +
                    "AND (:valorAteCentavos IS NULL OR valorCentavos <= :valorAteCentavos) " +
                    "AND (:status IS NULL OR status = :status) " +
                    "ORDER BY dataInclusao DESC, localId DESC"
    )
    LiveData<List<MaquinaEntity>> observeFiltered(
            @Nullable Long tipoLocalId,
            @Nullable Long marcaLocalId,
            @Nullable Long valorDeCentavos,
            @Nullable Long valorAteCentavos,
            @Nullable String status
    );

    @Query("SELECT * FROM maquinas WHERE localId = :localId LIMIT 1")
    MaquinaEntity getByLocalId(long localId);

    @Query("SELECT * FROM maquinas WHERE serverId = :serverId LIMIT 1")
    MaquinaEntity getByServerId(int serverId);

    @Query(
            "SELECT * FROM maquinas " +
                    "WHERE syncStatus != 'SYNCED' " +
                    "ORDER BY localId ASC"
    )
    List<MaquinaEntity> getPendingSync();

    @Query(
            "UPDATE maquinas " +
                    "SET syncStatus = 'DELETED' " +
                    "WHERE localId = :localId"
    )
    int markAsDeleted(long localId);
    @Query(
            "SELECT * FROM maquinas " +
                    "WHERE localId = :localId " +
                    "AND syncStatus != 'DELETED' " +
                    "LIMIT 1"
    )
    LiveData<MaquinaEntity> observeByLocalId(long localId);

    @Query("SELECT COUNT(*) FROM maquinas")
    int countAll();
}
