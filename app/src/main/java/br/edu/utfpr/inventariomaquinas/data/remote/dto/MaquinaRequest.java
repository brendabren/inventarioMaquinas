package br.edu.utfpr.inventariomaquinas.data.remote.dto;

import java.math.BigDecimal;

public class MaquinaRequest {

    public Integer id;

    public Integer idMarca;

    public Integer idTipo;

    public Integer anoFabricacao;

    public String contatoProprietario;

    public String dataInclusao;

    public String descricao;

    public String nomeProprietario;

    public BigDecimal percentualComissao;

    public String status;

    public BigDecimal valor;
}