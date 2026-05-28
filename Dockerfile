FROM gcr.io/distroless/java25-debian13:nonroot
WORKDIR /app
COPY build/libs/hm-joark-sink-all.jar app.jar
ENV TZ="Europe/Oslo"
EXPOSE 8080
CMD ["./app.jar"]
