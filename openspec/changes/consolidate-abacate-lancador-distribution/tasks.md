# Tasks

## Phase 1: Empacotamento, Assinatura Gradle e Pipelines CI/CD

Antes de implementar, leia:
1. `AGENTS.md`, `docs/agents/architecture.md` e `docs/agents/coding_guidelines.md` — regras de Fork Isolation, Zero Lixo e comandos de build/lint/teste
2. `openspec/changes/consolidate-abacate-lancador-distribution/proposal.md`, `openspec/changes/consolidate-abacate-lancador-distribution/specs/fork-governance/spec.md` e `openspec/changes/consolidate-abacate-lancador-distribution/design.md` — requisitos `RF-04`, `RF-05`, `RF-06` e limites RIGID

- [ ] T01 — Proteger keystores no `.gitignore` e criar `local.properties.example`
      Arquivos: `.gitignore`, `local.properties.example`
      Mudança: Adicionar `*.keystore` em `.gitignore` (complementando `*.jks`) e criar `local.properties.example` documentando `sdk.dir` obrigatório e as quatro propriedades opcionais de assinatura release (`KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) no padrão do `abacate-calendario`.
      Cobre: RF-05, RF-06, AC-01, AC-02
      Acceptance criteria: `.gitignore` ignora `*.jks` e `*.keystore` e `local.properties.example` existe na raiz documentando `sdk.dir`, `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` e `KEY_PASSWORD`.
      Testes: `git check-ignore -v release.keystore release.jks` retorna match válido em `.gitignore` e `./gradlew assembleDebug --no-daemon` permanece verde.

- [ ] T02 — Configurar `signingConfigs.release` (v2 + v3) e `dependenciesInfo` em `app/build.gradle`
      Arquivos: `app/build.gradle`
      Mudança: Carregar `local.properties` opcional no topo (enxerto `// FORK:fork-identity`), configurar `signingConfigs.release` em Groovy DSL resolvendo individualmente `System.getenv(k) ?: localProps.getProperty(k)` para `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` e `KEY_PASSWORD` apenas quando todas as 4 chaves forem não vazias e `file(keystorePath).exists()` for verdadeiro, habilitar `enableV2Signing = true` e `enableV3Signing = true`, associar `signingConfig signingConfigs.findByName("release")` em `buildTypes.release`, e definir `dependenciesInfo { includeInApk = false; includeInBundle = false }`.
      Cobre: RF-06, AC-02
      Acceptance criteria: `app/build.gradle` possui enxertos `// FORK:fork-identity` configurando `signingConfigs.release` com fallback individual `env ?: localProps`, `enableV2Signing = true`, `enableV3Signing = true`, `signingConfigs.findByName("release")` em `buildTypes.release` e `dependenciesInfo.includeInApk = false` e `includeInBundle = false`.
      Testes: `./gradlew assembleDebug --no-daemon` e `./gradlew assembleRelease --no-daemon` (sem keystore configurada, gerando APK release não assinado sem falha de avaliação Gradle) executam com código `0`.

- [ ] T03 — Alinhar `.github/workflows/build.yml` à branch `main`
      Arquivos: `.github/workflows/build.yml`
      Mudança: Atualizar o trigger `on.push.branches` removendo `master` e mantendo apenas `main` (preservando `pull_request`), JDK 17 e os passos `test_release.py`, `assembleDebug`, `testDebugUnitTest` e `lintDebug`.
      Cobre: RF-04, RNF-01, AC-03, AC-07
      Acceptance criteria: `.github/workflows/build.yml` dispara `push` apenas para `branches: [main]` (sem `master`) e `pull_request`, usando Java `17` e executando os quatro gates (`test_release.py`, `assembleDebug`, `testDebugUnitTest`, `lintDebug`).
      Testes: `make test`, `./gradlew assembleDebug --no-daemon` e `./gradlew lintDebug --no-daemon` retornam código `0`.

- [ ] T04 — Atualizar `.github/workflows/release.yml` para `v*` e `workflow_dispatch` com `apksigner`
      Arquivos: `.github/workflows/release.yml`
      Mudança: Adicionar trigger `workflow_dispatch`; manter JDK 17; decodificar `KEYSTORE_BASE64` para arquivo temporário; exportar `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` e `KEY_PASSWORD` para `./gradlew assembleRelease --no-daemon` (exercitando `signingConfigs.release` sem `-Pandroid.injected.signing.*`); resolver `version` via `${GITHUB_REF_NAME#v}` em `push` de tag `v*` ou `version.properties` em `workflow_dispatch`; verificar assinatura com `apksigner verify -v` localizando o binário em `$ANDROID_HOME/build-tools`; renomear APK para `AbacateLancador-${version}-release.apk`; extrair notas de `CHANGELOG.md` via `awk` no padrão do `abacate-calendario`; publicar GitHub Release via `softprops/action-gh-release@v2` apenas quando `github.event_name == 'push'`; fazer `actions/upload-artifact@v4` apenas quando `github.event_name == 'workflow_dispatch'`; e limpar a keystore temporária em passo `if: always()`.
      Cobre: RF-04, AC-03
      Acceptance criteria: `.github/workflows/release.yml` suporta `push` em `v*` e `workflow_dispatch`, passa as 4 env vars de keystore ao `./gradlew assembleRelease --no-daemon`, executa `apksigner verify -v`, produz `AbacateLancador-${version}-release.apk`, publica Release em tag push vs artifact em `workflow_dispatch` e limpa a keystore em passo `if: always()`.
      Testes: Validação sintática YAML (`python3 -c 'import yaml; ...'` ou parser equivalente se disponível) e revisão de conformidade com todos os cenários de `RF-04`.

## Phase 2: Automação Completa de Release CalVer (`release.py` e `test_release.py`)

Antes de implementar, leia:
1. `AGENTS.md`, `docs/agents/architecture.md` e `docs/agents/coding_guidelines.md` — políticas de código Python, Zero Lixo e testes
2. `openspec/changes/consolidate-abacate-lancador-distribution/proposal.md`, `openspec/changes/consolidate-abacate-lancador-distribution/specs/fork-governance/spec.md` e `openspec/changes/consolidate-abacate-lancador-distribution/design.md` — requisitos `RF-02`, `RNF-01` e `AC-04`

- [ ] T05 — Expandir `app/src/fork/tools/test_release.py` (TDD) para o novo contrato CalVer
      Arquivos: `app/src/fork/tools/test_release.py`
      Mudança: Escrever testes unitários cobrindo: `bump_version_code`, `resolve_version_name` (sem colisão, colisão `.1`, múltiplas colisões `.2`/`.3`), `check_preflight` (working tree sujo, branch diferente de `main`, limpo + `main`), `extract_changelog_from_proposal` (`## What Changes` e fallback `## Scope` -> `In:`), `find_latest_openspec_proposal` (priorizando `openspec/changes/archive/` descendente por nome com fallback para `openspec/changes/` ativo), `prepend_changelog` (preservando `# Changelog`), `fallback_manual_notes`, `--dry-run` sem mutação de disco/git, falha de preflight em execução sem `--dry-run`, e confirmação negativa de push mantendo commit `chore(release)` e tag anotada `v<VERSION_NAME>` locais.
      Cobre: RF-02, RNF-01, AC-04, AC-07
      Acceptance criteria: `app/src/fork/tools/test_release.py` contém testes automatizados explícitos para preflight, colisão `.1`/`.2`, extração de `proposal.md` (`What Changes` e `Scope` -> `In`), priorização `archive/` -> `changes/`, inserção em `CHANGELOG.md`, `--dry-run` e recusa de push preservando commit e tag locais.
      Testes: `python3 -m unittest app/src/fork/tools/test_release.py` (deve falhar antes da T06 e passar 100% após a T06).

- [ ] T06 — Evoluir `app/src/fork/tools/release.py` no padrão completo do `abacate-calendario`
      Arquivos: `app/src/fork/tools/release.py`
      Mudança: Reescrever `release.py` com descrição `"Assistente de Release - Abacate Lançador"` mantendo zero código morto (remover função obsoleta `extract_changelog_notes` se não mais utilizada): implementar `check_preflight` (exigindo branch `main` e `git status --porcelain` vazio), `bump_version_code`, `resolve_version_name` (calculando `.1`, `.2` sobre tags locais), prompt interativo com `[Enter]` para aceitar sugestão ou `--version` customizado, `find_latest_openspec_proposal` (priorizando `openspec/changes/archive/` em ordem descendente e considerando `openspec/changes/` ativo como fallback), `extract_changelog_from_proposal` (`## What Changes` com fallback `## Scope` -> `In:`), fluxo `[1] aceitar / [2] $EDITOR / [3] entrada manual` (`edit_in_editor`, `fallback_manual_notes`), `prepend_changelog` inserindo `## [VERSION_NAME]` no topo de `CHANGELOG.md`, commit `chore(release): bump version to <VERSION_NAME> (<VERSION_CODE>)`, tag anotada `v<VERSION_NAME>`, e confirmação explícita `[s/N]` antes de `git push origin main` e `git push origin v<VERSION_NAME>` (imprimindo comandos manuais caso recusado e mantendo `--dry-run` sem efeitos colaterais).
      Cobre: RF-02, AC-04
      Acceptance criteria: `app/src/fork/tools/release.py` executa preflight (`main` + tree limpo), resolve colisão `.1`/`.2`, extrai minuta do `proposal.md` mais recente, suporta `$EDITOR`/manual, insere seção em `CHANGELOG.md`, cria commit `chore(release)` + tag anotada `v<VERSION_NAME>`, confirma explicitamente antes de `git push origin main` / `v<VERSION_NAME>` e exibe `"Abacate Lançador"` na descrição `--help`.
      Testes: `python3 -m unittest app/src/fork/tools/test_release.py` passa integralmente e `python3 app/src/fork/tools/release.py --dry-run` retorna código `0` sem alterar arquivos ou refs Git.

- [ ] T07 — Validar `Makefile` e suíte de Phase 2
      Arquivos: `Makefile`
      Mudança: Verificar e garantir que os alvos `release`, `dry-run`, `test` e `install` no `Makefile` invocam corretamente `app/src/fork/tools/release.py` e os comandos Gradle esperados; executar `make dry-run` e `make test`.
      Cobre: RF-02, RNF-01, AC-04, AC-07
      Acceptance criteria: `Makefile` expõe os alvos `release`, `dry-run`, `test` e `install` funcionais e `make test` + `make dry-run` retornam código `0`.
      Testes: `make test` e `make dry-run` passam sem erro nem modificações em disco.

## Phase 3: Identidade Interna, Remoção de Fluxos de Play Store e Testes JVM

Antes de implementar, leia:
1. `AGENTS.md`, `docs/agents/architecture.md` e `docs/agents/coding_guidelines.md` — Fork Isolation, políticas puras testáveis em JVM e Zero Lixo
2. `openspec/changes/consolidate-abacate-lancador-distribution/proposal.md`, `openspec/changes/consolidate-abacate-lancador-distribution/specs/fork-governance/spec.md` e `openspec/changes/consolidate-abacate-lancador-distribution/design.md` — requisitos `RF-01`, `RNF-01` e `AC-06`, `AC-07`

- [ ] T08 — Criar políticas puras `ForkSharePolicy` e `ForkDistributionPolicy` com testes JVM (TDD)
      Arquivos: `app/src/main/java/app/olauncher/fork/settings/ForkSharePolicy.kt`, `app/src/main/java/app/olauncher/fork/settings/ForkDistributionPolicy.kt`, `app/src/test/java/app/olauncher/fork/settings/ForkSharePolicyTest.kt`, `app/src/test/java/app/olauncher/fork/settings/ForkDistributionPolicyTest.kt`
      Mudança: Criar `ForkSharePolicy` (expondo `shareUrl()` = `Constants.URL_FORK_GITHUB` e `buildShareMessage(prompt: String)` concatenando `prompt + "\n" + Constants.URL_FORK_GITHUB`) e `ForkDistributionPolicy` (expondo `resolveActiveUserState(userState: String): String` mapeando `REVIEW`/`RATE` -> `SHARE`, `nextStateAfterWallpaper(): String` -> `SHARE`, e `shouldSuppressDialog(dialog: String): Boolean` retornando `true` para `Dialog.REVIEW`, `Dialog.RATE` e `Dialog.PRO_MESSAGE`); escrever os testes unitários JVM correspondentes sem dependências Android.
      Cobre: RF-01, RNF-01, AC-06, AC-07
      Acceptance criteria: `ForkSharePolicy` retorna `Constants.URL_FORK_GITHUB` na mensagem de compartilhamento, `ForkDistributionPolicy` avança `WALLPAPER`/`REVIEW`/`RATE` para `SHARE` e suprime `REVIEW`/`RATE`/`PRO_MESSAGE`, e ambos possuem testes JVM passando.
      Testes: `./gradlew testDebugUnitTest --no-daemon --tests "app.olauncher.fork.settings.ForkSharePolicyTest" --tests "app.olauncher.fork.settings.ForkDistributionPolicyTest"` passa 100%.

- [ ] T09 — Atualizar overlays de strings conversacionais (`en` e `pt-rBR`) e remover `forkIssuesLink` / `fork_issues` dos recursos do fork
      Arquivos: `app/src/fork/res/values/strings.xml`, `app/src/fork/res/values-pt-rBR/strings.xml`, `app/src/fork/res/layout/fork_settings_section.xml`
      Mudança: Sobrescrever em `app/src/fork/res/values/strings.xml` e `app/src/fork/res/values-pt-rBR/strings.xml` as 11 strings conversacionais (`set_as_default_launcher_first`, `admin_permission_message`, `share_message`, `keyboard_message`, `wallpaper_message`, `accessibility_service_description`, `please_set_olauncher_as_default_first`, `hidden_apps_message`, `accessibility_disclosure`, `launcher_failed_to_lock_device`, `olauncher_is_not_default_launcher`) substituindo `"Olauncher"` por `"Abacate Lançador"` (preservando `app_name = @string/fork_label_name` e os `tools:ignore="MissingTranslation"` em `values/strings.xml`); remover `fork_issues` de ambos os arquivos de strings e remover o `TextView` `forkIssuesLink` de `app/src/fork/res/layout/fork_settings_section.xml` mantendo `forkGithubLink` e `forkUpstreamCredit`.
      Cobre: RF-01, AC-06
      Acceptance criteria: As 11 strings em `en` e `pt-rBR` referenciam `"Abacate Lançador"` sem `"Olauncher"`, `fork_issues` não existe em `app/src/fork/res/`, `forkIssuesLink` foi removido de `fork_settings_section.xml`, e `app/src/main/res/` não sofreu nenhuma edição.
      Testes: `./gradlew assembleDebug --no-daemon` e `./gradlew lintDebug --no-daemon` retornam código `0` sem novos avisos de lint.

- [ ] T10 — Remover `Constants.URL_FORK_ISSUES` e atualizar `ForkIdentityConstantsTest`
      Arquivos: `app/src/main/java/app/olauncher/data/Constants.kt`, `app/src/test/java/app/olauncher/fork/ForkIdentityConstantsTest.kt`
      Mudança: Remover `URL_FORK_ISSUES` de `Constants.kt` mantendo `URL_FORK_GITHUB` (`// FORK:fork-identity`) e todas as constantes upstream intactas; atualizar `ForkIdentityConstantsTest.kt` removendo a asserção sobre `URL_FORK_ISSUES` e verificando a preservação de `URL_FORK_GITHUB` e das URLs upstream.
      Cobre: RF-01, RNF-01, AC-06, AC-07
      Acceptance criteria: `Constants.URL_FORK_ISSUES` não existe em `Constants.kt`, todas as constantes upstream (`URL_ABOUT_OLAUNCHER`, `URL_OLAUNCHER_GITHUB`, `URL_TWITTER_TANUJ`, `URL_OLAUNCHER_PRIVACY`, `URL_OLAUNCHER_PLAY_STORE`, `URL_OLAUNCHER_PRO`, `URL_NTS`, `URL_PENTASTIC`) estão preservadas e `ForkIdentityConstantsTest` passa.
      Testes: `./gradlew testDebugUnitTest --no-daemon --tests "app.olauncher.fork.ForkIdentityConstantsTest"` passa 100%.

- [ ] T11 — Atualizar `ForkSettingsDelegate` para remover `forkIssuesLink` e ocultar `moreFeatures`, `rate` e `footer`
      Arquivos: `app/src/main/java/app/olauncher/fork/settings/ForkSettingsDelegate.kt`
      Mudança: Remover o binding e click listener de `inflated.forkIssuesLink`; em `applyIdentityAndSection(binding, ...)`, antes do guard `findViewById(R.id.forkSettingsSection) != null`, definir `visibility = View.GONE`, limpar `setOnClickListener(null)` e limpar compound drawables de `binding.rate` (`setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)`) para `binding.moreFeatures`, `binding.rate` e `binding.footer` (cobrindo portrait e `layout-land` sem editar os XMLs upstream).
      Cobre: RF-01, AC-06
      Acceptance criteria: `ForkSettingsDelegate.kt` não referencia `forkIssuesLink` nem `URL_FORK_ISSUES`, mantém `forkGithubLink` (`Constants.URL_FORK_GITHUB`) e `forkUpstreamCredit` (`Constants.URL_OLAUNCHER_GITHUB`), e aplica `View.GONE` + limpeza de listeners em `moreFeatures`, `rate` e `footer`.
      Testes: `./gradlew assembleDebug --no-daemon` e `./gradlew lintDebug --no-daemon` passam com código `0`.

- [ ] T12 — Aplicar enxertos `// FORK:fork-identity` em `Utils.kt` e `MainActivity.kt` e validar suíte completa
      Arquivos: `app/src/main/java/app/olauncher/helper/Utils.kt`, `app/src/main/java/app/olauncher/MainActivity.kt`
      Mudança: Em `Utils.kt:shareApp()`, substituir a concatenação de `URL_OLAUNCHER_PLAY_STORE` por `ForkSharePolicy.buildShareMessage(getString(R.string.are_you_using_your_phone_or_is_your_phone_using_you)) // FORK:fork-identity` preservando `Context.rateApp()` intacta; em `MainActivity.kt`, no observer `viewModel.showDialog` marcar enxerto `// FORK:fork-identity` suprimindo `Constants.Dialog.REVIEW`, `Constants.Dialog.RATE` e `Constants.Dialog.PRO_MESSAGE` via `ForkDistributionPolicy.shouldSuppressDialog(...)` e usando `ForkDistributionPolicy.nextStateAfterWallpaper()` no handler de `WALLPAPER`, e em `checkForMessages()` usar `ForkDistributionPolicy.resolveActiveUserState(prefs.userState)` (avançando `WALLPAPER` concluído e quaisquer estados `REVIEW`/`RATE` diretamente para `Constants.UserState.SHARE` com marcador `// FORK:fork-identity`).
      Cobre: RF-01, RNF-01, AC-06, AC-07
      Acceptance criteria: `Utils.kt` usa `ForkSharePolicy` em `shareApp()` mantendo `rateApp()` intacto, `MainActivity.kt` avança `WALLPAPER`/`REVIEW`/`RATE` para `SHARE` e suprime dialogs `REVIEW`/`RATE`/`PRO_MESSAGE` com marcadores `// FORK:fork-identity`, e todos os testes/build/lint passam.
      Testes: `make test`, `./gradlew assembleDebug --no-daemon` e `./gradlew lintDebug --no-daemon` retornam código `0`.

## Phase 4: Limpeza de Metadados de Loja, README PT-BR, Governança e Sync OpenSpec

Antes de implementar, leia:
1. `AGENTS.md`, `docs/agents/architecture.md` e `docs/fork/FORK_INTEGRATION.md` — regras de catalogação `FORK:` e governança
2. `openspec/changes/consolidate-abacate-lancador-distribution/proposal.md`, `openspec/changes/consolidate-abacate-lancador-distribution/specs/fork-governance/spec.md` e `openspec/changes/consolidate-abacate-lancador-distribution/design.md` — requisitos `RF-02`, `RF-03`, `RF-07`, `RNF-01` e `AC-05`

- [ ] T13 — Remover `fastlane/` e `.github/funding.yml`
      Arquivos: `fastlane/`, `.github/funding.yml`
      Mudança: Remover integralmente o diretório `fastlane/` (metadados de loja/Play Store) e o arquivo `.github/funding.yml` do repositório.
      Cobre: RF-07, AC-05
      Acceptance criteria: Nem `fastlane/` nem `.github/funding.yml` existem na árvore do repositório.
      Testes: `test ! -e fastlane && test ! -e .github/funding.yml` retorna código `0`.

- [ ] T14 — Reescrever `README.md` em PT-BR no padrão do `abacate-calendario`
      Arquivos: `README.md`
      Mudança: Reescrever `README.md` integralmente em português (PT-BR) no padrão estrutural e visual do `abacate-calendario`, incluindo: título/apresentação do **Abacate Lançador** (com nota sobre o rótulo minimalista **Lançador** e coexistência `app.olauncher.abacate`), badge e link direto 1-toque do Obtainium usando o payload JSON codificado `{"id":"app.olauncher.abacate","url":"https://github.com/eusoumadson/abacate-launcher","author":"eusoumadson","name":"Abacate Lançador"}`, seção de download direto de APK no GitHub Releases (`https://github.com/eusoumadson/abacate-launcher/releases`), seção de funcionalidades e Tela de Widgets, remoção de todos os links `play.google.com`, guia de build/teste local (`./gradlew assembleDebug`, `make test`, `./gradlew lintDebug`), e seção de créditos e licença GNU GPLv3 preservando `tanujnotes/Olauncher` e `@tanujnotes`.
      Cobre: RF-02, RF-07, AC-05
      Acceptance criteria: `README.md` está em PT-BR no padrão do `abacate-calendario`, contém badge e link JSON 1-toque do Obtainium com `app.olauncher.abacate` e `eusoumadson/abacate-launcher`, não contém nenhuma URL `play.google.com`, e preserva créditos GPLv3 a `tanujnotes/Olauncher` e `@tanujnotes`.
      Testes: `! grep -q "play.google.com" README.md` e `grep -q "app.olauncher.abacate" README.md` retornam código `0`.

- [ ] T15 — Atualizar `CONTRIBUTING.md`, `AGENTS.md`, `docs/fork/*` e `openspec/specs/fork-governance/spec.md`
      Arquivos: `CONTRIBUTING.md`, `AGENTS.md`, `docs/fork/FORK_INTEGRATION.md`, `docs/fork/FORK_FEATURES.md`, `openspec/specs/fork-governance/spec.md`
      Mudança: Atualizar `CONTRIBUTING.md` (para **Abacate Lançador**, branch `main`, verificações `make test`, `./gradlew assembleDebug --no-daemon`, `./gradlew lintDebug --no-daemon` e licença GPLv3); atualizar `AGENTS.md` com o novo pipeline/assinatura/branch `main` e distribuição exclusiva; atualizar `docs/fork/FORK_INTEGRATION.md` com as linhas exatas de todos os enxertos `FORK:` (incluindo `signingConfigs.release`/`dependenciesInfo` em `app/build.gradle`, `Utils.kt` e `MainActivity.kt`) e remoção de `forkIssuesLink`/`fork_issues`; atualizar `docs/fork/FORK_FEATURES.md` com a consolidação da distribuição, remoção de `URL_FORK_ISSUES`/fluxos de loja e novos testes; e sincronizar `openspec/specs/fork-governance/spec.md` incorporando os requisitos `MODIFIED` e `ADDED` de `openspec/changes/consolidate-abacate-lancador-distribution/specs/fork-governance/spec.md`.
      Cobre: RF-03, RF-07, AC-05
      Acceptance criteria: Todos os cinco documentos refletem `eusoumadson/abacate-launcher`, branch `main`, assinatura v2+v3 sem metadata Google, remoção de `forkIssuesLink`/`URL_FORK_ISSUES`, tabela completa de enxertos `FORK:` e distribuição exclusiva GitHub/Obtainium.
      Testes: `grep -rn "FORK:" app/src/main/ app/build.gradle gradle/libs.versions.toml` coincide integralmente com as entradas e linhas catalogadas em `docs/fork/FORK_INTEGRATION.md`, e `openspec validate --specs` (se suportado pelo CLI) ou revisão estrutural confirma consistência com `specs/fork-governance/spec.md`.

- [ ] T16 — Executar gate completo de código, testes, lint e auditoria `FORK:` da Phase 4
      Arquivos: `app/build.gradle`, `app/src/main/`, `app/src/test/`, `app/src/fork/`
      Mudança: Executar a bateria completa de verificação local após todas as alterações de código, recursos e documentação para garantir zero regressões antes das operações remotas no GitHub.
      Cobre: RF-03, RNF-01, AC-07
      Acceptance criteria: `make test`, `./gradlew assembleDebug --no-daemon`, `./gradlew lintDebug --no-daemon` e `grep -rn "FORK:" app/src/main/ app/build.gradle gradle/libs.versions.toml` executam com sucesso (`exit 0`) e zero enxertos órfãos.
      Testes: `make test && ./gradlew assembleDebug --no-daemon && ./gradlew lintDebug --no-daemon` passa integralmente.

## Phase 5: Configuração Git/GitHub, Branch `main`, Keystore, Secrets e Verificação Final

Antes de implementar, leia:
1. `AGENTS.md` e `docs/fork/FORK_INTEGRATION.md` — regras de governança e verificação final
2. `openspec/changes/consolidate-abacate-lancador-distribution/proposal.md`, `openspec/changes/consolidate-abacate-lancador-distribution/specs/fork-governance/spec.md` e `openspec/changes/consolidate-abacate-lancador-distribution/design.md` — requisitos `RF-05`, `RF-06`, `RNF-01` e `AC-01`, `AC-02`, `AC-07`

- [ ] T17 — Renomear repositório GitHub `eusoumadson/Olauncher` -> `eusoumadson/abacate-launcher`, ajustar remote `origin` e metadados PT-BR
      Arquivos: `.git/config`
      Mudança: Verificar o repositório `eusoumadson/Olauncher` via `gh` e renomeá-lo para `eusoumadson/abacate-launcher` (`gh repo rename abacate-launcher --yes` ou `gh repo edit`), atualizar `git remote set-url origin https://github.com/eusoumadson/abacate-launcher.git`, e configurar `description` em PT-BR, `homepageUrl` = `https://github.com/eusoumadson/abacate-launcher/releases`, `topics` pertinentes ao projeto (ex: `android,launcher,minimalist,kotlin,abacatelab,obtainium`) e `--enable-issues=false` (`hasIssuesEnabled = false`).
      Cobre: RF-02, RF-05, AC-01
      Acceptance criteria: `git remote get-url origin` retorna `https://github.com/eusoumadson/abacate-launcher.git` e `gh repo view eusoumadson/abacate-launcher --json nameWithOwner,description,homepageUrl,hasIssuesEnabled` confirma `nameWithOwner = "eusoumadson/abacate-launcher"`, descrição em PT-BR, `homepageUrl = "https://github.com/eusoumadson/abacate-launcher/releases"` e `hasIssuesEnabled = false`.
      Testes: `git remote get-url origin` e `gh repo view eusoumadson/abacate-launcher --json nameWithOwner,homepageUrl,hasIssuesEnabled`.

- [ ] T18 — Consolidar branch `main` a partir de `init-fork` e defini-la como `defaultBranchRef`
      Arquivos: `.git/HEAD`
      Mudança: Garantir que todas as alterações das Phases 1–4 estejam comitadas em `init-fork`, criar/atualizar a branch local `main` a partir do estado consolidado de `init-fork`, fazer `git push -u origin main`, definir `main` como branch padrão no GitHub (`gh repo edit eusoumadson/abacate-launcher --default-branch main`) e validar que o checkout local está limpo na branch `main`.
      Cobre: RF-05, AC-01
      Acceptance criteria: A branch local atual é `main` com working tree limpo e `gh repo view eusoumadson/abacate-launcher --json defaultBranchRef` retorna `"main"`.
      Testes: `git branch --show-current` retorna `main`, `git status --porcelain` está vazio e `gh repo view eusoumadson/abacate-launcher --json defaultBranchRef` confirma `main`.

- [ ] T19 — Gerar nova keystore de release local (gitignored), validar assinatura local e cadastrar os 4 secrets no GitHub Actions
      Arquivos: `local.properties`
      Mudança: Gerar uma nova keystore PKCS12/JKS local (ex: `app/release.keystore` ou caminho fora do rastreamento coberto pelo `.gitignore`) via `keytool -genkeypair`, configurar `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` e `KEY_PASSWORD` em `local.properties` (gitignored), executar `./gradlew assembleRelease --no-daemon` localmente e verificar a assinatura v2+v3 via `apksigner verify -v`, e cadastrar `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` e `KEY_PASSWORD` no repositório `eusoumadson/abacate-launcher` via `gh secret set`.
      Cobre: RF-05, RF-06, AC-01, AC-02
      Acceptance criteria: A keystore existe localmente fora do `git status` (gitignored), `./gradlew assembleRelease --no-daemon` + `apksigner verify -v` confirmam assinatura válida v2 e v3 (`Verified using v2 scheme: true`, `Verified using v3 scheme: true`), e `gh secret list -R eusoumadson/abacate-launcher` lista os quatro secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` e `KEY_PASSWORD`.
      Testes: `git status --porcelain` vazio, `apksigner verify -v app/build/outputs/apk/release/*.apk` com código `0` e `gh secret list -R eusoumadson/abacate-launcher` exibindo os 4 secrets.

- [ ] T20 — Executar verificação final ponta a ponta de todos os gates e critérios
      Arquivos: `openspec/changes/consolidate-abacate-lancador-distribution/tasks.md`
      Mudança: Executar todas as verificações finais após o último commit na branch `main`: `make test`, `./gradlew assembleDebug --no-daemon`, `./gradlew lintDebug --no-daemon`, `python3 app/src/fork/tools/release.py --dry-run` (agora em `main` limpa), `grep -rn "FORK:" app/src/main/ app/build.gradle gradle/libs.versions.toml`, `gh repo view` e `gh secret list`.
      Cobre: RF-01, RF-02, RF-03, RF-04, RF-05, RF-06, RF-07, RNF-01, AC-01, AC-02, AC-03, AC-04, AC-05, AC-06, AC-07
      Acceptance criteria: Todos os comandos finais de teste, build, lint, dry-run, auditoria `FORK:` e checagem GitHub/secrets retornam código `0` em `main` limpa.
      Testes: `make test && ./gradlew assembleDebug --no-daemon && ./gradlew lintDebug --no-daemon && python3 app/src/fork/tools/release.py --dry-run` passa 100%.
