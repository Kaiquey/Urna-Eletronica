public class Pessoa {
    private String nome;
    private int idade;
    private String CPF;

    public Pessoa(String nome, int idade, String CPF){
        this.nome = nome;
        setIdade(idade);
        this.CPF = CPF;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public int getIdade() {
        return idade;
    }

    public void setIdade(int idade) {
        if(this.idade <=15){
            throw new IllegalArgumentException("idade deve ser maior ou igual a 16 para votar!!");
        }else{
            this.idade = idade;
        }
    }

    public String getCPF() {
        return CPF;
    }

    public void setCPF(String CPF) {
        this.CPF = CPF;
    }
}
