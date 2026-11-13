# Spec Delta: fork-governance

## MODIFIED Requirements

### Requirement: RF-01 - Identidade Dual, Coexistência de Pacotes e Preservação GPLv3
THE SYSTEM SHALL manter `applicationIdSuffix = ".abacate"` (verified at `app/build.gradle:21`), `fork_label_name` (`"Lançador"`) e `fork_app_name` (`"Abacate Lançador"`) (verified at `app/build.gradle:26-27`), substituir `"Olauncher"` por `"Abacate Lançador"` nas strings conversacionais via `app/src/fork/res/`, remover `forkIssuesLink` (verified at `app/src/fork/res/layout/fork_settings_section.xml:145`), redirecionar `shareApp()` para `Constants.URL_FORK_GITHUB`, ocultar fluxos de Play Store e preservar créditos e licença GNU GPLv3.

#### Scenario: Coexistência de pacotes e rótulos distintos no sistema e acessibilidade
- **WHEN** o APK `release` ou `debug` for compilado mantendo `applicationIdSuffix ".debug"` (verified at `app/build.gradle:47`) e as strings conversacionais forem resolvidas em `en` e `pt-rBR` (verified at `app/src/main/res/values/strings.xml:30-161` e `app/src/main/res/values-pt-rBR/strings.xml:6-129`)
- **THEN** o `applicationId` resultante SHALL ser `app.olauncher.abacate` (`release`) ou `app.olauncher.abacate.debug` (`debug`)
- **AND** o rótulo na gaveta/sistema SHALL resolver `app_name` para `fork_label_name` (`"Lançador"`, verified at `app/src/fork/res/values/strings.xml:3`), enquanto `MyAccessibilityService` (verified at `app/src/main/AndroidManifest.xml:95`) e `SettingsFragment` (verified at `app/src/main/java/app/olauncher/fork/settings/ForkSettingsDelegate.kt:33`) SHALL usar `fork_app_name` (`"Abacate Lançador"`)
- **AND** `app/src/fork/res/xml/accessibility_service_config.xml` SHALL substituir o original com `android:packageNames="app.olauncher.abacate, app.olauncher.abacate.debug, app.olauncher.debug, app.olauncher"` (verified at `app/src/fork/res/xml/accessibility_service_config.xml:9`)
- **AND** as strings conversacionais sobrepostas em `app/src/fork/res/values/strings.xml` e `app/src/fork/res/values-pt-rBR/strings.xml` (`set_as_default_launcher_first`, `admin_permission_message`, `share_message`, `keyboard_message`, `wallpaper_message`, `accessibility_service_description`, `please_set_olauncher_as_default_first`, `hidden_apps_message`, `accessibility_disclosure`, `launcher_failed_to_lock_device`, `olauncher_is_not_default_launcher`) SHALL referenciar `"Abacate Lançador"` em vez de `"Olauncher"`.

#### Scenario: Remoção de fluxos de Play Store e link de Issues na UI interna
- **WHEN** `SettingsFragment`, `MainActivity.checkForMessages()` (verified at `app/src/main/java/app/olauncher/MainActivity.kt:296`) ou `Context.shareApp()` (verified at `app/src/main/java/app/olauncher/helper/Utils.kt:613`) forem executados
- **THEN** a seção `"Abacate Lançador"` (`fork_settings_section.xml`) SHALL remover/ocultar `forkIssuesLink` mantendo `forkGithubLink` (`Constants.URL_FORK_GITHUB`) e `forkUpstreamCredit` (`Constants.URL_OLAUNCHER_GITHUB`) (verified at `app/src/main/java/app/olauncher/fork/settings/ForkSettingsDelegate.kt:72-74`)
- **AND** `moreFeatures`, `rate` e `footer` (`Note to Self` / `Pentastic`, verified at `app/src/main/res/layout/fragment_settings.xml:84,559,590`) SHALL permanecer ocultos (`View.GONE`) e inativos sem acionar `rateApp()` (verified at `app/src/main/java/app/olauncher/helper/Utils.kt:626`), `URL_OLAUNCHER_PRO`, `URL_NTS` ou `URL_PENTASTIC`
- **AND** `MainActivity` SHALL NOT disparar nem exibir os dialogs `Constants.Dialog.REVIEW`, `Constants.Dialog.RATE` ou `Constants.Dialog.PRO_MESSAGE` (verified at `app/src/main/java/app/olauncher/data/Constants.kt:13-19` e `app/src/main/java/app/olauncher/MainActivity.kt:240-286`)
- **AND** `Context.shareApp()` SHALL compartilhar `Constants.URL_FORK_GITHUB` (`"https://github.com/eusoumadson/abacate-launcher"`, verified at `app/src/main/java/app/olauncher/data/Constants.kt:108`) em vez de `Constants.URL_OLAUNCHER_PLAY_STORE`.

