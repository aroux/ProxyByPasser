#! /bin/sh

BASEDIR=`dirname $0`/..
BASEDIR=`(cd "$BASEDIR"; pwd)`

APP=$1
shift
APPU=`echo $APP | sed 's/\([a-z]\)\([a-zA-Z0-9]*\)/\u\1\2/g'`
PID=`ps -fe | grep -v "grep" | grep java | grep proxybypasser | grep ${APP} | awk '{print $2}'`

if [ ! -z $PID ]; then
	echo "proxybypasser ${APP} is already running with pid ${PID}. Continue? [y/n]"
	read RESP
	if [ -z $RESP -o $RESP != 'y' ]; then
		echo "Exiting."
		exit 1
	fi
fi

EXTRA_JVM_ARGUMENTS=-Dlog4j.configuration=logging/log4j.${APP}.properties
CLASSPATH="${BASEDIR}/config:${BASEDIR}/lib/*"

if [ ! -d ${BASEDIR}/logs ]; then
	mkdir ${BASEDIR}/logs
	if [ $? -ne 0 ]; then
		echo "Impossible to create logs directory. Exiting."
		exit 1		
	fi
fi

exec java $JAVA_OPTS \
  $EXTRA_JVM_ARGUMENTS \
  -cp "$CLASSPATH" \
  -Dapp.name="proxybypasser_$APP" \
  -Dapp.pid="$$" \
  -Dbasedir="$BASEDIR" \
  com.proxy.bypasser.main.${APPU}Main \
  "$@"
