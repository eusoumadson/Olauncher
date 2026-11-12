# Tasks

## Phase 1: Fork Foundation, Identidade, CalVer e Governança

Antes de implementar, leia:
1. `AGENTS.md` e `docs/agents/architecture.md` — regras de arquitetura, licença GPLv3 e comandos de build/teste
2. `openspec/changes/fork-foundation-and-widget-screen/proposal.md`, `openspec/changes/fork-foundation-and-widget-screen/specs/` e `openspec/changes/fork-foundation-and-widget-screen/design.md` — requisitos RIGID (RF-01..RF-04), cenários e decisões técnicas

- [x] T01 — Criar versionamento CalVer, Makefile e script de release
      Arquivos: `version.properties`, `Makefile`, `app/src/fork/tools/release.py`, `app/src/fork/tools/test_release.py`
      Mudança: Criar `version.properties` com `VERSION_CODE=116` e `VERSION_NAME=2026.11.12`; criar `Makefile` com alvos `.PHONY: release dry-run test install`; criar `app/src/fork/tools/release.py` suportando `--dry-run`, bump CalVer `vYYYY.MM.DD` + incremento de `VERSION_CODE`, leitura de notas em `CHANGELOG.md`, commit `chore(release): bump version to ...` e criação de tag `vYYYY.MM.DD`; adicionar testes unitários em Python cobrindo cálculo de versão e `--dry-run`.
      Cobre: RF-02, AC-02
      Acceptance criteria: `version.properties` contém `VERSION_CODE=116` e `VERSION_NAME=2026.11.12`; `Makefile` expõe `release`, `dry-run`, `test`, `install`; `python3 app/src/fork/tools/release.py --dry-run` e `python3 -m unittest app/src/fork/tools/test_release.py` retornam código `0` sem sujar o repositório Git.
      Testes: `app/src/fork/tools/test_release.py` — validar parsing de `version.properties`, bump CalVer e execução em `--dry-run`.

- [x] T02 — Configurar identidade dual, overlay `app/src/fork/res/` e dependências em `app/build.gradle`
      Arquivos: `gradle/libs.versions.toml`, `app/build.gradle`, `app/src/fork/res/values/strings.xml`, `app/src/fork/res/values-pt-rBR/strings.xml`, `app/src/fork/res/xml/accessibility_service_config.xml`, `app/src/main/AndroidManifest.xml`
      Mudança: Adicionar `androidx.viewpager2:viewpager2` em `gradle/libs.versions.toml` e `app/build.gradle`; configurar leitura de `version.properties` em `app/build.gradle`, `applicationIdSuffix = ".abacate"` em `defaultConfig`, `resValue` `fork_app_name = "Abacate Lançador"` e `fork_label_name = "Lançador"`, e `sourceSets` para `src/fork/res` (`debug` e `release`), tudo demarcado com `// FORK:fork-identity` / `// FORK:widget-screen`; criar `app/src/fork/res/values/strings.xml` sobrescrevendo `app_name` para `@string/fork_label_name` (sem duplicar `fork_*` de `resValue`) e definindo strings exclusivas do fork (espelhadas em `values-pt-rBR/strings.xml`); criar `app/src/fork/res/xml/accessibility_service_config.xml` com `android:packageNames="app.olauncher.abacate, app.olauncher.abacate.debug, app.olauncher.debug, app.olauncher"`; aplicar enxerto único `<!-- FORK:fork-identity -->` no serviço `MyAccessibilityService` em `app/src/main/AndroidManifest.xml` usando `android:label="@string/fork_app_name"`.
      Cobre: RF-01, RF-02, RF-03, AC-01, AC-03
      Acceptance criteria: `./gradlew assembleDebug` compila `app.olauncher.abacate.debug` com `versionCode 116` e `versionName "2026.11.12"`; manifesto mesclado resolve `<application android:label>` para `"Lançador"` e `MyAccessibilityService` para `"Abacate Lançador"` com os 4 package names configurados em `accessibility_service_config.xml`.
      Testes: `app/src/test/java/app/olauncher/fork/ForkBuildConfigTest.kt` — validar `BuildConfig.APPLICATION_ID == "app.olauncher.abacate.debug"`, `BuildConfig.VERSION_CODE == 116` e `BuildConfig.VERSION_NAME == "2026.11.12"`.

