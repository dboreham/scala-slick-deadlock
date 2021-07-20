FROM postgres:9.6

RUN apt-get update \
  && apt-get -y install \
    gnupg2 \
    wget \
    lsb-release \
  && apt-get clean all \
  && rm -rf /var/lib/apt/lists/*

RUN wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | apt-key add -
RUN echo "deb http://apt.postgresql.org/pub/repos/apt/ `lsb_release -cs`-pgdg main" | tee  /etc/apt/sources.list.d/pgdg.list

RUN apt-get update \
  && apt-get -y install \
    postgis \
    postgresql-9.6-postgis-3 \
  && apt-get clean all \
  && rm -rf /var/lib/apt/lists/*


# RUN sed -i 's/md5/trust/g' /etc/postgresql/9.6/main/pg_hba.conf.template