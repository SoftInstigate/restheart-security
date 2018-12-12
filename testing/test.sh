#!/bin/bash
set -e

echo "###### test.sh #####"

CD=$PWD
SD="$( cd "$(dirname ${BASH_SOURCE[0]})" ; pwd -P )"

echo "###### start test stack"

cd $SD
docker-compose up -d

echo "###### cloning RESTHeart integration test suite"

TMPDIR=`mktemp -d`

echo "temp dir  ->" $TMPDIR

# this makes sure that temporary dir is removed and test stack is cleaned up on exit
trap "rm -rf $TMPDIR; echo '###### stop test stack'; cd $SD; docker-compose down; cd $CD" EXIT

cd $TMPDIR
git clone --depth=1 git@github.com:SoftInstigate/restheart.git
cd restheart

echo "###### run RESTHeart integration test suite"

mvn -Dtest="**/*IT.java" package surefire:test