- [x] T03 — Adicionar constantes do fork em `Constants.kt` preservando URLs upstream
      Arquivos: `app/src/main/java/app/olauncher/data/Constants.kt`, `app/src/test/java/app/olauncher/fork/ForkIdentityConstantsTest.kt`
      Mudança: Adicionar `URL_FORK_GITHUB = "https://github.com/eusoumadson/abacate-launcher"` e `URL_FORK_ISSUES = "https://github.com/eusoumadson/abacate-launcher/issues"` com comentário `// FORK:fork-identity` em `Constants.kt`, mantendo todas as constantes e URLs upstream (`URL_ABOUT_OLAUNCHER`, `URL_OLAUNCHER_GITHUB`, `URL_TWITTER_TANUJ`, `URL_OLAUNCHER_PRIVACY`) intactas.
      Cobre: RF-01, RF-03, AC-01, AC-03
      Acceptance criteria: `Constants.kt` possui `URL_FORK_GITHUB` e `URL_FORK_ISSUES` sob `// FORK:fork-identity` e preserva todas as URLs upstream originais sem modificação.
      Testes: `app/src/test/java/app/olauncher/fork/ForkIdentityConstantsTest.kt` — verificar valores das URLs do fork e presença intacta das URLs upstream.

- [x] T04 — Documentar governança Zero Lixo, catálogo do fork, `CHANGELOG.md` e `README.md`
      Arquivos: `AGENTS.md`, `docs/agents/coding_guidelines.md`, `docs/fork/FORK_INTEGRATION.md`, `docs/fork/FORK_FEATURES.md`, `CHANGELOG.md`, `README.md`
      Mudança: Registrar a política de **Zero Lixo de Compatibilidade** e o protocolo de isolamento `// FORK:<feature>` + `app/src/fork/res/` em `AGENTS.md` e `docs/agents/coding_guidelines.md`; criar `docs/fork/FORK_INTEGRATION.md` (mapa de pontos de enxerto e roteiro `git fetch upstream` / merge) e `docs/fork/FORK_FEATURES.md` (catálogo inicial de `fork-identity` e `widget-screen`); criar `CHANGELOG.md` com entrada `2026.11.12`; atualizar `README.md` com badge/deep-link Obtainium para `app.olauncher.abacate`, licença GPLv3, créditos explícitos a `@tanujnotes`/Olauncher e guia de build local com Java 17.
      Cobre: RF-01, RF-02, RF-03, AC-01, AC-02, AC-03
      Acceptance criteria: Todos os documentos existem com seções completas, `README.md` contém o link Obtainium com `app.olauncher.abacate` e créditos ao Olauncher, e `LICENSE` permanece inalterada (GPLv3).
      Testes: `python3 app/src/fork/tools/release.py --dry-run` — confirmar leitura correta da seção `2026.11.12` do `CHANGELOG.md`.

- [x] T05 — Criar esteira CI/CD GitHub Actions e configurar remote `origin`
      Arquivos: `.github/workflows/build.yml`, `.github/workflows/release.yml`
      Mudança: Criar `.github/workflows/build.yml` disparado em `push` (`main`/`master`) e `pull_request` com JDK 17, cache Gradle e execução de `./gradlew assembleDebug --no-daemon` e `./gradlew testDebugUnitTest --no-daemon`; criar `.github/workflows/release.yml` disparado em tags `v*` compilando APK release assinado via GitHub Secrets, extraindo notas do `CHANGELOG.md` e publicando GitHub Release com `AbacateLauncher-${version}-release.apk`; executar `git remote set-url origin https://github.com/eusoumadson/abacate-launcher.git`.
      Cobre: RF-02, RF-04, AC-02
      Acceptance criteria: Workflows `build.yml` e `release.yml` existem e são YAML válidos com JDK 17, `assembleDebug`, `testDebugUnitTest` e artefato `AbacateLauncher-${version}-release.apk`; `git remote get-url origin` retorna `https://github.com/eusoumadson/abacate-launcher.git`.
      Testes: `./gradlew testDebugUnitTest --no-daemon` e validação YAML (`python3 -c "import yaml..."` ou parser equivalente) dos workflows.

