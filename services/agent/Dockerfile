FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradle gradle
COPY gradlew settings.gradle.kts gradle.properties ./
COPY app/build.gradle.kts app/build.gradle.kts
COPY gradle/libs.versions.toml gradle/libs.versions.toml
RUN ./gradlew dependencies --no-daemon || true
COPY app app
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/app/build/libs/*.jar app.jar
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "app.jar"]