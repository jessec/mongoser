#!/bin/sh
# start up

# java options (like max memory it can use)
JAVA_OPTS="-Xms125m -Xmx512m"

JAVA_LOG_OPTS="-Djava.util.logging.config.file=config/logging.properties"

fg=0

if [ $# -eq 0 ] ; then
  echo "Usage $0 [start|stop] [-fg]"
  exit
fi

_umask=0022
umask $_umask

# resolve links - $0 may be a softlink
ARG0="$0"
while [ -h "$ARG0" ]; do
  ls=`ls -ld "$ARG0"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    ARG0="$link"
  else
    ARG0="`dirname $ARG0`/$link"
  fi
done

DIRNAME="`dirname $ARG0`"
PROGRAM="`basename $ARG0`"

if [ -z "$JAVA_HOME" ] ; then
  echo "JAVA_HOME not set"
  if java -version> /dev/null 2>&1 ; then
    JAVA=`which java`
  else
    echo "Please set JAVA_HOME"
    exit
  fi
else
  JAVA="$JAVA_HOME/bin/java"
fi

ulimit -n `ulimit -H -n`

cp=""
for f in `ls lib/*jar` ; do cp=$cp:$f ; done
if [ -d build/classes ] ; then
  cp=build/classes:$cp
fi

start()
{
if [ $fg -eq 1 ]; then
$JAVA $JAVA_OPTS $JAVA_LOG_OPTS -server -cp $cp com.andreig.jetty.SimpleMongodbServer
else
nohup $JAVA $JAVA_OPTS $JAVA_LOG_OPTS -server -cp $cp com.andreig.jetty.SimpleMongodbServer >logs/out.log 2>&1 &
PID=`ps u|grep SimpleMongodbServer|grep -v grep| awk '{ print $2 }'`
echo $PID|cat>logs/mongoser.pid
fi
}

for o
do
# foreground
  case "$o" in
    -fg )
        fg=1
        shift;
        continue
    ;;
    stop )
    if [ ! -f logs/mongoser.pid ] ; then
      echo "Mongodb server not running"
    else
      kill `cat logs/mongoser.pid`
      rm logs/mongoser.pid
    fi
    exit
    ;;
    start )
    if [ -f logs/mongoser.pid ] ; then
      echo "Mongodb already running"
      echo "if you are sure it's not running, delete logs/mongoser.pid and start again"
      exit
    else
      start
      shift;
      continue
    fi
    ;;
    * )
    echo "Usage $0 [start|stop] [-fg]"
      break
    ;;
  esac
done