## Phase 2: Preferências do Fork, Motor Bento Grid e Configurações

Antes de implementar, leia:
1. `AGENTS.md` e `docs/agents/architecture.md` — encapsulamento em `Prefs`, MVVM e testes JUnit 4
2. `openspec/changes/fork-foundation-and-widget-screen/specs/` e `openspec/changes/fork-foundation-and-widget-screen/design.md` — requisitos RIGID (RF-05, RF-07) e decisões D3/D6

- [ ] T06 — Criar modelos de widget, `ForkWidgetPrefs` e acessores em `Prefs.kt`
      Arquivos: `app/src/main/java/app/olauncher/fork/widgets/ForkWidgetModels.kt`, `app/src/main/java/app/olauncher/fork/widgets/ForkWidgetPrefs.kt`, `app/src/main/java/app/olauncher/data/Prefs.kt`, `app/src/test/java/app/olauncher/fork/widgets/ForkWidgetPrefsTest.kt`
      Mudança: Criar `WidgetScreenPosition` (`DISABLED`, `LEFT`, `RIGHT`), `ForkWidgetEntry` (`appWidgetId`, `colSpan`, `rowSpan`, `order`, `minColSpan`, `minRowSpan`, `resizeMode`) e `ForkWidgetPrefs` usando `SharedPreferences` + JSON puro (`org.json.JSONArray` / serializer puro testável na JVM) com defaults `DISABLED`, `allowMinWidth1 = false`, `allowMinHeight1 = false`; adicionar enxerto único `// FORK:widget-screen` em `Prefs.kt` expondo os accessors tipados sem poluir o arquivo upstream com lógica de serialização.
      Cobre: RF-05, RF-07, RF-08, AC-04, AC-06
      Acceptance criteria: Padrão de `widgetScreenPosition` é `DISABLED`, `allowMinWidth1` e `allowMinHeight1` iniciam `false`, e serialização/desserialização de `ForkWidgetEntry` preserva ordem, `appWidgetId` e spans sem perda de dados.
      Testes: `app/src/test/java/app/olauncher/fork/widgets/ForkWidgetPrefsTest.kt` — testar round-trip JSON, valores default e remoção/reordenação de entries.

- [ ] T07 — Implementar motor `BentoGridLayoutPlanner` responsivo e suíte de testes JVM
      Arquivos: `app/src/main/java/app/olauncher/fork/widgets/BentoGridLayoutPlanner.kt`, `app/src/test/java/app/olauncher/fork/widgets/BentoGridLayoutPlannerTest.kt`
      Mudança: Criar `BentoGridLayoutPlanner` em Kotlin puro (sem dependência de Android `View`) definindo breakpoints Apple (`< 600dp -> 4`, `600..839dp -> 6`, `>= 840dp -> 8` colunas), empacotamento *first-fit* em matriz de ocupação (preenchendo lacunas anteriores compatíveis sem sobreposição), clamp de spans com mínimo padrão `2x2` (ou `1` quando `allowMinWidth1` / `allowMinHeight1` estiver ativo) e tratamento de `RESIZE_NONE` (`isFixedSize = true`, centralizado na célula alocada).
      Cobre: RF-07, AC-06
      Acceptance criteria: `columnCountForWidthDp` retorna `4` para `360`, `6` para `700` e `8` para `900`; algoritmo *first-fit* produz zero sobreposições e zero lacunas órfãs anteriores que comportem itens subsequentes; itens `2x2` são respeitados por padrão e reduzidos a `1x` apenas quando habilitado; `RESIZE_NONE` sinaliza centralização sem redimensionamento.
      Testes: `app/src/test/java/app/olauncher/fork/widgets/BentoGridLayoutPlannerTest.kt` — cobrir 4/6/8 colunas, first-fit com preenchimento de lacunas, zero interseção, mínimos `2x2` vs `1x`, clamp em larguras extremas e `RESIZE_NONE`.

