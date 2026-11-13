# Proposal: consolidate-abacate-lancador-distribution

## Metadata
- **Tier**: standard
- **Architecture References**: AGENTS.md, docs/agents/architecture.md, docs/agents/domain_rules.md
- **Capability Policy**: ignore_for_this_run

## Why

O fork **Abacate Lançador** já possui identidade dual (`app.olauncher.abacate` / `app.olauncher.abacate.debug`), versionamento CalVer inicial e tela de widgets, porém o repositório remoto (`eusoumadson/Olauncher`), a branch padrão (`init-fork`), a assinatura de release no Gradle, a esteira de CI/CD, a automação local `release.py`, a documentação e fluxos internos de loja/avaliação do app ainda carregam resquícios do Olauncher upstream. Esta mudança consolida a identidade `"Abacate Lançador"`, a distribuição exclusiva via GitHub Releases / Obtainium e a automação completa de empacotamento e publicação no padrão do **Abacate Calendário**.

## Scope
- **In**:
  - Renomeação e configuração do repositório remoto no GitHub (`eusoumadson/Olauncher` -> `eusoumadson/abacate-launcher`), consolidação da branch `main` como padrão, geração de keystore local (gitignored) e cadastro dos 4 secrets no GitHub Actions (`AC-01`).
  - Configuração de `signingConfigs.release` em `app/build.gradle` (variáveis de ambiente + fallback `local.properties`, documentado em `local.properties.example`), assinatura v2 + v3 e `dependenciesInfo` desabilitado em APK e Bundle (`AC-02`).
  - Atualização de `.github/workflows/release.yml` (`push` em tags `v*` e `workflow_dispatch`, `apksigner verify -v`, asset `AbacateLancador-<version>-release.apk`, artifact em `workflow_dispatch`, notas do `CHANGELOG.md` e limpeza de keystore) e alinhamento de `.github/workflows/build.yml` à branch `main` (`AC-03`).
  - Evolução de `app/src/fork/tools/release.py` e `app/src/fork/tools/test_release.py` com preflight (`main` + working tree limpo), incremento de `VERSION_CODE`, resolução de colisão no mesmo dia (`.1`, `.2`), extração de minuta do `proposal.md` mais recente, `$EDITOR`/entrada manual, inserção em `CHANGELOG.md`, commit `chore(release)` + tag anotada `v<VERSION_NAME>` e confirmação explícita antes de `git push origin main` e `git push origin v<VERSION_NAME>` (`AC-04`).
  - Remoção de `fastlane/` e `.github/funding.yml`, reescrita do `README.md` em pt-BR com badge e deep-link JSON 1-toque do Obtainium sem links de Play Store, preservação dos créditos GPLv3 e atualização de `CONTRIBUTING.md`, `AGENTS.md`, `docs/fork/*` e especificação `fork-governance` (`AC-05`).
  - Substituição de `"Olauncher"` por `"Abacate Lançador"` nas strings conversacionais via overlay em `app/src/fork/res/`, remoção do link `"Reportar problema"` (`forkIssuesLink`) da seção `"Abacate Lançador"`, redirecionamento de `shareApp()` para `Constants.URL_FORK_GITHUB` e desativação/ocultação dos fluxos exclusivos de Play Store (`AC-06`).
  - Suítes de teste e verificação (`make test`, `./gradlew assembleDebug --no-daemon`, `./gradlew lintDebug --no-daemon` e auditoria `grep -rn "FORK:"`) com novos testes para `release.py`, `shareApp` e identidade (`AC-07`).
- **Out**:
  - Alterações funcionais na `widget-screen`, `HomeFragment`, `AppDrawerFragment` ou em regras de pesquisa/atalhos não vinculadas à identidade e distribuição.
  - Publicação na Google Play Store, F-Droid ou IzzyOnDroid.

## What Changes

