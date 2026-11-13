# Abacate Lançador

> **Nota:** Este projeto é um fork do [Olauncher](https://github.com/tanujnotes/Olauncher), criado originalmente por [@tanujnotes](https://x.com/tanujnotes). O fork é mantido sob a mesma licença **GNU GPLv3**, preservando o minimalismo, código aberto e ausência total de anúncios ou rastreamento.

O **Abacate Lançador** (`app.olauncher.abacate`) é um launcher minimalista, ad-free e de código aberto para Android, preparado para coexistir lado a lado com o Olauncher original e evoluir com recursos isolados do fork.

## Instalação e Atualizações (Obtainium)

Você pode instalar e receber atualizações diretamente das GitHub Releases usando o [Obtainium](https://github.com/ImranR98/Obtainium):

[![Get it on Obtainium](https://img.shields.io/badge/Obtainium-abacate--launcher-4CAF50?style=for-the-badge)](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/eusoumadson/abacate-launcher)

- **Repositório:** `https://github.com/eusoumadson/abacate-launcher`
- **Package ID (Release):** `app.olauncher.abacate`
- **Package ID (Debug):** `app.olauncher.abacate.debug`
- **Deep-link Obtainium:** `obtainium://add/https://github.com/eusoumadson/abacate-launcher`

## Build Local e Testes

### Pré-requisitos
- **Java 17** (JVM target 17)
- **Android SDK** com `compileSdk 36` / `targetSdk 36` (`minSdk 24`)
- **Python 3** (para o script de release CalVer e testes unitários do tooling)

### Comandos

```sh
# Compilar APK debug (app.olauncher.abacate.debug)
./gradlew assembleDebug --no-daemon

# Rodar testes unitários JVM (JUnit 4)
./gradlew testDebugUnitTest --no-daemon

# Rodar suíte completa (Python + Gradle) via Makefile
make test

# Simular bump de versão CalVer sem alterar o Git
make dry-run
```

## Governança do Fork

- O código exclusivo do fork fica isolado em `app/src/main/java/app/olauncher/fork/` e os recursos em `app/src/fork/res/`.
- Detalhes sobre pontos de enxerto (`// FORK:<feature>`) e sincronização com o upstream estão em [docs/fork/FORK_INTEGRATION.md](docs/fork/FORK_INTEGRATION.md) e [docs/fork/FORK_FEATURES.md](docs/fork/FORK_FEATURES.md).

## Licença e Créditos

- **Licença:** [GNU General Public License v3.0 (GPLv3)](LICENSE)
- **Projeto Original / Upstream:** [tanujnotes/Olauncher](https://github.com/tanujnotes/Olauncher)
- **Autor Original:** [@tanujnotes](https://x.com/tanujnotes)

### Outros projetos da equipe do Olauncher original

- [Pro Launcher](https://play.google.com/store/apps/details?id=app.prolauncher) - Versão Pro do Olauncher com widgets, clima, pastas, etc.
- [Note to Self](https://play.google.com/store/apps/details?id=com.makenotetoself) - App de anotações gratuito e [código aberto](https://github.com/jeerovan/ntsapp).
- [Pentastic](https://play.google.com/store/apps/details?id=app.pentastic) - App minimalista de tarefas e lembretes gratuito e [código aberto](https://github.com/tanujnotes/Pentastic).
- [Supernova](https://github.com/tanujnotes/Supernova) - Alternativa [open source](https://github.com/tanujnotes/Supernova) ao Nova Launcher.
