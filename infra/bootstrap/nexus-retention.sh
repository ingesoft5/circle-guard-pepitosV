#!/usr/bin/env bash
# Retention of the Docker images in Nexus (docker-hosted), run daily on the tools host by the
# systemd timer that bootstrap-nexus.sh installs. Nexus OSS has no cleanup-policy API, so the
# rules live here:
#   - Stable versions (X.Y.Z) are never deleted: rollback deploys them by exact version.
#   - An rc whose base version X.Y.Z exists as stable is kept: it is the promoted release
#     candidate (same digest) and the audit trail of what passed stage and E2E.
#   - Of the other prereleases (SNAPSHOT and rc), each image keeps the newest KEEP_PRERELEASES.
# Then it runs Nexus' "Cleanup unused docker blobs" task so the disk space is reclaimed.
#   infra/bootstrap/nexus-retention.sh            # deletes
#   DRY_RUN=true infra/bootstrap/nexus-retention.sh
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib/common.sh"

readonly CI_ENV_FILE="${COMPOSE_DIR}/ci/.env"
readonly RETENTION_REPOSITORY="${NEXUS_RETENTION_REPOSITORY:-docker-hosted}"

main() {
  require_cmd curl python3
  load_env "${CI_ENV_FILE}"
  local keep="${KEEP_PRERELEASES:-5}" dry_run="${DRY_RUN:-false}"
  [[ "${keep}" =~ ^[0-9]+$ ]] || die "KEEP_PRERELEASES must be an integer"

  NEXUS_URL="http://localhost:${NEXUS_PORT:-8081}" REPOSITORY="${RETENTION_REPOSITORY}" KEEP="${keep}" \
    DRY_RUN="${dry_run}" NEXUS_USER="admin" NEXUS_PASSWORD="${NEXUS_ADMIN_PASSWORD}" \
    python3 - <<'PY'
import base64, json, os, re, sys, urllib.parse, urllib.request

url, repo = os.environ["NEXUS_URL"], os.environ["REPOSITORY"]
keep, dry_run = int(os.environ["KEEP"]), os.environ["DRY_RUN"] == "true"
auth = "Basic " + base64.b64encode(f'{os.environ["NEXUS_USER"]}:{os.environ["NEXUS_PASSWORD"]}'.encode()).decode()

def call(method, path):
    req = urllib.request.Request(url + path, method=method, headers={"Authorization": auth})
    with urllib.request.urlopen(req, timeout=60) as r:
        body = r.read()
        return json.loads(body) if body else None

components, token = [], None
while True:
    q = {"repository": repo}
    if token:
        q["continuationToken"] = token
    page = call("GET", "/service/rest/v1/components?" + urllib.parse.urlencode(q))
    components += page["items"]
    token = page.get("continuationToken")
    if not token:
        break

stable = re.compile(r"^\d+\.\d+\.\d+$")
by_image = {}
for c in components:
    modified = max((a.get("lastModified") or "" for a in c.get("assets", [])), default="")
    by_image.setdefault(c["name"], []).append((modified, c["version"], c["id"]))

deleted = 0
for image, items in sorted(by_image.items()):
    stables = {v for _, v, _ in items if stable.match(v)}
    candidates = []
    for modified, version, cid in items:
        if stable.match(version):
            continue
        base = version.split("-", 1)[0]
        if "-rc." in version and base in stables:
            continue  # promoted release candidate: audit trail, shares the stable digest
        candidates.append((modified, version, cid))
    candidates.sort(reverse=True)
    for modified, version, cid in candidates[keep:]:
        print(f"{'would delete' if dry_run else 'delete'} {image}:{version} (last modified {modified})")
        if not dry_run:
            call("DELETE", f"/service/rest/v1/components/{cid}")
        deleted += 1
print(f"{len(components)} tags in {repo}; {deleted} prerelease tags {'to delete' if dry_run else 'deleted'}")

if deleted and not dry_run:
    # Deleting a tag frees no disk in Nexus OSS: the layers stay until the Docker GC drops the
    # unreferenced ones and the compact task removes the soft-deleted blobs. Without them the
    # tools disk filled up and SonarQube's Elasticsearch went read-only (flood stage, 95 %).
    # Run in order and wait for each, because each step feeds the next.
    import time
    tasks = call("GET", "/service/rest/v1/tasks")["items"]
    steps = [
        ("assetBlob.cleanup", lambda t: "docker" in t["name"].lower()),
        ("repository.docker.gc", lambda t: True),
        ("blobstore.compact", lambda t: True),
    ]
    for task_type, match in steps:
        found = [t for t in tasks if t["type"] == task_type and match(t)]
        if not found:
            print(f"missing task {task_type}: create it in Nexus (Settings > System > Tasks)", file=sys.stderr)
            continue
        task = found[0]
        call("POST", f"/service/rest/v1/tasks/{task['id']}/run")
        print(f"started task: {task['name']}")
        time.sleep(5)
        for _ in range(180):
            if call("GET", f"/service/rest/v1/tasks/{task['id']}")["currentState"] == "WAITING":
                break
            time.sleep(10)
PY
}

main "$@"
