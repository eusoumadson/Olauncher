# Fork Integration Guide (Abacate Lançador)

Este documento mapeia todos os pontos de enxerto (`FORK:<feature>`) em arquivos compartilhados com o Olauncher upstream, descreve a estratégia de overlay de recursos e define o roteiro padrão de sincronização (`git fetch upstream` / merge).

## 1. Arquitetura de Isolamento

- **Código exclusivo do fork**: `app/src/main/java/app/olauncher/fork/`
- **Ferramentas de release/build do fork**: `app/src/fork/tools/`
- **Overlay de recursos do fork**: `app/src/fork/res/` (configurado em `sourceSets.debug.res.srcDirs` e `sourceSets.release.res.srcDirs` para sobrescrever `app/src/main/res/` sem duplicar arquivos no `main` sourceSet)
- **Identificadores de recurso gerados via Gradle**: `resValue` em `app/build.gradle` (`fork_app_name = "Abacate Lançador"`, `fork_label_name = "Lançador"`)
- **Política de código**: **Zero Lixo de Compatibilidade** — nenhum shim legado, fallback morto ou alias obsoleto deve permanecer após migrações.

## 2. Mapa de Pontos de Enxerto Ativos

Catálogo verificado via `grep -rn "FORK:" app/src/main/ app/build.gradle gradle/libs.versions.toml`:

