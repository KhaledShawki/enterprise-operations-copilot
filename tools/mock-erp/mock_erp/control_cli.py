from __future__ import annotations

import argparse
import json

import grpc

import mock_erp_control_pb2 as pb
import mock_erp_control_pb2_grpc as pb_grpc


CONTROL_TARGET = "127.0.0.1:9091"


def main() -> int:
    parser = argparse.ArgumentParser(prog="mock-erp-control")
    subcommands = parser.add_subparsers(dest="command", required=True)
    configure = subcommands.add_parser("configure")
    configure.add_argument("--seed", required=True, type=int)
    configure.add_argument("--records", required=True, type=int)
    subcommands.add_parser("metadata")
    args = parser.parse_args()

    with grpc.insecure_channel(CONTROL_TARGET) as channel:
        stub = pb_grpc.MockErpControlServiceStub(channel)
        if args.command == "configure":
            response = stub.ConfigureDataset(
                pb.ConfigureDatasetRequest(seed=args.seed, invoice_count=args.records), timeout=5
            )
        else:
            response = stub.GetDatasetMetadata(pb.GetDatasetMetadataRequest(), timeout=5)

    print(
        json.dumps(
            {
                "specVersion": response.spec_version,
                "seed": response.seed,
                "invoiceCount": response.invoice_count,
                "customerCount": response.customer_count,
                "datasetSha256": response.dataset_sha256,
            },
            sort_keys=True,
            separators=(",", ":"),
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
