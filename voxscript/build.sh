#!/bin/bash
set -e

echo "Building VoxScript..."

CORE_SRC=$(find core/src/main -name "*.java")
RENDERER_SRC=$(find renderer/src/main -name "*.java")
CLI_SRC=$(find cli/src/main -name "*.java")
SERVER_SRC=$(find server/src/main -name "*.java")

mkdir -p build/core/classes build/renderer/classes build/cli/classes build/server/classes dist

echo "  Compiling core..."
javac --release 21 -d build/core/classes $CORE_SRC

echo "  Compiling renderer..."
javac --release 21 -cp build/core/classes -d build/renderer/classes $RENDERER_SRC

echo "  Compiling cli..."
javac --release 21 -cp "build/core/classes:build/renderer/classes" -d build/cli/classes $CLI_SRC

echo "  Compiling server..."
javac --release 21 -cp "build/core/classes:build/renderer/classes" -d build/server/classes $SERVER_SRC

echo "  Building fat JAR..."
jar --create --file=dist/vox.jar \
    --main-class=org.voxscript.cli.VoxCli \
    -C build/core/classes . \
    -C build/renderer/classes . \
    -C build/cli/classes . \
    -C build/server/classes .

echo ""
echo "✓ Built: dist/vox.jar"
echo ""
echo "Usage:"
echo "  java -jar dist/vox.jar help"
echo "  java -jar dist/vox.jar init my-project"
echo "  java -jar dist/vox.jar build doc.vox"
