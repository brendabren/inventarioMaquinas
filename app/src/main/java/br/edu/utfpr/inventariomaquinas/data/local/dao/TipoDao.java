package br.edu.utfpr.inventariomaquinas.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import br.edu.utfpr.inventariomaquinas.data.local.entity.TipoEntity;

@Dao
public interface TipoDao {

    @Insert
    long insert(TipoEntity tipo);

    @Update
    int update(TipoEntity tipo);

    @Delete
    int delete(TipoEntity tipo);

    @Query(
            "SELECT * FROM tipos " +
                    "WHERE syncStatus != 'DELETED' " +
                    "ORDER BY descricao COLLATE NOCASE ASC"
    )
    LiveData<List<TipoEntity>> observeAll();

    @Query(
            "SELECT * FROM tipos " +
                    "WHERE syncStatus != 'DELETED' " +
                    "ORDER BY descricao COLLATE NOCASE ASC"
    )
    List<TipoEntity> getAllActive();

    @Query("SELECT * FROM tipos WHERE localId = :localId LIMIT 1")
    TipoEntity getByLocalId(long localId);

    @Query("SELECT * FROM tipos WHERE serverId = :serverId LIMIT 1")
    TipoEntity getByServerId(int serverId);

    @Query(
            "SELECT * FROM tipos " +
                    "WHERE syncStatus != 'SYNCED' " +
                    "ORDER BY localId ASC"
    )
    List<TipoEntity> getPendingSync();

    @Query(
            "UPDATE tipos " +
                    "SET syncStatus = 'DELETED' " +
                    "WHERE localId = :localId"
    )
    int markAsDeleted(long localId);

    @Query(
            "SELECT COUNT(*) FROM maquinas " +
                    "WHERE tipoLocalId = :tipoLocalId " +
                    "AND syncStatus != 'DELETED'"
    )
    int countActiveMachines(long tipoLocalId);

    @Query(
            "SELECT COUNT(*) FROM maquinas " +
                    "WHERE tipoLocalId = :tipoLocalId"
    )
    int countAllMachines(long tipoLocalId);

    @Query("SELECT COUNT(*) FROM tipos")
    int countAll();
}
