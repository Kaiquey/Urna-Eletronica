package service;

import exception.EleitorInaptoException;
import exception.votoInvalidoException;

import model.Candidato;
import model.Eleitor;
import model.StatusEleitoral;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class UrnaEletronica {

    public static final int VOTO_BRANCO = 0;
    public static final int VOTO_NULO = -1;

    public final Map<Integer, Candidato> mapaCandidatos;
    public final Map<Integer, Integer> mapaVotos;

    public UrnaEletronica() {
        this.mapaCandidatos = new HashMap<>();
        this.mapaVotos = new HashMap<>();

        this.mapaVotos.put(VOTO_BRANCO, 0);
        this.mapaVotos.put(VOTO_NULO, 0);
    }
    public void cadastraCandidato(Candidato candidato) {
        if(candidato == null){
            throw new IllegalArgumentException("O candidato não pode ser nulo.");
        }
        if(mapaCandidatos.containsKey(candidato.getId())){
            throw new IllegalArgumentException("Já existe candidato com o número "+candidato.getId());
        }
        mapaCandidatos.put(candidato.getId(), candidato);
        mapaVotos.put(candidato.getId(), 0);
    }

    public void registrarVoto(Eleitor eleitor, int numeroVoto){
        if(eleitor == null){
            throw new IllegalArgumentException("O eleitor não pode ser nulo");
        }
        if(!eleitor.aptoVoto()){
            if(eleitor.getStatus() == StatusEleitoral.INAPTO){
                throw new EleitorInaptoException("Eleitor inapto (menor que 16 anos)");
            }if(eleitor.getStatus() == StatusEleitoral.JA_Votei){
                throw new EleitorInaptoException("O eleitor portador do título " + eleitor.getTituloEleitor()+ " já votou");
            }
        }
        if(numeroVoto == VOTO_BRANCO){
            votosAumenta(VOTO_BRANCO);
        } else if (mapaCandidatos.containsKey(numeroVoto)) {
            votosAumenta(numeroVoto);
        }else{
            votosAumenta(VOTO_NULO);
        }
        eleitor.registrarVoto();
    }

    private void votosAumenta(int key){
        int qtdAtual = mapaVotos.getOrDefault(key,0);
        mapaVotos.put(key,qtdAtual + 1);
    }
    public int getVotoCandidato(int numCandidato){
        if(!mapaCandidatos.containsKey(numCandidato)){
            throw new votoInvalidoException("Candidato não encontrado.");
        }
        return mapaVotos.getOrDefault(numCandidato, 0);
    }
    public int getVotoBranco(){
        return mapaVotos.get(VOTO_BRANCO);
    }
    public int getVotoNulo(){
        return mapaVotos.get(VOTO_NULO);
    }

    public int getTotalVotos(){
        return mapaVotos.values().stream().mapToInt(Integer::intValue).sum();
    }

    public List<Candidato> getCandidatosCadastros(){
        return new ArrayList<>(mapaCandidatos.values());
    }

}

