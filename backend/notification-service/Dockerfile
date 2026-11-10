FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
ARG MAVEN_CENTRAL_MIRROR=central
# Optional public Central mirror. Keep HTTPS and Maven artifact coordinates unchanged.
RUN case "$MAVEN_CENTRAL_MIRROR" in \
      central) printf '%s\n' '<settings/>' > /tmp/maven-settings.xml ;; \
      google) printf '%s\n' '<settings><mirrors><mirror><id>google-central</id><mirrorOf>central</mirrorOf><url>https://maven-central.storage-download.googleapis.com/maven2</url></mirror></mirrors></settings>' > /tmp/maven-settings.xml ;; \
      *) echo 'MAVEN_CENTRAL_MIRROR must be central or google' >&2; exit 1 ;; \
    esac
RUN --mount=type=cache,id=cloud-native-maven,target=/root/.m2,sharing=locked \
    mvn -s /tmp/maven-settings.xml -U -B -ntp -DskipTests dependency:go-offline
COPY src ./src
RUN --mount=type=cache,id=cloud-native-maven,target=/root/.m2,sharing=locked \
    mvn -s /tmp/maven-settings.xml -U -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
