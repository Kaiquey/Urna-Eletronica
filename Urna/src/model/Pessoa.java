package model;

import java.time.LocalDate;
import java.time.Period;

public abstract class Pessoa {
    private String nome;
    private final LocalDate dataNascimento;
    private String CPF;

    protected Pessoa(String nome, String CPF, LocalDate dataNascimento) {
        if(dataNascimento == null || dataNascimento.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Data de nascimento inválida");
        }
        this.dataNascimento = dataNascimento;
        setNome(nome);
        setCPF(CPF);
    }

    public int getIdade(){
        return Period.between(dataNascimento, LocalDate.now()).getYears();
    }

    public void setNome(String nome) {
        if(nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome não pode ser nulo ou vazio");
        }
        this.nome = nome;
    }
    public void setCPF(String CPF) {
        if(CPF == null || CPF.isBlank()) {
            throw new IllegalArgumentException("O cpf não pode ser nulo ou vazio");
        }
        String formatedCPF = CPF.replaceAll("[^0-9]", "");
        if(!formatedCPF.matches("\\d{11}")){
            throw new IllegalArgumentException("O cpf deve conter exatamente 11 dígitos");
        }
        this.CPF = formatedCPF;
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
