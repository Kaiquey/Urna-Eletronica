public class Candidato{
    private final int id;
    private final String Partido;

    public Candidato(String nome, String cpf, LocalDate dataNascimento, int id,String Partido) {
        super(nome,cpf,dataNascimento);
        if(id<=0){
            throw new IllegalArgumentException("O número do canditado não pode ser nulo ou negativo!");
        }if(partido ==null || partido.isBlank()){
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

    @Override
    public String toString() {
        return String.format("%d - %s (%s)", this.id, getNome(), this.Partido);
    }

}
