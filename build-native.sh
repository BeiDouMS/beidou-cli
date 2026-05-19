#!/usr/bin/env bash
set -euo pipefail

echo "============================================================"
echo "  beidou native-image build script"
echo "============================================================"

# === 1. Locate GraalVM JDK =============================================
find_graalvm() {
    # User override
    if [ -n "${GRAALVM_HOME:-}" ]; then
        echo "$GRAALVM_HOME"
        return
    fi

    # Check current JAVA_HOME
    if [ -n "${JAVA_HOME:-}" ]; then
        if "$JAVA_HOME/bin/java" -version 2>&1 | grep -q "GraalVM"; then
            echo "$JAVA_HOME"
            return
        fi
    fi

    # Scan common locations
    for base in /usr/lib/jvm /usr/local/lib /opt "$HOME/.sdkman/candidates/java" /Library/Java/JavaVirtualMachines; do
        if [ -d "$base" ]; then
            for d in "$base"/graalvm-*; do
                if [ -d "$d" ] && [ -x "$d/bin/java" ]; then
                    if "$d/bin/java" -version 2>&1 | grep -q "GraalVM"; then
                        echo "$d"
                        return
                    fi
                fi
            done
        fi
    done

    # Check sibling of current JAVA_HOME
    if [ -n "${JAVA_HOME:-}" ]; then
        parent="$(dirname "$JAVA_HOME")"
        for d in "$parent"/graalvm-*; do
            if [ -d "$d" ] && [ -x "$d/bin/java" ]; then
                if "$d/bin/java" -version 2>&1 | grep -q "GraalVM"; then
                    echo "$d"
                    return
                fi
            fi
        done
    fi
}

JAVA_HOME=$(find_graalvm)
if [ -z "$JAVA_HOME" ]; then
    echo ""
    echo "Could not find GraalVM JDK automatically."
    echo "Common locations: /usr/lib/jvm/graalvm-*, /opt/graalvm-*, ~/.sdkman/"
    read -r -p "Enter GraalVM JDK path: " JAVA_HOME
    if [ ! -x "$JAVA_HOME/bin/java" ]; then
        echo "[FAIL] java executable not found in $JAVA_HOME/bin"
        exit 1
    fi
fi
echo "[OK] JAVA_HOME = $JAVA_HOME"

# Ensure native-image is on PATH
PATH="$JAVA_HOME/bin:$JAVA_HOME/lib/svm/bin:$PATH"
export PATH JAVA_HOME

if ! command -v native-image > /dev/null 2>&1; then
    echo "[FAIL] native-image not found. Install with: gu install native-image"
    exit 1
fi

# === 2. Check for C toolchain =========================================
UNAME_S=$(uname -s)
if [ "$UNAME_S" = "Linux" ]; then
    if command -v gcc > /dev/null 2>&1 || command -v cc > /dev/null 2>&1; then
        echo "[OK] C compiler found (Linux)"
    else
        echo "[FAIL] Need GCC or clang. Install with: apt install build-essential"
        exit 1
    fi
elif [ "$UNAME_S" = "Darwin" ]; then
    if command -v xcrun > /dev/null 2>&1; then
        echo "[OK] C compiler found (macOS)"
    else
        echo "[FAIL] Need Xcode Command Line Tools. Install with: xcode-select --install"
        exit 1
    fi
fi

# === 3. Build =========================================================
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
echo ""
echo "Building native image for beidou..."
echo ""

mvn -f "$SCRIPT_DIR/pom.xml" package -DskipTests -Pnative

echo ""
echo "============================================================"
echo "  Build successful: $SCRIPT_DIR/target/beidou"
echo "============================================================"
