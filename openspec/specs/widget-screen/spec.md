# widget-screen Specification

## Purpose
Prover uma Tela Dedicada de Widgets com transição física lado a lado via `ViewPager2`, motor Bento Grid responsivo estilo Apple, gerenciamento completo de ciclo de vida `AppWidgetHost` e pinning externo, e edição puramente por gestos sem comprometer o minimalismo da `HomeFragment`.

## Requirements

### Requirement: RF-05 - Seção "Abacate Lançador" nas Configurações e Precedência de Gestos
THE SYSTEM SHALL adicionar a seção `"Abacate Lançador"` em `SettingsFragment` (verified at `app/src/main/java/app/olauncher/ui/SettingsFragment.kt:67-163`) com posição da Tela de Widgets (`Desativada` padrão, `Esquerda`, `Direita`), toggles de largura/altura `1` e links/versão do fork e upstream; WHILE um lado estiver ativo para widgets, THE SYSTEM SHALL desabilitar abertura de app nesse gesto preservando o gesto oposto.

#### Scenario: Estrutura da seção e precedência de gesto lateral
- **WHEN** o usuário abrir `SettingsFragment` (verified at `app/src/main/res/layout/fragment_settings.xml:531-598`) e selecionar `Esquerda` ou `Direita` para a Tela de Widgets
- **THEN** a preferência SHALL ser persistida em `Prefs` (verified at `app/src/main/java/app/olauncher/data/Prefs.kt:9-10`)
- **AND** a abertura de aplicativo no gesto lateral correspondente (`openSwipeRightApp` ou `openSwipeLeftApp`, verified at `app/src/main/java/app/olauncher/ui/HomeFragment.kt:510-534`) SHALL ser desabilitada mantendo o gesto oposto intacto conforme `swipeLeftEnabled` / `swipeRightEnabled` (verified at `app/src/main/java/app/olauncher/data/Prefs.kt:187-193`) e refletido em `populateSwipeApps` (verified at `app/src/main/java/app/olauncher/ui/SettingsFragment.kt:650-657`)
- **AND** os toggles de largura `1` (`allowMinWidth1`) e altura `1` (`allowMinHeight1`) SHALL iniciar desativados e exibir o estado atual junto à versão e links do fork/upstream.

### Requirement: RF-06 - Transição Física Lado a Lado via ViewPager2 e Bloqueio Contextual
WHILE a Tela de Widgets estiver ativa (`Esquerda` ou `Direita`), THE SYSTEM SHALL deslizar `WidgetScreen` e `HomeFragment` pixel a pixel via `ViewPager2` (`WorkspacePagerDelegate` sobre overlay `app/src/fork/res/layout/activity_main.xml`, verified at `app/src/main/res/layout/activity_main.xml:5-16`) mantendo papel de parede contínuo, bloqueando swipe fora de `mainFragment` e retornando via swipe oposto, Voltar ou `onNewIntent`.

#### Scenario: Paginação contínua na Home e bloqueio fora da Home
- **WHEN** o usuário deslizar lateralmente a partir de `HomeFragment` com a Tela de Widgets ativa
- **THEN** o `ViewPager2` SHALL transicionar pixel a pixel entre `HomeFragment` e `WidgetScreen` sobre o papel de parede contínuo
- **AND** WHILE o destino do `NavController` (verified at `app/src/main/java/app/olauncher/MainActivity.kt:86` e `app/src/main/res/navigation/nav_graph.xml:8-27`) for diferente de `R.id.mainFragment`, `ViewPager2.isUserInputEnabled` SHALL ser `false`.

#### Scenario: Retorno à Home por gesto oposto, Voltar ou botão Home
- **WHEN** o usuário estiver na `WidgetScreen` e executar swipe oposto, acionar `OnBackPressedCallback` (verified at `app/src/main/java/app/olauncher/MainActivity.kt:89-96`) ou pressionar Home (`MainActivity.onNewIntent`, verified at `app/src/main/java/app/olauncher/MainActivity.kt:180-187`)
- **THEN** `WorkspacePagerDelegate` SHALL retornar o `ViewPager2` para a página da `HomeFragment`.

### Requirement: RF-07 - Motor Bento Grid Responsivo e Empacotamento First-Fit
THE SYSTEM SHALL implementar `BentoGridLayoutPlanner` em `app/src/main/java/app/olauncher/fork/` com `4` colunas (`< 600dp`), `6` colunas (`600dp..839dp`) ou `8` colunas (`>= 840dp`), usando empacotamento *first-fit* sem lacunas órfãs nem sobreposição, mínimo padrão `2x2` (ou `1x` quando liberado nas configurações) e centralizando widgets `RESIZE_NONE`.

