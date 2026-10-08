package model;

public enum StatusEleitoral {
    APTO("Eleitor apto para voto"),
    INAPTO("Eleitor inapto para voto (Idade é menor a 16 anos."),
    JA_Votei("Eleitor já registrou o voto nesta sessão");

    private final String descricao;

    StatusEleitoral(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
