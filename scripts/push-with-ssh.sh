#!/usr/bin/env bash
# Push MathWake to GitHub over SSH and verify the CI build + prerelease.
#
# Usage:
#   ./scripts/push-with-ssh.sh                    # push HEAD to main and verify CI
#   ./scripts/push-with-ssh.sh --key ~/.ssh/id_ed25519
#   ./scripts/push-with-ssh.sh --generate-key     # create a key, then add it to GitHub
#   ./scripts/push-with-ssh.sh --verify-only      # verify CI for the current HEAD only
#   ./scripts/push-with-ssh.sh --timeout 3600
#
# Environment:
#   MATHWAKE_SSH_KEY  SSH private key path (same as --key)
#   GITHUB_TOKEN      optional token for CI verification (avoids API rate limits)
#
# Requirements: git, ssh, ssh-keygen, curl, python3.
# The script never prints private key material and never writes tokens to git config.

set -euo pipefail

OWNER="Minashin1120"
REPO="alarm-pro-android"
SLUG="${OWNER}/${REPO}"
SSH_URL="git@github.com:${SLUG}.git"
BRANCH="main"
DEFAULT_KEY="${HOME}/.ssh/mathwake_ed25519"

KEY_PATH="${MATHWAKE_SSH_KEY:-}"
GENERATE_KEY=0
VERIFY_ONLY=0
VERIFY=1
EXPECT_CI=1
TIMEOUT_SECONDS=1800

usage() {
  awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"
}

die() {
  echo "ERROR: $*" >&2
  exit 1
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --key)
      [ "$#" -ge 2 ] || die "--key requires a path"
      KEY_PATH="$2"
      shift 2
      ;;
    --key=*)
      KEY_PATH="${1#--key=}"
      shift
      ;;
    --generate-key)
      GENERATE_KEY=1
      shift
      ;;
    --verify-only)
      VERIFY_ONLY=1
      shift
      ;;
    --no-verify)
      VERIFY=0
      shift
      ;;
    --branch)
      [ "$#" -ge 2 ] || die "--branch requires a name"
      BRANCH="$2"
      shift 2
      ;;
    --timeout)
      [ "$#" -ge 2 ] || die "--timeout requires seconds"
      TIMEOUT_SECONDS="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "unknown argument: $1 (try --help)"
      ;;
  esac
done

for tool in git ssh ssh-keygen curl python3; do
  command -v "$tool" >/dev/null 2>&1 || die "$tool is required but was not found in PATH"
done

cd "$(git rev-parse --show-toplevel 2>/dev/null)" || die "not inside a git repository"

case "$TIMEOUT_SECONDS" in
  ''|*[!0-9]*) die "--timeout must be a number of seconds" ;;
esac
[ -n "$BRANCH" ] || die "--branch must not be empty"

SHA="$(git rev-parse HEAD)"
SHORT_SHA="$(git rev-parse --short HEAD)"

# ---------------------------------------------------------------------------
# CI verification (GitHub REST API, no gh CLI required)
# ---------------------------------------------------------------------------

API_CODE=""
API_BODY=""

api_get() {
  local url="$1"
  local headers=(
    -H "Accept: application/vnd.github+json"
    -H "X-GitHub-Api-Version: 2022-11-28"
  )
  if [ -n "${GITHUB_TOKEN:-}" ]; then
    headers+=(-H "Authorization: Bearer ${GITHUB_TOKEN}")
  fi

  local tmp
  tmp="$(mktemp)"
  API_CODE="$(curl -sS --max-time 25 -o "$tmp" -w '%{http_code}' \
    "${headers[@]}" "$url" 2>/dev/null || echo "000")"
  API_BODY="$(cat "$tmp" 2>/dev/null || true)"
  rm -f "$tmp"
}

