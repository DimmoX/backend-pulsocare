# ---- build: compila el jar con Maven ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# Copiamos primero el pom para cachear la descarga de dependencias.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ---- runtime: solo el JRE + el jar ----
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# El wallet NO va en la imagen: se monta como volumen y se apunta con WALLET_DIR.
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
