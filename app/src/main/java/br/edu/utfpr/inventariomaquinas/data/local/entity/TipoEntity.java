package br.edu.utfpr.inventariomaquinas.data.local.entity;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "tipos",
        indices = {
                @Index(value = {"serverId"}, unique = true)
        }
)
public class TipoEntity {

    @PrimaryKey(autoGenerate = true)
    public long localId;

    public Integer serverId;

    public String descricao;

    public String syncStatus = "SYNCED";
}