require_api_ok() {
  case "$API_CODE" in
    200) return 0 ;;
    403|429)
      echo "ERROR: GitHub API rate limit reached (HTTP ${API_CODE})." >&2
      echo "       Set GITHUB_TOKEN (repo read is enough) and re-run." >&2
      exit 1
      ;;
    *)
      echo "ERROR: GitHub API returned HTTP ${API_CODE}: ${1:-}" >&2
      printf '%s\n' "$API_BODY" | head -5 >&2
      exit 1
      ;;
  esac
}

# Prints: id<TAB>status<TAB>conclusion<TAB>run_number<TAB>html_url
first_run_fields() {
  python3 -c '
import json, sys
try:
    data = json.load(sys.stdin)
except Exception:
    sys.exit(0)
runs = data.get("workflow_runs") or []
if not runs:
    sys.exit(0)
run = runs[0]
print("\t".join(str(run.get(key, "") or "") for key in
                ("id", "status", "conclusion", "run_number", "html_url")))
'
}

# Prints: status<TAB>conclusion<TAB>run_number<TAB>html_url
run_fields() {
  python3 -c '
import json, sys
try:
    run = json.load(sys.stdin)
except Exception:
    sys.exit(0)
print("\t".join(str(run.get(key, "") or "") for key in
                ("status", "conclusion", "run_number", "html_url")))
'
}

# Prints: html_url app-debug.apk download url
release_fields() {
  python3 -c '
import json, sys
try:
    release = json.load(sys.stdin)
except Exception:
    sys.exit(0)
url = release.get("html_url", "")
apk = ""
for asset in release.get("assets") or []:
    if asset.get("name") == "app-debug.apk":
        apk = asset.get("browser_download_url", "")
        break
print(url, apk)
'
}

verify_ci() {
  local sha="$1"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  local appear_deadline=$((SECONDS + 120))
  local run_id="" run_status="" run_conclusion="" run_number="" run_url=""

  echo
  echo "Waiting for GitHub Actions to pick up ${sha:0:7} ..."
  while [ "$SECONDS" -lt "$appear_deadline" ]; do
    api_get "https://api.github.com/repos/${SLUG}/actions/runs?head_sha=${sha}&per_page=10"
    require_api_ok "while listing workflow runs"

    IFS=$'\t' read -r run_id run_status run_conclusion run_number run_url \
      < <(printf '%s' "$API_BODY" | first_run_fields) || true
    if [ -n "$run_id" ]; then
      break
    fi
    sleep 5
  done

  if [ -z "$run_id" ]; then
    if [ "$EXPECT_CI" -eq 0 ]; then
      echo "No CI run expected: the pushed commits do not touch app/** or the build files."
      echo "Skipping CI verification."
      return 0
    fi
    echo "ERROR: no workflow run appeared within 120s." >&2
    echo "       The push may not have touched app/** or CI is delayed." >&2
    exit 1
  fi

  echo "Found run #${run_number}: ${run_url}"

  local last_status=""
  while [ "$SECONDS" -lt "$deadline" ]; do
    api_get "https://api.github.com/repos/${SLUG}/actions/runs/${run_id}"
    require_api_ok "while polling run ${run_id}"

    IFS=$'\t' read -r run_status run_conclusion run_number run_url \
      < <(printf '%s' "$API_BODY" | run_fields) || true

    if [ "$run_status" != "$last_status" ]; then
      echo "  status: ${run_status:-unknown}"
      last_status="$run_status"
    fi

    if [ "$run_status" = "completed" ]; then
      break
    fi
    sleep 10
  done

  if [ "$run_status" != "completed" ]; then
    echo "ERROR: CI did not finish within ${TIMEOUT_SECONDS}s." >&2
    echo "       Check: ${run_url}" >&2
    exit 1
  fi

  if [ "$run_conclusion" != "success" ]; then
    echo "ERROR: CI finished with '${run_conclusion}'." >&2
    echo "       Logs: ${run_url}" >&2
    exit 1
  fi

  echo "CI succeeded."

  # The workflow publishes a ci-run-<run_number> prerelease after the build.
  local release_url="" apk_url=""
  local attempt=0
  while [ "$attempt" -lt 12 ]; do
    api_get "https://api.github.com/repos/${SLUG}/releases/tags/ci-run-${run_number}"
    if [ "$API_CODE" = "200" ]; then
      read -r release_url apk_url \
        < <(printf '%s' "$API_BODY" | release_fields) || true
      break
    fi
    attempt=$((attempt + 1))
    sleep 5
  done

  echo
  echo "=== Push & CI verification complete ==="
  echo "Commit:  ${sha}"
  echo "Run:     ${run_url} (${run_conclusion})"
  if [ -n "$release_url" ]; then
    echo "Release: ${release_url}"
    [ -n "$apk_url" ] && echo "APK:     ${apk_url}"
  else
    echo "Release: ci-run-${run_number} not published yet (Actions may still be uploading)."
  fi
}

