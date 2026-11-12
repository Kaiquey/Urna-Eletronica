package view;

import service.CandidatoFirebaseService;
import service.EleitorAuthService;
import service.firebase.FirebaseAdminSession;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/** Interface gráfica da urna, integrada aos serviços de autenticação e votação Firebase. */
public final class UrnaView {
    private static final Dimension WINDOW_SIZE = new Dimension(1100, 760);
    // Ajuste manual do teclado numérico: dimensões e tamanho dos algarismos.
    private static final int NUMERIC_KEY_WIDTH = 132;
    private static final int NUMERIC_KEY_HEIGHT = 80;
    private static final int NUMERIC_KEY_FONT_SIZE = 28;
    private static final Color NAVY = new Color(17, 38, 66);
    private static final Color GREEN = new Color(0, 125, 91);
    private final JFrame frame = new JFrame("Urna Eletrônica");
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final FirebaseAdminSession firebase = new FirebaseAdminSession();
    private final EleitorAuthService auth = new EleitorAuthService(firebase);
    private final CandidatoFirebaseService candidates = new CandidatoFirebaseService(firebase, auth, "candidatos");
    private String voterName;
    private EleitorAuthService.TipoVoto[] ballot = {
            EleitorAuthService.TipoVoto.GOVERNADOR, EleitorAuthService.TipoVoto.PRESIDENTE
    };
    private int ballotIndex;
    private String selectedCandidate;
    private List<CandidatoFirebaseService.CandidatoData> candidatesForBallot = List.of();
    private final java.util.List<String> votedOptions = new java.util.ArrayList<>();
    private JLabel ballotTitle;
    private JPanel candidateList;
    private JButton voteButton;
    private JProgressBar progress;
    private JLabel status;
    private JTextField voterNameField;
    private JPasswordField voterPasswordField;
    private JButton voterLoginButton;
    private JButton voterSignupButton;
    private JLabel numberPrompt;
    private JLabel candidateDetails;
    private JLabel[] numberBoxes;
    private JLabel candidatePortrait;
    private java.util.Map<String, ImageIcon> candidateImages = java.util.Map.of();
    private final StringBuilder numberInput = new StringBuilder();
    private JScrollPane candidateMenu;
    private JPanel votedOptionsPanel;
    private String specialVote;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new UrnaView().start());
    }

    private void start() {
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setResizable(true);
        frame.setContentPane(root);
        frame.setSize(WINDOW_SIZE);
        frame.setLocationRelativeTo(null);
        root.add(adminPanel(), "admin");
        root.add(loginPanel(), "login");
        root.add(votingPanel(), "voting");
        root.add(donePanel(), "done");
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { close(); }
        });
        frame.setVisible(true);
        cards.show(root, "admin");
    }

    private JPanel adminPanel() {
        JPanel panel = shell("Acesso administrativo", "Entre na conta Firebase para abrir a sessão da urna.");
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(225, 231, 237)), new EmptyBorder(28, 32, 28, 32)));
        GridBagConstraints c = new GridBagConstraints(); c.gridx = 0; c.gridy = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(7, 0, 7, 0);
        JTextField email = new JTextField(24); JPasswordField password = new JPasswordField(24);
        form.add(label("Email"), c); c.gridy++; form.add(email, c);
        c.gridy++; form.add(label("Senha"), c); c.gridy++; form.add(password, c);
        JLabel adminStatus = new JLabel(" "); adminStatus.setForeground(new Color(170, 62, 52));
        JButton enter = button("Abrir urna", GREEN); c.gridy++; c.insets = new Insets(18, 0, 5, 0); form.add(enter, c);
        c.gridy++; form.add(adminStatus, c);
        enter.addActionListener(e -> {
            enter.setEnabled(false); adminStatus.setText("Conectando ao Firebase...");
            char[] secret = password.getPassword();
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() throws Exception { firebase.signInAdmin(email.getText(), secret); return null; }
                @Override protected void done() {
                    enter.setEnabled(true);
                    try { get(); status.setText("Sessão da urna aberta. Entre como eleitor."); cards.show(root, "login"); }
                    catch (Exception ex) { adminStatus.setText(message(ex)); }
                }
            }.execute();
        });
        password.addActionListener(e -> enter.doClick());
        panel.add(form, BorderLayout.CENTER);
        return panel;
    }

    private JPanel shell(String title, String subtitle) {
        JPanel panel = new JPanel(new BorderLayout(0, 18));
        panel.setBackground(new Color(244, 247, 250));
        panel.setBorder(new EmptyBorder(28, 42, 32, 42));
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel brand = new JLabel("URNA ELETRÔNICA");
        brand.setForeground(GREEN); brand.setFont(new Font("SansSerif", Font.BOLD, 13));
        JLabel heading = new JLabel(title);
        heading.setForeground(NAVY); heading.setFont(new Font("SansSerif", Font.BOLD, 29));
        JLabel detail = new JLabel(subtitle);
        detail.setForeground(new Color(95, 109, 125)); detail.setFont(new Font("SansSerif", Font.PLAIN, 15));
        header.add(brand); header.add(Box.createVerticalStrut(10)); header.add(heading);
        header.add(Box.createVerticalStrut(5)); header.add(detail);
        panel.add(header, BorderLayout.NORTH);
        return panel;
    }

    private JPanel loginPanel() {
        JPanel panel = shell("Identificação do eleitor", "Entre com seu nome e senha para iniciar a votação.");
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(225, 231, 237)), new EmptyBorder(28, 32, 28, 32)));
        GridBagConstraints c = new GridBagConstraints(); c.gridx = 0; c.gridy = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(7, 0, 7, 0);
        voterNameField = new JTextField(24); voterPasswordField = new JPasswordField(24);
        form.add(label("Nome de usuário (sem espaços)"), c); c.gridy++; form.add(voterNameField, c);
        c.gridy++; form.add(label("Senha"), c); c.gridy++; form.add(voterPasswordField, c);
        JPanel actions = new JPanel(new GridLayout(1, 2, 12, 0)); actions.setOpaque(false);
        voterLoginButton = button("Entrar", GREEN); voterSignupButton = button("Criar cadastro", NAVY);
        actions.add(voterLoginButton); actions.add(voterSignupButton); c.gridy++; c.insets = new Insets(18, 0, 5, 0); form.add(actions, c);
        status = new JLabel(" "); status.setForeground(new Color(170, 62, 52)); c.gridy++; form.add(status, c);
        voterLoginButton.addActionListener(e -> authenticate(voterNameField.getText(), voterPasswordField.getPassword(), false));
        voterSignupButton.addActionListener(e -> authenticate(voterNameField.getText(), voterPasswordField.getPassword(), true));
        voterPasswordField.addActionListener(e -> authenticate(voterNameField.getText(), voterPasswordField.getPassword(), false));
        panel.add(form, BorderLayout.CENTER);
        JLabel footer = new JLabel("Votação protegida por autenticação e registro no Firebase.");
        footer.setForeground(new Color(100, 112, 124)); panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    private void authenticate(String name, char[] secret, boolean create) {
        status.setText("Conectando...");
        setLoginEnabled(false);
        new SwingWorker<EleitorAuthService.EleitorAccount, Void>() {
            @Override protected EleitorAuthService.EleitorAccount doInBackground() throws Exception {
                if (create) return auth.signUp(name, secret);
                return auth.signIn(name, secret);
            }
            @Override protected void done() {
                setLoginEnabled(true);
                try {
                    voterName = get().nome(); votedOptions.clear();
                    new SwingWorker<Boolean[], Void>() {
                        @Override protected Boolean[] doInBackground() throws Exception {
                            return new Boolean[]{auth.jaVotou(EleitorAuthService.TipoVoto.GOVERNADOR), auth.jaVotou(EleitorAuthService.TipoVoto.PRESIDENTE)};
                        }
                        @Override protected void done() {
                            try {
                                Boolean[] voted = get();
                                if (voted[0] && voted[1]) { showCompletion(); return; }
                                ballotIndex = voted[0] ? 1 : 0;
                                cards.show(root, "voting"); loadCandidates();
                            } catch (Exception error) { status.setText(message(error)); auth.signOut(); }
                        }
                    }.execute();
                } catch (Exception ex) { status.setText(message(ex)); }
            }
        }.execute();
    }

    private void setLoginEnabled(boolean enabled) {
        if (voterNameField != null) voterNameField.setEnabled(enabled);
        if (voterPasswordField != null) voterPasswordField.setEnabled(enabled);
        if (voterLoginButton != null) voterLoginButton.setEnabled(enabled);
        if (voterSignupButton != null) voterSignupButton.setEnabled(enabled);
        if (status != null) status.setEnabled(enabled);
    }

    private JPanel votingPanel() {
        JPanel panel = shell("Escolha seu candidato", "Selecione uma opção e confirme seu voto.");
        JPanel center = new JPanel(new BorderLayout(0, 14)); center.setOpaque(false);
        JPanel top = new JPanel(new BorderLayout()); top.setOpaque(false);
        ballotTitle = new JLabel("Carregando candidatos..."); ballotTitle.setFont(new Font("SansSerif", Font.BOLD, 19)); ballotTitle.setForeground(NAVY);
        top.add(ballotTitle, BorderLayout.WEST);
        JButton logout = button("Sair", new Color(100, 112, 124)); logout.addActionListener(e -> logout()); top.add(logout, BorderLayout.EAST);
        center.add(top, BorderLayout.NORTH);
        candidateList = new JPanel(); candidateList.setOpaque(false); candidateList.setLayout(new BoxLayout(candidateList, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(candidateList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(new Color(244, 247, 250));
        candidateMenu = scroll;
        candidateMenu.setVisible(false);
        JButton menuArrow = new JButton("❯");
        menuArrow.setFont(new Font("SansSerif", Font.BOLD, 22));
        menuArrow.setToolTipText("Mostrar candidatos e partidos");
        menuArrow.addActionListener(e -> {
            candidateMenu.setVisible(!candidateMenu.isVisible());
            menuArrow.setText(candidateMenu.isVisible() ? "❮" : "❯");
            center.revalidate(); center.repaint();
        });
        JPanel leftMenu = new JPanel(new BorderLayout(4, 0)); leftMenu.setOpaque(false);
        leftMenu.add(menuArrow, BorderLayout.WEST); leftMenu.add(candidateMenu, BorderLayout.CENTER);
        center.add(leftMenu, BorderLayout.WEST);
        JPanel keypad = new JPanel(new BorderLayout(8, 8)); keypad.setBackground(new Color(35, 39, 41));
        keypad.setBorder(new EmptyBorder(12, 16, 12, 16)); keypad.setPreferredSize(new Dimension(900, 540));
        JPanel screenAndActions = new JPanel(new BorderLayout(10, 0)); screenAndActions.setOpaque(false);
        JPanel display = new JPanel(new BorderLayout(8, 8)); display.setBackground(Color.WHITE);
        display.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(190, 196, 201)), new EmptyBorder(12, 16, 12, 16)));
        numberPrompt = new JLabel("Digite o número do candidato", SwingConstants.CENTER);
        numberPrompt.setForeground(Color.BLACK); numberPrompt.setFont(new Font("SansSerif", Font.BOLD, 19));
        display.add(numberPrompt, BorderLayout.NORTH);
        JPanel digitPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 12)); digitPanel.setOpaque(false);
        digitPanel.setBorder(new EmptyBorder(0, 250, 0, 0));
        numberBoxes = new JLabel[2];
        for (int i = 0; i < numberBoxes.length; i++) {
            numberBoxes[i] = new JLabel(" ", SwingConstants.CENTER);
            numberBoxes[i].setOpaque(true); numberBoxes[i].setBackground(Color.WHITE); numberBoxes[i].setForeground(Color.BLACK);
            numberBoxes[i].setFont(new Font("Monospaced", Font.BOLD, 38)); numberBoxes[i].setPreferredSize(new Dimension(62, 74));
            numberBoxes[i].setBorder(BorderFactory.createLineBorder(new Color(70, 76, 82), 2)); digitPanel.add(numberBoxes[i]);
        }
        JPanel candidateInfo = new JPanel(new BorderLayout()); candidateInfo.setOpaque(false);
        candidateInfo.add(digitPanel, BorderLayout.CENTER);
        candidateDetails = new JLabel(" ", SwingConstants.CENTER);
        candidateDetails.setForeground(Color.BLACK);
        candidateDetails.setFont(new Font("SansSerif", Font.BOLD, 21));
        candidateDetails.setBorder(new EmptyBorder(8, 270, 4, 4));
        candidateInfo.add(candidateDetails, BorderLayout.SOUTH);
        display.add(candidateInfo, BorderLayout.CENTER);
        candidatePortrait = new JLabel("", SwingConstants.CENTER); candidatePortrait.setPreferredSize(new Dimension(250, 250));
        candidatePortrait.setBorder(BorderFactory.createLineBorder(new Color(190, 196, 201)));
        candidatePortrait.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent event) {
                if (selectedCandidate != null) fitCandidatePortrait(candidateImages.get(selectedCandidate));
            }
        });
        display.add(candidatePortrait, BorderLayout.EAST);
        screenAndActions.add(display, BorderLayout.CENTER);
        JPanel actions = new JPanel(new GridLayout(3, 1, 0, 8)); actions.setOpaque(false); actions.setPreferredSize(new Dimension(155, 230));
        JButton red = button("CORRIGIR", new Color(194, 66, 65)); red.setFont(new Font("SansSerif", Font.BOLD, 13)); red.setBorder(new EmptyBorder(8, 8, 8, 8)); red.addActionListener(e -> resetNumber());
        JButton green = button("CONFIRMAR", GREEN); green.setFont(new Font("SansSerif", Font.BOLD, 13)); green.setBorder(new EmptyBorder(8, 8, 8, 8)); green.addActionListener(e -> confirmOrSubmitVote());
        JButton blank = button("BRANCO", Color.WHITE); blank.setForeground(Color.BLACK); blank.setFont(new Font("SansSerif", Font.BOLD, 13)); blank.addActionListener(e -> { numberInput.setLength(0); renderBallotDisplay(null); specialVote = "BRANCO"; selectedCandidate = null; candidateDetails.setText("Voto em branco"); numberPrompt.setText("Confirme seu voto"); voteButton.setText("CONFIRMAR"); voteButton.setEnabled(true); });
        actions.add(red); actions.add(green); actions.add(blank); screenAndActions.add(actions, BorderLayout.EAST);
        keypad.add(screenAndActions, BorderLayout.NORTH);
        JPanel keys = new JPanel(new GridLayout(4, 3, 5, 5)); keys.setOpaque(false);
        keys.setPreferredSize(new Dimension(430, 340));
        for (int n = 1; n <= 9; n++) addKey(keys, Integer.toString(n));
        keys.add(new JLabel());
        addKey(keys, "0");
        keys.add(new JLabel());
        JPanel centeredKeys = new JPanel(new GridBagLayout()); centeredKeys.setOpaque(false);
        centeredKeys.add(keys); keypad.add(centeredKeys, BorderLayout.CENTER);
        center.add(keypad, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(12, 8)); bottom.setOpaque(false);
        progress = new JProgressBar(0, 2); progress.setStringPainted(true); progress.setString("Etapa 1 de 2");
        voteButton = green;
        voteButton.setEnabled(false);
        bottom.add(progress, BorderLayout.CENTER);
        center.add(bottom, BorderLayout.SOUTH); panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private void addKey(JPanel keys, String key) {
        JButton button = new JButton(key); button.setFont(new Font("SansSerif", Font.BOLD, NUMERIC_KEY_FONT_SIZE));
        button.setPreferredSize(new Dimension(NUMERIC_KEY_WIDTH, NUMERIC_KEY_HEIGHT));
        button.addActionListener(e -> {
            if (numberInput.length() < 2) { numberInput.append(key); updateNumberDisplay(); }
        });
        keys.add(button);
    }

    private void updateNumberDisplay() {
        specialVote = null; selectedCandidate = null;
        CandidatoFirebaseService.CandidatoData match = numberInput.length() == 2 ? candidatesForBallot.stream().filter(c -> c.numero() == Integer.parseInt(numberInput.toString())).findFirst().orElse(null) : null;
        renderBallotDisplay(match);
        if (numberInput.length() == 1) {
            String prefix = numberInput.toString();
            String possibilities = candidatesForBallot.stream().filter(c -> Integer.toString(c.numero()).startsWith(prefix))
                    .map(c -> c.partido() + " — " + c.nome()).distinct().reduce((a, b) -> a + "   |   " + b).orElse("Nenhum candidato");
            candidateDetails.setText(possibilities);
        }
        if (numberInput.length() == 2 && match == null) { specialVote = "NULO"; candidateDetails.setText("Voto nulo"); }
        voteButton.setText("CONFIRMA"); voteButton.setEnabled(!numberInput.isEmpty());
    }

    private void resetNumber() { numberInput.setLength(0); updateNumberDisplay(); }

    private void chooseNumber() {
        if (numberInput.length() != 2) { showError("Digite os dois números do candidato."); return; }
        try {
            int number = Integer.parseInt(numberInput.toString());
            var match = candidatesForBallot.stream().filter(c -> c.numero() == number).findFirst();
            if (match.isEmpty()) { specialVote = "NULO"; selectedCandidate = null; candidateDetails.setText("Voto nulo"); return; }
            specialVote = null;
            selectedCandidate = match.get().id();
            renderBallotDisplay(match.get()); voteButton.setEnabled(true);
        } catch (NumberFormatException ex) { showError("Digite o número do candidato."); }
    }

    private void confirmOrSubmitVote() {
        if (selectedCandidate == null && specialVote == null) {
            if (numberInput.length() != 2) { showError("Digite os dois números do candidato."); return; }
            chooseNumber();
        }
        if (selectedCandidate != null || specialVote != null) submitVote();
    }

    private void loadCandidates() {
        selectedCandidate = null; specialVote = null; numberInput.setLength(0); renderBallotDisplay(null); voteButton.setText("CONFIRMAR"); voteButton.setEnabled(false);
        EleitorAuthService.TipoVoto type = ballot[ballotIndex];
        ballotTitle.setText((type == EleitorAuthService.TipoVoto.PRESIDENTE ? "Presidente" : "Governador(a)") + " — " + voterName);
        progress.setValue(ballotIndex); progress.setString("Etapa " + (ballotIndex + 1) + " de 2");
        candidateList.removeAll(); candidateList.add(new JLabel("Buscando candidatos no Firebase…"));
        new SwingWorker<List<CandidatoFirebaseService.CandidatoData>, Void>() {
            @Override protected List<CandidatoFirebaseService.CandidatoData> doInBackground() throws Exception {
                return candidates.listarCandidatos();
            }
            @Override protected void done() {
                candidateList.removeAll();
                try {
                    String wanted = type == EleitorAuthService.TipoVoto.PRESIDENTE ? "president" : "governador";
                    List<CandidatoFirebaseService.CandidatoData> list = get().stream()
                            .filter(c -> normalize(c.cargo()).startsWith(wanted)).toList();
                    candidatesForBallot = list;
                    if (list.isEmpty()) candidateList.add(new JLabel("Nenhum candidato cadastrado para este cargo."));
                    for (var candidate : list) {
                        JLabel choice = new JLabel("  " + candidate.numero() + "   " + candidate.nome() + "   ·   " + candidate.partido());
                        choice.setHorizontalAlignment(SwingConstants.LEFT);
                        choice.setFont(new Font("SansSerif", Font.PLAIN, 15)); choice.setOpaque(true); choice.setBackground(Color.WHITE);
                        choice.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(225,231,237)), new EmptyBorder(10, 10, 10, 10)));
                        choice.setAlignmentX(Component.LEFT_ALIGNMENT); choice.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
                        candidateList.add(choice); candidateList.add(Box.createVerticalStrut(8));
                    }
                } catch (Exception ex) { candidateList.add(new JLabel("Falha ao carregar candidatos: " + message(ex))); }
                candidateList.revalidate(); candidateList.repaint();
                new SwingWorker<java.util.Map<String, ImageIcon>, Void>() {
                    @Override protected java.util.Map<String, ImageIcon> doInBackground() {
                        java.util.Map<String, ImageIcon> icons = new java.util.HashMap<>();
                        for (var candidate : candidatesForBallot) icons.put(candidate.id(), candidateImage(candidate.id()));
                        return icons;
                    }
                    @Override protected void done() {
                        try {
                            var icons = get();
                            candidateImages = icons;
                            for (Component component : candidateList.getComponents()) {
                                if (component instanceof JLabel choice && !choice.getText().isBlank()) {
                                    var candidate = candidatesForBallot.stream().filter(c -> choice.getText().contains(c.nome())).findFirst().orElse(null);
                                    if (candidate != null) choice.setIcon(squarePortrait(icons.get(candidate.id()), 64));
                                }
                            }
                            candidateList.revalidate(); candidateList.repaint();
                        } catch (Exception ignored) { }
                    }
                }.execute();
            }
        }.execute();
    }

    private void renderBallotDisplay(CandidatoFirebaseService.CandidatoData candidate) {
        for (int i = 0; i < numberBoxes.length; i++) {
            numberBoxes[i].setText(i < numberInput.length() ? String.valueOf(numberInput.charAt(i)) : " ");
        }
        numberPrompt.setText("Digite o número do candidato");
        candidateDetails.setText(candidate == null ? " " : candidate.numero() + " - " + candidate.nome());
        ImageIcon portrait = candidate == null ? null : candidateImages.get(candidate.id());
        fitCandidatePortrait(portrait);
        candidatePortrait.setText(candidate != null && portrait == null ? candidate.nome() : "");
    }

    private void fitCandidatePortrait(ImageIcon portrait) {
        if (portrait != null) {
            int size = Math.max(1, Math.min(candidatePortrait.getWidth() > 0 ? candidatePortrait.getWidth() : 250,
                    candidatePortrait.getHeight() > 0 ? candidatePortrait.getHeight() : 250));
            candidatePortrait.setIcon(squarePortrait(portrait, size));
        } else {
            candidatePortrait.setIcon(null);
        }
    }

    /** Recorta ao centro e mantém a proporção original para preencher um quadro quadrado. */
    private static ImageIcon squarePortrait(ImageIcon source, int size) {
        if (source == null || source.getIconWidth() <= 0 || source.getIconHeight() <= 0) return null;
        BufferedImage output = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        double scale = Math.max((double) size / source.getIconWidth(), (double) size / source.getIconHeight());
        int width = (int) Math.ceil(source.getIconWidth() * scale);
        int height = (int) Math.ceil(source.getIconHeight() * scale);
        int x = (size - width) / 2;
        int y = (size - height) / 2;
        graphics.drawImage(source.getImage(), x, y, width, height, null);
        graphics.dispose();
        return new ImageIcon(output);
    }

    /** Os SVGs de output_candidatos armazenam o retrato como PNG em base64. */
    private static ImageIcon candidateImage(String candidateId) {
        try {
            Path svg = Path.of("output_candidatos", candidateId, "image.svg");
            if (!Files.isRegularFile(svg)) return null;
            String content = Files.readString(svg);
            Matcher matcher = Pattern.compile("href=\"data:image/png;base64,([^\"]+)\"").matcher(content);
            if (!matcher.find()) return null;
            byte[] png = Base64.getDecoder().decode(matcher.group(1));
            var image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null) return null;
            return new ImageIcon(image);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void submitVote() {
        if (selectedCandidate == null && specialVote == null) return;
        voteButton.setEnabled(false); voteButton.setText("Registrando…");
        String key = selectedCandidate; EleitorAuthService.TipoVoto type = ballot[ballotIndex];
        String mode = specialVote;
        String selectedName = mode != null ? ("Voto " + mode.toLowerCase(Locale.ROOT)) : candidatesForBallot.stream().filter(c -> c.id().equals(key)).findFirst()
                .map(c -> c.numero() + " — " + c.nome() + " (" + c.partido() + ")").orElse(key);
        new SwingWorker<Long, Void>() {
            @Override protected Long doInBackground() throws Exception { return mode == null ? candidates.incrementarVoto(key, type) : candidates.incrementarVotoEspecial(type, "BRANCO".equals(mode)); }
            @Override protected void done() {
                voteButton.setText("Confirmar voto");
                try {
                    get();
                    votedOptions.add((type == EleitorAuthService.TipoVoto.PRESIDENTE ? "Presidente: " : "Governador(a): ") + selectedName);
                    if (ballotIndex == 0 && !auth.jaVotou(EleitorAuthService.TipoVoto.PRESIDENTE)) { ballotIndex = 1; loadCandidates(); }
                    else { showCompletion(); }
                } catch (Exception ex) { voteButton.setEnabled(selectedCandidate != null); showError(message(ex)); }
            }
        }.execute();
    }

    private JPanel donePanel() {
        JPanel panel = shell("FIM!!", "Votação concluída. Obrigado por votar.");
        JPanel content = new JPanel(new BorderLayout(18, 0)); content.setOpaque(false);
        votedOptionsPanel = new JPanel();
        votedOptionsPanel.setOpaque(false);
        votedOptionsPanel.setLayout(new BoxLayout(votedOptionsPanel, BoxLayout.Y_AXIS));
        content.add(votedOptionsPanel, BorderLayout.WEST);
        JButton finish = button("FINALIZAR  ›", GREEN); finish.addActionListener(e -> logout());
        content.add(finish, BorderLayout.EAST); panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private void showCompletion() {
        votedOptionsPanel.removeAll();
        for (String item : votedOptions) {
            JLabel candidate = new JLabel("  " + item);
            candidate.setOpaque(true);
            candidate.setBackground(Color.WHITE);
            candidate.setFont(new Font("SansSerif", Font.BOLD, 16));
            candidate.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(225, 231, 237)),
                    new EmptyBorder(18, 16, 18, 16)));
            candidate.setAlignmentX(Component.LEFT_ALIGNMENT);
            candidate.setMaximumSize(new Dimension(520, 68));
            votedOptionsPanel.add(candidate);
            votedOptionsPanel.add(Box.createVerticalStrut(10));
        }
        votedOptionsPanel.revalidate();
        votedOptionsPanel.repaint();
        cards.show(root, "done");
        playUrnaSound();
    }

    private void logout() { auth.signOut(); voterName = null; votedOptions.clear(); cards.show(root, "login"); }
    private void close() { auth.signOut(); firebase.close(); frame.dispose(); }
    private static String normalize(String s) { return java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT); }
    private static JLabel label(String text) { JLabel l = new JLabel(text); l.setFont(new Font("SansSerif", Font.BOLD, 14)); l.setForeground(NAVY); return l; }
    private static JButton button(String text, Color color) { JButton b = new JButton(text); b.setBackground(color); b.setForeground(Color.WHITE); b.setFont(new Font("SansSerif", Font.BOLD, 15)); b.setFocusPainted(false); b.setBorder(new EmptyBorder(12, 20, 12, 20)); return b; }
    private static String message(Exception e) { Throwable cause = e; while (cause.getCause() != null) cause = cause.getCause(); return cause.getMessage() == null ? "Erro inesperado." : cause.getMessage(); }
    private void showError(String text) { JOptionPane.showMessageDialog(frame, text, "Não foi possível concluir", JOptionPane.WARNING_MESSAGE); }
    private static void playUrnaSound() {
        Thread sound = new Thread(() -> {
            float sampleRate = 22_050;
            AudioFormat format = new AudioFormat(sampleRate, 8, 1, true, false);
            try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
                line.open(format); line.start();
                playTone(line, sampleRate, 740, 180);
                playTone(line, sampleRate, 0, 70);
                playTone(line, sampleRate, 587, 180);
                playTone(line, sampleRate, 0, 70);
                playTone(line, sampleRate, 494, 360);
                line.drain();
            } catch (Exception ex) {
                Toolkit.getDefaultToolkit().beep();
            }
        }, "som-urna");
        sound.setDaemon(true);
        sound.start();
    }

    private static void playTone(SourceDataLine line, float sampleRate, int frequency, int durationMs) {
        byte[] samples = new byte[(int) (sampleRate * durationMs / 1000)];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = frequency == 0 ? 0 : (byte) (Math.sin(2 * Math.PI * frequency * i / sampleRate) * 105);
        }
        line.write(samples, 0, samples.length);
    }
}
