#!/bin/sh

echo "${GITHUB_REPOSITORY}"
echo "${DOCKER_SERVICE}"
if [ "${GITHUB_REPOSITORY}" != "KvalitetsIT/medcom-vdx-healthcheck" ] && [ "${DOCKER_SERVICE}" = "kvalitetsit/medcom-vdx-healthcheck" ]; then
  echo "Please run setup.sh REPOSITORY_NAME"
  exit 1
fi
