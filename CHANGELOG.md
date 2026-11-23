# Changelog

Todas las versiones de CircleGuard. Este archivo lo genera el pipeline de Jenkins a partir de
los Conventional Commits (HU-22): no se edita a mano.
Esquema de versiones: [docs/release-notes/etiquetado.md](docs/release-notes/etiquetado.md).

## [0.9.0](https://github.com/ingesoft5/circle-guard-pepitosV/compare/v0.8.0...v0.9.0) (2026-11-23)

### Features

- **k8s:** add install-mesh.sh and enable the mesh in stage and prod ([#162](https://github.com/ingesoft5/circle-guard-pepitosV/pull/162)) ([7eeccf0](https://github.com/ingesoft5/circle-guard-pepitosV/commit/7eeccf005ff490f24bec4ace3d0e893d312e1b4c))

### Fixes

- **deploy:** issue the TLS certificate for the sslip.io name ([#169](https://github.com/ingesoft5/circle-guard-pepitosV/pull/169)) ([3eef080](https://github.com/ingesoft5/circle-guard-pepitosV/commit/3eef080c499337d124c067e9b39efbb277639716))
- **deploy:** replace pods without surge on the single-node clusters ([#169](https://github.com/ingesoft5/circle-guard-pepitosV/pull/169)) ([50c7def](https://github.com/ingesoft5/circle-guard-pepitosV/commit/50c7defa4aee3db40fefb786d8bb622cbde5958f))
- **deploy:** lower the cpu request of the services in every environment ([#166](https://github.com/ingesoft5/circle-guard-pepitosV/pull/166)) ([dff1086](https://github.com/ingesoft5/circle-guard-pepitosV/commit/dff108648291dd480099405145033be0a0dbd232))
- **deploy:** lower the cpu request of the dev services ([#165](https://github.com/ingesoft5/circle-guard-pepitosV/pull/165)) ([8498a89](https://github.com/ingesoft5/circle-guard-pepitosV/commit/8498a89a111b7c836ab0d67b96affd7cb4dc860a))

## [0.8.0](https://github.com/ingesoft5/circle-guard-pepitosV/compare/v0.7.0...v0.8.0) (2026-11-23)

### Features

- **k8s:** add Flagger canary for the gateway ([#159](https://github.com/ingesoft5/circle-guard-pepitosV/pull/159)) ([634c54a](https://github.com/ingesoft5/circle-guard-pepitosV/commit/634c54a72722f01d7872deeac7e86fd824ab248a))
- **k8s:** add Flagger canary for the gateway ([#158](https://github.com/ingesoft5/circle-guard-pepitosV/pull/158)) ([a11de15](https://github.com/ingesoft5/circle-guard-pepitosV/commit/a11de15c49427a6af85955263feeb67e6e02e68d))
- **k8s:** add Linkerd mesh with mTLS between the services ([#157](https://github.com/ingesoft5/circle-guard-pepitosV/pull/157)) ([3ea4fe5](https://github.com/ingesoft5/circle-guard-pepitosV/commit/3ea4fe528fe8ffeb87e377ff792eb0aa42b4f5c9))
- **deploy:** point file-service at the bucket of each environment ([#152](https://github.com/ingesoft5/circle-guard-pepitosV/pull/152)) ([2895b47](https://github.com/ingesoft5/circle-guard-pepitosV/commit/2895b47f25953f6f5f2e2939293c20cd20be2b14))
- **file:** store uploads in the environment s3 bucket ([#152](https://github.com/ingesoft5/circle-guard-pepitosV/pull/152)) ([d8f180a](https://github.com/ingesoft5/circle-guard-pepitosV/commit/d8f180a3c595ccdee64f06cfeda4197a6836fa5a))
- **infra:** give the k3s node an iam role for its bucket ([#152](https://github.com/ingesoft5/circle-guard-pepitosV/pull/152)) ([755e191](https://github.com/ingesoft5/circle-guard-pepitosV/commit/755e191422012948857a09ffbaa86c851a1a03cc))
- **jenkins:** audit the release notes of every tag ([#145](https://github.com/ingesoft5/circle-guard-pepitosV/pull/145)) ([afb8f87](https://github.com/ingesoft5/circle-guard-pepitosV/commit/afb8f870e84a24534c688c5c22f561b4ba349c04))
- **jenkins:** record each deployment in github and a log branch ([#145](https://github.com/ingesoft5/circle-guard-pepitosV/pull/145)) ([64fe264](https://github.com/ingesoft5/circle-guard-pepitosV/commit/64fe2641f282cf1867cff1cbbf89055f8c60c0de))
- **k8s:** serve the ingress over TLS and redirect HTTP ([#143](https://github.com/ingesoft5/circle-guard-pepitosV/pull/143)) ([8e65877](https://github.com/ingesoft5/circle-guard-pepitosV/commit/8e65877eaef7903a68dc6f8755c9c40f726bafa2))
- **infra:** add critical circuit breaker open alert ([#142](https://github.com/ingesoft5/circle-guard-pepitosV/pull/142)) ([eaa30fa](https://github.com/ingesoft5/circle-guard-pepitosV/commit/eaa30faa3ef9ccc58bcfdee997589f55458cf692))
- **access:** split jenkins and cluster roles ([#138](https://github.com/ingesoft5/circle-guard-pepitosV/pull/138)) ([e4ed99f](https://github.com/ingesoft5/circle-guard-pepitosV/commit/e4ed99f2c6dfaabe8b343c8d344f152a8303cf9d))

### Fixes

- **jenkins:** create the zap work directory before the baseline scan ([#153](https://github.com/ingesoft5/circle-guard-pepitosV/pull/153)) ([39f4738](https://github.com/ingesoft5/circle-guard-pepitosV/commit/39f47383e5e462005bb7423ec0b2701d9fc98c8c))
- **file:** drop the apache5 http client from the s3 sdk ([#152](https://github.com/ingesoft5/circle-guard-pepitosV/pull/152)) ([36b4fa2](https://github.com/ingesoft5/circle-guard-pepitosV/commit/36b4fa22115f6ba23c6999e93f307a32d0ce483a))
- **mobile:** forward the browser scheme to the services ([#151](https://github.com/ingesoft5/circle-guard-pepitosV/pull/151)) ([182a789](https://github.com/ingesoft5/circle-guard-pepitosV/commit/182a7891881bdab8180f21d2d38a3ad5b18faf7e))
- **jenkins:** run the k8s smoke test over https ([#148](https://github.com/ingesoft5/circle-guard-pepitosV/pull/148)) ([a951610](https://github.com/ingesoft5/circle-guard-pepitosV/commit/a9516101a5ee6c75a6d48f4c3c978f02d370a2a0))
- **jenkins:** report the real error when a k8s deploy fails ([#147](https://github.com/ingesoft5/circle-guard-pepitosV/pull/147)) ([f70c88a](https://github.com/ingesoft5/circle-guard-pepitosV/commit/f70c88a6b0d4c0bc8dce8160973078ddf2c9c7a0))
- **deploy:** make cert-manager and node ip scripts executable ([#147](https://github.com/ingesoft5/circle-guard-pepitosV/pull/147)) ([eb71a38](https://github.com/ingesoft5/circle-guard-pepitosV/commit/eb71a381f413b89448c311f1b40715197365691c))
- **infra:** free nexus disk after image retention ([#142](https://github.com/ingesoft5/circle-guard-pepitosV/pull/142)) ([278c2b7](https://github.com/ingesoft5/circle-guard-pepitosV/commit/278c2b78a5736a63232892f3b3170691e0e2517b))
- **gateway:** fail fast on redis and keep readiness off redis ([#142](https://github.com/ingesoft5/circle-guard-pepitosV/pull/142)) ([62d8f7d](https://github.com/ingesoft5/circle-guard-pepitosV/commit/62d8f7dd886d757a2923c25d8f04c5ec29a4de7c))

## [0.7.0](https://github.com/ingesoft5/circle-guard-pepitosV/compare/v0.6.0...v0.7.0) (2026-11-22)

### Features

- **infra:** add two jenkins build workers ([#123](https://github.com/ingesoft5/circle-guard-pepitosV/pull/123)) ([badecab](https://github.com/ingesoft5/circle-guard-pepitosV/commit/badecaba7281acd64917474be8eb82f5d6e1d2a5))

### Performance

- **jenkins:** reuse the gradle daemon on the workers ([#125](https://github.com/ingesoft5/circle-guard-pepitosV/pull/125)) ([b1291d8](https://github.com/ingesoft5/circle-guard-pepitosV/commit/b1291d85d0ec61b2804add5e496de782d7330758))
- **infra:** share a gradle build cache between the ci workers ([#125](https://github.com/ingesoft5/circle-guard-pepitosV/pull/125)) ([9debec2](https://github.com/ingesoft5/circle-guard-pepitosV/commit/9debec2b370e2a3270ac79febfc3d33714f7ca59))
- **repo:** enable the gradle build cache and parallel projects ([#123](https://github.com/ingesoft5/circle-guard-pepitosV/pull/123)) ([2038475](https://github.com/ingesoft5/circle-guard-pepitosV/commit/20384757447f07b4be3c8d68fea195c1c807fdd6))

## [0.6.0](https://github.com/ingesoft5/circle-guard-pepitosV/compare/v0.5.0...v0.6.0) (2026-11-22)

### Features

- **infra:** alert on service down, errors and latency ([#106](https://github.com/ingesoft5/circle-guard-pepitosV/pull/106)) ([6eaf1de](https://github.com/ingesoft5/circle-guard-pepitosV/commit/6eaf1dea187ce6db7cdb8723db49fd2d686c9f0a))
- **form:** publish pending outbox events ([#107](https://github.com/ingesoft5/circle-guard-pepitosV/pull/107)) ([e68052b](https://github.com/ingesoft5/circle-guard-pepitosV/commit/e68052b3155dd60fe56e5f5b81781203bb66edd8))
- **form:** enqueue survey events in the outbox ([#107](https://github.com/ingesoft5/circle-guard-pepitosV/pull/107)) ([dc66af3](https://github.com/ingesoft5/circle-guard-pepitosV/commit/dc66af34db3f08d020e2d279b0a1de0ff7e7cf83))
- **form:** add transactional outbox storage ([#107](https://github.com/ingesoft5/circle-guard-pepitosV/pull/107)) ([5c8165a](https://github.com/ingesoft5/circle-guard-pepitosV/commit/5c8165a391753d8e25c06478358a2fdc5934ebcc))
- **ci:** run terraform plan and apply in the pipeline ([#105](https://github.com/ingesoft5/circle-guard-pepitosV/pull/105)) ([b030235](https://github.com/ingesoft5/circle-guard-pepitosV/commit/b0302358dea32c81fd29ae56abbc12d5172b816d))
- **observability:** ship json logs to loki ([#103](https://github.com/ingesoft5/circle-guard-pepitosV/pull/103)) ([7405c6e](https://github.com/ingesoft5/circle-guard-pepitosV/commit/7405c6e75c3ab7e0a35bb5fcd877afc6d747909b))
- **repo:** add service and business dashboards to grafana ([#103](https://github.com/ingesoft5/circle-guard-pepitosV/pull/103)) ([4d09eaf](https://github.com/ingesoft5/circle-guard-pepitosV/commit/4d09eaf35dc6a152fb9c65140c287124a4429022))
- **repo:** expose service and business metrics for Prometheus ([#102](https://github.com/ingesoft5/circle-guard-pepitosV/pull/102)) ([b08d3ad](https://github.com/ingesoft5/circle-guard-pepitosV/commit/b08d3ad3eb59fd4dd8fe49480dc9bc707a12d043))
- **k8s:** seal cluster secrets after a clean history scan ([#104](https://github.com/ingesoft5/circle-guard-pepitosV/pull/104)) ([4d196ca](https://github.com/ingesoft5/circle-guard-pepitosV/commit/4d196ca1bf5a5de304758443c8b537e9a74d9848))
- **observability:** publish prometheus and alertmanager ([#99](https://github.com/ingesoft5/circle-guard-pepitosV/pull/99)) ([879dcc3](https://github.com/ingesoft5/circle-guard-pepitosV/commit/879dcc3848856b03497613f5acc49bff9c7d0634))
- **observability:** ship json logs to loki ([#98](https://github.com/ingesoft5/circle-guard-pepitosV/pull/98)) ([85c3d40](https://github.com/ingesoft5/circle-guard-pepitosV/commit/85c3d405aa2566abc739d30bd3803fef62c2891c))
- **k8s:** run jaeger in the cluster ([#96](https://github.com/ingesoft5/circle-guard-pepitosV/pull/96)) ([0d5c1d3](https://github.com/ingesoft5/circle-guard-pepitosV/commit/0d5c1d353d2184d976e7c3d2c6f16f33ad723365))
- **k8s:** set qr lifetime and diagnostics from the configmap ([#95](https://github.com/ingesoft5/circle-guard-pepitosV/pull/95)) ([c14a6a5](https://github.com/ingesoft5/circle-guard-pepitosV/commit/c14a6a528acafcd1885d66a14a8641b1cffe1f33))
- **gateway:** publish the redis health circuit to grafana ([#94](https://github.com/ingesoft5/circle-guard-pepitosV/pull/94)) ([37f7ad9](https://github.com/ingesoft5/circle-guard-pepitosV/commit/37f7ad9377b75f1b1b439e0743b3135d5d086508))
- **infra:** publish grafana through the cluster ingress ([#92](https://github.com/ingesoft5/circle-guard-pepitosV/pull/92)) ([0d437e3](https://github.com/ingesoft5/circle-guard-pepitosV/commit/0d437e3217b85d1d1a00535dc4973decf631ae2c))
- **deploy:** let prometheus scrape the spring services ([#92](https://github.com/ingesoft5/circle-guard-pepitosV/pull/92)) ([34dfcc4](https://github.com/ingesoft5/circle-guard-pepitosV/commit/34dfcc43219078085d5e0b9e5011172b0a62aa1f))
- **jenkins:** roll back kubernetes environments with helm ([#90](https://github.com/ingesoft5/circle-guard-pepitosV/pull/90)) ([aee30b7](https://github.com/ingesoft5/circle-guard-pepitosV/commit/aee30b79fdc7613dee84db6d206b37267b8b3778))
- **jenkins:** deploy every environment to kubernetes with helm ([#90](https://github.com/ingesoft5/circle-guard-pepitosV/pull/90)) ([481b4bb](https://github.com/ingesoft5/circle-guard-pepitosV/commit/481b4bbd638de7078db8d817c6fb033c047ce744))
- **deploy:** add idempotent script for the environment secret ([#89](https://github.com/ingesoft5/circle-guard-pepitosV/pull/89)) ([7aae825](https://github.com/ingesoft5/circle-guard-pepitosV/commit/7aae8258f22ee7b1cb0e25509d4f9d553c6ba9f8))
- **deploy:** add helm chart for one environment namespace ([#89](https://github.com/ingesoft5/circle-guard-pepitosV/pull/89)) ([2abc13d](https://github.com/ingesoft5/circle-guard-pepitosV/commit/2abc13d322591259340717031d6d7fd13dfd292c))
- **infra:** install prometheus stack from the observability module ([#88](https://github.com/ingesoft5/circle-guard-pepitosV/pull/88)) ([d4e748a](https://github.com/ingesoft5/circle-guard-pepitosV/commit/d4e748a2dd06ed7bdc3bdd98f31c5adc361b42d0))
- **infra:** harden and document terraform remote state ([#87](https://github.com/ingesoft5/circle-guard-pepitosV/pull/87)) ([ec394ee](https://github.com/ingesoft5/circle-guard-pepitosV/commit/ec394eee5660fa122473451b46301a4dfe2cc85e))
- **repo:** write service logs as json ([#86](https://github.com/ingesoft5/circle-guard-pepitosV/pull/86)) ([9b693cc](https://github.com/ingesoft5/circle-guard-pepitosV/commit/9b693cc47cc2ace6ed831b02c66e1561ddcf690c))
- **infra:** wire reusable modules into environments ([#82](https://github.com/ingesoft5/circle-guard-pepitosV/pull/82)) ([3b6504e](https://github.com/ingesoft5/circle-guard-pepitosV/commit/3b6504e434470ac4f1fb0f35269695c4c1819254))
- **infra:** add registry storage observability modules ([#82](https://github.com/ingesoft5/circle-guard-pepitosV/pull/82)) ([170bd85](https://github.com/ingesoft5/circle-guard-pepitosV/commit/170bd85386548013c89a41b47dfa9682bcb6f1c9))
- **form:** propagate traceparent across kafka ([#81](https://github.com/ingesoft5/circle-guard-pepitosV/pull/81)) ([31bb868](https://github.com/ingesoft5/circle-guard-pepitosV/commit/31bb868530a8ef86b7d663941a46da6842f550c5))
- **auth:** read the QR lifetime from configuration ([#76](https://github.com/ingesoft5/circle-guard-pepitosV/pull/76)) ([0c99c55](https://github.com/ingesoft5/circle-guard-pepitosV/commit/0c99c55a93ab7d752884adcb1d2479fcfd25056c))
- **gateway:** open the circuit when redis stays down ([#75](https://github.com/ingesoft5/circle-guard-pepitosV/pull/75)) ([6651d20](https://github.com/ingesoft5/circle-guard-pepitosV/commit/6651d20fa58f65cb540ff627239f9b0f3edeb03b))

### Fixes

- **infra:** route alerts to discord through an AlertmanagerConfig ([#111](https://github.com/ingesoft5/circle-guard-pepitosV/pull/111)) ([383b6f8](https://github.com/ingesoft5/circle-guard-pepitosV/commit/383b6f8fcd911424198f295f9e45015fc04e02a0))
- **deploy:** run the sealed secrets installer on a fresh cluster ([#110](https://github.com/ingesoft5/circle-guard-pepitosV/pull/110)) ([b63cbc8](https://github.com/ingesoft5/circle-guard-pepitosV/commit/b63cbc86bbe32b49a15351ed209ac6a2dbd2397d))
- **jenkins:** stash the terraform plan and keep the ssh key out of logs ([#109](https://github.com/ingesoft5/circle-guard-pepitosV/pull/109)) ([8df9d22](https://github.com/ingesoft5/circle-guard-pepitosV/commit/8df9d22c06709e7891f928cf606546e7bad29f4a))
- **infra:** remove the webhook temp file when the Discord script exits ([#106](https://github.com/ingesoft5/circle-guard-pepitosV/pull/106)) ([01c6d53](https://github.com/ingesoft5/circle-guard-pepitosV/commit/01c6d539bef57469e43d0045c64a2b00ea9c197a))
- **form:** re-interrupt the outbox relay on interruption ([#107](https://github.com/ingesoft5/circle-guard-pepitosV/pull/107)) ([3788697](https://github.com/ingesoft5/circle-guard-pepitosV/commit/3788697d07a085bfbb3eb9ab42ba0d9cf90fc8d7))
- **observability:** keep prometheus redirect under /prometheus ([#100](https://github.com/ingesoft5/circle-guard-pepitosV/pull/100)) ([b9b5a90](https://github.com/ingesoft5/circle-guard-pepitosV/commit/b9b5a906cf5acdb1b38f63afe1e6316d6a0a9039))
- **infra:** avoid k3s node replacement on comment change ([#93](https://github.com/ingesoft5/circle-guard-pepitosV/pull/93)) ([5b944b0](https://github.com/ingesoft5/circle-guard-pepitosV/commit/5b944b004ff164e5ee5d849f9e40912413627054))
- **deploy:** generate secrets without requiring openssl ([#90](https://github.com/ingesoft5/circle-guard-pepitosV/pull/90)) ([2a9b9ba](https://github.com/ingesoft5/circle-guard-pepitosV/commit/2a9b9bae78aaef2d86f7fec8bb4ec1cf2808c1be))
- **deploy:** run containers with read-only root and security context ([#89](https://github.com/ingesoft5/circle-guard-pepitosV/pull/89)) ([5680186](https://github.com/ingesoft5/circle-guard-pepitosV/commit/5680186328b3db565d093e26345458b8843ce35f))
- **jenkins:** scan terraform and helm charts in one config run ([#89](https://github.com/ingesoft5/circle-guard-pepitosV/pull/89)) ([599aac2](https://github.com/ingesoft5/circle-guard-pepitosV/commit/599aac2447f33eab93d8b7af43e6d3103af918cb))
- **mobile:** refresh web image packages once a day ([#89](https://github.com/ingesoft5/circle-guard-pepitosV/pull/89)) ([03cdb71](https://github.com/ingesoft5/circle-guard-pepitosV/commit/03cdb71c7dcdf15f4d471603ec18d9cc06a1605e))
- **jenkins:** copy the workspace into the scans ([#79](https://github.com/ingesoft5/circle-guard-pepitosV/pull/79)) ([8d8f288](https://github.com/ingesoft5/circle-guard-pepitosV/commit/8d8f2888c091cf9228b5a54896b3b9bf967159a1))

## [0.5.0](https://github.com/ingesoft5/circle-guard-pepitosV/compare/v0.4.0...v0.5.0) (2026-11-15)

### Features

- **infra:** provision one k3s cluster per environment with terraform ([#71](https://github.com/ingesoft5/circle-guard-pepitosV/pull/71)) ([43c67c9](https://github.com/ingesoft5/circle-guard-pepitosV/commit/43c67c97930e1a456dc74f5d84a281448629ea48))
- **jenkins:** give jenkins access to the k3s clusters ([#70](https://github.com/ingesoft5/circle-guard-pepitosV/pull/70)) ([498cffc](https://github.com/ingesoft5/circle-guard-pepitosV/commit/498cffce7652c6c5cddf6e924bf46d4e50ca9f78))
- **infra:** split the CI stack into jenkins and tools hosts ([#66](https://github.com/ingesoft5/circle-guard-pepitosV/pull/66)) ([b71c99f](https://github.com/ingesoft5/circle-guard-pepitosV/commit/b71c99fc53fcae394e0c99cdf78890e5f2901a65))

### Fixes

- **jenkins:** diff pull requests against the target branch ([#70](https://github.com/ingesoft5/circle-guard-pepitosV/pull/70)) ([06faf10](https://github.com/ingesoft5/circle-guard-pepitosV/commit/06faf106643cfee45a6f89431755dae5dd7e6f83))
- **jenkins:** skip the size policy on dev to main promotion PRs ([#70](https://github.com/ingesoft5/circle-guard-pepitosV/pull/70)) ([613f12a](https://github.com/ingesoft5/circle-guard-pepitosV/commit/613f12a5b7092bc24b8be091fb20041d642ec721))
- **semver:** use latest stable release from main as version baseline ([#60](https://github.com/ingesoft5/circle-guard-pepitosV/pull/60)) ([e8b9286](https://github.com/ingesoft5/circle-guard-pepitosV/commit/e8b9286e4e801331f794e972cf751f370ad11044))

### Performance

- **jenkins:** build service images from the tested jar ([#70](https://github.com/ingesoft5/circle-guard-pepitosV/pull/70)) ([b47d235](https://github.com/ingesoft5/circle-guard-pepitosV/commit/b47d23575c76a54b58e1000b728bb7b52ac35a50))

## [0.4.0](https://github.com/ingesoft5/circle-guard-pepitosV/compare/v0.3.0...v0.4.0) (2026-11-08)

### Features

- **jenkins:** restrict production approval to the admin account ([#49](https://github.com/ingesoft5/circle-guard-pepitosV/pull/49)) ([b9286cb](https://github.com/ingesoft5/circle-guard-pepitosV/commit/b9286cbd725b43efab8c12b9a37d9e990dbeed3a))
- **ci:** add sonar analysis and blocjing quality gate ([#48](https://github.com/ingesoft5/circle-guard-pepitosV/pull/48)) ([d853b91](https://github.com/ingesoft5/circle-guard-pepitosV/commit/d853b918ac9184eb766cd618536511a61b1b1bd9))

### Fixes

- **infra:** install Jenkins plugins when building the image ([#49](https://github.com/ingesoft5/circle-guard-pepitosV/pull/49)) ([da3237c](https://github.com/ingesoft5/circle-guard-pepitosV/commit/da3237cc960e8912e66258d210b39139fe54db18))
- **tests:** pin Testcontainers so Docker 29 accepts the client ([#46](https://github.com/ingesoft5/circle-guard-pepitosV/pull/46)) ([1f36097](https://github.com/ingesoft5/circle-guard-pepitosV/commit/1f360978103d7feb768bc7526e98c722d9382190))

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
