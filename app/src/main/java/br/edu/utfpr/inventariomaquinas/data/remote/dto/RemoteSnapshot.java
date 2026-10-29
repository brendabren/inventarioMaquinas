package br.edu.utfpr.inventariomaquinas.data.remote.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RemoteSnapshot {

    public final List<TipoDto> tipos;

    public final List<MarcaDto> marcas;

    public final List<MaquinaDto> maquinas;

    public RemoteSnapshot(
            List<TipoDto> tipos,
            List<MarcaDto> marcas,
            List<MaquinaDto> maquinas
    ) {
        this.tipos = Collections.unmodifiableList(
                new ArrayList<>(tipos)
        );

        this.marcas = Collections.unmodifiableList(
                new ArrayList<>(marcas)
        );

        this.maquinas = Collections.unmodifiableList(
                new ArrayList<>(maquinas)
        );
    }
}