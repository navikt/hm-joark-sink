FROM gcr.io/distroless/java21-debian12:nonroot
COPY /build/libs/hm-joark-sink-all.jar /app.jar
ENV TZ="Europe/Oslo"
EXPOSE 8080
CMD ["/app.jar"]
