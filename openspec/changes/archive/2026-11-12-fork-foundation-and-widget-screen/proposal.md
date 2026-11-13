# Proposal: fork-foundation-and-widget-screen

## Metadata
- **Tier**: standard
- **Architecture References**: `AGENTS.md`, `docs/agents/architecture.md`, `docs/agents/domain_rules.md`, `docs/agents/coding_guidelines.md`
- **Capability Policy**: ignore_for_this_run

## Why
O fork **Abacate Lançador** (baseado no Olauncher) precisa de uma fundação estruturada que garanta coexistência lado a lado com o app original, versionamento CalVer automatizado, isolamento estrito de código/recursos para facilitar merges futuros com o upstream e uma Tela Dedicada de Widgets com transição física em `ViewPager2` e layout Bento Grid responsivo sem comprometer o minimalismo da tela inicial.

## Scope
- **In**:
  - Identidade dual (`applicationIdSuffix = ".abacate"`, label de sistema `"Lançador"`, nome interno/acessibilidade `"Abacate Lançador"`), preservação integral da licença GPLv3 e créditos ao Olauncher upstream.
  - Versionamento CalVer (`version.properties` `2026.11.12` / code `116`), `Makefile`, script `app/src/fork/tools/release.py`, `CHANGELOG.md`, `README.md` com badge Obtainium, documentação `docs/fork/FORK_INTEGRATION.md` e `docs/fork/FORK_FEATURES.md`, workflows `.github/workflows/build.yml` e `release.yml`, e configuração do remote `origin`.
  - Governança de isolamento em `app/src/main/java/app/olauncher/fork/` e `app/src/fork/res/`, pontos de enxerto únicos `// FORK:<feature>` (Delegate/Decorator) e política Zero Lixo de Compatibilidade em `AGENTS.md` e `docs/agents/coding_guidelines.md`.
  - Seção "Abacate Lançador" em `SettingsFragment`, paginação física via `ViewPager2` (`WorkspacePagerDelegate`), motor `BentoGridLayoutPlanner`, ciclo de vida de `AppWidgetHost`, adição/pinning via `PinItemActivity` e gestão por gestos com `ForkAppWidgetHostView`.
- **Out**:
  - Suporte a widgets dentro da própria `HomeFragment` (tela inicial minimalista permanece inalterada).
  - Alterações em busca do App Drawer, Private Space, WallpaperWorker ou telemetria/anúncios.
  - Múltiplas páginas de widgets além de uma única tela dedicada (`Esquerda` ou `Direita`).

## What Changes
- Configuração de `app/build.gradle` com `version.properties`, `applicationIdSuffix = ".abacate"`, overlay de recursos `app/src/fork/res`, `resValues` do fork e dependência `androidx.viewpager2`.
- Criação de artefatos de governança e automação (`version.properties`, `Makefile`, `app/src/fork/tools/release.py`, `CHANGELOG.md`, `README.md`, `docs/fork/FORK_INTEGRATION.md`, `docs/fork/FORK_FEATURES.md`, `.github/workflows/build.yml`, `.github/workflows/release.yml`).
- Enxertos mínimos `// FORK:<feature>` em `MainActivity.kt`, `HomeFragment.kt`, `SettingsFragment.kt`, `PinItemActivity.kt`, `Prefs.kt`, `Constants.kt` e `libs.versions.toml` delegando para módulos sob `app/src/main/java/app/olauncher/fork/` e overlays em `app/src/fork/res/`.
- Implementação da Tela Dedicada de Widgets com `WorkspacePagerDelegate`, `BentoGridLayoutPlanner`, `ForkAppWidgetHostView` e fluxo completo de pick/bind/configure/pin/edit/remove.

## Capabilities

### New Capabilities
- `fork-governance`: Identidade dual do fork, coexistência de pacotes, versionamento CalVer, overlay de recursos, isolamento via Delegate/Decorator com sentinelas `// FORK:<feature>`, política Zero Lixo de Compatibilidade, documentação e esteira CI/CD.
- `widget-screen`: Tela Dedicada de Widgets com paginação física via `ViewPager2`, motor Bento Grid responsivo, ciclo de vida de `AppWidgetHost`, pinning externo, gestão puramente por gestos e seção de configurações do fork.

