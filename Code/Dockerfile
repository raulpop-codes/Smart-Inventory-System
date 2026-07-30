FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre

RUN apt-get update && apt-get install -y \
    libxext6 \
    libxrender1 \
    libxtst6 \
    libxi6 \
    fontconfig \
    x11-apps \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /app/target/Code-1.0-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-Dsun.java2d.opengl=false", "-Dawt.useSystemAAFontSettings=on", "-jar", "app.jar"]