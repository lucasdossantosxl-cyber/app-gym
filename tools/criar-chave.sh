#!/bin/sh
# Cria a chave que assina o APK de release do Meu Treino. Rode uma única vez.
#
#   sh tools/criar-chave.sh            cria a chave e o keystore.properties local
#   sh tools/criar-chave.sh --github   além disso, grava os secrets no repositório com o GitHub CLI
#
# A chave fica em ~/.meutreino/, fora do Git. Guarde uma cópia desse arquivo e da senha em um gerenciador
# de senhas: sem eles não dá para publicar atualizações que instalem por cima da versão anterior.
set -eu

raiz="$(cd "$(dirname "$0")/.." && pwd)"
pasta="$HOME/.meutreino"
chave="$pasta/meutreino-release.jks"
apelido="meutreino"

command -v keytool >/dev/null || { echo "keytool não encontrado. Instale um JDK 17."; exit 1; }
command -v openssl >/dev/null || { echo "openssl não encontrado."; exit 1; }
if [ "${1:-}" = "--github" ]; then
    command -v gh >/dev/null || { echo "GitHub CLI (gh) não encontrado."; exit 1; }
fi
if [ -e "$chave" ]; then
    echo "Já existe uma chave em $chave. Nada foi alterado."
    echo "Trocar de chave obriga quem já instalou o app a desinstalar e instalar de novo."
    exit 1
fi

umask 077
mkdir -p "$pasta"
SENHA="$(openssl rand -base64 48 | tr -d '/+=\n' | cut -c1-32)"
export SENHA

keytool -genkeypair -keystore "$chave" -storetype PKCS12 -alias "$apelido" \
    -keyalg RSA -keysize 4096 -validity 10950 -dname "CN=Meu Treino" \
    -storepass:env SENHA -keypass:env SENHA >/dev/null 2>&1

# impressão digital do certificado, no formato que o apksigner mostra (hexadecimal minúsculo, sem dois-pontos)
digital="$(keytool -J-Duser.language=en -list -v -keystore "$chave" -alias "$apelido" -storepass:env SENHA |
    sed -n 's/.*SHA256: //p' | head -1 | tr -d ': ' | tr 'A-F' 'a-f')"
[ -n "$digital" ] || { echo "Não foi possível ler a impressão digital da chave."; exit 1; }

printf '%s\n' "$digital" > "$raiz/signing-cert.sha256"
cat > "$raiz/keystore.properties" <<EOF
MEUTREINO_KEYSTORE=$chave
MEUTREINO_KEYSTORE_PASSWORD=$SENHA
MEUTREINO_KEY_ALIAS=$apelido
MEUTREINO_KEY_PASSWORD=$SENHA
EOF
chmod 600 "$raiz/keystore.properties"

echo "Chave criada em $chave"
echo "Impressão digital (SHA-256): $digital"
echo "A senha está em keystore.properties (fora do Git). Guarde a chave e a senha em um gerenciador de senhas."
echo

if [ "${1:-}" = "--github" ]; then
    cd "$raiz"
    base64 < "$chave" | tr -d '\n' | gh secret set ANDROID_KEYSTORE_BASE64
    printf '%s' "$SENHA" | gh secret set ANDROID_KEYSTORE_PASSWORD
    printf '%s' "$apelido" | gh secret set ANDROID_KEY_ALIAS
    printf '%s' "$SENHA" | gh secret set ANDROID_KEY_PASSWORD
    echo "Secrets gravados no repositório. Faça commit de signing-cert.sha256 e envie para a branch main."
else
    echo "Para o GitHub Actions publicar o APK assinado, grave os secrets do repositório:"
    echo "  base64 < \"$chave\" | tr -d '[:space:]' | gh secret set ANDROID_KEYSTORE_BASE64"
    echo "  gh secret set ANDROID_KEYSTORE_PASSWORD   (cole MEUTREINO_KEYSTORE_PASSWORD de keystore.properties)"
    echo "  gh secret set ANDROID_KEY_PASSWORD        (a mesma senha)"
    echo "  printf meutreino | gh secret set ANDROID_KEY_ALIAS"
    echo "Depois faça commit de signing-cert.sha256."
fi