- [ ] T08 — Implementar `ForkSwipePolicy`, seção `"Abacate Lançador"` e integração em `SettingsFragment`
      Arquivos: `app/src/main/java/app/olauncher/fork/settings/ForkSwipePolicy.kt`, `app/src/main/java/app/olauncher/fork/settings/ForkSettingsDelegate.kt`, `app/src/fork/res/layout/fork_settings_section.xml`, `app/src/fork/res/values/strings.xml`, `app/src/fork/res/values-pt-rBR/strings.xml`, `app/src/main/java/app/olauncher/ui/SettingsFragment.kt`, `app/src/test/java/app/olauncher/fork/settings/ForkSwipePolicyTest.kt`
      Mudança: Criar `ForkSwipePolicy` (funções puras resolvendo se swipe esquerdo/direito está reservado pela Tela de Widgets e qual app/label deve ser desabilitado); criar `fork_settings_section.xml` e `ForkSettingsDelegate` para injetar dinamicamente a seção `"Abacate Lançador"` em `SettingsFragment` (seletor de posição `Desativada`/`Esquerda`/`Direita`, toggles `allowMinWidth1`/`allowMinHeight1`, versão `BuildConfig.VERSION_NAME`, links `URL_FORK_GITHUB`/`URL_FORK_ISSUES` e atribuição upstream) além de atualizar o cabeçalho `olauncherHiddenApps` para `fork_app_name` e refletir o bloqueio em `populateSwipeApps` / `showAppListIfEnabled`; inserir enxertos mínimos `// FORK:fork-identity` / `// FORK:widget-screen` em `SettingsFragment.kt` preservando `fragment_settings.xml` e `layout-land/fragment_settings.xml` intocados.
      Cobre: RF-01, RF-03, RF-05, AC-01, AC-03, AC-04
      Acceptance criteria: `SettingsFragment` exibe `"Abacate Lançador"`, controles da Tela de Widgets (padrão `Desativada`), toggles `1x` (padrão `Off`) e links do fork/upstream; selecionar `Esquerda` desabilita escolha/abertura de app do swipe para direita mantendo o swipe oposto intacto, e vice-versa para `Direita`.
      Testes: `app/src/test/java/app/olauncher/fork/settings/ForkSwipePolicyTest.kt` — testar matriz completa `DISABLED` / `LEFT` / `RIGHT` x `swipeLeftEnabled` / `swipeRightEnabled`.

## Phase 3: AppWidgetHost, Pinning Externo, WidgetsFragment e Edição por Gestos

Antes de implementar, leia:
1. `AGENTS.md` e `docs/agents/architecture.md` — ciclo de vida de Activity/Fragment e delegação
2. `openspec/changes/fork-foundation-and-widget-screen/specs/widget-screen/spec.md` e `openspec/changes/fork-foundation-and-widget-screen/design.md` — requisitos RIGID (RF-07..RF-09) e decisões D4/D5

- [ ] T09 — Implementar `ForkWidgetHostManager`, limpeza de IDs órfãos e lifecycle em `MainActivity`
      Arquivos: `app/src/main/java/app/olauncher/fork/widgets/ForkWidgetHostManager.kt`, `app/src/main/java/app/olauncher/MainActivity.kt`, `app/src/test/java/app/olauncher/fork/widgets/ForkWidgetHostManagerTest.kt`
      Mudança: Criar `ForkWidgetHostManager` encapsulando `AppWidgetHost` (`FORK_WIDGET_HOST_ID = 1024`), controle idempotente de `isListening` (`startListening()` apenas quando `position != DISABLED && widgets.isNotEmpty()`; `stopListening()` apenas quando `isListening == true`) e limpeza de `appWidgetId` órfãos (`getAppWidgetInfo(id) == null` -> remove de `ForkWidgetPrefs` e chama `deleteAppWidgetId(id)`); adicionar enxertos mínimos `// FORK:widget-screen` em `MainActivity.onStart()` e `MainActivity.onStop()`.
      Cobre: RF-08, AC-07
      Acceptance criteria: `startListening()` não é chamado quando Widgets está `DISABLED` ou vazio; `stopListening()` não é chamado sem `startListening()` prévio; IDs órfãos são removidos e liberados na carga.
      Testes: `app/src/test/java/app/olauncher/fork/widgets/ForkWidgetHostManagerTest.kt` — validar máquina de estado `shouldStartListening` / `shouldStopListening` e algoritmo de filtragem/deleção de IDs órfãos via interface fake.

