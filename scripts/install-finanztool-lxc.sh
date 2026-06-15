#!/usr/bin/env bash
set -Eeuo pipefail

# finanztool Proxmox-LXC Bootstrap Installer
#
# Zweck:
# - räumt eine alte finanztool-Installation gezielt auf
# - installiert benötigte Pakete
# - installiert eine aktuelle GitHub CLI aus dem offiziellen GitHub-CLI-Repository
# - legt App-User, Verzeichnisse, systemd-Service, Deploy-Script und Timer an
# - führt einen ersten verifizierten Deploy aus
#
# Sicherheit:
# - GitHub Token wird NICHT im Repository abgelegt
# - Token wird aus GH_TOKEN gelesen oder interaktiv abgefragt
# - Deploy akzeptiert nur Artefakte aus kruemelnerd/finanztool
# - SHA256 und GitHub Artifact Attestation müssen erfolgreich sein
# - App-User kann Release-Verzeichnis nicht beschreiben
# - Datenbank wird standardmäßig NICHT gelöscht
#
# Nutzung:
#   chmod +x scripts/install-finanztool-lxc.sh
#   sudo GH_TOKEN=github_pat_xxx ./scripts/install-finanztool-lxc.sh
#
# Alternativ ohne GH_TOKEN:
#   sudo ./scripts/install-finanztool-lxc.sh
#
# Optional destruktiv:
#   sudo GH_TOKEN=github_pat_xxx ./scripts/install-finanztool-lxc.sh --purge-data
#
# --purge-data löscht auch /var/lib/finanztool und /var/backups/finanztool.
# Für echte Finanzdaten nur verwenden, wenn du wirklich komplett neu anfangen willst.

APP_NAME="finanztool"
REPO="kruemelnerd/finanztool"
APP_USER="finanztool"
APP_GROUP="finanztool"

INSTALL_DIR="/opt/finanztool"
RELEASES_DIR="${INSTALL_DIR}/releases"
DATA_DIR="/var/lib/finanztool"
STATE_DIR="/var/lib/finanztool-deployer"
BACKUP_DIR="/var/backups/finanztool"
ENV_DIR="/etc/github-deploy"
ENV_FILE="${ENV_DIR}/finanztool.env"

DEPLOY_SCRIPT="/usr/local/bin/deploy-finanztool.sh"

APP_SERVICE="/etc/systemd/system/finanztool.service"
DEPLOY_SERVICE="/etc/systemd/system/finanztool-deploy.service"
DEPLOY_TIMER="/etc/systemd/system/finanztool-deploy.timer"

SERVER_PORT="8080"
PURGE_DATA="false"

for arg in "$@"; do
  case "$arg" in
    --purge-data)
      PURGE_DATA="true"
      ;;
    -h|--help)
      sed -n '1,45p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown argument: $arg"
      echo "Allowed: --purge-data"
      exit 1
      ;;
  esac
done

log() {
  echo
  echo "==> $*"
}

warn() {
  echo
  echo "WARN: $*" >&2
}

fail() {
  echo
  echo "ERROR: $*" >&2
  exit 1
}

require_root() {
  if [[ "${EUID}" -ne 0 ]]; then
    fail "Bitte als root ausführen, z. B. mit sudo."
  fi
}

require_systemd() {
  command -v systemctl >/dev/null 2>&1 || fail "systemctl nicht gefunden. Dieses Skript erwartet einen systemd-basierten LXC."
}

read_or_store_token() {
  log "GitHub Token prüfen"

  mkdir -p "$ENV_DIR"
  chmod 700 "$ENV_DIR"

  if [[ -n "${GH_TOKEN:-}" ]]; then
    echo "GH_TOKEN wurde aus der Umgebung gelesen."
  elif [[ -f "$ENV_FILE" ]]; then
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    if [[ -n "${GH_TOKEN:-}" ]]; then
      echo "GH_TOKEN wurde aus $ENV_FILE gelesen."
    fi
  fi

  if [[ -z "${GH_TOKEN:-}" ]]; then
    echo "Bitte GitHub Fine-grained PAT eingeben."
    echo "Benötigte Rechte für ${REPO}: Contents read-only, Metadata read-only."
    read -r -s -p "GH_TOKEN: " GH_TOKEN
    echo
  fi

  [[ -n "${GH_TOKEN:-}" ]] || fail "GH_TOKEN ist leer."

  cat > "$ENV_FILE" <<EOF
GH_TOKEN=${GH_TOKEN}
EOF

  chmod 600 "$ENV_FILE"
  chown root:root "$ENV_FILE"
}

