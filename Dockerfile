FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY . .

RUN javac -d . *.java

CMD ["java", "com.bustrack.server.MainServer"]