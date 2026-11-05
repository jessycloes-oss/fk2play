#!/usr/bin/env bash
# Compile et execute les tests du protocole (Java pur, sans Android ni materiel).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d)"
javac --release 8 -d "$OUT" "$ROOT"/app/src/main/java/com/fk2play/protocol/*.java "$ROOT"/test/com/fk2play/protocol/ProtocolTest.java 2>&1 | grep -v "warning" || true
java -cp "$OUT" com.fk2play.protocol.ProtocolTest
