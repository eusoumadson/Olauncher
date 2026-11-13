# Fork Features Catalog (Abacate Lançador)

Catálogo oficial das funcionalidades exclusivas do fork **Abacate Lançador** sobre o Olauncher upstream.

---

## 1. `fork-identity` — Identidade Dual, Coexistência, CalVer e Governança

- **Status**: Implementada
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

- **Status**: Implementada
- **Identificador de Enxerto**: `FORK:widget-screen`
- **Escopo e Comportamento**:
  - **Configurações e precedência de gestos (`ForkSettingsDelegate`, `ForkSwipePolicy`)**: seção `"Abacate Lançador"` injetada em `SettingsFragment` com posição da Tela de Widgets (`Desativada` padrão, `Esquerda`, `Direita`), toggles `allowMinWidth1`/`allowMinHeight1`, versão e links do fork/upstream; ativar `Esquerda` ou `Direita` desabilita a abertura de app no gesto correspondente preservando o gesto oposto.
  - **Paginação física lado a lado (`WorkspacePagerDelegate`, overlay `activity_main.xml`)**: desliza `HomeFragment` e `WidgetsFragment` via `ViewPager2` sobre o papel de parede contínuo quando `LEFT`/`RIGHT`, mantém `workspacePager` `GONE` com `nav_host_fragment` direto em `mainActivityLayout` quando `DISABLED`, bloqueia `isUserInputEnabled = false` fora de `R.id.mainFragment` ou durante drag/resize de widgets, e retorna à Home por swipe contrário, `OnBackPressedCallback` e `backToHomeScreen()` (`onStop`, `onUserLeaveHint`, `onNewIntent`).
  - **Motor Bento Grid responsivo (`BentoGridLayoutPlanner`)**: grade responsiva de `4` colunas (`< 600dp`), `6` colunas (`600dp..839dp`) e `8` colunas (`>= 840dp`) com empacotamento *first-fit* determinístico sem sobreposição nem lacunas órfãs, mínimo padrão `2x2` (ou `1x` via configurações) e centralização sem alças de redimensionamento para widgets `RESIZE_NONE`.
  - **Ciclo de vida `AppWidgetHost`, adição e pinning (`ForkWidgetHostManager`, `ForkWidgetPinDelegate`, `PinItemActivity`)**: `startListening()` em `onStart()` e `stopListening()` em `onStop()` somente quando ativo e com `>= 1` widget salvo; limpeza automática de IDs órfãos via `deleteAppWidgetId()`; adição via `ACTION_APPWIDGET_PICK` -> `ACTION_APPWIDGET_BIND` -> `ACTION_APPWIDGET_CONFIGURE` por toque longo no fundo vazio; pinning externo `REQUEST_TYPE_APPWIDGET` em `PinItemActivity` quando ativo (mantendo `R.string.widgets_not_supported` quando `Desativada`).
  - **Edição puramente por gestos (`ForkAppWidgetHostView`, `BentoGridLayoutView`, `WidgetsFragment`)**: toque longo tátil sobre qualquer widget ativa Drag & Drop de reordenação, alças de redimensionamento na grade e remoção; toque fora encerra a edição e persiste ordem/spans sem botões estáticos (`+` ou `Editar`).
  - **Isolamento da Home (`ForkHomeTouchDecorator`, `HomeFragment`)**: `ForkHomeTouchDecorator` limpa `view.isPressed = false` em `ACTION_CANCEL` quando `ViewPager2` intercepta o gesto sem alterar `OnSwipeTouchListener`/`ViewSwipeTouchListener`; `fragment_home.xml` e `HomeFragment` permanecem livres de widgets e controles de edição.
- **Regras de Não-Regressão**:
  - `ForkSwipePolicyTest` e `HomeSwipePrecedenceTest`: garantem bloqueio exclusivo do gesto lateral ocupado pela Tela de Widgets, preservação do gesto oposto, retorno `onBackPressed`/`onNewIntent` e limpeza de estado pressionado em `ACTION_CANCEL`.
  - `WorkspacePagerPolicyTest`: garante índices de página para `DISABLED`/`LEFT`/`RIGHT` e bloqueio de `isUserInputEnabled` em `appListFragment`, `settingsFragment` e durante edição ativa.
  - `BentoGridLayoutPlannerTest` e `BentoGridEditActionsTest`: garantem breakpoints `4`/`6`/`8`, *first-fit* sem sobreposição, mínimos `2x2`/`1x`, `RESIZE_NONE`, reordenação, resize e remoção.
  - `ForkWidgetHostManagerTest`, `ForkWidgetPinPolicyTest`, `ForkWidgetPrefsTest` e `ForkWidgetEditStateTest`: garantem ciclo de vida econômico, limpeza de órfãos, fluxo de pinning e estado de edição por gestos.
  - `HomeFragment` e `fragment_home.xml` nunca devem exibir widgets ou controles de edição; com Tela de Widgets em `Desativada`, todos os gestos, navegação e `PinItemActivity` mantêm o comportamento original do Olauncher upstream.