- [ ] T10 — Implementar fluxo `PICK -> BIND -> CONFIGURE` e pinning externo em `PinItemActivity`
      Arquivos: `app/src/main/java/app/olauncher/fork/widgets/ForkWidgetPinDelegate.kt`, `app/src/main/java/app/olauncher/helper/PinItemActivity.kt`, `app/src/main/java/app/olauncher/MainActivity.kt`, `app/src/test/java/app/olauncher/fork/widgets/ForkWidgetPinPolicyTest.kt`
      Mudança: Criar `ForkWidgetPinDelegate` implementando o fluxo `ACTION_APPWIDGET_PICK` -> `bindAppWidgetIdIfAllowed` / `ACTION_APPWIDGET_BIND` -> `ACTION_APPWIDGET_CONFIGURE` (com retorno tratado via enxertos `// FORK:widget-screen` em `MainActivity.onActivityResult` usando request codes reservados) e o tratamento de `PinItemRequest` (`REQUEST_TYPE_APPWIDGET`) em `PinItemActivity`: quando `DISABLED`, exibe `R.string.widgets_not_supported` e finaliza; quando ativo, aloca `appWidgetId`, executa bind/configure assíncrono se necessário, aceita o `PinItemRequest` com `EXTRA_APPWIDGET_ID`, persiste o widget em `ForkWidgetPrefs` e só então finaliza `PinItemActivity`.
      Cobre: RF-08, AC-07
      Acceptance criteria: `PinItemActivity` exibe `R.string.widgets_not_supported` quando `DISABLED` e conclui vinculação + persistência para `REQUEST_TYPE_APPWIDGET` quando ativo; fluxo `PICK -> BIND -> CONFIGURE` na `MainActivity` adiciona e persiste o widget configurado limpando o ID alocado em caso de cancelamento.
      Testes: `app/src/test/java/app/olauncher/fork/widgets/ForkWidgetPinPolicyTest.kt` — testar decisão de pin (`DISABLED` vs `LEFT`/`RIGHT`), cálculo de span inicial a partir de `minWidth`/`minHeight`/`resizeMode` e cleanup em cancelamento.

- [ ] T11 — Criar `ForkAppWidgetHostView` com interceptação de toque longo e feedback tátil
      Arquivos: `app/src/main/java/app/olauncher/fork/widgets/ForkAppWidgetHostView.kt`, `app/src/test/java/app/olauncher/fork/widgets/ForkWidgetEditStateTest.kt`
      Mudança: Criar `ForkAppWidgetHostView` estendendo `AppWidgetHostView` com `onInterceptTouchEvent` que detecta toque longo (`ViewConfiguration.getLongPressTimeout()`) respeitando `touchSlop`, cancela em scroll/up, dispara `performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)` e invoca callback de entrada no modo edição (bloqueando cliques acidentais no widget enquanto em edição e evitando interferência com toques normais fora do modo edição).
      Cobre: RF-09, AC-08
      Acceptance criteria: `ForkAppWidgetHostView` dispara callback de toque longo com `HapticFeedbackConstants.LONG_PRESS`, cancela timer se o dedo mover além de `touchSlop` e repassa toques simples para o widget normalmente quando fora do modo edição.
      Testes: `app/src/test/java/app/olauncher/fork/widgets/ForkWidgetEditStateTest.kt` — validar máquina de estado pura de edição (enter/exit edit mode, seleção ativa, limpeza ao tocar fora).