cleanup_old_installation() {
  log "Alte finanztool-Installation gezielt aufräumen"

  systemctl stop finanztool-deploy.timer 2>/dev/null || true
  systemctl disable finanztool-deploy.timer 2>/dev/null || true

  systemctl stop finanztool-deploy.service 2>/dev/null || true
  systemctl reset-failed finanztool-deploy.service 2>/dev/null || true

  systemctl stop finanztool.service 2>/dev/null || true
  systemctl reset-failed finanztool.service 2>/dev/null || true

  rm -f "$APP_SERVICE" "$DEPLOY_SERVICE" "$DEPLOY_TIMER"
  rm -f "$DEPLOY_SCRIPT"

  rm -rf /tmp/finanztool-deploy
  rm -f /run/finanztool-deploy.lock

  if [[ "$PURGE_DATA" == "true" ]]; then
    warn "--purge-data aktiv: Daten, Backups und Releases werden gelöscht."
    rm -rf "$INSTALL_DIR" "$DATA_DIR" "$STATE_DIR" "$BACKUP_DIR"
  else
    echo "Daten bleiben erhalten: $DATA_DIR"
    echo "Backups bleiben erhalten: $BACKUP_DIR"
    echo "Installierte Releases bleiben erhalten: $INSTALL_DIR"
  fi

  systemctl daemon-reload
}

install_packages() {
  log "Basispakete installieren"

  apt-get update
  apt-get install -y \
    curl \
    ca-certificates \
    gpg \
    jq \
    git \
    unzip \
    sqlite3 \
    util-linux \
    openjdk-21-jre-headless

  log "Altes gh-Paket entfernen, falls vorhanden"

  if command -v gh >/dev/null 2>&1; then
    apt-get remove -y gh || true
    apt-get autoremove -y || true
  fi

  log "Offizielles GitHub-CLI-Repository einrichten"

  mkdir -p -m 755 /etc/apt/keyrings
  mkdir -p -m 755 /etc/apt/sources.list.d

  TMP_KEYRING="$(mktemp)"
  curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg -o "$TMP_KEYRING"

  # SHA256 laut offizieller GitHub-CLI-Linux-Installationsdokumentation.
  echo "6084d5d7bd8e288441e0e94fc6275570895da18e6751f70f057485dc2d1a811b  $TMP_KEYRING" | sha256sum -c -

  install -o root -g root -m 0644 "$TMP_KEYRING" /etc/apt/keyrings/githubcli-archive-keyring.gpg
  rm -f "$TMP_KEYRING"

  cat > /etc/apt/sources.list.d/github-cli.list <<EOF
deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main
EOF

  apt-get update
  apt-get install -y gh

  log "GitHub CLI prüfen"

  gh --version
  gh attestation --help >/dev/null
  gh attestation verify --help >/dev/null
}

create_user_and_directories() {
  log "User und Verzeichnisse anlegen"

  if ! id "$APP_USER" >/dev/null 2>&1; then
    useradd --system --home "$DATA_DIR" --shell /usr/sbin/nologin "$APP_USER"
  fi

  mkdir -p "$RELEASES_DIR" "$DATA_DIR" "$STATE_DIR" "$BACKUP_DIR"

  chown -R root:root "$INSTALL_DIR"
  chmod 755 "$INSTALL_DIR"
  chmod 755 "$RELEASES_DIR"

  chown -R "$APP_USER:$APP_GROUP" "$DATA_DIR"
  chmod 750 "$DATA_DIR"

  chown -R root:root "$STATE_DIR" "$BACKUP_DIR"
  chmod 750 "$STATE_DIR" "$BACKUP_DIR"
}

write_app_service() {
  log "systemd-Service für finanztool schreiben"

  cat > "$APP_SERVICE" <<EOF
[Unit]
Description=Finanztool
After=network-online.target
Wants=network-online.target

[Service]
User=${APP_USER}
Group=${APP_GROUP}
WorkingDirectory=${DATA_DIR}

Environment=SERVER_PORT=${SERVER_PORT}
Environment=SPRING_DATASOURCE_URL=jdbc:sqlite:${DATA_DIR}/finanzapp.db

ExecStart=/usr/bin/java -jar ${INSTALL_DIR}/current/app.jar

Restart=on-failure
RestartSec=5

NoNewPrivileges=true
PrivateTmp=true
ProtectHome=true
ProtectSystem=strict
ReadWritePaths=${DATA_DIR}

CapabilityBoundingSet=
LockPersonality=true
SystemCallArchitectures=native
RestrictAddressFamilies=AF_INET AF_INET6 AF_UNIX

[Install]
WantedBy=multi-user.target
EOF
}

