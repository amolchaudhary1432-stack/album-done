#!/bin/sh
# Locks a file so only Claude can open it: a fresh random key encrypts the file (AES-256), and the
# key itself is encrypted with Claude's public key (tools/realphotos/claude_pub.pem); the private key
# never leaves Claude's machine. No secret is needed on GitHub.
#   sh tools/realphotos/lock.sh IN OUT.locked
set -e
in="$1"; out="$2"
dir=$(mktemp -d)
openssl rand -hex 32 | tr -d '\n' > "$dir/pass"
openssl enc -aes-256-cbc -pbkdf2 -iter 100000 -salt -pass "file:$dir/pass" -in "$in" -out "$dir/data"
openssl pkeyutl -encrypt -pubin -inkey "$(dirname "$0")/claude_pub.pem" \
  -pkeyopt rsa_padding_mode:oaep -pkeyopt rsa_oaep_md:sha256 -in "$dir/pass" -out "$dir/key"
tar -cf "$out" -C "$dir" key data
rm -rf "$dir"
