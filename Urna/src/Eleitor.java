public class Eleitor extends Pessoa{
    private String tituloEleitor;
    private boolean voto;

    public Eleitor(String nome, String cpf, Localdate dataNascimento, String tituloEleitor){
        super(nome, cpf, dataNascimento);
        this.tituloEleitor = tituloEleitor;
        this.voto = false;
    }


}
