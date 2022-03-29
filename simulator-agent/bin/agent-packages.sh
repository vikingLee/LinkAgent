#!/bin/bash

# simulator's target dir
AGENT_TARGET_DIR=../target/simulator-agent

# exit shell with err_code
# $1 : err_code
# $2 : err_msg
exit_on_err() {
  [[ ! -z "${2}" ]] && echo "${2}" 1>&2
  exit ${1}
}

# maven package the simulator
if [ $# != 0 ]; then
    mvn clean install -P $1 -Dmaven.test.skip=true -f ../pom.xml ||
      exit_on_err 1 "package agent failed."
else
    mvn clean install -Dmaven.test.skip=true -f ../pom.xml ||
      exit_on_err 1 "package agent failed."
fi

if [ ! -x "../target" ]; then
    mkdir "../target"
fi

if [ ! -x "$AGENT_TARGET_DIR" ]; then
    mkdir "$AGENT_TARGET_DIR"
fi

if [ ! -x "$AGENT_TARGET_DIR/config" ]; then
    mkdir "$AGENT_TARGET_DIR/config"
fi

if [ ! -x "$AGENT_TARGET_DIR/core" ]; then
    mkdir "$AGENT_TARGET_DIR/core"
fi

if [ ! -x "$AGENT_TARGET_DIR/bootstrap" ]; then
    mkdir "$AGENT_TARGET_DIR/bootstrap"
fi

if [ ! -x "$AGENT_TARGET_DIR/bin" ]; then
    mkdir "$AGENT_TARGET_DIR/bin"
fi

if [ ! -x "$AGENT_TARGET_DIR/bin/linux" ]; then
    mkdir "$AGENT_TARGET_DIR/bin/linux"
fi

if [ ! -x "$AGENT_TARGET_DIR/bin/windows" ]; then
    mkdir "$AGENT_TARGET_DIR/bin/windows"
fi

if [ ! -x "$AGENT_TARGET_DIR/logs" ]; then
    mkdir "$AGENT_TARGET_DIR/logs"
fi

cp *.properties ${AGENT_TARGET_DIR}/config/
cp simulator-agent-logback.xml ${AGENT_TARGET_DIR}/config/simulator-agent-logback.xml
cp ignore.config ${AGENT_TARGET_DIR}/config/ignore.config
cp linux/* ${AGENT_TARGET_DIR}/bin/linux
cp windows/* ${AGENT_TARGET_DIR}/bin/windows

# reset the target dir
AGENT_VERSION=$(cat ..//simulator-agent-core/target/classes/com/shulie/instrument/simulator/agent/version)
# copy jar to TARGET_DIR
cp ../simulator-launcher-standalone/target/simulator-launcher-standalone-*-jar-with-dependencies.jar ${AGENT_TARGET_DIR}/simulator-launcher-standalone.jar
cp ../simulator-launcher-instrument/target/simulator-launcher-instrument-*-jar-with-dependencies.jar ${AGENT_TARGET_DIR}/simulator-launcher-instrument.jar
cp ../simulator-launcher-embedded/target/simulator-launcher-embedded-*-jar-with-dependencies.jar ${AGENT_TARGET_DIR}/simulator-launcher-embedded.jar
cp ../simulator-launcher-lite/target/simulator-launcher-lite-*-jar-with-dependencies.jar ${AGENT_TARGET_DIR}/simulator-launcher-lite.jar
cp ../simulator-agent-core/target/simulator-agent-core-*-jar-with-dependencies.jar ${AGENT_TARGET_DIR}/core/simulator-agent-core.jar
cp ../simulator-bootstrap-extras/target/lib/* ${AGENT_TARGET_DIR}/bootstrap/

# zip the simulator.zip
cd ../target/
zip -r simulator-agent-${AGENT_VERSION}-bin.zip simulator-agent/
cd -

# tar the simulator.tar
cd ../target/simulator-agent
current=`date "+%Y%m%d%H%M%S"`
touch $current.tag
cd ..
tar -zcvf simulator-agent-${AGENT_VERSION}-bin.tar simulator-agent/
cd -

# release stable version
cp ../simulator-agent-${AGENT_VERSION}-bin.zip ../simulator-agent-stable-bin.zip
cp ../simulator-agent-${AGENT_VERSION}-bin.tar ../simulator-agent-stable-bin.tar

echo "package simulator-${AGENT_VERSION}-bin.zip finish."
