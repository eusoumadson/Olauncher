# fork-governance Specification

## Purpose
Estabelecer a fundação estrutural, identidade dual, coexistência de pacotes, versionamento CalVer, isolamento de código/recursos por Delegate e overlay, documentação de governança e esteira CI/CD do fork **Abacate Lançador** sobre o Olauncher upstream.

## Requirements

### Requirement: RF-01 - Identidade Dual, Coexistência de Pacotes e Preservação GPLv3
THE SYSTEM SHALL aplicar `applicationIdSuffix = ".abacate"` em `defaultConfig` (verified at `app/build.gradle:16-25`), overlay em `app/src/fork/res/` com `fork_label_name` (`"Lançador"`) e `fork_app_name` (`"Abacate Lançador"`), e manter créditos e URLs upstream GPLv3 (verified at `app/src/main/java/app/olauncher/data/Constants.kt:93-107`).

#### Scenario: Coexistência de pacotes e rótulos distintos no sistema e acessibilidade
- **WHEN** o APK `release` ou `debug` for compilado mantendo `applicationIdSuffix ".debug"` (verified at `app/build.gradle:36`)
- **THEN** o `applicationId` resultante SHALL ser `app.olauncher.abacate` (`release`) ou `app.olauncher.abacate.debug` (`debug`)
- **AND** o rótulo na gaveta/sistema SHALL resolver para `fork_label_name` (`"Lançador"`), enquanto telas internas e `MyAccessibilityService` (verified at `app/src/main/AndroidManifest.xml:94`) SHALL usar `fork_app_name` (`"Abacate Lançador"`)
- **AND** `app/src/fork/res/xml/accessibility_service_config.xml` SHALL substituir o original (verified at `app/src/main/res/xml/accessibility_service_config.xml:9`) com `android:packageNames="app.olauncher.abacate, app.olauncher.abacate.debug, app.olauncher.debug, app.olauncher"`.

#### Scenario: Preservação integral da licença GPLv3 e atribuição upstream
- **WHEN** `LICENSE`, `README.md`, `SettingsFragment` e `Constants.kt` forem auditados
- **THEN** a licença GNU GPLv3 SHALL permanecer inalterada e todas as URLs originais (`URL_ABOUT_OLAUNCHER`, `URL_OLAUNCHER_GITHUB`, `URL_TWITTER_TANUJ`, `URL_OLAUNCHER_PRIVACY`) SHALL permanecer intactas.

### Requirement: RF-02 - Versionamento CalVer, Ferramentas de Release e Remoto Git
THE SYSTEM SHALL carregar `VERSION_CODE=116` e `VERSION_NAME=2026.11.12` de `version.properties` em `app/build.gradle` (substituindo `versionCode 115` e `versionName "v6.11.3"`, verified at `app/build.gradle:20-21`), prover `Makefile`, `app/src/fork/tools/release.py`, `CHANGELOG.md`, `README.md` com badge Obtainium e configurar o remote `origin`.

#### Scenario: Versionamento dinâmico e simulação de release CalVer
- **WHEN** `python3 app/src/fork/tools/release.py --dry-run` e `./gradlew assembleDebug --no-daemon` forem executados
- **THEN** `release.py --dry-run` SHALL sair com código `0` simulando bump `vYYYY.MM.DD` e notas de `CHANGELOG.md` sem alterar o histórico Git
- **AND** o `Makefile` SHALL expor alvos `release`, `dry-run`, `test` e `install`
- **AND** o APK compilado SHALL conter `versionCode` `116` e `versionName` `"2026.11.12"`.

#### Scenario: Remoto origin e README com Obtainium
- **WHEN** `git remote get-url origin` for verificado e `README.md` for inspecionado
- **THEN** `origin` SHALL apontar para `https://github.com/eusoumadson/abacate-launcher.git`
- **AND** `README.md` SHALL conter o badge/deep-link Obtainium para `app.olauncher.abacate` em `https://github.com/eusoumadson/abacate-launcher`, créditos ao upstream e guia de build local.

### Requirement: RF-03 - Isolamento de Código, Overlay de Recursos e Governança Zero Lixo
THE SYSTEM SHALL isolar código exclusivo em `app/src/main/java/app/olauncher/fork/` e recursos em `app/src/fork/res/`, restringir modificações em arquivos upstream a enxertos únicos `// FORK:<feature>` (Delegate/Decorator) seguindo a política Zero Lixo de Compatibilidade em `AGENTS.md` (verified at `AGENTS.md:37-47`) e `docs/agents/coding_guidelines.md` (verified at `docs/agents/coding_guidelines.md:1-30`).

#### Scenario: Auditoria de pontos de enxerto, catálogo e Zero Lixo
- **WHEN** `grep -rn "// FORK:" app/src/main/ app/build.gradle` for comparado a `docs/fork/FORK_INTEGRATION.md` e `docs/fork/FORK_FEATURES.md`
- **THEN** cada enxerto em arquivos compartilhados (`app/build.gradle`, `gradle/libs.versions.toml`, `MainActivity.kt`, `HomeFragment.kt`, `SettingsFragment.kt`, `PinItemActivity.kt`, `Prefs.kt`, `Constants.kt`) SHALL possuir linha única com `// FORK:<feature>` catalogada em `docs/fork/FORK_INTEGRATION.md`
- **AND** `docs/fork/FORK_FEATURES.md` SHALL catalogar todas as features do fork e `AGENTS.md` / `docs/agents/coding_guidelines.md` SHALL registrar a política de Zero Lixo de Compatibilidade.

### Requirement: RF-04 - Esteira de Integração Contínua e Publicação de Releases
WHEN ocorrer push na branch principal, pull request ou push de tag `v*`, THE SYSTEM SHALL executar os workflows `.github/workflows/build.yml` (JDK 17, `assembleDebug`, `testDebugUnitTest`) e `.github/workflows/release.yml` (APK `release` assinado via secrets com notas do `CHANGELOG.md` para Obtainium).

#### Scenario: Execução de CI em PRs e publicação automática em tags CalVer
- **WHEN** os workflows `.github/workflows/build.yml` e `.github/workflows/release.yml` forem inspecionados
- **THEN** `build.yml` SHALL usar Java 17 e executar `./gradlew assembleDebug --no-daemon` e `./gradlew testDebugUnitTest --no-daemon`
- **AND** `release.yml` SHALL disparar em tags `v*`, assinar o APK via GitHub Secrets, extrair notas de `CHANGELOG.md` e anexar `AbacateLauncher-${version}-release.apk` na GitHub Release.

### Requirement: RNF-01 - Integridade de Build, Testes Unitários e Lint
THE SYSTEM SHALL manter compilação debug, suíte de testes unitários JVM e análise estática Android Lint livres de erros bloqueantes.

#### Scenario: Execução completa de verificação local
- **WHEN** `./gradlew assembleDebug --no-daemon`, `./gradlew testDebugUnitTest --no-daemon` e `./gradlew lintDebug --no-daemon` forem executados
- **THEN** os três comandos SHALL retornar código de saída `0`.
