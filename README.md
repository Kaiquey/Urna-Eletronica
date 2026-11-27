# 🗳️ Urna Eletrônica

**Simulador de urna eletrônica desktop**, desenvolvido em Java, com sistema de votação para os cargos de Presidente e Governador(a).

O projeto simula o funcionamento de uma urna eletrônica, oferecendo autenticação de eleitores, gerenciamento de candidatos e registro de votos, com armazenamento integrado ao Firebase.

---

## 📑 Sumário

- [✨ Funcionalidades](#-funcionalidades)
- [🛠️ Tecnologias](#️-tecnologias)
- [📁 Estrutura do Projeto](#-estrutura-do-projeto)
- [👥 Candidatos](#-candidatos)
- [📋 Pré-requisitos](#-pré-requisitos)
- [⚙️ Configuração](#️-configuração)
- [📥 Importação de Candidatos](#-importação-de-candidatos)
- [🚀 Compilação e Execução](#-compilação-e-execução)
- [🧪 Testes](#-testes)
- [🔄 CI/CD](#-cicd)
- [📜 Regras de Negócio](#-regras-de-negócio)

---

## ✨ Funcionalidades

- 🔐 **Autenticação administrativa:** login do administrador utilizando Firebase Authentication.
- 🧑‍💼 **Cadastro e autenticação de eleitores:** sistema de cadastro e login com senhas protegidas por hash PBKDF2-SHA256.
- 🏛️ **Sistema de votação:** votação dividida em duas etapas, Presidente e Governador(a).
- ✅ **Modalidades de voto:** possibilidade de votar em candidatos, votar em branco ou anular o voto.
- 🚫 **Controle de votação única:** cada eleitor pode votar apenas uma vez por cargo, utilizando os indicadores `votou_P` e `votou_G`.
- 🖼️ **Identificação dos candidatos:** exibição de fotografia, número de identificação e partido.
- 💻 **Distribuição para Windows:** geração de executável `.exe` utilizando Launch4j.

---

## 🛠️ Tecnologias

| Tecnologia | Utilização |
|---|---|
| ☕ Java 17 | Linguagem principal do projeto |
| 🐘 Gradle 9.6.0 | Gerenciamento de dependências e build |
| 🧩 Gson 2.14.0 | Manipulação de dados JSON |
| 🔥 Firebase Authentication | Autenticação de usuários |
| 🔥 Firebase Realtime Database | Armazenamento de dados |
| 🌐 Firebase REST API | Comunicação com os serviços Firebase |
| 🖥️ Java Swing | Desenvolvimento da interface gráfica |
| 🧪 JUnit 6 | Testes automatizados |
| 📦 Launch4j | Geração do executável Windows |
| 🤖 GitHub Actions | Integração contínua e automação de testes |

---

## 📁 Estrutura do Projeto

```text
Urna-Eletronica/
│
├── Urna/
│   ├── resources/
│   │   └── urna_View.fxml
│   │
│   └── src/
│       ├── exception/
│       │   ├── EleitorInaptoException
│       │   └── votoInvalidoException
│       │
│       ├── model/
│       │   ├── Pessoa
│       │   ├── Eleitor
│       │   ├── Candidato
│       │   └── StatusEleitoral
│       │
│       ├── service/
│       │   ├── UrnaEletronica
│       │   ├── EleitorAuthService
│       │   ├── CandidatoFirebaseService
│       │   │
│       │   └── firebase/
│       │       ├── FirebaseAdminSession
│       │       └── FirebaseBuildConfig
│       │
│       ├── test/
│       │   └── java/
│       │
│       └── view/
│           └── UrnaView
│
├── output_candidatos/
│   └── Arquivos JSON e SVG dos candidatos
│
├── scripts/
│   └── FirebaseCandidateImporter.java
│
├── .github/
│   └── workflows/
│       ├── gradle.yml
│       └── test-eleitores.yml
│
├── build.gradle.kts
└── .env.example
```

### Organização dos diretórios

- **model:** classes responsáveis pela representação dos dados e entidades do sistema.
- **service:** implementação das regras de negócio, autenticação e comunicação com o Firebase.
- **exception:** exceções personalizadas para situações específicas do sistema.
- **view:** componentes responsáveis pela interface gráfica.
- **test:** testes automatizados e de integração.
- **output_candidatos:** arquivos de dados e imagens dos candidatos.
- **scripts:** ferramentas auxiliares, incluindo o importador de candidatos.
- **.github/workflows:** configurações dos processos de integração contínua.

---

## 👥 Candidatos

O sistema possui oito candidatos, distribuídos entre quatro partidos políticos fictícios.

| Número | Nome | Cargo | Partido |
|---|---|---|---|
| 10 | Caio Cubista | 🎖️ Presidente | MLG |
| 11 | Luna Redstone | 🏢 Governadora | MLG |
| 20 | Rafael Vortex | 🎖️ Presidente | PVP |
| 21 | Maya Clutch | 🏢 Governadora | PVP |
| 30 | Otávio Nogueira | 🎖️ Presidente | CV |
| 31 | Bianca Falcão | 🏢 Governadora | CV |
| 40 | Dr. Teodoro Faísca | 🎖️ Presidente | PHD |
| 41 | Dra. Helena Volta | 🏢 Governadora | PHD |

### Partidos políticos

- **MLG:** Movimento Liberal e Garantista.
- **PVP:** Partido da Vida Pública.
- **CV:** Cidadania e Valores.
- **PHD:** Partido Humanista Democrático.

---

## 📋 Pré-requisitos

Antes de executar o projeto, é necessário possuir:

- ☕ JDK 17 ou superior.
- 🔥 Projeto configurado no Firebase.
- 🔐 Firebase Authentication com o provedor E-mail/Senha habilitado.
- 🗄️ Firebase Realtime Database criado e configurado.
- 👤 Usuário administrador previamente cadastrado no Firebase.

---

## ⚙️ Configuração

1. Clone o repositório:

```bash
git clone https://github.com/Kaiquey/Urna-Eletronica.git
```

2. Entre no diretório do projeto:

```bash
cd Urna-Eletronica
```

3. Configure as credenciais e variáveis necessárias para a integração com o Firebase, utilizando o arquivo `.env.example` como referência.

4. Verifique se o projeto Firebase possui:
   - Authentication habilitado.
   - Provedor de autenticação por E-mail/Senha ativo.
   - Realtime Database criado.
   - Usuário administrador cadastrado.

**Importante:** não compartilhe credenciais, chaves privadas ou arquivos com informações sensíveis no repositório público.

---

## 📥 Importação de Candidatos

Os dados dos candidatos estão organizados no diretório `output_candidatos/`.

Cada candidato possui arquivos de dados e imagem utilizados na identificação durante a votação.

O projeto também disponibiliza o script:

```text
scripts/FirebaseCandidateImporter.java
```

Esse script é responsável pelo processo de importação dos candidatos para o Firebase.

---

## 🚀 Compilação e Execução

O gerenciamento de dependências e a compilação do projeto são realizados pelo Gradle.

**Ferramentas utilizadas:**
- Gradle 9.6.0.
- JDK 17 ou superior.
- Launch4j para geração do executável Windows.

O projeto também possui suporte à geração de um executável `.exe`, facilitando sua distribuição e execução em ambientes Windows.

---

## 🧪 Testes

O projeto utiliza **JUnit 6** para a realização de testes automatizados.

Os testes estão organizados no diretório:

```text
Urna/src/test/java/
```

Também existem configurações específicas de testes automatizados no workflow:

```text
.github/workflows/test-eleitores.yml
```

---

## 🔄 CI/CD

O projeto utiliza **GitHub Actions** para automatizar processos de integração contínua.

Workflows disponíveis:

| Arquivo | Responsabilidade |
|---|---|
| `gradle.yml` | Automação relacionada ao build do Gradle |
| `test-eleitores.yml` | Automação dos testes relacionados aos eleitores |

Esses processos auxiliam na verificação e manutenção da qualidade do código durante o desenvolvimento.

---

## 📜 Regras de Negócio

O sistema segue as seguintes regras:

1. 🔞 **Idade mínima:** eleitores com menos de 16 anos são considerados inaptos para votar.
2. 1️⃣ **Voto único:** cada eleitor pode votar apenas uma vez para cada cargo.
3. ⚪ **Voto em branco:** o número `0` representa o voto em branco.
4. ⚫ **Voto nulo:** números que não correspondem a candidatos cadastrados representam votos nulos.
5. 🪪 **CPF:** deve possuir exatamente 11 dígitos.
6. 🗳️ **Título de eleitor:** deve possuir exatamente 12 dígitos.
7. 🏛️ **Etapas de votação:** o eleitor deve votar para Presidente e Governador(a), respeitando o controle individual de cada cargo.

---

## 👨‍💻 Desenvolvimento

Projeto desenvolvido como atividade acadêmica, com foco na aplicação prática de conceitos de programação orientada a objetos, desenvolvimento de interfaces gráficas, autenticação, persistência de dados e integração com serviços externos.

**Urna Eletrônica — Simulando o processo democrático através da tecnologia.** 🗳️

## Executar a versão compilada

Baixe `Urna-Eletronica.zip` em Releases, extraia todo o conteúdo para uma pasta e abra `Urna-Eletronica.exe`. O pacote inclui o runtime Java e as bibliotecas da aplicação, então não é necessário instalar o JDK ou o JRE.

Mantenha o executável na pasta extraída, ao lado das pastas `jre` e `lib`.