write_deploy_script() {
  log "Deploy-Script schreiben"

  cat > "$DEPLOY_SCRIPT" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail

REPO="kruemelnerd/finanztool"
SERVICE="finanztool.service"

INSTALL_DIR="/opt/finanztool"
RELEASES_DIR="${INSTALL_DIR}/releases"
STATE_DIR="/var/lib/finanztool-deployer"
DATA_DIR="/var/lib/finanztool"
BACKUP_DIR="/var/backups/finanztool"
TMP_BASE="/tmp/finanztool-deploy"
LOCK_FILE="/run/finanztool-deploy.lock"

mkdir -p "$RELEASES_DIR" "$STATE_DIR" "$BACKUP_DIR" "$TMP_BASE"

exec 9>"$LOCK_FILE"
flock -n 9 || exit 0

export GH_TOKEN="${GH_TOKEN:?GH_TOKEN is missing}"

TAG="$(
  gh release view \
    --repo "$REPO" \
    --json tagName,isDraft,isPrerelease \
    --jq 'select(.isDraft == false and .isPrerelease == false) | .tagName'
)"

if [[ -z "$TAG" ]]; then
  echo "No stable release found."
  exit 1
fi

if [[ -f "$STATE_DIR/current-tag" ]] && [[ "$(cat "$STATE_DIR/current-tag")" == "$TAG" ]]; then
  echo "Already deployed: $TAG"
  exit 0
fi

WORKDIR="$(mktemp -d "$TMP_BASE/$TAG.XXXXXX")"
trap 'rm -rf "$WORKDIR"' EXIT

echo "Downloading $REPO $TAG"

gh release download "$TAG" \
  --repo "$REPO" \
  --pattern '*.jar' \
  --pattern '*checksum*' \
  --pattern '*checksums*' \
  --pattern 'checksums_sha256.txt' \
  --dir "$WORKDIR" \
  --clobber

JAR_COUNT="$(find "$WORKDIR" -maxdepth 1 -type f -name '*.jar' | wc -l)"
if [[ "$JAR_COUNT" -ne 1 ]]; then
  echo "Expected exactly one JAR, found $JAR_COUNT"
  find "$WORKDIR" -maxdepth 1 -type f -print
  exit 1
fi

JAR="$(find "$WORKDIR" -maxdepth 1 -type f -name '*.jar' | head -n1)"
JAR_NAME="$(basename "$JAR")"

CHECKSUM_FILE="$(
  find "$WORKDIR" -maxdepth 1 -type f \
    \( -name 'checksums_sha256.txt' -o -name '*checksum*' -o -name '*checksums*' \) \
    | sort \
    | head -n1
)"

if [[ -z "$CHECKSUM_FILE" || ! -f "$CHECKSUM_FILE" ]]; then
  echo "Missing checksum file."
  find "$WORKDIR" -maxdepth 1 -type f -print
  exit 1
fi

echo "Verifying SHA256 checksum"

CHECKSUM_LINE="$(grep -F "$JAR_NAME" "$CHECKSUM_FILE" | head -n1 || true)"
if [[ -z "$CHECKSUM_LINE" ]]; then
  echo "Checksum file does not contain entry for $JAR_NAME"
  cat "$CHECKSUM_FILE"
  exit 1
fi

(
  cd "$WORKDIR"
  printf '%s\n' "$CHECKSUM_LINE" | sha256sum -c -
)

echo "Verifying GitHub artifact attestation"

gh attestation verify "$JAR" \
  --repo "$REPO" \
  --signer-workflow "kruemelnerd/finanztool/.github/workflows/build.yml" \
  --source-ref "refs/heads/main" \
  --deny-self-hosted-runners

PREVIOUS_TARGET="$(readlink -f "$INSTALL_DIR/current" || true)"
NEW_RELEASE_DIR="$RELEASES_DIR/$TAG"

