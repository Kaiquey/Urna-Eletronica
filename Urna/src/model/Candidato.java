package model;

import java.time.LocalDate;

public class Candidato extends Pessoa {
    private  int id;
    private  String Partido;

    public Candidato(String nome, String cpf, LocalDate dataNascimento, int id, String Partido) {
        super(nome,cpf,dataNascimento);
        setId(id);
        setPartido(Partido);
        if(id<=0){
            throw new IllegalArgumentException("O número do canditado não pode ser nulo ou negativo!");
        }if(Partido == null || Partido.isBlank()){
            throw new IllegalArgumentException("a filiação partidária deste candidato é obrigatória!!");
        }
        this.id = id;
        this.Partido = Partido;
    }
    public int getId() {
        return id;
    }
    public String getPartido() {
        return Partido;
    }

    public void setId(int id) {
        if(id <=0){
            throw new IllegalArgumentException("O número do candidato deve ser maior que zero!");
        }
        this.id = id;
    }
   public void setPartido(String Partido) {
        if(Partido == null || Partido.isBlank()){
            throw new IllegalArgumentException("A filiação partidária é obrigatória!!");
        }
        this.Partido = Partido;
   }


    @Override
    public String toString() {
        return String.format("%d - %s (%s)", this.id, getNome(), this.Partido);
    }

}
