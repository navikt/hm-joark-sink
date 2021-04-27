FROM navikt/java:15

COPY build/libs/hm-joark-sink-all.jar app.jar

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 \
               -XX:+HeapDumpOnOutOfMemoryError \
               -XX:HeapDumpPath=/tmp/oom-dump.hprof"

COPY run-java-debug.sh /run-java.sh
COPY run-java-debug/run-java.bin /run-java.bin

RUN echo 'java -XX:MaxRAMPercentage=75 -XX:+PrintFlagsFinal -version | grep -Ei "maxheapsize|maxram"' > /init-scripts/0-dump-memory-config.sh