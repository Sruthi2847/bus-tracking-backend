FROM openjdk:17

WORKDIR /app

COPY . .

RUN javac *.java

CMD ["java", "MainServer"]