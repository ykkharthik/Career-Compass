# Multi-stage build: compile with the full JDK, run on a slimmer JRE image.
# No Maven/Gradle in this project by design (see git history) - the build
# step here is exactly the two commands documented for running it locally.

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY src ./src
COPY lib ./lib
RUN javac -cp "lib/h2-2.2.224.jar" -d out $(find src -name "*.java")

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/out ./out
COPY lib ./lib
COPY data ./data

# Render (and similar platforms) inject $PORT and route traffic to it;
# WebMain reads that env var when no command-line argument is given.
# EXPOSE is documentation only - the platform decides the actual port.
EXPOSE 8080

CMD ["java", "-cp", "out:lib/h2-2.2.224.jar", "web.WebMain"]
