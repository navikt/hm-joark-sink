FROM gcr.io/distroless/java17-debian12:latest
COPY /build/libs/hm-joark-sink-all.jar /app.jar
ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb:NO.UTF-8' TZ="Europe/Oslo"
CMD ["/app.jar"]
