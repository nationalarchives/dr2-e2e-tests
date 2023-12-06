FROM amazoncorretto:21
WORKDIR /test
COPY . /test
RUN yum install -y wget unzip && wget -q https://github.com/sbt/sbt/releases/download/v1.9.7/sbt-1.9.7.zip && \
    unzip -qq sbt-1.9.7.zip
CMD cp -R /test/* /tests/boot && cd /tests/boot && sbt/bin/sbt -Duser.home=/tests/boot test
