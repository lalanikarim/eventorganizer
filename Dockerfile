FROM docker.io/bitnami/minideb:stretch

RUN apt update
RUN apt install openjdk-8-jdk-headless tar vim -y
RUN apt install mysql-client -y
WORKDIR /root
ADD https://github.com/sbt/sbt/releases/download/v0.13.18/sbt-0.13.18.tgz .
COPY ./ /app
RUN tar xvf sbt-0.13.18.tgz
CMD ["/app/entrypoint.sh"]
EXPOSE 9000

