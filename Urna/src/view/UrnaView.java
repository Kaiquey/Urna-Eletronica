import exception.EleitorInaptoException;
import model.Candidato;
import model.Eleitor;
import service.UrnaEletronica;

import javax.swing.JOptionPane;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;


private static final DateTimeFormatter dataFormatada = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    void main() {
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
                    default -> JOptionPane.showMessageDialog(null, "Opção inválida!", "Aviso!!", JOptionPane.WARNING_MESSAGE);
                }
            }catch (NumberFormatException err) {
                JOptionPane.showMessageDialog(null, "Digite apenas números no menu!!");
            }catch (Exception e){
                JOptionPane.showMessageDialog(null, "Erro: " + e.getMessage(), "Erro de Validação", JOptionPane.WARNING_MESSAGE);
            }
        }
    }
        private static void cadastrarCandidato(UrnaEletronica urna){
            String nome = JOptionPane.showInputDialog("Nome do Candidato:");
            String cpf = JOptionPane.showInputDialog("CPF do Candidato:");
            String nascimento = JOptionPane.showInputDialog("Nascimento do Candidato (dd/mm/aaaa):");
            LocalDate dataNasc = LocalDate.parse(nascimento, dataFormatada);
            int numero = Integer.parseInt(JOptionPane.showInputDialog("Numero do Candidato:"));
            String partido = JOptionPane.showInputDialog("Partido do Candidato:");

            Candidato candidato = new Candidato(nome, cpf, dataNasc, numero, partido);
            urna.cadastraCandidato(candidato);

            JOptionPane.showMessageDialog(null, "Candidato cadastrado com sucesso!\n");
    }

