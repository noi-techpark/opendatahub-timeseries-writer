FROM eclipse-temurin:17-jre-alpine
WORKDIR /bdp
RUN apk add --no-cache wget
COPY writer/target/bdp.jar /bdp/bdp.jar
CMD [ "java", "-XX:G1PeriodicGCSystemLoadThreshold=0", "-XX:G1PeriodicGCInterval=20000", "-XX:-G1PeriodicGCInvokesConcurrent", "-XX:+UseG1GC", "-jar", "bdp.jar" ]