### Modified Capabilities
- Nenhuma (catálogo `openspec/specs/` inicialmente vazio).

## Confirmed Acceptance Criteria
- **AC-01 (Identidade & Coexistência)**: Compila com `applicationIdSuffix = ".abacate"` (`app.olauncher.abacate`), exibe `"Lançador"` como label do sistema/gaveta (`fork_label_name`) e `"Abacate Lançador"` como nome oficial no README, telas internas e serviço de acessibilidade (`fork_app_name`), preservando créditos e links ao Olauncher upstream (GPLv3).
- **AC-02 (Versionamento, Docs & CI/CD)**: Existem `version.properties` (CalVer `2026.11.12` / code `116`), `Makefile`, `app/src/fork/tools/release.py` (`--dry-run` funcional), `CHANGELOG.md`, `README.md` com badge Obtainium, `docs/fork/FORK_INTEGRATION.md`, `docs/fork/FORK_FEATURES.md` e workflows `.github/workflows/build.yml` e `release.yml`, além de remote `origin` apontado para `https://github.com/eusoumadson/abacate-launcher.git`.
- **AC-03 (Governança & Isolamento)**: Todo código exclusivo reside em `app/src/main/java/app/olauncher/fork/` e recursos exclusivos em `app/src/fork/res/`; arquivos compartilhados com upstream contêm apenas enxertos mínimos de linha única marcados com `// FORK:<feature>` (Delegate/Decorator pattern), seguindo a política de Zero Lixo de Compatibilidade documentada em `AGENTS.md` e `docs/agents/coding_guidelines.md`.
- **AC-04 (Configurações do Fork)**: `SettingsFragment` possui seção dedicada `"Abacate Lançador"` com posição da Tela de Widgets (`Desativada` padrão, `Esquerda`, `Direita`), toggles para permitir largura `1` ou altura `1` no grid, e informações/links do fork e do upstream; ativar um lado para widgets desabilita a abertura de app naquele gesto e mantém o gesto oposto intacto.
- **AC-05 (Transição Física Lado a Lado)**: Quando ativada, a Tela de Widgets e a Home deslizam pixel a pixel via `ViewPager2` (`WorkspacePagerDelegate` sobre `activity_main.xml` em overlay) mantendo o papel de parede contínuo; o swipe lateral bloqueia automaticamente fora da Home (Gaveta/Configurações), e o retorno ocorre por swipe contrário, botão/gesto Voltar ou botão Home (`onNewIntent`).
- **AC-06 (Motor Bento Grid Responsivo)**: `BentoGridLayoutPlanner` posiciona widgets em grade de `4` colunas (`< 600dp`), `6` colunas (`600dp..839dp`) ou `8` colunas (`>= 840dp`) usando empacotamento first-fit sem lacunas órfãs nem sobreposição, respeitando mínimo padrão `2x2` (ou `1x` quando liberado nas configurações) e centralizando widgets `RESIZE_NONE`.
- **AC-07 (Host, Adição & Pinning)**: `AppWidgetHost` chama `startListening()` em `MainActivity.onStart()` e `stopListening()` em `onStop()` apenas quando ativo e com widgets; toque longo no fundo vazio da WidgetScreen abre o fluxo `ACTION_APPWIDGET_PICK` -> `ACTION_APPWIDGET_BIND` -> `ACTION_APPWIDGET_CONFIGURE`; `PinItemActivity` aceita `REQUEST_TYPE_APPWIDGET` quando ativo (mantendo toast padrão do upstream quando desativado); IDs órfãos são limpos na carga.
- **AC-08 (Edição sem Botoes)**: Toque longo sobre qualquer widget (interceptado via `ForkAppWidgetHostView` com feedback tátil) habilita reordenação por Drag & Drop, alças de redimensionamento na grade e remoção; toque fora encerra a edição; nenhum botão estático (`+` ou `Editar`) existe na WidgetScreen e nada vaza para a Home.
- **AC-09 (Build, Testes & Lint)**: `./gradlew assembleDebug --no-daemon`, `./gradlew testDebugUnitTest --no-daemon` e `./gradlew lintDebug --no-daemon` executam com código de saída `0`.