#### Scenario: Preservação integral da licença GPLv3 e atribuição upstream
- **WHEN** `LICENSE`, `README.md`, `SettingsFragment`, `ForkSettingsDelegate` e `Constants.kt` forem auditados
- **THEN** a licença GNU GPLv3 SHALL permanecer inalterada e todas as URLs originais (`URL_ABOUT_OLAUNCHER`, `URL_OLAUNCHER_GITHUB`, `URL_TWITTER_TANUJ`, `URL_OLAUNCHER_PRIVACY`, verified at `app/src/main/java/app/olauncher/data/Constants.kt:93-100`) SHALL permanecer intactas.

### Requirement: RF-02 - Versionamento CalVer, Ferramentas de Release e Remoto Git
THE SYSTEM SHALL carregar `VERSION_CODE` e `VERSION_NAME` de `version.properties` em `app/build.gradle` (verified at `app/build.gradle:4-5,24-25`), prover `Makefile` (verified at `Makefile:1-17`) e evoluir `app/src/fork/tools/release.py` (descrição `"Abacate Lançador"`) e `app/src/fork/tools/test_release.py` com preflight na branch `main`, colisão `.1`/`.2`, minuta via OpenSpec `proposal.md`, atualização de `CHANGELOG.md`, tag anotada e push interativo para `origin`.

#### Scenario: Versionamento dinâmico e simulação de release CalVer
- **WHEN** `python3 app/src/fork/tools/release.py --dry-run` e `./gradlew assembleDebug --no-daemon` forem executados
- **THEN** `release.py --dry-run` SHALL sair com código `0` simulando preflight, incremento de `VERSION_CODE`, resolução de `vYYYY.MM.DD` (ou `.1`/`.2`), minuta extraída do `proposal.md` mais recente, seção `## [VERSION_NAME]` em `CHANGELOG.md`, commit `chore(release)` e tag anotada sem alterar disco ou Git
- **AND** o `Makefile` SHALL expor alvos `release`, `dry-run`, `test` e `install`
- **AND** o APK compilado SHALL ler `versionCode` e `versionName` dinamicamente de `version.properties`.

#### Scenario: Preflight, colisão no mesmo dia, atualização de CHANGELOG e push interativo para origin
- **WHEN** `app/src/fork/tools/release.py` for executado sem `--dry-run`
- **THEN** IF o working tree possuir alterações não comitadas OR a branch atual for diferente de `main`, THEN `release.py` SHALL falhar com código de saída diferente de `0` sem efetuar alterações
- **AND** quando a tag `vYYYY.MM.DD` já existir, `release.py` SHALL sugerir `YYYY.MM.DD.1`, `YYYY.MM.DD.2` sucessivamente
- **AND** `release.py` SHALL extrair minuta do `proposal.md` mais recente em `openspec/changes/` ou `openspec/changes/archive/`, permitir edição via `$EDITOR` ou entrada manual, inserir `## [VERSION_NAME]` no topo de `CHANGELOG.md`, criar commit `chore(release)` + tag anotada `v<VERSION_NAME>` e exigir confirmação explícita antes de `git push origin main` e `git push origin v<VERSION_NAME>`.

#### Scenario: Remoto origin e README com Obtainium
- **WHEN** `git remote get-url origin` for verificado e `README.md` for inspecionado
- **THEN** `origin` SHALL apontar para `https://github.com/eusoumadson/abacate-launcher.git`
- **AND** `README.md` SHALL conter o badge e deep-link JSON 1-toque do Obtainium para `app.olauncher.abacate` em `https://github.com/eusoumadson/abacate-launcher`, créditos GPLv3 ao upstream e guia de build local.

### Requirement: RF-03 - Isolamento de Código, Overlay de Recursos e Governança Zero Lixo
THE SYSTEM SHALL isolar código exclusivo em `app/src/main/java/app/olauncher/fork/` e recursos em `app/src/fork/res/`, restringir modificações em arquivos upstream a enxertos únicos `FORK:<feature>` seguindo a política Zero Lixo de Compatibilidade em `AGENTS.md` (verified at `AGENTS.md:49-52`) e `docs/agents/coding_guidelines.md` (verified at `docs/agents/coding_guidelines.md:7-12`).

