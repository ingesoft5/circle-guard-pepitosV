/**
 * Promueve el release candidate de una versión a su versión estable sin reconstruir (HU-15).
 *
 *   promoteRelease(version: '0.4.1', services: SERVICES)
 *   promoteRelease(version: '0.4.1', services: SERVICES, dryRun: true)
 *
 * El RC se identifica por el árbol de código de HEAD: X.Y.Z-rc.<7 primeros del árbol>. El PR de
 * promoción lo publica con dockerPublish; main solo le añade el tag X.Y.Z. Para ello lee el
 * manifest del RC por la API del registro y lo publica con el nuevo tag, byte a byte: el digest
 * no cambia, así que lo que pasó stage y los E2E es exactamente lo que se promueve.
 *
 * Primero verifica que existen los RC de todas las imágenes y solo entonces crea tags estables.
 * Es idempotente: si el tag estable ya apunta al mismo digest se omite; si apunta a otro, falla.
 * dryRun solo verifica los RC y no crea nada.
 *
 * Usa la API del registro dentro de la red de CI (como rollback.Jenkinsfile): NEXUS_REGISTRY_API,
 * por defecto http://nexus:5000, con la credencial nexus-ci.
 */
def call(Map config = [:]) {
    String version = (config.version ?: '').toString().trim()
    if (!semver.isVersion(version)) {
        error("promoteRelease: '${version}' no es una versión MAJOR.MINOR.PATCH")
    }

    List<String> images = ((config.services ?: []) as List).collect {
        it.toString().replaceFirst(/^circleguard-/, '')
    }
    images.addAll(config.extraImages ?: ['web-client'])
    if (!images) {
        error('promoteRelease: indique "services" con los servicios a promover')
    }

    String api = config.registryApi ?: (env.NEXUS_REGISTRY_API ?: 'http://nexus:5000')
    String namespace = config.namespace ?: (env.IMAGE_NAMESPACE ?: 'circleguard')
    String credentialsId = config.credentialsId ?: 'nexus-ci'
    boolean dryRun = config.dryRun == true

    String tree = sh(script: 'git rev-parse HEAD^{tree}', returnStdout: true).trim()
    String rcTag = "${version}-rc.${tree.take(7)}"
    echo "promoteRelease: ${rcTag} -> ${version} (${dryRun ? 'solo verificación' : 'promoción'}), imágenes: ${images.join(', ')}"

    withEnv([
        "REGISTRY_API=${api}",
        "NAMESPACE=${namespace}",
        "IMAGES=${images.join(' ')}",
        "RC_TAG=${rcTag}",
        "STABLE_TAG=${version}",
        "DRY_RUN=${dryRun}"
    ]) {
        withCredentials([usernamePassword(credentialsId: credentialsId,
                usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_PASSWORD')]) {
            sh '''
set +x
set -eu

ACCEPT='application/vnd.oci.image.index.v1+json, application/vnd.docker.distribution.manifest.list.v2+json, application/vnd.docker.distribution.manifest.v2+json, application/vnd.oci.image.manifest.v1+json'
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# fetch <imagen> <tag> <prefijo>: guarda cabeceras y cuerpo en $WORK/<prefijo>.head|.body
# e imprime el código HTTP.
fetch() {
  curl -sS -o "$WORK/$3.body" -D "$WORK/$3.head" -w '%{http_code}' \
    -u "$REGISTRY_USER:$REGISTRY_PASSWORD" -H "Accept: $ACCEPT" \
    "$REGISTRY_API/v2/$NAMESPACE/$1/manifests/$2"
}

# header <prefijo> <nombre>: valor de una cabecera guardada por fetch.
header() {
  sed 's/[[:space:]]*$//' "$WORK/$1.head" | awk -F': ' -v n="$2" 'tolower($1) == tolower(n) { print $2; exit }'
}

# El registro debe devolver el manifest tal cual: así el digest se conserva al republicarlo.
check_digest() {
  expected="$(header "$1" docker-content-digest)"
  actual="sha256:$(sha256sum "$WORK/$1.body" | cut -d' ' -f1)"
  if [ "$expected" != "$actual" ]; then
    echo "ERROR: el manifest de $2 no coincide con su digest ($expected / $actual); no se puede promover sin cambiar el digest." >&2
    exit 1
  fi
}

echo "Release candidate $RC_TAG -> versión estable $STABLE_TAG ($REGISTRY_API/v2/$NAMESPACE)"

# Fase 1: todos los RC deben existir antes de crear ningún tag estable.
for img in $IMAGES; do
  code="$(fetch "$img" "$RC_TAG" rc)"
  case "$code" in
    200) ;;
    404)
      echo "ERROR: no existe el release candidate $img:$RC_TAG" >&2
      echo "Lo publica el build del PR dev -> main; main solo promueve lo que ese PR probó." >&2
      exit 1 ;;
    *)
      echo "ERROR: el registro respondió HTTP $code al consultar $img:$RC_TAG" >&2
      exit 1 ;;
  esac
  check_digest rc "$img:$RC_TAG"
  echo "RC verificado: $img $(header rc docker-content-digest)"
done

if [ "$DRY_RUN" = "true" ]; then
  echo "Verificación sin cambios: no se crea ningún tag estable."
  exit 0
fi

# Fase 2: el mismo manifest bajo el tag estable.
for img in $IMAGES; do
  code="$(fetch "$img" "$RC_TAG" rc)"
  if [ "$code" != "200" ]; then
    echo "ERROR: HTTP $code al leer $img:$RC_TAG" >&2
    exit 1
  fi
  check_digest rc "$img:$RC_TAG"
  rc_digest="$(header rc docker-content-digest)"
  ctype="$(header rc content-type)"
  ctype="${ctype%%;*}"

  code="$(fetch "$img" "$STABLE_TAG" stable)"
  case "$code" in
    200)
      stable_digest="$(header stable docker-content-digest)"
      if [ "$stable_digest" != "$rc_digest" ]; then
        echo "ERROR: $img:$STABLE_TAG ya existe con otro contenido ($stable_digest; el RC es $rc_digest)." >&2
        echo "Una versión publicada no se sobrescribe." >&2
        exit 1
      fi
      echo "Ya promovida: $img:$STABLE_TAG ($stable_digest)"
      continue ;;
    404) ;;
    *)
      echo "ERROR: HTTP $code al consultar $img:$STABLE_TAG" >&2
      exit 1 ;;
  esac

  code="$(curl -sS -o "$WORK/put.out" -w '%{http_code}' -X PUT \
    -u "$REGISTRY_USER:$REGISTRY_PASSWORD" -H "Content-Type: $ctype" \
    --data-binary @"$WORK/rc.body" "$REGISTRY_API/v2/$NAMESPACE/$img/manifests/$STABLE_TAG")"
  if [ "$code" != "201" ] && [ "$code" != "200" ]; then
    echo "ERROR: HTTP $code al publicar $img:$STABLE_TAG: $(head -c 300 "$WORK/put.out")" >&2
    exit 1
  fi

  code="$(fetch "$img" "$STABLE_TAG" stable)"
  new_digest="$(header stable docker-content-digest)"
  if [ "$code" != "200" ] || [ "$new_digest" != "$rc_digest" ]; then
    echo "ERROR: $img:$STABLE_TAG quedó con digest '$new_digest' y el RC tiene $rc_digest." >&2
    exit 1
  fi
  echo "Promovida: $img:$STABLE_TAG ($new_digest)"
done
'''
        }
    }
}