- [ ] T12 — Criar `WidgetsFragment` e `BentoGridLayoutView` com edição por gestos e zero botões estáticos
      Arquivos: `app/src/main/java/app/olauncher/fork/widgets/WidgetsFragment.kt`, `app/src/main/java/app/olauncher/fork/widgets/BentoGridLayoutView.kt`, `app/src/fork/res/layout/fragment_widgets.xml`, `app/src/fork/res/drawable/fork_widget_edit_border.xml`, `app/src/fork/res/drawable/fork_widget_resize_handle.xml`, `app/src/test/java/app/olauncher/fork/widgets/BentoGridEditActionsTest.kt`
      Mudança: Criar `fragment_widgets.xml` (apenas container `ScrollView` + `BentoGridLayoutView` com fundo transparente para preservar o papel de parede contínuo, sem nenhum botão estático `+` ou `Editar`); implementar `BentoGridLayoutView` medindo a grade com `BentoGridLayoutPlanner` (`widthDp`, colunas `4`/`6`/`8`, células quadradas proporcionais), renderizando `ForkAppWidgetHostView` (centralizado quando `RESIZE_NONE`), tratando toque longo no fundo vazio para abrir `ACTION_APPWIDGET_PICK`, e em modo edição exibindo alças de redimensionamento respeitando mínimos (`2x2` ou `1x`), Drag & Drop para reordenação e badge de remoção com `deleteAppWidgetId`; toque fora dos cards em edição encerra a edição e persiste em `ForkWidgetPrefs`.
      Cobre: RF-07, RF-08, RF-09, AC-06, AC-07, AC-08
      Acceptance criteria: `fragment_widgets.xml` e `WidgetsFragment` não possuem nenhum botão estático `+` ou `Editar`; toque longo no fundo vazio inicia `ACTION_APPWIDGET_PICK`; toque longo em um widget ativa Drag & Drop, alças (exceto `RESIZE_NONE`) e remoção; toque fora encerra edição persistindo ordem/spans.
      Testes: `app/src/test/java/app/olauncher/fork/widgets/BentoGridEditActionsTest.kt` — validar reordenação por Drag & Drop, clamp de redimensionamento (`2x2` vs `1x`, bloqueio em `RESIZE_NONE`) e remoção com persistência.

## Phase 4: Paginação ViewPager2, Integração Home/Swipes e Verificação Final

Antes de implementar, leia:
1. `AGENTS.md` e `docs/agents/architecture.md` — preservação da `HomeFragment` e backstack Navigation
2. `openspec/changes/fork-foundation-and-widget-screen/specs/` e `openspec/changes/fork-foundation-and-widget-screen/design.md` — requisitos RIGID (RF-03, RF-05, RF-06, RF-09, RNF-01) e decisões D1/D2

- [ ] T13 — Criar overlay `activity_main.xml` e `WorkspacePagerDelegate` com `ViewPager2`
      Arquivos: `app/src/fork/res/layout/activity_main.xml`, `app/src/main/java/app/olauncher/fork/pager/WorkspacePagerDelegate.kt`, `app/src/main/java/app/olauncher/MainActivity.kt`, `app/src/test/java/app/olauncher/fork/pager/WorkspacePagerPolicyTest.kt`
      Mudança: Criar `app/src/fork/res/layout/activity_main.xml` contendo `workspacePager` (`ViewPager2`, `visibility="gone"`) e `nav_host_fragment`; implementar `WorkspacePagerDelegate` gerenciando modo `DISABLED` (recolhe `ViewPager2` e mantém `nav_host_fragment` direto em `mainActivityLayout`) e modos `LEFT`/`RIGHT` (`offscreenPageLimit = 1`, `setHasStableIds(true)`, holders não recicláveis para Home e `WidgetsFragment`), bloqueando `isUserInputEnabled = false` sempre que `NavController.currentDestination?.id != R.id.mainFragment` ou durante drag/resize na `WidgetsFragment`, e reagindo a mudanças de preferência sem vazar estado; adicionar enxerto único `// FORK:widget-screen` em `MainActivity.onCreate()`.
      Cobre: RF-06, AC-05
      Acceptance criteria: `ViewPager2` transiciona lado a lado sobre o papel de parede contínuo quando `LEFT`/`RIGHT`, fica `GONE` quando `DISABLED`, e bloqueia swipe lateral quando o destino do `NavController` é `appListFragment` ou `settingsFragment`.
      Testes: `app/src/test/java/app/olauncher/fork/pager/WorkspacePagerPolicyTest.kt` — testar índice da página Home/Widgets para `LEFT`/`RIGHT`, e regra de `isUserInputEnabled` para destinos `mainFragment`, `appListFragment`, `settingsFragment` e modo edição ativo.