#### Scenario: Seleção de colunas responsiva e empacotamento sem sobreposição
- **WHEN** `BentoGridLayoutPlanner` calcular posições para larguras de `360dp`, `700dp` e `900dp`
- **THEN** a grade SHALL adotar `4`, `6` e `8` colunas respectivamente
- **AND** todos os itens SHALL ser alocados por *first-fit* sem interseção entre células ocupadas nem lacunas órfãs anteriores que comportem o widget.

#### Scenario: Limites mínimos 2x2 vs 1x e widgets RESIZE_NONE
- **WHEN** um widget for planejado com toggles `1x` desativados ou possuir `AppWidgetProviderInfo.RESIZE_NONE`
- **THEN** o planner SHALL impor mínimo `2x2` (salvo quando largura `1` ou altura `1` estiver liberada nas configurações)
- **AND** widgets com `RESIZE_NONE` SHALL ser centralizados na célula alocada sem alças de redimensionamento.

### Requirement: RF-08 - Ciclo de Vida do AppWidgetHost, Fluxo de Adição e Pinning Externo
THE SYSTEM SHALL executar `AppWidgetHost.startListening()` em `MainActivity.onStart()` (verified at `app/src/main/java/app/olauncher/MainActivity.kt:128-131`) e `stopListening()` em `onStop()` (verified at `MainActivity.kt:169-173`) apenas quando ativo e com widgets, limpar IDs órfãos na carga, abrir pick->bind->configure ao toque longo no fundo vazio e aceitar `REQUEST_TYPE_APPWIDGET` em `PinItemActivity` quando ativo.

#### Scenario: Ciclo de vida econômico e limpeza de IDs órfãos
- **WHEN** `MainActivity.onStart()` ou `onStop()` executar
- **THEN** `startListening()` SHALL ser invocado somente se a Tela de Widgets estiver ativa e com `>= 1` widget salvo, e `stopListening()` somente se estiver escutando
- **AND** na carga, qualquer `appWidgetId` cujo `AppWidgetProviderInfo` seja nulo SHALL ser removido e liberado via `AppWidgetHost.deleteAppWidgetId()`.

#### Scenario: Adição por toque longo no fundo e pinning externo em PinItemActivity
- **WHEN** o usuário realizar toque longo no fundo vazio da `WidgetScreen` ou um app externo disparar `CONFIRM_PIN_APPWIDGET` para `PinItemActivity` (verified at `app/src/main/java/app/olauncher/helper/PinItemActivity.kt:31-32` e `app/src/main/AndroidManifest.xml:58-61`)
- **THEN** o toque longo no fundo SHALL iniciar `ACTION_APPWIDGET_PICK` -> `ACTION_APPWIDGET_BIND` -> `ACTION_APPWIDGET_CONFIGURE` via `MainActivity.onActivityResult` (verified at `app/src/main/java/app/olauncher/MainActivity.kt:403-417`)
- **AND** `PinItemActivity` SHALL aceitar e vincular `REQUEST_TYPE_APPWIDGET` quando a Tela de Widgets estiver ativa, ou exibir `R.string.widgets_not_supported` (verified at `app/src/main/res/values/strings.xml:173`) quando `Desativada`.

### Requirement: RF-09 - Edição Puramente por Gestos na Tela de Widgets sem Vazamento para a Home
WHEN o usuário realizar toque longo sobre um widget (interceptado por `ForkAppWidgetHostView` com feedback tátil), THE SYSTEM SHALL habilitar Drag & Drop, alças de redimensionamento e remoção até que um toque fora encerre a edição, sem renderizar botões estáticos (`+` ou `Editar`) nem alterar a `HomeFragment` (verified at `app/src/main/res/layout/fragment_home.xml:2-9` e `app/src/main/java/app/olauncher/ui/HomeFragment.kt:212-223, 641-721`).

#### Scenario: Edição tátil por toque longo e encerramento ao tocar fora
- **WHEN** o usuário pressionar e segurar um widget na `WidgetScreen` e posteriormente tocar fora do widget
- **THEN** `ForkAppWidgetHostView` SHALL disparar feedback tátil (`LONG_PRESS`) ativando Drag & Drop, alças de redimensionamento respeitando limites da grade e controle de remoção
- **AND** ao tocar fora dos controles de edição, o modo de edição SHALL encerrar persistindo a ordem e os spans atualizados.

#### Scenario: Zero botões estáticos e isolamento da HomeFragment
- **WHEN** a interface da `WidgetScreen` e da `HomeFragment` for inspecionada
- **THEN** nenhum botão estático `+` ou `Editar` SHALL existir na `WidgetScreen`
- **AND** nenhum widget, alça ou alteração de toque SHALL vazar para `HomeFragment`.
