FROM navikt/java:15

COPY build/libs/hm-joark-sink-all.jar app.jar

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 \
               -XX:+HeapDumpOnOutOfMemoryError \
               -XX:HeapDumpPath=/oom-dump.hprof"

COPY run-java-debug.sh /run-java.sh