import java.time.LocalDate;
import java.time.Period;

public abstract class Pessoa {
    private String nome;
    private LocalDate dataNascimento;
    private String CPF;

    public Pessoa(String nome, String CPF, LocalDate dataNascimento) {
        this.nome = nome;
        this.CPF = CPF;
        this.dataNascimento = dataNascimento;
    }

    public int getIdade(){
        return Period.between(dataNascimento, LocalDate.now()).getYears();
    }
    public boolean isElegivel(){
        return getIdade() >= 16;
    }

    public String getNome() {
        return nome;
    }
    public String getCPF() {
        return CPF;
    }
    public LocalDate getDataNascimento() {
        return dataNascimento;
    }

}
