# ShopSync container image (used by Render, or any Docker host).
# Stage 1 compiles the Java sources; stage 2 runs them on a small Java runtime.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY src ./src
RUN javac -encoding UTF-8 -d out $(find src -name "*.java")

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/out ./out
COPY web ./web

# Render tells the app which port to use through $PORT (Main.java reads it).
ENV PORT=10000
# Stay within the free plan's 512 MB of memory.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 10000

CMD ["java", "-cp", "out", "com.shopsync.Main"]
