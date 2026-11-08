# Changelog

Todas las versiones de CircleGuard. Este archivo lo genera el pipeline de Jenkins a partir de
los Conventional Commits (HU-22): no se edita a mano.
Esquema de versiones: [docs/release-notes/etiquetado.md](docs/release-notes/etiquetado.md).

## [0.3.0](https://github.com/ingesoft5/circle-guard-pepitosV/compare/v0.2.0...v0.3.0) (2026-11-07)

### Features

- **jenkins:** add a parameterized rollback job ([#43](https://github.com/ingesoft5/circle-guard-pepitosV/pull/43)) ([9de55f6](https://github.com/ingesoft5/circle-guard-pepitosV/commit/9de55f674eff95b82d43888852935777e5cbaaa5))

### Fixes

- **jenkins:** fetch the changelog base branch into origin refs ([#43](https://github.com/ingesoft5/circle-guard-pepitosV/pull/43)) ([5cbe01f](https://github.com/ingesoft5/circle-guard-pepitosV/commit/5cbe01f0154ff4346464dd8275eb516ed2f584ed))
- **mobile:** restore Expo 55 dependencies from dev ([#41](https://github.com/ingesoft5/circle-guard-pepitosV/pull/41)) ([f530ed8](https://github.com/ingesoft5/circle-guard-pepitosV/commit/f530ed830f1bb7272477926d2c21567dca2954f8))

## [0.2.0](https://github.com/ingesoft5/circle-guard-pepitosV/compare/v0.1.0...v0.2.0) (2026-11-07)

### Features

- **jenkins:** notify pipeline failures to Discord ([#29](https://github.com/ingesoft5/circle-guard-pepitosV/pull/29)) ([c4ad268](https://github.com/ingesoft5/circle-guard-pepitosV/commit/c4ad26830975aa40827ae0b3932755aaa970133f))
- **deploy:** configure allowed CORS origins per environment ([#27](https://github.com/ingesoft5/circle-guard-pepitosV/pull/27)) ([13dee1b](https://github.com/ingesoft5/circle-guard-pepitosV/commit/13dee1b18c314ea020e893a2b1a56cf45b0b1476))

### Fixes

- **mobile:** show the questionnaire confirmation on the web client ([#34](https://github.com/ingesoft5/circle-guard-pepitosV/pull/34)) ([bbb3329](https://github.com/ingesoft5/circle-guard-pepitosV/commit/bbb33299c4c0c634e7e6c64799b068789355c7e9))
- **auth:** keep QR tokens valid for the advertised 60 seconds ([#34](https://github.com/ingesoft5/circle-guard-pepitosV/pull/34)) ([2fa28e4](https://github.com/ingesoft5/circle-guard-pepitosV/commit/2fa28e49594f17655ac5817dbff2d480c2fe0ed8))
- **jenkins:** read branch revision from SCM action ([#29](https://github.com/ingesoft5/circle-guard-pepitosV/pull/29)) ([d8bbc6e](https://github.com/ingesoft5/circle-guard-pepitosV/commit/d8bbc6e8c9b6b321a03cc1e891f3f30fc0ecc558))
- **jenkins:** normalize previous successful build revision ([#29](https://github.com/ingesoft5/circle-guard-pepitosV/pull/29)) ([18ec9e3](https://github.com/ingesoft5/circle-guard-pepitosV/commit/18ec9e31e340f9af7ffeaac5193d9ff9ef4738a6))
- **jenkins:** use previous successful build as change base ([#29](https://github.com/ingesoft5/circle-guard-pepitosV/pull/29)) ([b824f63](https://github.com/ingesoft5/circle-guard-pepitosV/commit/b824f63334d90c8e63c3b9eeafdb4710fa0c44a7))
- **infra:** persist Discord webhook credential via JCasC ([#29](https://github.com/ingesoft5/circle-guard-pepitosV/pull/29)) ([5fa05e3](https://github.com/ingesoft5/circle-guard-pepitosV/commit/5fa05e3191f8e9c29403732e674eb9ed5bed1796))
- **mobile:** keep the public host when proxying to the services ([#25](https://github.com/ingesoft5/circle-guard-pepitosV/pull/25)) ([a3ac9f0](https://github.com/ingesoft5/circle-guard-pepitosV/commit/a3ac9f0b7c4b903c16d2ac5dec0c296c3879ade0))

## [0.1.0](https://github.com/ingesoft5/circle-guard-pepitosV/releases/tag/v0.1.0) (2026-11-05)

### Features

- **deploy:** deploy published versions to dev from main ([#23](https://github.com/ingesoft5/circle-guard-pepitosV/pull/23)) ([beecf5b](https://github.com/ingesoft5/circle-guard-pepitosV/commit/beecf5b485be775529b916adc2e25c446dc8b2da))
- **deploy:** add docker compose per environment with healthchecks ([#20](https://github.com/ingesoft5/circle-guard-pepitosV/pull/20)) ([7c204a9](https://github.com/ingesoft5/circle-guard-pepitosV/commit/7c204a9b5da1c81f398a04566393be48a5656691))
- **auth:** make service addresses configurable and expose health ([#20](https://github.com/ingesoft5/circle-guard-pepitosV/pull/20)) ([a967880](https://github.com/ingesoft5/circle-guard-pepitosV/commit/a9678807099df60989fe2f143589c15a65a80e07))
- **infra:** replace owner's previous rules in hosts allow ([#11](https://github.com/ingesoft5/circle-guard-pepitosV/pull/11)) ([f134a38](https://github.com/ingesoft5/circle-guard-pepitosV/commit/f134a38f9eec95141276fed8367211d5cf7b6cb0))
- **jenkins:** register shared library in jcasc ([#12](https://github.com/ingesoft5/circle-guard-pepitosV/pull/12)) ([431a339](https://github.com/ingesoft5/circle-guard-pepitosV/commit/431a33961e1a2286c50fdb46797de46c231c0ad9))
- **jenkins:** add reusable gradle library steps ([#12](https://github.com/ingesoft5/circle-guard-pepitosV/pull/12)) ([8c024b8](https://github.com/ingesoft5/circle-guard-pepitosV/commit/8c024b8f2103fc1d582d4ceb5f3c3ff13c44006b))
- **jenkins:** declare multibranch job with github source ([#8](https://github.com/ingesoft5/circle-guard-pepitosV/pull/8)) ([f850b25](https://github.com/ingesoft5/circle-guard-pepitosV/commit/f850b25868a3412e9df2545214877f10ffa66e1c))
- **infra:** provision isolated dev, stage and prod hosts ([#5](https://github.com/ingesoft5/circle-guard-pepitosV/pull/5)) ([e374033](https://github.com/ingesoft5/circle-guard-pepitosV/commit/e3740336142f7b2c35322c07aad7d20cefef28a7))
- **infra:** add bootstrap orchestrator and from-scratch rebuild ([#3](https://github.com/ingesoft5/circle-guard-pepitosV/pull/3)) ([52d9aa2](https://github.com/ingesoft5/circle-guard-pepitosV/commit/52d9aa2577a0e12704c592472e5db24c344674a1))
- **infra:** provision CI hosts and per-tool bootstrap scripts ([#3](https://github.com/ingesoft5/circle-guard-pepitosV/pull/3)) ([7597006](https://github.com/ingesoft5/circle-guard-pepitosV/commit/759700681d53caac82f729a459af5c3581fe1171))

### Fixes

- **infra:** keep the docker group of jenkins in its env file ([#23](https://github.com/ingesoft5/circle-guard-pepitosV/pull/23)) ([b8d6d38](https://github.com/ingesoft5/circle-guard-pepitosV/commit/b8d6d389e57af92df4fbba186bda802222d4c343))
- **infra:** merge daemon.json and log in to registry on env hosts ([#16](https://github.com/ingesoft5/circle-guard-pepitosV/pull/16)) ([fed359b](https://github.com/ingesoft5/circle-guard-pepitosV/commit/fed359beb162b908aa7a64063065d381ac202704))
- **mobile:** read API URLs from Config and Export API_BASE_URL ([#15](https://github.com/ingesoft5/circle-guard-pepitosV/pull/15)) ([b00deba](https://github.com/ingesoft5/circle-guard-pepitosV/commit/b00debab4c21e64c0187136217223d838697bbe1))
- **infra:** make CI bootstrap work on clean hosts and reruns ([#5](https://github.com/ingesoft5/circle-guard-pepitosV/pull/5)) ([0456662](https://github.com/ingesoft5/circle-guard-pepitosV/commit/0456662bbd921cad80191230041c42e53e1b9fb1))
- **infra:** size CI host for the full stack ([#3](https://github.com/ingesoft5/circle-guard-pepitosV/pull/3)) ([61b3907](https://github.com/ingesoft5/circle-guard-pepitosV/commit/61b3907bc2482e6dcdde28a7316332f1238496cf))
