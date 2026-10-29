package br.edu.utfpr.inventariomaquinas.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

import br.edu.utfpr.inventariomaquinas.data.local.entity.MarcaEntity;

@Dao
public interface MarcaDao {

    @Insert
    long insert(MarcaEntity marca);

    @Update
    int update(MarcaEntity marca);

    @Delete
    int delete(MarcaEntity marca);

    @Query(
            "SELECT * FROM marcas " +
                    "WHERE syncStatus != 'DELETED' " +
                    "ORDER BY nome COLLATE NOCASE ASC"
    )
    LiveData<List<MarcaEntity>> observeAll();

    @Query(
            "SELECT * FROM marcas " +
                    "WHERE syncStatus != 'DELETED' " +
                    "ORDER BY nome COLLATE NOCASE ASC"
    )
    List<MarcaEntity> getAllActive();

    @Query("SELECT * FROM marcas WHERE localId = :localId LIMIT 1")
    MarcaEntity getByLocalId(long localId);

    @Query("SELECT * FROM marcas WHERE serverId = :serverId LIMIT 1")
    MarcaEntity getByServerId(int serverId);

    @Query(
            "SELECT * FROM marcas " +
                    "WHERE syncStatus != 'SYNCED' " +
                    "ORDER BY localId ASC"
    )
    List<MarcaEntity> getPendingSync();

    @Query(
            "UPDATE marcas " +
                    "SET syncStatus = 'DELETED' " +
                    "WHERE localId = :localId"
    )
    int markAsDeleted(long localId);

    @Query(
            "SELECT COUNT(*) FROM maquinas " +
                    "WHERE marcaLocalId = :marcaLocalId " +
                    "AND syncStatus != 'DELETED'"
    )
    int countActiveMachines(long marcaLocalId);

    @Query(
            "SELECT COUNT(*) FROM maquinas " +
                    "WHERE marcaLocalId = :marcaLocalId"
    )
    int countAllMachines(long marcaLocalId);

    @Query("SELECT COUNT(*) FROM marcas")
    int countAll();
}
