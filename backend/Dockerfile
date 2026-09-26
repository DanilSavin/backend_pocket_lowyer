FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY backend/pom.xml backend/pom.xml
RUN mvn -f backend/pom.xml --batch-mode dependency:go-offline

COPY backend/src backend/src
RUN mvn -f backend/pom.xml --batch-mode package -DskipTests

FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app

# GigaChat OAuth requires the Russian Trusted Root CA in the container's JDK truststore.
COPY backend/certs/russian_trusted_root_ca_pem.crt /tmp/russian_trusted_root_ca_pem.crt
RUN keytool -importcert -storepass changeit -noprompt -alias rus_root_ca -cacerts -trustcacerts \
    -file /tmp/russian_trusted_root_ca_pem.crt && rm /tmp/russian_trusted_root_ca_pem.crt

COPY --from=build /workspace/backend/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
