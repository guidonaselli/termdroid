#!/usr/bin/env bash
# Verifica Termdroid contra el device conectado y da un veredicto.
#
#   bash verify-on-device.sh
set -uo pipefail

SDK="${ANDROID_HOME:-D:/Android/Sdk}"
ADB="$SDK/platform-tools/adb"
[ -x "$ADB" ] || ADB="$SDK/platform-tools/adb.exe"
RAIZ="$(cd "$(dirname "$0")" && pwd)"

fallos=0
paso() { echo "  OK   $1"; }
falla() { echo "  FALLA $1"; fallos=$((fallos + 1)); }

echo "== device =="
DEVICES=$("$ADB" devices | tail -n +2 | grep -c "device$")
if [ "$DEVICES" -eq 0 ]; then
  echo "No hay ningun device conectado."
  echo "Conecta el telefono por depuracion inalambrica o USB y volve a correr esto."
  exit 1
fi

"$ADB" shell getprop ro.product.manufacturer | tr -d '\r' | sed 's/^/  fabricante: /'
"$ADB" shell getprop ro.product.model        | tr -d '\r' | sed 's/^/  modelo:     /'
"$ADB" shell getprop ro.build.version.release | tr -d '\r' | sed 's/^/  android:    /'
"$ADB" shell getprop ro.product.cpu.abi      | tr -d '\r' | sed 's/^/  abi:        /'
"$ADB" shell getenforce                      | tr -d '\r' | sed 's/^/  selinux:    /'

echo
echo "== suite completa =="
if (cd "$RAIZ" && ./gradlew testDebugUnitTest connectedDebugAndroidTest --no-daemon -q >/dev/null 2>&1); then
  paso "tests unitarios e instrumentados"
else
  falla "la suite no paso; corre ./gradlew connectedDebugAndroidTest para ver el detalle"
fi

echo
echo "== gate de ejecucion en este device =="
"$ADB" logcat -c
(cd "$RAIZ" && ./gradlew :probe:connectedDebugAndroidTest --no-daemon -q >/dev/null 2>&1)
VEREDICTO=$("$ADB" logcat -d -s TermdroidProbe 2>/dev/null | tr -d '\r' | sed 's/^.*TermdroidProbe: //')

if [ -n "$VEREDICTO" ]; then
  echo "$VEREDICTO" | sed 's/^/  /'
  echo "$VEREDICTO" | grep -q "backend  *= NONE" && falla "ningun backend de ejecucion sirve" || paso "hay backend de ejecucion"
  echo "$VEREDICTO" | grep -q "exec linker  *= true" \
    && paso "nivel 2 disponible: se pueden instalar paquetes" \
    || echo "  nota: sin nivel 2, la app anda con el set fijo de binarios"
else
  falla "el probe no dejo veredicto en logcat"
fi

echo
echo "== la app instala y abre =="
if (cd "$RAIZ" && ./gradlew :app:installDebug --no-daemon -q >/dev/null 2>&1); then
  paso "instalada"
  "$ADB" logcat -c
  "$ADB" shell am start -n com.termdroid/.MainActivity >/dev/null 2>&1
  sleep 6
  if [ -z "$("$ADB" logcat -d -b crash 2>/dev/null | grep -i termdroid)" ]; then
    paso "abre sin crashear"
  else
    falla "crashea al abrir"
  fi
else
  falla "no se pudo instalar"
fi

echo
if [ "$fallos" -eq 0 ]; then
  echo "TODO OK en este device."
  echo "Falta un solo paso manual: abrir la app, poner la clave de la API y pedirle algo."
else
  echo "$fallos verificacion(es) fallaron."
fi
exit "$fallos"
