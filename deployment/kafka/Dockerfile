# syntax=docker/dockerfile:1.7

FROM apache/kafka:4.2.1

USER root

RUN mkdir -p /var/lib/eoc-kafka/data \
    && chown -R 1000:1000 /var/lib/eoc-kafka \
    && chmod 0755 /var/lib/eoc-kafka \
    && chmod 0750 /var/lib/eoc-kafka/data

USER 1000:1000
