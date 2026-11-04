package view;

import exception.EleitorInaptoException;
import model.Candidato;
import model.Eleitor;
import service.UrnaEletronica;

import javax.swing.JOptionPane;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class UrnaView {

    private static final DateTimeFormatter dataFormatada = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static void main(String[] args) {
        UrnaEletronica urna = new UrnaEletronica();
        List<Eleitor> eleitores = new ArrayList<>();
        boolean execut = true;

        while (execut) {
            String menu = """
                    1. Cadastro Candidato
                    2. Cadastro Eleitor
                    3. Votar
                    4. Emitir Boletim de Urna
                    0. Sair
                    Escolha uma opção:
                    """;
            String opcao = JOptionPane.showInputDialog(null, menu, "Menu Principal", JOptionPane.QUESTION_MESSAGE);
            if (opcao == null) break;
            try {
                int op = Integer.parseInt(opcao);
                switch (op) {
                    case 1 -> cadastrarCandidato(urna);
                    case 2 -> cadastrarEleitor(eleitores);
                    case 3 -> realizarVotacao(urna, eleitores);
                    case 4 -> emitirBoletim(urna);
                    case 0 -> execut = false;
                    default ->
                            JOptionPane.showMessageDialog(null, "Opção inválida!", "Aviso!!", JOptionPane.WARNING_MESSAGE);
                }
            } catch (NumberFormatException err) {
                JOptionPane.showMessageDialog(null, "Digite apenas números no menu!!");
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Erro: " + e.getMessage(), "Erro de Validação", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private static void cadastrarCandidato(UrnaEletronica urna) {
        try {
            String nome = JOptionPane.showInputDialog("Nome do Candidato:");
            String cpf = JOptionPane.showInputDialog("CPF do Candidato:");
            String nascimento = JOptionPane.showInputDialog("Nascimento do Candidato (dd/mm/aaaa):");
            LocalDate dataNasc = LocalDate.parse(nascimento, dataFormatada);
            int numero = Integer.parseInt(JOptionPane.showInputDialog("Numero do Candidato:"));
            String partido = JOptionPane.showInputDialog("Partido do Candidato:");

            Candidato candidato = new Candidato(nome, cpf, dataNasc, numero, partido);
            urna.cadastraCandidato(candidato);

            JOptionPane.showMessageDialog(null, "Candidato cadastrado com sucesso!\n");
        }catch (IllegalArgumentException err) {
            JOptionPane.showMessageDialog(null,err.getMessage(), "Dados do Candidato inválidos.", JOptionPane.WARNING_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,"Erro ao processar os dados: " + e.getMessage(), "Erro",JOptionPane.ERROR_MESSAGE);
        }

    }

    private static void cadastrarEleitor(List<Eleitor> eleitores) {
        try {
            String nome = JOptionPane.showInputDialog("Nome do Eleitor:");
            String cpf = JOptionPane.showInputDialog("CPF do Eleitor:");
            String nascimento = JOptionPane.showInputDialog("Nascimento do Eleitor:");
            LocalDate dataNasc = LocalDate.parse(nascimento, dataFormatada);
            String titulo = JOptionPane.showInputDialog("Título de Eleitor");

            Eleitor eleitor = new Eleitor(nome, cpf, dataNasc, titulo);
            eleitores.add(eleitor);

            String msg = "Eleitor cadastrado com sucesso!\n" + eleitor.getStatus().getDescricao();
            JOptionPane.showMessageDialog(null, msg);
        } catch (IllegalArgumentException err) {
            JOptionPane.showMessageDialog(null, err.getMessage(), "Dados do Eleitor estão inválidos!", JOptionPane.WARNING_MESSAGE);
        }
    }

    private static void realizarVotacao(UrnaEletronica urna, List<Eleitor> eleitores) {
        if (eleitores.isEmpty()) {
            JOptionPane.showMessageDialog(null, "nenhum eleitor cadastrado!");
            return;
        }
        String titulo = JOptionPane.showInputDialog("Informe o Título de eleitor para votar (ex: 123456789102");
        Eleitor eleitor = eleitores.stream().filter(e -> e.getTituloEleitor().equals(titulo)).findFirst().orElse(null);
        if (eleitor == null) {
            JOptionPane.showMessageDialog(null, "Eleitor não foi encontrado", "Erro", JOptionPane.WARNING_MESSAGE);
            return;
        }
        StringBuilder options = new StringBuilder();
        for (Candidato c : urna.getCandidatosCadastros()) {
            options.append(c).append("\n");
        }
        options.append("0 - Voto em Branco\n");
        options.append("Qualquer outro número - Voto Nulo\n\n");
        options.append("Digite o número do seu voto:");

        int numeroVoto = Integer.parseInt(JOptionPane.showInputDialog(null, options.toString()));
        try {
            urna.registrarVoto(eleitor, numeroVoto);
            JOptionPane.showMessageDialog(null, "Voto foi registrado com sucesso! Obrigado pelo voto ");
        } catch (EleitorInaptoException e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "Eleitor Inapto", JOptionPane.WARNING_MESSAGE);
        }
    }

    private static void emitirBoletim(UrnaEletronica urna) {
        StringBuilder boletim = new StringBuilder();

        for (Candidato c : urna.getCandidatosCadastros()) {
            boletim.append("O candidato: \n").append(c.getNome()).append(" do ").append(c.getPartido()).append(": ").append("possui ").append(urna.getVotoCandidato(c.getId())).append(" votos\n");
        }

        boletim.append("\n Votos em branco: ").append(urna.getVotoBranco());
        boletim.append("\n Votos nulos: ").append(urna.getVotoNulo());
        boletim.append("\n Votos gerais: ").append(urna.getTotalVotos());
        JOptionPane.showMessageDialog(null, boletim.toString(), "Apuração de Urna", JOptionPane.INFORMATION_MESSAGE);
    }
}