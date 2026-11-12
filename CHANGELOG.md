# Changelog

Todas as alterações notáveis do **Abacate Lançador** serão documentadas neste arquivo, seguindo versionamento **CalVer (`YYYY.MM.DD`)**.

## [2026.11.12] - 2026-11-12

### Added
- Identidade dual do fork (`app.olauncher.abacate` / `app.olauncher.abacate.debug`) para coexistência lado a lado com o Olauncher upstream.
- Rótulo de sistema `"Lançador"` (`fork_label_name`) via overlay `app/src/fork/res/values/strings.xml` e rótulo `"Abacate Lançador"` (`fork_app_name`) para o serviço de acessibilidade e telas do fork.
- Configuração de acessibilidade em `app/src/fork/res/xml/accessibility_service_config.xml` cobrindo os pacotes do fork e do upstream.
- Versionamento CalVer dinâmico via `version.properties` (`VERSION_CODE=116`, `VERSION_NAME=2026.11.12`), `Makefile` e script `app/src/fork/tools/release.py`.
- Constantes `URL_FORK_GITHUB` e `URL_FORK_ISSUES` em `Constants.kt` preservando integralmente as URLs e créditos do Olauncher upstream (GNU GPLv3).
- Documentação de governança e isolamento (`FORK_INTEGRATION.md`, `FORK_FEATURES.md`, política Zero Lixo de Compatibilidade em `AGENTS.md` e `docs/agents/coding_guidelines.md`).
- Provisionamento da dependência `androidx.viewpager2:viewpager2` para a Tela Dedicada de Widgets.