#### Scenario: Auditoria de pontos de enxerto, catálogo e Zero Lixo
- **WHEN** `grep -rn "FORK:" app/src/main/ app/build.gradle gradle/libs.versions.toml` for comparado a `docs/fork/FORK_INTEGRATION.md` e `docs/fork/FORK_FEATURES.md`
- **THEN** cada enxerto em arquivos compartilhados (`app/build.gradle`, `gradle/libs.versions.toml`, `AndroidManifest.xml`, `MainActivity.kt`, `HomeFragment.kt`, `SettingsFragment.kt`, `PinItemActivity.kt`, `Prefs.kt`, `Constants.kt`, `Utils.kt`) SHALL possuir marcador `FORK:<feature>` catalogado em `docs/fork/FORK_INTEGRATION.md`
- **AND** `docs/fork/FORK_FEATURES.md` SHALL catalogar todas as features do fork e `AGENTS.md` / `docs/agents/coding_guidelines.md` SHALL registrar a política de Zero Lixo de Compatibilidade.

### Requirement: RF-04 - Esteira de Integração Contínua e Publicação de Releases
WHEN ocorrer `push` na branch `main`, `pull_request`, `push` de tag `v*` ou acionamento manual `workflow_dispatch`, THE SYSTEM SHALL executar `.github/workflows/build.yml` na branch `main` e `.github/workflows/release.yml` com verificação via `apksigner verify -v`, publicação do asset `AbacateLancador-<version>-release.apk` e limpeza garantida da keystore.

#### Scenario: Execução de CI em PRs e publicação automática em tags CalVer
- **WHEN** os workflows `.github/workflows/build.yml` (verified at `.github/workflows/build.yml:1-37`) e `.github/workflows/release.yml` (verified at `.github/workflows/release.yml:1-84`) forem inspecionados ou executados
- **THEN** `build.yml` SHALL disparar em `push` para `main` (sem `master`) e `pull_request`, usando Java 17 e executando `python3 -m unittest app/src/fork/tools/test_release.py`, `./gradlew assembleDebug --no-daemon`, `./gradlew testDebugUnitTest --no-daemon` e `./gradlew lintDebug --no-daemon`
- **AND** `release.yml` SHALL suportar `push` em tags `v*` e `workflow_dispatch`, decodificar `KEYSTORE_BASE64`, assinar o APK de release, validar a assinatura com `apksigner verify -v` e extrair notas de `CHANGELOG.md`
- **AND** em `push` de tag `v*`, `release.yml` SHALL anexar `AbacateLancador-${version}-release.apk` na GitHub Release; em `workflow_dispatch`, SHALL enviar `AbacateLancador-${version}-release.apk` via `actions/upload-artifact` sem criar Release
- **AND** `release.yml` SHALL remover o arquivo temporário da keystore em passo com `if: always()`.

### Requirement: RNF-01 - Integridade de Build, Testes Unitários e Lint
THE SYSTEM SHALL manter compilação debug, suíte de testes unitários JVM, testes Python do ferramental de release, análise estática Android Lint e auditoria `FORK:` livres de erros bloqueantes, incluindo cobertura automatizada para `release.py`, `shareApp` e identidade do fork.

#### Scenario: Execução completa de verificação local
- **WHEN** `make test`, `./gradlew assembleDebug --no-daemon`, `./gradlew lintDebug --no-daemon` e `grep -rn "FORK:" app/src/main/ app/build.gradle gradle/libs.versions.toml` forem executados
- **THEN** `make test` (`test_release.py` + `testDebugUnitTest`), `assembleDebug` e `lintDebug` SHALL retornar código de saída `0`
- **AND** `test_release.py` SHALL cobrir preflight, colisão `.1`/`.2`, extração de `proposal.md` e inserção em `CHANGELOG.md`, e os testes JVM SHALL cobrir o redirecionamento de `shareApp()` para `Constants.URL_FORK_GITHUB` e a identidade do fork.

## ADDED Requirements

### Requirement: RF-05 - Configuração do Repositório Remoto GitHub, Branch `main` e Keystore de Release
THE SYSTEM SHALL consolidar o repositório remoto GitHub em `eusoumadson/abacate-launcher` com `defaultBranchRef = main` (a partir de `init-fork`), metadados em PT-BR, `hasIssuesEnabled = false`, keystore de release local ignorada pelo Git e os quatro secrets de assinatura cadastrados no GitHub Actions.

