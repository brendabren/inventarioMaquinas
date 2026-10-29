package br.edu.utfpr.inventariomaquinas.data.remote;

import java.math.BigDecimal;

import br.edu.utfpr.inventariomaquinas.data.local.entity.MarcaEntity;
import br.edu.utfpr.inventariomaquinas.data.local.entity.MaquinaEntity;
import br.edu.utfpr.inventariomaquinas.data.local.entity.TipoEntity;
import br.edu.utfpr.inventariomaquinas.data.remote.dto.MarcaDto;
import br.edu.utfpr.inventariomaquinas.data.remote.dto.MaquinaRequest;
import br.edu.utfpr.inventariomaquinas.data.remote.dto.TipoDto;

public final class SyncPayloadMapper {

    private SyncPayloadMapper() {
    }

    public static TipoDto toTipo(TipoEntity entity) {
        if (entity == null) {
            throw invalid("Tipo must not be null");
        }

        requireNotDeleted(entity.syncStatus);
        requireOptionalServerId(entity.serverId);
        requireText(entity.descricao, "tipo.descricao");

        TipoDto dto = new TipoDto();

        dto.id = entity.serverId;
        dto.descricao = entity.descricao;

        return dto;
    }

    public static MarcaDto toMarca(MarcaEntity entity) {
        if (entity == null) {
            throw invalid("Marca must not be null");
        }

        requireNotDeleted(entity.syncStatus);
        requireOptionalServerId(entity.serverId);
        requireText(entity.nome, "marca.nome");

        MarcaDto dto = new MarcaDto();

        dto.id = entity.serverId;
        dto.nome = entity.nome;

        return dto;
    }

    public static MaquinaRequest toMaquina(
            MaquinaEntity entity,
            TipoEntity tipo,
            MarcaEntity marca
    ) {
        if (entity == null || tipo == null || marca == null) {
            throw invalid(
                    "Maquina, tipo and marca must not be null"
            );
        }

        requireNotDeleted(entity.syncStatus);
        requireNotDeleted(tipo.syncStatus);
        requireNotDeleted(marca.syncStatus);

        requireOptionalServerId(entity.serverId);

        if (entity.tipoLocalId != tipo.localId
                || entity.marcaLocalId != marca.localId) {
            throw invalid(
                    "Tipo or marca does not match the maquina"
            );
        }

        requireRemoteParentId(tipo.serverId, "tipo");
        requireRemoteParentId(marca.serverId, "marca");

        requireText(entity.dataInclusao, "maquina.dataInclusao");
        requireText(entity.descricao, "maquina.descricao");
        requireText(
                entity.nomeProprietario,
                "maquina.nomeProprietario"
        );
        requireText(
                entity.contatoProprietario,
                "maquina.contatoProprietario"
        );

        if (entity.valorCentavos < 0) {
            throw invalid("Maquina value must not be negative");
        }

        if (entity.percentualComissaoCentesimos < 0
                || entity.percentualComissaoCentesimos > 10000) {
            throw invalid("Commission must be between 0 and 100");
        }

        if (!"D".equals(entity.status)
                && !"N".equals(entity.status)
                && !"R".equals(entity.status)
                && !"V".equals(entity.status)) {
            throw invalid("Invalid maquina.status");
        }

        MaquinaRequest request = new MaquinaRequest();

        request.id = entity.serverId;
        request.idTipo = tipo.serverId;
        request.idMarca = marca.serverId;

        request.dataInclusao = entity.dataInclusao;
        request.descricao = entity.descricao;
        request.anoFabricacao = entity.anoFabricacao;

        request.valor = BigDecimal.valueOf(
                entity.valorCentavos,
                2
        );

        request.nomeProprietario = entity.nomeProprietario;
        request.contatoProprietario =
                entity.contatoProprietario;

        request.percentualComissao = BigDecimal.valueOf(
                entity.percentualComissaoCentesimos,
                2
        );

        request.status = entity.status;

        return request;
    }

    private static void requireOptionalServerId(Integer id) {
        if (id != null && id <= 0) {
            throw invalid("Invalid server ID");
        }
    }

    private static void requireRemoteParentId(
            Integer id,
            String entityName
    ) {
        if (id == null || id <= 0) {
            throw invalid(
                    entityName
                            + " must be uploaded before the maquina"
            );
        }
    }

    private static void requireNotDeleted(String syncStatus) {
        if ("DELETED".equals(syncStatus)) {
            throw invalid(
                    "Deleted records must use the deletion flow"
            );
        }
    }

    private static void requireText(
            String value,
            String field
    ) {
        if (value == null || value.trim().isEmpty()) {
            throw invalid("Invalid or missing " + field);
        }
    }

    private static IllegalArgumentException invalid(
            String message
    ) {
        return new IllegalArgumentException(message);
    }
}