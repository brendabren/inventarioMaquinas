package br.edu.utfpr.inventariomaquinas.data.local.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "maquinas",
        foreignKeys = {
                @ForeignKey(
                        entity = TipoEntity.class,
                        parentColumns = "localId",
                        childColumns = "tipoLocalId",
                        onDelete = ForeignKey.RESTRICT
                ),
                @ForeignKey(
                        entity = MarcaEntity.class,
                        parentColumns = "localId",
                        childColumns = "marcaLocalId",
                        onDelete = ForeignKey.RESTRICT
                )
        },
        indices = {
                @Index(value = {"serverId"}, unique = true),
                @Index(value = {"tipoLocalId"}),
                @Index(value = {"marcaLocalId"})
        }
)
public class MaquinaEntity {

    @PrimaryKey(autoGenerate = true)
    public long localId;

    public Integer serverId;

    public long tipoLocalId;

    public long marcaLocalId;

    public String dataInclusao;

    public String descricao;

    public int anoFabricacao;

    public long valorCentavos;

    public String nomeProprietario;

    public String contatoProprietario;

    public int percentualComissaoCentesimos;

    public String status = "D";

    public String syncStatus = "SYNCED";
}