- [ ] T14 — Integrar retorno Back/Home (`onNewIntent`), precedência de swipe e isolamento em `HomeFragment`
      Arquivos: `app/src/main/java/app/olauncher/fork/pager/ForkHomeTouchDecorator.kt`, `app/src/main/java/app/olauncher/ui/HomeFragment.kt`, `app/src/main/java/app/olauncher/MainActivity.kt`, `app/src/test/java/app/olauncher/fork/pager/HomeSwipePrecedenceTest.kt`
      Mudança: Conectar retorno à página da Home em `MainActivity.onBackPressedDispatcher` (volta da `WidgetScreen` para a Home antes de checar `navController`) e em `MainActivity.backToHomeScreen()` (cobrindo `onStop`, `onUserLeaveHint` e `onNewIntent` via enxertos `// FORK:widget-screen`); criar `ForkHomeTouchDecorator` para limpar `view.isPressed = false` em `ACTION_CANCEL` quando `ViewPager2` intercepta o gesto sem alterar `OnSwipeTouchListener.kt`/`ViewSwipeTouchListener.kt`; adicionar guardas de linha única `// FORK:widget-screen` em `HomeFragment.initSwipeTouchListener()`, `openSwipeRightApp()` e `openSwipeLeftApp()` impedindo abertura de app no lado reservado aos widgets e mantendo `fragment_home.xml` 100% inalterado.
      Cobre: RF-05, RF-06, RF-09, AC-04, AC-05, AC-08
      Acceptance criteria: Voltar ou botão Home (`onNewIntent`) retorna da `WidgetScreen` para a Home; swipe na direção reservada pelos widgets nunca abre o app de swipe; `fragment_home.xml` permanece inalterado e zero controles de widget existem na `HomeFragment`.
      Testes: `app/src/test/java/app/olauncher/fork/pager/HomeSwipePrecedenceTest.kt` — validar decisão de bloqueio em `openSwipeLeftApp`/`openSwipeRightApp` e comportamento de retorno `onBackPressed`/`onNewIntent` para `WorkspacePagerDelegate`.

- [ ] T15 — Atualizar catálogo final de governança, auditar enxertos `// FORK:` e validar build/testes/lint
      Arquivos: `docs/fork/FORK_INTEGRATION.md`, `docs/fork/FORK_FEATURES.md`
      Mudança: Atualizar `docs/fork/FORK_INTEGRATION.md` com todos os enxertos exatos (`// FORK:fork-identity` e `// FORK:widget-screen`, arquivo, linha aproximada, função e impacto) obtidos via `grep -rn "FORK:" app/src/main/ app/build.gradle`; atualizar `docs/fork/FORK_FEATURES.md` marcando `fork-identity` e `widget-screen` como implementadas com suas regras de não-regressão; executar auditoria completa de build, testes JVM, lint e `openspec validate`.
      Cobre: RF-03, RNF-01, AC-03, AC-09
      Acceptance criteria: Todos os resultados de `grep -rn "FORK:" app/src/main/ app/build.gradle` correspondem exatamente à tabela em `docs/fork/FORK_INTEGRATION.md`; `./gradlew assembleDebug --no-daemon`, `./gradlew testDebugUnitTest --no-daemon`, `./gradlew lintDebug --no-daemon` e `openspec validate fork-foundation-and-widget-screen` executam com código de saída `0`.
      Testes: `./gradlew assembleDebug --no-daemon && ./gradlew testDebugUnitTest --no-daemon && ./gradlew lintDebug --no-daemon && python3 app/src/fork/tools/release.py --dry-run`.
