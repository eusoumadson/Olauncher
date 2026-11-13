# Fork Integration Guide (Abacate Lançador)

Este documento mapeia todos os pontos de enxerto (`// FORK:<feature>`) em arquivos compartilhados com o Olauncher upstream, descreve a estratégia de overlay de recursos e define o roteiro padrão de sincronização (`git fetch upstream` / merge).

## 1. Arquitetura de Isolamento

- **Código exclusivo do fork**: `app/src/main/java/app/olauncher/fork/`
- **Ferramentas de release/build do fork**: `app/src/fork/tools/`
- **Overlay de recursos do fork**: `app/src/fork/res/` (configurado em `sourceSets.debug.res.srcDirs` e `sourceSets.release.res.srcDirs` para sobrescrever `app/src/main/res/` sem duplicar arquivos no `main` sourceSet)
- **Identificadores de recurso gerados via Gradle**: `resValue` em `app/build.gradle` (`fork_app_name = "Abacate Lançador"`, `fork_label_name = "Lançador"`)
- **Política de código**: **Zero Lixo de Compatibilidade** — nenhum shim legado, fallback morto ou alias obsoleto deve permanecer após migrações.

## 2. Mapa de Pontos de Enxerto Ativos

| Arquivo compartilhado | Marcador | Finalidade |
|---|---|---|
| `gradle/libs.versions.toml` | `# FORK:widget-screen` | Declara a versão e biblioteca `androidx.viewpager2:viewpager2` |
| `app/build.gradle` | `// FORK:fork-identity` | Carrega `version.properties` (CalVer), define `applicationIdSuffix ".abacate"`, `versionCode`/`versionName` dinâmicos, `resValue` (`fork_app_name`, `fork_label_name`) e `sourceSets` de `src/fork/res` |
| `app/build.gradle` | `// FORK:widget-screen` | Adiciona `implementation libs.viewpager2` |
| `app/src/main/AndroidManifest.xml` | `<!-- FORK:fork-identity -->` | Define `android:label="@string/fork_app_name"` no serviço `MyAccessibilityService` |
| `app/src/main/java/app/olauncher/data/Constants.kt` | `// FORK:fork-identity` | Adiciona `URL_FORK_GITHUB` e `URL_FORK_ISSUES` mantendo todas as URLs upstream intactas |
| `app/src/main/java/app/olauncher/data/Prefs.kt` | `// FORK:widget-screen` | Expõe accessors tipados delegando para `ForkWidgetPrefs` (`widgetScreenPosition`, `allowMinWidth1`, `allowMinHeight1`, `widgetEntries`) |
| `app/src/main/java/app/olauncher/ui/SettingsFragment.kt` | `// FORK:fork-identity`, `// FORK:widget-screen` | Delega para `ForkSettingsDelegate` injetando a seção `"Abacate Lançador"`, cabeçalho `fork_app_name` e bloqueio de swipe reservado |
| `app/src/main/java/app/olauncher/MainActivity.kt` | `// FORK:widget-screen` | Delega `onStart`/`onStop` para `ForkWidgetHostManager` e `onActivityResult` para `ForkWidgetPinDelegate` |
| `app/src/main/java/app/olauncher/helper/PinItemActivity.kt` | `// FORK:widget-screen` | Delega `REQUEST_TYPE_APPWIDGET`, `onActivityResult` e `onDestroy` para `ForkWidgetPinDelegate` |

### Overlays ativos em `app/src/fork/res/`

| Recurso | Sobrescreve / Adiciona | Descrição |
|---|---|---|
| `app/src/fork/res/layout/fork_settings_section.xml` | Adiciona layout exclusivo do fork | Seção `"Abacate Lançador"` injetada dinamicamente em `SettingsFragment` sem modificar `fragment_settings.xml` |
| `app/src/fork/res/layout/fragment_widgets.xml` | Adiciona layout exclusivo do fork | Container transparente (`ScrollView` + `BentoGridLayoutView`) da Tela de Widgets sem botões estáticos |
| `app/src/fork/res/drawable/fork_widget_edit_border.xml` | Adiciona drawable exclusivo do fork | Borda de seleção do card em modo edição usando tokens do tema |
| `app/src/fork/res/drawable/fork_widget_resize_handle.xml` | Adiciona drawable exclusivo do fork | Alça circular de redimensionamento na grade Bento usando tokens do tema |
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
4. Em caso de conflito em arquivos compartilhados (`app/build.gradle`, `gradle/libs.versions.toml`, `AndroidManifest.xml`, `Constants.kt`, etc.):
   - Aceite as alterações do upstream e reaplique apenas as linhas marcadas com `FORK:<feature>` listadas na tabela acima.
   - Nunca copie recursos do fork para `app/src/main/res/`; mantenha todos os overrides em `app/src/fork/res/`.
5. Audite os pontos de enxerto e valide build/testes localmente:
   ```sh
   grep -rn "FORK:" app/src/main/ app/build.gradle gradle/libs.versions.toml
   ./gradlew assembleDebug --no-daemon
   ./gradlew testDebugUnitTest --no-daemon
   python3 -m unittest app/src/fork/tools/test_release.py
   ```
