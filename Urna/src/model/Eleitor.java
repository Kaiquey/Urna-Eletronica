package model;

import java.time.LocalDate;

public class Eleitor extends Pessoa {
    private String tituloEleitor;
    private StatusEleitoral status;

    public Eleitor(String nome, String cpf, LocalDate dataNascimento, String tituloEleitor){
        super(nome, cpf, dataNascimento);
        setTituloEleitor(tituloEleitor);
        validarElegibilidade();
    }

    private void validarElegibilidade(){
        if(getIdade() < 16){
            this.status = StatusEleitoral.INAPTO;
        }else{
            this.status = StatusEleitoral.APTO;
        }
    }

    public void registrarVoto(){
        if(this.status == StatusEleitoral.INAPTO){
            throw new IllegalStateException("Eleitor menor de 16 anos não pode votar");
        }
        if(this.status == StatusEleitoral.JA_Votei){
            throw new IllegalStateException("Eleitor ja realizou voto nessa sessão!!");
        }
        this.status = StatusEleitoral.APTO;
    }

    public StatusEleitoral getStatus(){
        return status;
    }
    public boolean aptoVoto(){
        return this.status == StatusEleitoral.APTO;
    }

    public String getTituloEleitor(){
        return tituloEleitor;
    }

    public void setTituloEleitor(String tituloEleitor){
        if(tituloEleitor == null || tituloEleitor.isBlank()){
        throw new IllegalArgumentException("O titulo de eleitor é obrigatório.");
        }
        String format = tituloEleitor.trim();
        if(!format.matches("\\d{12}")){
            throw new IllegalArgumentException("O título de eleitor deve conter apenas 12 digitos.");
        }
        this.tituloEleitor = tituloEleitor;
    }
}
