from __future__ import annotations

from concurrent import futures
import threading

import grpc

import mock_erp_control_pb2 as control_pb
import mock_erp_control_pb2_grpc as control_grpc
import mock_erp_source_pb2 as source_pb
import mock_erp_source_pb2_grpc as source_grpc

from mock_erp.dataset import Dataset, SPEC_VERSION, build_dataset
from mock_erp.pagination import InvalidSourcePosition, page_records


SOURCE_ADDRESS = "0.0.0.0:9090"
CONTROL_ADDRESS = "127.0.0.1:9091"
DEFAULT_SEED = 42
DEFAULT_INVOICE_COUNT = 12


class DatasetState:
    def __init__(self) -> None:
        self._lock = threading.RLock()
        self._dataset = build_dataset(DEFAULT_SEED, DEFAULT_INVOICE_COUNT)

    def configure(self, seed: int, invoice_count: int) -> Dataset:
        dataset = build_dataset(seed, invoice_count)
        with self._lock:
            self._dataset = dataset
        return dataset

    def snapshot(self) -> Dataset:
        with self._lock:
            return self._dataset


class SourceService(source_grpc.MockErpSourceServiceServicer):
    def __init__(self, state: DatasetState) -> None:
        self._state = state

    def CheckConnection(self, request, context):  # noqa: N802
        return source_pb.CheckConnectionResponse(schema_version="v1")

    def VerifySchema(self, request, context):  # noqa: N802
        return source_pb.VerifySchemaResponse()

    def GetCustomers(self, request, context):  # noqa: N802
        dataset = self._state.snapshot()
        page = _page("customer", dataset.customers, request, context)
        return source_pb.CustomerPageResponse(
            records=[
                source_pb.CustomerRecord(
                    source_id=record.source_id,
                    source_version=record.source_version,
                    source_modified_at=record.source_modified_at,
                    customer_number=record.customer_number,
                    display_name=record.display_name,
                    email_address=record.email_address,
                )
                for record in page.records
            ],
            position=_position(page),
        )

    def GetInvoices(self, request, context):  # noqa: N802
        dataset = self._state.snapshot()
        page = _page("invoice", dataset.invoices, request, context)
        return source_pb.InvoicePageResponse(
            records=[
                source_pb.InvoiceRecord(
                    source_id=record.source_id,
                    source_version=record.source_version,
                    source_modified_at=record.source_modified_at,
                    customer_source_id=record.customer_source_id,
                    invoice_number=record.invoice_number,
                    issue_date=record.issue_date,
                    due_date=record.due_date,
                    currency=record.currency,
                    total_amount=record.total_amount,
                    open_amount=record.open_amount,
                    source_status=record.source_status,
                )
                for record in page.records
            ],
            position=_position(page),
        )

    def GetPayments(self, request, context):  # noqa: N802
        dataset = self._state.snapshot()
        page = _page("payment", dataset.payments, request, context)
        return source_pb.PaymentPageResponse(
            records=[
                source_pb.PaymentRecord(
                    source_id=record.source_id,
                    source_version=record.source_version,
                    source_modified_at=record.source_modified_at,
                    customer_source_id=record.customer_source_id,
                    payment_date=record.payment_date,
                    currency=record.currency,
                    amount=record.amount,
                    reversed=record.reversed,
                )
                for record in page.records
            ],
            position=_position(page),
        )


class ControlService(control_grpc.MockErpControlServiceServicer):
    def __init__(self, state: DatasetState) -> None:
        self._state = state

    def ConfigureDataset(self, request, context):  # noqa: N802
        try:
            dataset = self._state.configure(request.seed, request.invoice_count)
        except ValueError as exception:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, str(exception))
        return _metadata(dataset)

    def GetDatasetMetadata(self, request, context):  # noqa: N802
        return _metadata(self._state.snapshot())


def _page(entity, records, request, context):
    try:
        return page_records(
            entity,
            records,
            page_size=request.page_size,
            page_token=request.page_token,
            incremental_cursor=request.incremental_cursor,
        )
    except InvalidSourcePosition as exception:
        context.abort(grpc.StatusCode.INVALID_ARGUMENT, str(exception))


def _position(page):
    return source_pb.SourcePagePosition(
        next_page_token=page.next_page_token,
        candidate_cursor=page.candidate_cursor,
    )


def _metadata(dataset: Dataset) -> control_pb.DatasetMetadata:
    return control_pb.DatasetMetadata(
        spec_version=SPEC_VERSION,
        seed=dataset.seed,
        invoice_count=len(dataset.invoices),
        customer_count=len(dataset.customers),
        dataset_sha256=dataset.sha256,
    )


def serve() -> None:
    state = DatasetState()
    source_server = grpc.server(futures.ThreadPoolExecutor(max_workers=16))
    control_server = grpc.server(futures.ThreadPoolExecutor(max_workers=4))
    source_grpc.add_MockErpSourceServiceServicer_to_server(SourceService(state), source_server)
    control_grpc.add_MockErpControlServiceServicer_to_server(ControlService(state), control_server)
    source_server.add_insecure_port(SOURCE_ADDRESS)
    control_server.add_insecure_port(CONTROL_ADDRESS)
    source_server.start()
    control_server.start()
    try:
        source_server.wait_for_termination()
    finally:
        control_server.stop(grace=0)
        source_server.stop(grace=0)


if __name__ == "__main__":
    serve()