- **BREAKING**: Renomear repositório remoto `eusoumadson/Olauncher` para `eusoumadson/abacate-launcher`, consolidar a branch `main` a partir de `init-fork` como `defaultBranchRef`, desabilitar Issues no GitHub (`hasIssuesEnabled = false`) e remover `fastlane/` e `.github/funding.yml`.
- Gerar keystore de release local ignorada pelo Git e cadastrar os secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` e `KEY_PASSWORD` no GitHub Actions.
- Configurar `signingConfigs.release` (com `enableV2Signing = true` e `enableV3Signing = true`), `dependenciesInfo { includeInApk = false; includeInBundle = false }` em `app/build.gradle` e criar `local.properties.example`.
- Atualizar `.github/workflows/release.yml` para `push` em tags `v*` e `workflow_dispatch`, validação via `apksigner verify -v`, publicação do asset `AbacateLancador-<version>-release.apk` e limpeza garantida da keystore; alinhar `.github/workflows/build.yml` à branch `main`.
- Evoluir `app/src/fork/tools/release.py` e `app/src/fork/tools/test_release.py` para automação completa de release CalVer (preflight, resolução de colisão no mesmo dia, minuta via OpenSpec `proposal.md`, `$EDITOR`/manual, atualização de `CHANGELOG.md`, commit/tag e push interativo para `origin`).
- Reescrever `README.md` em pt-BR no padrão do Abacate Calendário com badge + deep-link JSON 1-toque do Obtainium e créditos GPLv3 ao Olauncher; atualizar `CONTRIBUTING.md`, `AGENTS.md` e `docs/fork/*`.
- Substituir `"Olauncher"` por `"Abacate Lançador"` nas strings conversacionais via overlays em `app/src/fork/res/`, remover `forkIssuesLink` da seção `"Abacate Lançador"`, redirecionar `shareApp()` para `Constants.URL_FORK_GITHUB`, ocultar/desativar fluxos exclusivos de Play Store (`rateApp`, dialogs `REVIEW`/`RATE`/`PRO_MESSAGE`, e rodapé `Note to Self`/`Pentastic`) e adicionar testes automatizados correspondentes.

## Capabilities

### New Capabilities

### Modified Capabilities
- `fork-governance`: Estende a governança do fork para consolidar o repositório `eusoumadson/abacate-launcher` na branch `main`, empacotamento/assinatura Gradle v2+v3 sem metadata do Google, pipeline de release com `workflow_dispatch` e `apksigner`, CLI `release.py` completa, distribuição exclusiva GitHub/Obtainium e identidade interna `"Abacate Lançador"` livre de fluxos de Play Store.

## Confirmed Acceptance Criteria (Source of Truth)
- **AC-01 (GitHub, Branch `main` & Keystore)**: Renomear `eusoumadson/Olauncher` -> `eusoumadson/abacate-launcher` via `gh`, configurar `description` em PT-BR, `homepageUrl` para `https://github.com/eusoumadson/abacate-launcher/releases`, `topics`, `hasIssuesEnabled = false`, consolidar branch `main` (a partir do fork atual em `init-fork`) como `defaultBranchRef`, gerar nova keystore de release local (gitignored) e cadastrar os 4 secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) no GitHub Actions.
- **AC-02 (Empacotamento & Assinatura Gradle)**: Configurar `signingConfigs.release` em `app/build.gradle` (env vars `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` + fallback `local.properties`, documentado em `local.properties.example`), manter assinatura v2 + v3 (`enableV2Signing = true`, `enableV3Signing = true` por compatibilidade com `minSdk 24`) e definir `dependenciesInfo { includeInApk = false; includeInBundle = false }`.
- **AC-03 (CI/CD Release & Build)**: Atualizar `.github/workflows/release.yml` para suportar `push` em tags `v*` e `workflow_dispatch`, validar assinatura com `apksigner verify -v`, publicar asset `AbacateLancador-<version>-release.apk` (e upload artifact em `workflow_dispatch`), extrair notas do `CHANGELOG.md` e limpar a keystore com `if: always()`; alinhar `.github/workflows/build.yml` à branch `main`.
- **AC-04 (Automação Completa de Release Local)**: Evoluir `app/src/fork/tools/release.py` e `app/src/fork/tools/test_release.py` com preflight (working tree limpo + branch `main`), incremento de `VERSION_CODE`, resolução de colisão no mesmo dia (`.1`, `.2`), extração de minuta do `proposal.md` mais recente em `openspec/changes/` ou `archive/` (`$EDITOR`/manual), inserção automática de `## [VERSION_NAME]` no topo de `CHANGELOG.md`, commit `chore(release)` + tag anotada `v<VERSION_NAME>`, e confirmação explícita antes de `git push origin main` e `git push origin v<VERSION_NAME>` (mantendo `--dry-run` sem efeitos colaterais e descrição "Abacate Lançador").
- **AC-05 (Exclusividade GitHub/Obtainium & Docs)**: Remover `fastlane/` e `.github/funding.yml` upstream; reescrever `README.md` em PT-BR no padrão do `abacate-calendario` com badge + deep-link JSON 1-toque do Obtainium (`{"id":"app.olauncher.abacate","url":"https://github.com/eusoumadson/abacate-launcher","author":"eusoumadson","name":"Abacate Lançador"}`) sem links de Play Store e preservando créditos GPLv3 ao Olauncher; atualizar `CONTRIBUTING.md`, `AGENTS.md`, `docs/fork/*` e estender `openspec/specs/fork-governance/spec.md` (via delta em `specs/fork-governance/spec.md`) para refletir `Abacate Lançador`, branch `main` e distribuição exclusiva.
- **AC-06 (Identidade Interna & Remoção de Lojas no App)**: Via overlay em `app/src/fork/res/` (`values/strings.xml` e `values-pt-rBR/strings.xml`) e enxertos `// FORK:`, substituir `"Olauncher"` por `"Abacate Lançador"` nas strings conversacionais (mantendo `fork_label_name = "Lançador"` e `fork_app_name = "Abacate Lançador"`), remover o link `"Reportar problema"` (`forkIssuesLink`) da seção `"Abacate Lançador"` (pois `hasIssuesEnabled = false`), redirecionar `shareApp()` para `Constants.URL_FORK_GITHUB`, e desativar/ocultar fluxos exclusivos de Play Store (`rateApp`, dialogs `REVIEW`/`RATE`/`PRO_MESSAGE`, e rodapé `Note to Self`/`Pentastic`), preservando créditos e links upstream na seção `"Abacate Lançador"`.
- **AC-07 (Verificação & Testes)**: `make test`, `./gradlew assembleDebug --no-daemon`, `./gradlew lintDebug --no-daemon` e auditoria `grep -rn "FORK:"` passando integralmente, com novos testes para `release.py`, `shareApp` e identidade.

## Impact
- **Build & Assinatura**: `app/build.gradle`, `local.properties.example`, `.gitignore`.
- **CI/CD & Ferramentas**: `.github/workflows/release.yml`, `.github/workflows/build.yml`, `app/src/fork/tools/release.py`, `app/src/fork/tools/test_release.py`.
- **Código Android & Overlays**: `app/src/main/java/app/olauncher/helper/Utils.kt`, `app/src/main/java/app/olauncher/MainActivity.kt`, `app/src/main/java/app/olauncher/ui/SettingsFragment.kt`, `app/src/main/java/app/olauncher/fork/settings/ForkSettingsDelegate.kt`, `app/src/fork/res/layout/fork_settings_section.xml`, `app/src/fork/res/values/strings.xml`, `app/src/fork/res/values-pt-rBR/strings.xml`.
- **Documentação & Repositório**: `README.md`, `CONTRIBUTING.md`, `AGENTS.md`, `docs/fork/FORK_INTEGRATION.md`, `docs/fork/FORK_FEATURES.md`, remoção de `fastlane/` e `.github/funding.yml`, configuração do repositório GitHub e Secrets.
- **Testes**: `app/src/fork/tools/test_release.py` e novos testes JVM em `app/src/test/java/app/olauncher/fork/`.