#### Scenario: Metadados do repositório remoto e branch padrão main
- **WHEN** os metadados do repositório `eusoumadson/abacate-launcher` forem consultados via `gh repo view`
- **THEN** o repositório SHALL estar renomeado de `eusoumadson/Olauncher` para `eusoumadson/abacate-launcher`
- **AND** `defaultBranchRef` SHALL ser `main` consolidada a partir de `init-fork`
- **AND** `description` SHALL estar redigida em PT-BR, `homepageUrl` SHALL ser `"https://github.com/eusoumadson/abacate-launcher/releases"`, `topics` SHALL refletir o projeto e `hasIssuesEnabled` SHALL ser `false`.

#### Scenario: Keystore local gitignored e secrets de assinatura no GitHub Actions
- **WHEN** a keystore local de release e os secrets de Actions de `eusoumadson/abacate-launcher` forem verificados
- **THEN** a keystore de release local SHALL estar fora do rastreamento do Git (coberta pelo `.gitignore`, verified at `.gitignore:2`)
- **AND** o repositório SHALL possuir cadastrados os 4 secrets no GitHub Actions: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` e `KEY_PASSWORD`.

### Requirement: RF-06 - Empacotamento e Assinatura de Release no Gradle
THE SYSTEM SHALL configurar `signingConfigs.release` em `app/build.gradle` com leitura de variáveis de ambiente e fallback em `local.properties` (documentado em `local.properties.example`), manter assinatura v2 e v3 para compatibilidade com `minSdkVersion 24` (verified at `app/build.gradle:22`) e desabilitar `dependenciesInfo` em APK e Bundle.

#### Scenario: Configuração de assinatura release com fallback local.properties e assinatura v2 + v3
- **WHEN** `app/build.gradle` avaliar `signingConfigs` e `buildTypes.release`
- **THEN** `signingConfigs.release` SHALL resolver `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` e `KEY_PASSWORD` priorizando variáveis de ambiente sobre `local.properties` somente quando todas estiverem presentes e o arquivo existir
- **AND** `signingConfigs.release` SHALL definir `enableV2Signing = true` e `enableV3Signing = true`
- **AND** `buildTypes.release` SHALL associar `signingConfigs.findByName("release")` sem quebrar builds sem keystore local
- **AND** `local.properties.example` SHALL documentar `sdk.dir` e as chaves opcionais `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` e `KEY_PASSWORD`.

#### Scenario: Exclusão de dependenciesInfo em APK e Bundle
- **WHEN** o bloco `dependenciesInfo` em `app/build.gradle` (verified at `app/build.gradle:55-58`) for inspecionado
- **THEN** `includeInApk` SHALL ser `false` AND `includeInBundle` SHALL ser `false`.

### Requirement: RF-07 - Exclusividade de Distribuição GitHub/Obtainium e Documentação de Governança
THE SYSTEM SHALL remover `fastlane/` e `.github/funding.yml`, reescrever `README.md` em PT-BR no padrão do Abacate Calendário com distribuição exclusiva GitHub/Obtainium (sem links de Play Store e com créditos GPLv3) e alinhar `CONTRIBUTING.md`, `AGENTS.md` e `docs/fork/*` à branch `main`.

#### Scenario: Remoção de fastlane/ e funding.yml e README.md com deep-link 1-toque Obtainium
- **WHEN** a raiz do repositório, `.github/` e `README.md` forem inspecionados
- **THEN** `fastlane/` e `.github/funding.yml` SHALL NOT existir no repositório
- **AND** `README.md` SHALL estar redigido em PT-BR no padrão do Abacate Calendário, contendo badge e deep-link JSON 1-toque do Obtainium com payload `{"id":"app.olauncher.abacate","url":"https://github.com/eusoumadson/abacate-launcher","author":"eusoumadson","name":"Abacate Lançador"}`
- **AND** `README.md` SHALL NOT conter links para Google Play Store (`play.google.com`) e SHALL preservar créditos GPLv3 ao projeto upstream `tanujnotes/Olauncher` e ao autor `@tanujnotes`.

#### Scenario: Alinhamento de CONTRIBUTING.md, AGENTS.md e docs/fork/* à branch main e distribuição exclusiva
- **WHEN** `CONTRIBUTING.md`, `AGENTS.md`, `docs/fork/FORK_INTEGRATION.md` e `docs/fork/FORK_FEATURES.md` forem inspecionados
- **THEN** `CONTRIBUTING.md` SHALL refletir o **Abacate Lançador**, a branch `main`, as verificações locais (`make test`, `assembleDebug`, `lintDebug`) e a licença GPLv3
- **AND** `AGENTS.md`, `docs/fork/FORK_INTEGRATION.md` e `docs/fork/FORK_FEATURES.md` SHALL refletir a remoção de `forkIssuesLink`, os pontos de enxerto atualizados, a assinatura Gradle e a distribuição exclusiva via GitHub Releases / Obtainium.
