import java.time.LocalDate;

public class Eleitor extends Pessoa{
    private String tituloEleitor;
    private boolean voto;

    public Eleitor(String nome, String cpf, LocalDate dataNascimento, String tituloEleitor){
        super(nome, cpf, dataNascimento);
        this.tituloEleitor = tituloEleitor;
        this.voto = false;
    }

    public String getTituloEleitor() {
        return tituloEleitor;
    }
    public void setTituloEleitor(String tituloEleitor) {
        this.tituloEleitor = tituloEleitor;
    }
    public boolean isVoto() {
        return voto;
    }
    public void setVoto(boolean voto) {}

}
