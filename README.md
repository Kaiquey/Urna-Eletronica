# Urna Eletrônica
🗳️ Urna Eletrônica

Simulador de urna eletrônica desktop com votação para Presidente e Governador(a)



📑 Sumário

✨ Funcionalidades

🛠️ Tecnologias

📁 Estrutura

👥 Candidatos

📋 Pré-requisitos

⚙️ Configuração

📥 Importar candidatos

🚀 Compilar e executar

🧪 Testes

🔄 CI/CD

📜 Regras de negócio



✨ Funcionalidades
🔐 Login do administrador via Firebase Authentication
🧑‍💼 Cadastro e login de eleitores com hash de senha PBKDF2-SHA256
🏛️ Votação em duas etapas: Presidente e Governador(a)
✅ Voto em candidato, em branco ou nulo
🚫 Controle de voto único por cargo (votou_P e votou_G)
🖼️ Exibição de foto, número e partido do candidato
💻 Geração de executável Windows (.exe) via Launch4j



🛠️ Tecnologias
Tecnologia	Uso
☕ Java 17	Linguagem principal
🐘 Gradle 9.6.0	Build e dependências
🧩 Gson 2.14.0	Manipulação de JSON
🔥 Firebase Auth + Realtime Database	Autenticação e armazenamento (REST)
🖥️ Swing	Interface gráfica
🧪 JUnit 6	Testes
📦 Launch4j	Geração do .exe
🤖 GitHub Actions	Integração contínua



📁 Estrutura
Urna-Eletronica/
├── 📂 Urna/
│   ├── 📂 resources/          # urna_View.fxml
│   └── 📂 src/
│       ├── 📂 exception/      # EleitorInaptoException, votoInvalidoException
│       ├── 📂 model/          # Pessoa, Eleitor, Candidato, StatusEleitoral
│       ├── 📂 service/        # UrnaEletronica, EleitorAuthService, CandidatoFirebaseService
│       │   └── 📂 firebase/   # FirebaseAdminSession, FirebaseBuildConfig
│       ├── 📂 test/java/      # Testes de integração
│       └── 📂 view/           # UrnaView (interface Swing)
├── 📂 output_candidatos/      # data.json e image.svg de cada candidato
├── 📂 scripts/                # FirebaseCandidateImporter.java
├── 📂 .github/workflows/      # gradle.yml, test-eleitores.yml
├── 📄 build.gradle.kts
└── 📄 .env.example



👥 Candidatos
Nº	Nome	Cargo	Partido
10	Caio Cubista	🎖️ Presidente	MLG - Movimento Liberal e Garantista
11	Luna Redstone	🏢 Governadora	MLG - Movimento Liberal e Garantista
20	Rafael Vortex	🎖️ Presidente	PVP - Partido da Vida Pública
21	Maya Clutch	🏢 Governadora	PVP - Partido da Vida Pública
30	Otávio Nogueira	🎖️ Presidente	CV - Cidadania e Valores
31	Bianca Falcão	🏢 Governadora	CV - Cidadania e Valores
40	Dr. Teodoro Faísca	🎖️ Presidente	PHD - Partido Humanista Democrático
41	Dra. Helena Volta	🏢 Governadora	PHD - Partido Humanista Democrático



📋 Pré-requisitos
☕ JDK 17 ou superior
🔥 Projeto Firebase com:
Authentication (provedor E-mail/Senha) habilitado
Realtime Database criado
Usuário administrador cadastrado


📜 Regras de negócio
🔞 Eleitor com menos de 16 anos é considerado inapto
1️⃣ Cada eleitor vota uma única vez por cargo
⚪ Número 0 = voto em branco
⚫ Número inexistente = voto nulo
🪪 CPF com 11 dígitos e título de eleitor com 12 dígitos
