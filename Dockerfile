FROM eclipse-temurin:17-jdk

# install sbt
RUN echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list
RUN curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | apt-key add
RUN apt-get update && apt-get install -y sbt

WORKDIR /app

# cache dependencies
COPY project/build.properties project/build.properties
COPY project/Dependencies.scala project/Dependencies.scala
COPY build.sbt .
RUN sbt update

# copy source
COPY . .

# build
RUN sbt package

# run
ENTRYPOINT ["sbt", "runMain"]
