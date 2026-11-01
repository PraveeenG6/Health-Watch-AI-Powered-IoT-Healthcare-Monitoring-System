FROM mcr.microsoft.com/openjdk/jdk:21-ubuntu

WORKDIR /app
COPY . .

EXPOSE 80

CMD ["jwebserver", "-b", "0.0.0.0", "-p", "80", "-d", "/app"]
