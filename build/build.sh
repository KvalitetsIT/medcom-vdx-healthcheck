#!/bin/sh

# Build resource container and start it
docker build -t resources -f ./integrationtest/docker/Dockerfile-resources --no-cache ./integrationtest/docker
docker run -d --name medcom-vdx-healthcheck-resources resources

# Build inside docker container
docker run -v /var/run/docker.sock:/var/run/docker.sock  -v $HOME/.docker/config.json:/root/.docker/config.json -v $(pwd):/src -v $HOME/.m2:/root/.m2 --volumes-from medcom-vdx-healthcheck-resources  maven:3-ibm-semeru-21-jammy /src/build/maven.sh
