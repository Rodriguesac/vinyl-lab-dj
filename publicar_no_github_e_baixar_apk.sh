#!/data/data/com.termux/files/usr/bin/bash
# Vinyl Lab DJ — publicação automática via Termux + GitHub Actions
# Uso: bash publicar_no_github_e_baixar_apk.sh [nome-do-repo] [private|public]
set -Eeuo pipefail

REPO_NAME="${1:-vinyl-lab-dj}"
VISIBILITY="${2:-private}"
WORKFLOW_FILE="android-debug.yml"
ARTIFACT_NAME="VinylLab-DJ-debug-apk"
PROJECT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
APK_DIR="$PROJECT_DIR/APK_GERADO"

red() { printf '\n\033[1;31m%s\033[0m\n' "$*"; }
green() { printf '\n\033[1;32m%s\033[0m\n' "$*"; }
yellow() { printf '\n\033[1;33m%s\033[0m\n' "$*"; }
info() { printf '\n\033[1;36m%s\033[0m\n' "$*"; }

fail() {
  red "ERRO: $*"
  exit 1
}

case "$VISIBILITY" in
  private|public) ;;
  *) fail "A visibilidade precisa ser private ou public." ;;
esac

case "$REPO_NAME" in
  ""|*[!a-zA-Z0-9._-]*) fail "Nome de repositório inválido: $REPO_NAME" ;;
esac

command -v git >/dev/null 2>&1 || fail "Git não encontrado. Rode: pkg install git"
command -v gh >/dev/null 2>&1 || fail "GitHub CLI não encontrado. Rode: pkg install gh"

cd "$PROJECT_DIR"
[ -f ".github/workflows/$WORKFLOW_FILE" ] || fail "Workflow .github/workflows/$WORKFLOW_FILE não encontrado."

info "1/5 — Conferindo a conta do GitHub"
if ! gh auth status -h github.com >/dev/null 2>&1; then
  yellow "Sua conta ainda não está autenticada no GitHub CLI."
  yellow "Quando abrir o navegador, entre na sua conta e autorize o GitHub CLI."
  gh auth login -h github.com -p https -w
fi

gh auth status -h github.com >/dev/null || fail "Não foi possível autenticar no GitHub."
OWNER="$(gh api user --jq '.login')"
USER_ID="$(gh api user --jq '.id')"
FULL_REPO="$OWNER/$REPO_NAME"
info "Conta: $OWNER | Repositório: $FULL_REPO"

info "2/5 — Preparando o Git"
if [ ! -d .git ]; then
  git init -b main
else
  git branch -M main
fi

if ! git config user.name >/dev/null; then
  git config user.name "$OWNER"
fi
if ! git config user.email >/dev/null; then
  git config user.email "$USER_ID+$OWNER@users.noreply.github.com"
fi

git add -A
if ! git diff --cached --quiet; then
  git commit -m "Vinyl Lab DJ — publicação automática"
else
  yellow "Não havia arquivos novos para commit; vou usar o último commit existente."
fi

info "3/5 — Criando ou atualizando o repositório"
if gh repo view "$FULL_REPO" >/dev/null 2>&1; then
  yellow "O repositório já existe. Atualizando a branch main sem apagar nada remoto."
  if git remote get-url origin >/dev/null 2>&1; then
    git remote set-url origin "https://github.com/$FULL_REPO.git"
  else
    git remote add origin "https://github.com/$FULL_REPO.git"
  fi
  git push -u origin main
else
  gh repo create "$FULL_REPO" "--$VISIBILITY" --source=. --remote=origin --push
fi

COMMIT_SHA="$(git rev-parse HEAD)"
info "4/5 — Encontrando a compilação do APK no GitHub Actions"
RUN_ID=""
for tentativa in $(seq 1 24); do
  RUN_ID="$(gh run list -R "$FULL_REPO" --workflow "$WORKFLOW_FILE" --commit "$COMMIT_SHA" --limit 1 --json databaseId --jq '.[0].databaseId' 2>/dev/null || true)"
  if [ -n "$RUN_ID" ]; then
    break
  fi
  printf 'Aguardando o GitHub registrar a compilação (%s/24)...\n' "$tentativa"
  sleep 5
done

if [ -z "$RUN_ID" ]; then
  yellow "A execução automática ainda não apareceu. Disparando o workflow manualmente."
  gh workflow run "$WORKFLOW_FILE" -R "$FULL_REPO"
  for tentativa in $(seq 1 24); do
    RUN_ID="$(gh run list -R "$FULL_REPO" --workflow "$WORKFLOW_FILE" --branch main --limit 1 --json databaseId --jq '.[0].databaseId' 2>/dev/null || true)"
    if [ -n "$RUN_ID" ]; then
      break
    fi
    printf 'Aguardando o GitHub iniciar a compilação (%s/24)...\n' "$tentativa"
    sleep 5
  done
fi

[ -n "$RUN_ID" ] || fail "O GitHub não iniciou a Action. Confira a aba Actions do repositório: https://github.com/$FULL_REPO/actions"

info "Compilação encontrada: #$RUN_ID"
if ! gh run watch "$RUN_ID" -R "$FULL_REPO" --compact --exit-status; then
  red "A compilação falhou. Vou mostrar o log das etapas que falharam:"
  gh run view "$RUN_ID" -R "$FULL_REPO" --log-failed || true
  exit 1
fi

info "5/5 — Baixando o APK gerado"
rm -rf "$APK_DIR"
mkdir -p "$APK_DIR"
gh run download "$RUN_ID" -R "$FULL_REPO" --name "$ARTIFACT_NAME" --dir "$APK_DIR"
APK_FILE="$(find "$APK_DIR" -type f -name '*.apk' | head -n 1 || true)"
[ -n "$APK_FILE" ] || fail "A Action terminou, mas nenhum APK foi encontrado no artefato baixado."

green "PRONTO! APK baixado em:"
printf '%s\n' "$APK_FILE"
printf '\nRepositório: https://github.com/%s\n' "$FULL_REPO"
printf 'Actions:      https://github.com/%s/actions\n' "$FULL_REPO"
