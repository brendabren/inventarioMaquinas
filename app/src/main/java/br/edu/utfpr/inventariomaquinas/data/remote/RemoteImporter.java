package br.edu.utfpr.inventariomaquinas.data.remote;

import android.os.Looper;

import java.math.BigDecimal;

import br.edu.utfpr.inventariomaquinas.data.local.AppDatabase;
import br.edu.utfpr.inventariomaquinas.data.local.entity.MarcaEntity;
import br.edu.utfpr.inventariomaquinas.data.local.entity.MaquinaEntity;
import br.edu.utfpr.inventariomaquinas.data.local.entity.TipoEntity;
import br.edu.utfpr.inventariomaquinas.data.remote.dto.MarcaDto;
import br.edu.utfpr.inventariomaquinas.data.remote.dto.MaquinaDto;
import br.edu.utfpr.inventariomaquinas.data.remote.dto.RemoteSnapshot;
import br.edu.utfpr.inventariomaquinas.data.remote.dto.TipoDto;

public final class RemoteImporter {

    private final AppDatabase database;

    public RemoteImporter(AppDatabase database) {
        if (database == null) {
            throw new IllegalArgumentException(
                    "AppDatabase must not be null"
            );
        }

        this.database = database;
    }

    public void importSnapshot(RemoteSnapshot snapshot) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException(
                    "Import must run on a background thread"
            );
        }

        if (snapshot == null) {
            throw new IllegalArgumentException(
                    "RemoteSnapshot must not be null"
            );
        }

        synchronized (TransferCoordinator.LOCK) {
        database.runInTransaction(() -> {
            // Reject the entire snapshot rather than importing a second copy.
            for (TipoEntity item : database.tipoDao().getPendingSync()) {
                if (item.serverId == null) throw invalid("Sync or link new local types before downloading");
            }
            for (MarcaEntity item : database.marcaDao().getPendingSync()) {
                if (item.serverId == null) throw invalid("Sync or link new local brands before downloading");
            }
            for (MaquinaEntity item : database.maquinaDao().getPendingSync()) {
                if (item.serverId == null) throw invalid("Sync or link new local machines before downloading");
            }
            for (TipoDto dto : snapshot.tipos) {
                importTipo(dto);
            }

            for (MarcaDto dto : snapshot.marcas) {
                importMarca(dto);
            }

            for (MaquinaDto dto : snapshot.maquinas) {
                importMaquina(dto);
            }
        });
        }
    }

    private void importTipo(TipoDto dto) {
        if (dto == null) {
            throw invalid("Null tipo");
        }

        int serverId = requireId(dto.id, "tipo.id");

        TipoEntity entity =
                database.tipoDao().getByServerId(serverId);

        if (entity != null && !isSynced(entity.syncStatus)) {
            return;
        }

        requireText(dto.descricao, "tipo.descricao");

        boolean insert = entity == null;

        if (insert) {
            entity = new TipoEntity();
        }

        entity.serverId = serverId;
        entity.descricao = dto.descricao;
        entity.syncStatus = "SYNCED";

        if (insert) {
            database.tipoDao().insert(entity);
        } else {
            database.tipoDao().update(entity);
        }
    }

    private void importMarca(MarcaDto dto) {
        if (dto == null) {
            throw invalid("Null marca");
        }

        int serverId = requireId(dto.id, "marca.id");

        MarcaEntity entity =
                database.marcaDao().getByServerId(serverId);

        if (entity != null && !isSynced(entity.syncStatus)) {
            return;
        }

        requireText(dto.nome, "marca.nome");

        boolean insert = entity == null;

        if (insert) {
            entity = new MarcaEntity();
        }

        entity.serverId = serverId;
        entity.nome = dto.nome;
        entity.syncStatus = "SYNCED";

        if (insert) {
            database.marcaDao().insert(entity);
        } else {
            database.marcaDao().update(entity);
        }
    }

    private void importMaquina(MaquinaDto dto) {
        if (dto == null) {
            throw invalid("Null maquina");
        }

        int serverId = requireId(dto.id, "maquina.id");

        MaquinaEntity entity =
                database.maquinaDao().getByServerId(serverId);

        if (entity != null && !isSynced(entity.syncStatus)) {
            return;
        }

        int tipoId = requireId(dto.idTipo, "maquina.idTipo");
        int marcaId = requireId(dto.idMarca, "maquina.idMarca");

        TipoEntity tipo =
                database.tipoDao().getByServerId(tipoId);

        MarcaEntity marca =
                database.marcaDao().getByServerId(marcaId);

        if (tipo == null || marca == null) {
            throw invalid(
                    "Missing tipo or marca for maquina " + serverId
            );
        }

        if ("DELETED".equals(tipo.syncStatus)
                || "DELETED".equals(marca.syncStatus)) {
            throw invalid(
                    "Maquina references a locally deleted parent: "
                            + serverId
            );
        }

        requireText(dto.dataInclusao, "maquina.dataInclusao");
        requireText(dto.descricao, "maquina.descricao");
        requireText(dto.nomeProprietario, "maquina.nomeProprietario");
        requireText(
                dto.contatoProprietario,
                "maquina.contatoProprietario"
        );

        if (dto.anoFabricacao == null) {
            throw invalid("Missing maquina.anoFabricacao");
        }

        if (!"D".equals(dto.status)
                && !"N".equals(dto.status)
                && !"R".equals(dto.status)
                && !"V".equals(dto.status)) {
            throw invalid("Invalid maquina.status");
        }

        long valor = toHundredths(dto.valor, "maquina.valor");

        long comissao = toHundredths(
                dto.percentualComissao,
                "maquina.percentualComissao"
        );

        if (comissao > 10000) {
            throw invalid("Commission must not exceed 100 percent");
        }

        boolean insert = entity == null;

        if (insert) {
            entity = new MaquinaEntity();
        }

        entity.serverId = serverId;
        entity.tipoLocalId = tipo.localId;
        entity.marcaLocalId = marca.localId;
        entity.dataInclusao = dto.dataInclusao;
        entity.descricao = dto.descricao;
        entity.anoFabricacao = dto.anoFabricacao;
        entity.valorCentavos = valor;
        entity.nomeProprietario = dto.nomeProprietario;
        entity.contatoProprietario = dto.contatoProprietario;
        entity.percentualComissaoCentesimos = (int) comissao;
        entity.status = dto.status;
        entity.syncStatus = "SYNCED";

        if (insert) {
            database.maquinaDao().insert(entity);
        } else {
            database.maquinaDao().update(entity);
        }
    }

    private boolean isSynced(String status) {
        return "SYNCED".equals(status);
    }

    private int requireId(Integer value, String field) {
        if (value == null || value <= 0) {
            throw invalid("Invalid or missing " + field);
        }

        return value;
    }

    private void requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw invalid("Invalid or missing " + field);
        }
    }

    private long toHundredths(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw invalid("Invalid or missing " + field);
        }

        try {
            return value.movePointRight(2).longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Invalid precision or out-of-range value: " + field,
                    exception
            );
        }
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
