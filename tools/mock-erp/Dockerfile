# syntax=docker/dockerfile:1.7

FROM python:3.12.11-slim AS proto-build

WORKDIR /build

COPY tools/mock-erp/requirements-build.txt requirements-build.txt
RUN pip install --no-cache-dir -r requirements-build.txt

COPY contracts/mock-erp/v1/source/mock_erp_source.proto /proto/mock_erp_source.proto
COPY tools/mock-erp/contracts/mock_erp_control.proto /proto/mock_erp_control.proto
RUN mkdir -p /generated \
    && python -m grpc_tools.protoc \
      -I/proto \
      --python_out=/generated \
      --grpc_python_out=/generated \
      /proto/mock_erp_source.proto \
      /proto/mock_erp_control.proto

FROM python:3.12.11-slim

WORKDIR /opt/mock-erp

COPY tools/mock-erp/requirements.txt requirements.txt
RUN pip install --no-cache-dir -r requirements.txt \
    && useradd --system --uid 10001 --no-create-home mockerp

COPY --from=proto-build /generated/ /opt/mock-erp/generated/
COPY tools/mock-erp/mock_erp/ /opt/mock-erp/mock_erp/

RUN chmod -R a+rX \
    /opt/mock-erp/generated \
    /opt/mock-erp/mock_erp

ENV PYTHONPATH=/opt/mock-erp:/opt/mock-erp/generated \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

USER 10001:10001

EXPOSE 9090

HEALTHCHECK --interval=5s --timeout=3s --start-period=5s --retries=12 \
  CMD ["python", "-m", "mock_erp.control_cli", "metadata"]

ENTRYPOINT ["python", "-m", "mock_erp.server"]
