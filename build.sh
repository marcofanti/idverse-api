#!/bin/bash
# Builds idverse-api and installs the JAR into idverse/local-repo.
# Run this from the idverse-api project root after making library changes.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
IDVERSE_DIR="$SCRIPT_DIR/../idverse"

if [ ! -d "$IDVERSE_DIR" ]; then
    echo "ERROR: idverse directory not found at $IDVERSE_DIR"
    exit 1
fi

echo "==> Building idverse-api..."
mvn clean package -DskipTests

JAR="$SCRIPT_DIR/target/idverse-api-1.0-SNAPSHOT.jar"

echo "==> Installing JAR into $IDVERSE_DIR/local-repo..."
mvn install:install-file \
    -Dfile="$JAR" \
    -DgroupId=org.itnaf \
    -DartifactId=idverse-api \
    -Dversion=1.0-SNAPSHOT \
    -Dpackaging=jar \
    -DlocalRepositoryPath="$IDVERSE_DIR/local-repo"

echo ""
echo "Done. To commit the updated library:"
echo "  cd $IDVERSE_DIR"
echo "  git add local-repo/"
echo "  git commit -m 'Update idverse-api library JAR'"