# ---------------------------------------------------------------------------
# Verify only
# ---------------------------------------------------------------------------

if [ "$VERIFY_ONLY" -eq 1 ]; then
  verify_ci "$SHA"
  exit 0
fi

# ---------------------------------------------------------------------------
# SSH key selection
# ---------------------------------------------------------------------------

if [ -n "$KEY_PATH" ] && [ ! -f "$KEY_PATH" ]; then
  die "SSH key not found: ${KEY_PATH}"
fi

if [ -z "$KEY_PATH" ]; then
  for candidate in "$DEFAULT_KEY" "${HOME}/.ssh/id_ed25519" "${HOME}/.ssh/id_rsa"; do
    if [ -f "$candidate" ]; then
      KEY_PATH="$candidate"
      break
    fi
  done
fi

if [ -z "$KEY_PATH" ]; then
  if [ "$GENERATE_KEY" -eq 1 ]; then
    mkdir -p "${HOME}/.ssh"
    chmod 700 "${HOME}/.ssh"
    ssh-keygen -t ed25519 -N "" -C "mathwake-push" -f "$DEFAULT_KEY" >/dev/null
    echo "Generated SSH key: ${DEFAULT_KEY}"
    echo
    echo "Add this public key to GitHub"
    echo "  https://github.com/settings/keys  (Settings -> SSH and GPG keys -> New SSH key)"
    echo
    cat "${DEFAULT_KEY}.pub"
    echo
    echo "Then re-run: $0"
    exit 2
  fi

  cat >&2 <<EOF
No SSH key found.

Either pass an existing key:
  $0 --key /path/to/private_key

or create a new one and register it with GitHub:
  $0 --generate-key
EOF
  exit 2
fi

case "$(stat -c '%a' "$KEY_PATH" 2>/dev/null || true)" in
  ""|400|600) ;;
  *) echo "WARNING: ${KEY_PATH} is readable by other users. Run: chmod 600 ${KEY_PATH}" >&2 ;;
esac

echo "Using SSH key: ${KEY_PATH}"

# ---------------------------------------------------------------------------
# SSH connectivity (fall back to ssh.github.com:443 when port 22 is blocked)
# ---------------------------------------------------------------------------

SSH_ARGS=(
  -i "$KEY_PATH"
  -o IdentitiesOnly=yes
  -o StrictHostKeyChecking=accept-new
  -o ConnectTimeout=15
)

auth_output="$(ssh "${SSH_ARGS[@]}" -T -o BatchMode=yes git@github.com 2>&1 || true)"

if ! grep -q "successfully authenticated" <<<"$auth_output"; then
  auth_output_443="$(ssh "${SSH_ARGS[@]}" -T -o BatchMode=yes -p 443 git@ssh.github.com 2>&1 || true)"
  if grep -q "successfully authenticated" <<<"$auth_output_443"; then
    SSH_ARGS+=(-p 443 -o HostName=ssh.github.com)
    auth_output="$auth_output_443"
  else
    cat >&2 <<EOF
GitHub SSH authentication failed.

