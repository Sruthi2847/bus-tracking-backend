FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY . .

RUN javac -cp ".:mysql-connector-j-9.7.0.jar" -d . *.java

CMD ["java", "-cp", ".:mysql-connector-j-9.7.0.jar", "com.bustrack.server.MainServer"]