| Arquivo compartilhado | Linha(s) | Marcador | Função / Bloco | Impacto |
|---|---|---|---|---|
| `gradle/libs.versions.toml` | `12`, `26` | `# FORK:widget-screen` | `[versions]`, `[libraries]` | Declara `viewpager2 = "1.1.0"` e a biblioteca `androidx.viewpager2:viewpager2` |
| `app/build.gradle` | `4-5` | `// FORK:fork-identity` | Topo do script Gradle | Carrega `version.properties` (CalVer) |
| `app/build.gradle` | `21`, `24-27` | `// FORK:fork-identity` | `android.defaultConfig` | Define `applicationIdSuffix ".abacate"`, `versionCode`/`versionName` dinâmicos e `resValue` (`fork_app_name`, `fork_label_name`) |
| `app/build.gradle` | `33-36` | `// FORK:fork-identity` | `android.sourceSets` | Adiciona `src/fork/res` aos overlays `debug` e `release` |
| `app/build.gradle` | `60-62` | `// FORK:fork-identity` | `android.lint` | Configura `baseline = file("lint-baseline.xml")` para isolar os erros legados do upstream |
| `app/build.gradle` | `84` | `// FORK:widget-screen` | `dependencies` | Adiciona `implementation libs.viewpager2` |
| `app/src/main/AndroidManifest.xml` | `91` | `<!-- FORK:fork-identity -->` | `<service android:name=".helper.MyAccessibilityService">` | Define `android:label="@string/fork_app_name"` no serviço de acessibilidade |
| `app/src/main/java/app/olauncher/data/Constants.kt` | `108-109` | `// FORK:fork-identity` | `object Constants` | Adiciona `URL_FORK_GITHUB` e `URL_FORK_ISSUES` mantendo todas as URLs upstream intactas |
| `app/src/main/java/app/olauncher/data/Prefs.kt` | `8-10` | `// FORK:widget-screen` | Imports | Importa `ForkWidgetEntry`, `ForkWidgetPrefs` e `WidgetScreenPosition` |
| `app/src/main/java/app/olauncher/data/Prefs.kt` | `651-663` | `// FORK:widget-screen` | Propriedades delegadas em `Prefs` | Expõe `forkWidgetPrefs`, `widgetScreenPosition`, `allowMinWidth1`, `allowMinHeight1` e `widgetEntries` delegando para `ForkWidgetPrefs` |
| `app/src/main/java/app/olauncher/MainActivity.kt` | `27-29` | `// FORK:widget-screen` | Imports | Importa `WorkspacePagerDelegate`, `ForkWidgetHostManager` e `ForkWidgetPinDelegate` |
| `app/src/main/java/app/olauncher/MainActivity.kt` | `66-68` | `// FORK:widget-screen` | Campos da `MainActivity` | Declara `forkWidgetHostManager`, `forkWidgetPinDelegate` e `workspacePagerDelegate` |
| `app/src/main/java/app/olauncher/MainActivity.kt` | `84-85`, `95` | `// FORK:widget-screen` | `onCreate()` | Instancia `ForkWidgetHostManager`, `ForkWidgetPinDelegate` e anexa `WorkspacePagerDelegate` ao `ViewPager2` e `NavController` |
| `app/src/main/java/app/olauncher/MainActivity.kt` | `100` | `// FORK:widget-screen` | `onBackPressedCallback.handleOnBackPressed()` | Retorna da `WidgetScreen` para a página Home antes de consultar o backstack do `NavController` |
| `app/src/main/java/app/olauncher/MainActivity.kt` | `140` | `// FORK:widget-screen` | `onStart()` | Chama `forkWidgetHostManager.onStart()` (inicia escuta apenas se ativo e com widgets) |
| `app/src/main/java/app/olauncher/MainActivity.kt` | `182` | `// FORK:widget-screen` | `onStop()` | Chama `forkWidgetHostManager.onStop()` (encerra escuta se estiver ativa) |
| `app/src/main/java/app/olauncher/MainActivity.kt` | `361` | `// FORK:widget-screen` | `backToHomeScreen()` | Chama `workspacePagerDelegate.returnToHome(smoothScroll = isResumed)` cobrindo `onStop()`, `onUserLeaveHint()` e `onNewIntent()` |
| `app/src/main/java/app/olauncher/MainActivity.kt` | `419` | `// FORK:widget-screen` | `onActivityResult()` | Encaminha resultados de `ACTION_APPWIDGET_PICK`/`BIND`/`CONFIGURE` para `forkWidgetPinDelegate.onActivityResult()` |
| `app/src/main/java/app/olauncher/ui/HomeFragment.kt` | `33-34` | `// FORK:widget-screen` | Imports | Importa `ForkHomeTouchDecorator` e `ForkSwipePolicy` |
| `app/src/main/java/app/olauncher/ui/HomeFragment.kt` | `225` | `// FORK:widget-screen` | `initSwipeTouchListener()` | Decora listeners de toque da Home via `ForkHomeTouchDecorator.decorate(...)` limpando `isPressed` em `ACTION_CANCEL` |
| `app/src/main/java/app/olauncher/ui/HomeFragment.kt` | `515` | `// FORK:widget-screen` | `openSwipeRightApp()` | Guarda impedindo abertura do app de swipe direito quando reservado para `WidgetScreenPosition.LEFT` |
| `app/src/main/java/app/olauncher/ui/HomeFragment.kt` | `529` | `// FORK:widget-screen` | `openSwipeLeftApp()` | Guarda impedindo abertura do app de swipe esquerdo quando reservado para `WidgetScreenPosition.RIGHT` |
| `app/src/main/java/app/olauncher/ui/SettingsFragment.kt` | `30` | `// FORK:widget-screen` | Imports | Importa `ForkSettingsDelegate` |
| `app/src/main/java/app/olauncher/ui/SettingsFragment.kt` | `54` | `// FORK:widget-screen` | Campos de `SettingsFragment` | Declara `forkSettingsDelegate` |
| `app/src/main/java/app/olauncher/ui/SettingsFragment.kt` | `72` | `// FORK:widget-screen` | `onViewCreated()` | Instancia `ForkSettingsDelegate(requireContext(), prefs)` |
| `app/src/main/java/app/olauncher/ui/SettingsFragment.kt` | `100` | `// FORK:fork-identity` | `onViewCreated()` | Chama `forkSettingsDelegate.applyIdentityAndSection(binding)` exibindo `fork_app_name` e a seção `"Abacate Lançador"` |
| `app/src/main/java/app/olauncher/ui/SettingsFragment.kt` | `162-163` | `// FORK:widget-screen` | `onLongClick()` | Bloqueia toggle de swipe em `swipeLeftApp`/`swipeRightApp` se o lado estiver reservado para widgets |
| `app/src/main/java/app/olauncher/ui/SettingsFragment.kt` | `661` | `// FORK:widget-screen` | `populateSwipeApps()` | Atualiza texto/cor do gesto reservado para widgets via `forkSettingsDelegate.populateSwipeApps(binding)` |
| `app/src/main/java/app/olauncher/ui/SettingsFragment.kt` | `671` | `// FORK:widget-screen` | `showAppListIfEnabled()` | Bloqueia seleção de app de swipe quando o lado está reservado para widgets |
| `app/src/main/java/app/olauncher/helper/PinItemActivity.kt` | `3`, `5`, `9-10` | `// FORK:widget-screen` | Imports | Importa `Intent`, `Build`, `ForkWidgetHostManager` e `ForkWidgetPinDelegate` |
| `app/src/main/java/app/olauncher/helper/PinItemActivity.kt` | `14` | `// FORK:widget-screen` | Campos de `PinItemActivity` | Declara `forkWidgetPinDelegate` |
| `app/src/main/java/app/olauncher/helper/PinItemActivity.kt` | `32` | `// FORK:widget-screen` | `onCreate()` | Finaliza a Activity somente quando `shouldFinish` é verdadeiro (permite fluxo assíncrono de bind/configure) |
| `app/src/main/java/app/olauncher/helper/PinItemActivity.kt` | `43-46`, `49` | `// FORK:widget-screen` | `handleRequestType()` | Delega `REQUEST_TYPE_APPWIDGET` (API >= 26) para `ForkWidgetPinDelegate.handlePinItemRequest()` |
| `app/src/main/java/app/olauncher/helper/PinItemActivity.kt` | `59-63` | `// FORK:widget-screen` | `onActivityResult()` | Encaminha resultado de bind/configure para `forkWidgetPinDelegate?.onActivityResult()` |
| `app/src/main/java/app/olauncher/helper/PinItemActivity.kt` | `65-68` | `// FORK:widget-screen` | `onDestroy()` | Executa limpeza `forkWidgetPinDelegate?.onDestroy()` |

