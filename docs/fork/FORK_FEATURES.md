# Fork Features Catalog (Abacate Lançador)

Catálogo oficial das funcionalidades exclusivas do fork **Abacate Lançador** sobre o Olauncher upstream.

---

## 1. `fork-identity` — Identidade Dual, Coexistência, CalVer e Governança

- **Status**: Implementada (Fase 1)
- **Identificador de Enxerto**: `FORK:fork-identity`
- **Escopo e Comportamento**:
  - **Coexistência de pacotes**: `applicationIdSuffix = ".abacate"` em `defaultConfig` gera `app.olauncher.abacate` (`release`) e `app.olauncher.abacate.debug` (`debug`), permitindo instalação lado a lado com o Olauncher upstream (`app.olauncher`).
  - **Identidade dual de rótulos**:
    - `fork_label_name` (`"Lançador"`) aplicado em `app_name` via `app/src/fork/res/values/strings.xml` para exibição limpa na gaveta/sistema.
    - `fork_app_name` (`"Abacate Lançador"`) aplicado no `MyAccessibilityService` (`AndroidManifest.xml`), README e UI interna do fork.
  - **Configuração de acessibilidade**: `app/src/fork/res/xml/accessibility_service_config.xml` cobre os quatro package names (`app.olauncher.abacate`, `app.olauncher.abacate.debug`, `app.olauncher.debug`, `app.olauncher`).
  - **Versionamento CalVer**: `version.properties` (`VERSION_CODE=116`, `VERSION_NAME=2026.11.12`) lido em `app/build.gradle` e automatizado via `Makefile` + `app/src/fork/tools/release.py` (`--dry-run` e bump `vYYYY.MM.DD`).
  - **Preservação GPLv3 & Créditos**: `LICENSE` (GNU GPLv3) e todas as URLs upstream em `Constants.kt` (`URL_ABOUT_OLAUNCHER`, `URL_OLAUNCHER_GITHUB`, `URL_TWITTER_TANUJ`, `URL_OLAUNCHER_PRIVACY`) permanecem intactas, acompanhadas das constantes `URL_FORK_GITHUB` e `URL_FORK_ISSUES`.
- **Regras de Não-Regressão**:
  - `ForkBuildConfigTest`: garante `BuildConfig.APPLICATION_ID == "app.olauncher.abacate.debug"`, `VERSION_CODE == 116` e `VERSION_NAME == "2026.11.12"`.
  - `ForkIdentityConstantsTest`: garante os valores de `URL_FORK_GITHUB`/`URL_FORK_ISSUES` e preservação das URLs upstream.
  - `app/src/fork/tools/test_release.py`: garante parsing de `version.properties`, cálculo CalVer e `--dry-run` sem efeitos colaterais.

---

## 2. `widget-screen` — Tela Dedicada de Widgets com ViewPager2 e Bento Grid

- **Status**: Em preparação (Dependência `androidx.viewpager2:viewpager2` provisionada na Fase 1; implementação nas Fases 2–4)
- **Identificador de Enxerto**: `FORK:widget-screen`
- **Escopo Planejado**:
  - Seção `"Abacate Lançador"` em `SettingsFragment` com posição da Tela de Widgets (`Desativada` padrão, `Esquerda`, `Direita`) e toggles de largura/altura mínima `1` no grid.
  - Paginação física lado a lado via `ViewPager2` (`WorkspacePagerDelegate`) mantendo papel de parede contínuo e `HomeFragment` minimalista sem widgets internos.
  - Motor `BentoGridLayoutPlanner` responsivo (`4`/`6`/`8` colunas) com empacotamento *first-fit* determinístico e suporte a `RESIZE_NONE`.
  - Ciclo de vida econômico de `AppWidgetHost`, fluxo `PICK -> BIND -> CONFIGURE`, pinning externo (`PinItemActivity`) e edição puramente por gestos (`ForkAppWidgetHostView`) sem botões estáticos.
- **Regras de Não-Regressão**:
  - `HomeFragment` e `fragment_home.xml` não devem exibir widgets ou controles de edição.
  - Com a Tela de Widgets em `Desativada`, todos os gestos, navegação e `PinItemActivity` mantêm o comportamento original do Olauncher upstream.