Server said:
$(printf '%s\n' "$auth_output" | tail -3)

Checklist:
  - The public key (${KEY_PATH}.pub) is registered at https://github.com/settings/keys
  - The key is the one for the ${OWNER} account (or a deploy key with write access)
  - Passphrase-protected key: start ssh-agent and run: ssh-add ${KEY_PATH}
EOF
    exit 1
  fi
fi

ssh_user="$(sed -n 's/^Hi \(.*\)!.*/\1/p' <<<"$auth_output" | head -1)"
echo "SSH authentication OK${ssh_user:+ (GitHub user: ${ssh_user})}"
if [ -n "$ssh_user" ] && [ "$ssh_user" != "$OWNER" ]; then
  echo "WARNING: authenticated as '${ssh_user}', expected '${OWNER}'." >&2
  echo "         Push will fail unless that account can write to ${SLUG}." >&2
fi

ssh_command="ssh"
for arg in "${SSH_ARGS[@]}"; do
  ssh_command+=" $(printf '%q' "$arg")"
done
export GIT_SSH_COMMAND="$ssh_command"

# ---------------------------------------------------------------------------
# Remote + push
# ---------------------------------------------------------------------------

if ! git remote get-url origin >/dev/null 2>&1; then
  git remote add origin "$SSH_URL"
fi

current_url="$(git remote get-url origin)"
if [ "$current_url" != "$SSH_URL" ]; then
  echo "Pointing origin at ${SSH_URL} (was: ${current_url})"
  git remote set-url origin "$SSH_URL"
fi

if [ -n "$(git status --porcelain)" ]; then
  echo "WARNING: uncommitted changes are not part of the push. Commit them if intended." >&2
fi

# Android CI only runs for build-related paths (see .github/workflows/android.yml).
CHANGED_FILES=""
if base_sha="$(git rev-parse --verify --quiet "origin/${BRANCH}" 2>/dev/null)"; then
  CHANGED_FILES="$(git diff --name-only "${base_sha}..HEAD" 2>/dev/null || true)"
fi
if [ -n "$CHANGED_FILES" ] && ! printf '%s\n' "$CHANGED_FILES" | grep -Eq \
  '^(app/|build\.gradle\.kts|settings\.gradle\.kts|gradle/|gradlew|gradle\.properties|ci/debug\.keystore|\.github/workflows/android\.yml)'; then
  EXPECT_CI=0
  echo "Note: these commits only touch non-build files, so Android CI will not run:"
  printf '%s\n' "$CHANGED_FILES" | sed 's/^/  /'
fi

if [ "$(git branch --show-current)" != "$BRANCH" ]; then
  echo "Current branch is '$(git branch --show-current || true)'; pushing HEAD to origin/${BRANCH}."
fi

echo "Pushing ${SHORT_SHA} to ${SLUG} (${BRANCH}) ..."
if ! git push -u origin "HEAD:refs/heads/${BRANCH}"; then
  cat >&2 <<EOF

Push failed. If the remote has commits you do not have locally:
  git pull --rebase origin ${BRANCH}
then re-run this script.
EOF
  exit 1
fi

remote_sha="$(git ls-remote origin "refs/heads/${BRANCH}" | awk 'NR==1 {print $1}' || true)"
if [ -z "$remote_sha" ]; then
  echo "WARNING: could not read back origin/${BRANCH} to verify the push." >&2
elif [ "$remote_sha" != "$SHA" ]; then
  echo "WARNING: origin/${BRANCH} is ${remote_sha:0:7}, expected ${SHA:0:7}." >&2
else
  echo "Remote branch verified: ${SHORT_SHA} is on origin/${BRANCH}."
fi

if [ "$VERIFY" -eq 0 ]; then
  echo "Skipped CI verification (--no-verify)."
elif [ "$EXPECT_CI" -eq 0 ]; then
  echo "Skipped CI verification: no build paths changed."
else
  verify_ci "$SHA"
fi
