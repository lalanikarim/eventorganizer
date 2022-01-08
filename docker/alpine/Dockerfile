FROM docker.io/openjdk:8-jdk-alpine3.9
RUN echo "nameserver 8.8.8.8" > /etc/resolv.conf
RUN apk add tar vim bash
RUN apk add mysql-client
WORKDIR /root
ADD https://github.com/sbt/sbt/releases/download/v0.13.18/sbt-0.13.18.tgz .
RUN tar xvf sbt-0.13.18.tgz
CMD ["/app/entrypoint.sh"]
EXPOSE 9000

