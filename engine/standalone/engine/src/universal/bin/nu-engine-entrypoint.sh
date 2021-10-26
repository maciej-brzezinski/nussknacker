#!/usr/bin/env bash

NUSSKNACKER_DIR=`dirname "$0" | xargs -I{} readlink -f {}/..`
export STORAGE_DIR="$NUSSKNACKER_DIR/storage"
CONF_DIR="$NUSSKNACKER_DIR/conf"
LIB_DIR="$NUSSKNACKER_DIR/lib"
MODELS_DIR="$NUSSKNACKER_DIR/models"

CONFIG_FILE=${CONFIG_FILE-${2-"$CONF_DIR/nu-engine.conf"}}
LOG_FILE=${NUSSKNACKER_LOG_FILE-${3-"$CONF_DIR/docker-logback.xml"}}
APPLICATION_APP=${NUSSKNACKER_APPLICATION_APP-${4-"pl.touk.nussknacker.engine.standalone.NuEngineApp"}}

#
mkdir -p ${STORAGE_DIR}/logs
#
chmod -R ug+wr ${STORAGE_DIR}


#exec