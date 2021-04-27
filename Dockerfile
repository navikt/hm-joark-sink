FROM navikt/java:15

COPY build/libs/hm-joark-sink-all.jar app.jar

ENV JAVA_OPTS="-XX:+HeapDumpOnOutOfMemoryError \
               -XX:HeapDumpPath=/tmp/oom-dump.hprof"

COPY run-java-debug.sh /run-java.sh
COPY run-java-debug/run-java.bin /run-java.bin