### Overlays ativos em `app/src/fork/res/`

| Recurso | Sobrescreve / Adiciona | Descrição |
|---|---|---|
| `app/src/fork/res/layout/activity_main.xml` | Sobrescreve `res/layout/activity_main.xml` | Adiciona `workspacePager` (`ViewPager2`, `visibility="gone"`) ao lado de `nav_host_fragment` sem alterar `app/src/main/res/layout/activity_main.xml` |
| `app/src/fork/res/layout/fork_settings_section.xml` | Adiciona layout exclusivo do fork | Seção `"Abacate Lançador"` injetada dinamicamente em `SettingsFragment` sem modificar `fragment_settings.xml` |
| `app/src/fork/res/layout/fragment_widgets.xml` | Adiciona layout exclusivo do fork | Container transparente (`ScrollView` + `BentoGridLayoutView`) da Tela de Widgets sem botões estáticos |
| `app/src/fork/res/drawable/fork_widget_edit_border.xml` | Adiciona drawable exclusivo do fork | Borda de seleção do card em modo edição usando tokens do tema |
| `app/src/fork/res/drawable/fork_widget_resize_handle.xml` | Adiciona drawable exclusivo do fork | Alça circular de redimensionamento na grade Bento usando tokens do tema |
| `app/src/fork/res/values/ids.xml` | Adiciona IDs exclusivos do fork | Declara `@id/fork_widgets_page_container` para hospedar `WidgetsFragment` dentro do `ViewPager2` |
| `app/src/fork/res/values/strings.xml` | Sobrescreve `app_name` + adiciona `fork_*` | Define `app_name` como `@string/fork_label_name` (`"Lançador"`) e strings exclusivas do fork em inglês |
| `app/src/fork/res/values-pt-rBR/strings.xml` | Adiciona `fork_*` (pt-BR) | Traduções em português brasileiro das strings exclusivas do fork |
| `app/src/fork/res/xml/accessibility_service_config.xml` | Sobrescreve `res/xml/accessibility_service_config.xml` | Inclui os 4 pacotes (`app.olauncher.abacate`, `app.olauncher.abacate.debug`, `app.olauncher.debug`, `app.olauncher`) |

## 3. Roteiro de Sincronização com o Upstream (`tanujnotes/Olauncher`)

1. Verifique que a árvore de trabalho está limpa:
   ```sh
   git status
   ```
2. Busque atualizações do upstream:
   ```sh
   git fetch upstream
   ```
3. Inicie o merge em uma branch de integração dedicada:
   ```sh
   git checkout -b chore/sync-upstream-$(date +%Y%m%d)
   git merge upstream/main
   ```
4. Em caso de conflito em arquivos compartilhados (`app/build.gradle`, `gradle/libs.versions.toml`, `AndroidManifest.xml`, `MainActivity.kt`, `HomeFragment.kt`, `SettingsFragment.kt`, `PinItemActivity.kt`, `Prefs.kt`, `Constants.kt`):
   - Aceite as alterações do upstream e reaplique apenas as linhas marcadas com `FORK:<feature>` listadas na tabela acima.
   - Nunca copie recursos do fork para `app/src/main/res/`; mantenha todos os overrides em `app/src/fork/res/`.
5. Audite os pontos de enxerto e valide build/testes/lint localmente:
   ```sh
   grep -rn "FORK:" app/src/main/ app/build.gradle gradle/libs.versions.toml
   ./gradlew assembleDebug --no-daemon
   ./gradlew testDebugUnitTest --no-daemon
   ./gradlew lintDebug --no-daemon
   python3 -m unittest app/src/fork/tools/test_release.py
   ```