if [[ -f "$DATA_DIR/finanzapp.db" ]]; then
  echo "Creating SQLite backup"
  sqlite3 "$DATA_DIR/finanzapp.db" ".backup '$BACKUP_DIR/finanzapp-before-$TAG.db'"
fi

echo "Installing $TAG"

install -d -o root -g root -m 0755 "$NEW_RELEASE_DIR"
install -o root -g root -m 0644 "$JAR" "$NEW_RELEASE_DIR/app.jar"

ln -sfn "$NEW_RELEASE_DIR" "$INSTALL_DIR/current.new"
mv -Tf "$INSTALL_DIR/current.new" "$INSTALL_DIR/current"

rollback() {
  echo "Deployment failed. Rolling back."
  if [[ -n "${PREVIOUS_TARGET:-}" && -d "$PREVIOUS_TARGET" ]]; then
    ln -sfn "$PREVIOUS_TARGET" "$INSTALL_DIR/current.new"
    mv -Tf "$INSTALL_DIR/current.new" "$INSTALL_DIR/current"
    systemctl restart "$SERVICE" || true
  fi
}

echo "Restarting $SERVICE"

if ! systemctl restart "$SERVICE"; then
  rollback
  exit 1
fi

echo "Running smoke test"

for i in {1..30}; do
  if curl -fsS -o /dev/null "http://127.0.0.1:8080/login"; then
    echo "$TAG" > "$STATE_DIR/current-tag"
    echo "Deployment successful: $TAG"
    exit 0
  fi

  if curl -fsS -o /dev/null "http://127.0.0.1:8080/"; then
    echo "$TAG" > "$STATE_DIR/current-tag"
    echo "Deployment successful: $TAG"
    exit 0
  fi

  sleep 1
done

rollback
exit 1
EOF

  chmod 750 "$DEPLOY_SCRIPT"
  chown root:root "$DEPLOY_SCRIPT"
}

write_deploy_units() {
  log "systemd Deploy-Service und Timer schreiben"

  cat > "$DEPLOY_SERVICE" <<EOF
[Unit]
Description=Deploy latest verified Finanztool release

[Service]
Type=oneshot
EnvironmentFile=${ENV_FILE}
ExecStart=${DEPLOY_SCRIPT}
EOF

  cat > "$DEPLOY_TIMER" <<EOF
[Unit]
Description=Check for new Finanztool release every 15 minutes

[Timer]
OnBootSec=2min
OnUnitActiveSec=15min
RandomizedDelaySec=60s

[Install]
WantedBy=timers.target
EOF

  systemctl daemon-reload
}

test_github_access() {
  log "GitHub-Zugriff testen"

  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a

  gh release view --repo "$REPO" --json tagName --jq '.tagName'
}

enable_and_first_deploy() {
  log "Deploy-Timer aktivieren"

  systemctl enable --now finanztool-deploy.timer

  log "Ersten verifizierten Deploy ausführen"

  systemctl start finanztool-deploy.service

  log "Status prüfen"

  systemctl status finanztool-deploy.service --no-pager || true
  systemctl status finanztool.service --no-pager || true

  echo
  echo "Letzte Deploy-Logs:"
  journalctl -u finanztool-deploy.service -n 120 --no-pager || true
}

print_summary() {
  echo
  echo "============================================================"
  echo "finanztool Installation abgeschlossen"
  echo "============================================================"
  echo
  echo "Wichtige Befehle:"
  echo
  echo "  systemctl status finanztool"
  echo "  journalctl -u finanztool -n 100 --no-pager"
  echo "  journalctl -u finanztool-deploy.service -n 100 --no-pager"
  echo "  systemctl list-timers '*finanztool*'"
  echo
  echo "Manueller Deploy:"
  echo
  echo "  systemctl start finanztool-deploy.service"
  echo
  echo "Lokaler Test:"
  echo
  echo "  curl -I http://127.0.0.1:${SERVER_PORT}/login"
  echo
  echo "Datenverzeichnis:"
  echo
  echo "  ${DATA_DIR}"
  echo
  echo "Backups:"
  echo
  echo "  ${BACKUP_DIR}"
  echo
}

main() {
  require_root
  require_systemd

  read_or_store_token
  cleanup_old_installation
  install_packages
  create_user_and_directories
  write_app_service
  write_deploy_script
  write_deploy_units
  test_github_access
  enable_and_first_deploy
  print_summary
}

main "$@"