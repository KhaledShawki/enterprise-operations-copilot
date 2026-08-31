# External Mock ERP

`tools/mock-erp` is an external gRPC source simulator used by the EOC evidence environment. It is
not linked into the EOC application runtime.

The source API is exposed on port `9090` and implements the versioned protobuf contract under
`contracts/mock-erp/v1/source/mock_erp_source.proto`. The evidence-only control contract is kept separately at `tools/mock-erp/contracts/mock_erp_control.proto`; its service binds to container loopback on port `9091`,
so `platform-service` cannot configure datasets or fault modes.

The simulator owns source pagination and incremental cursor semantics. EOC treats both values as
opaque strings.

For PR73 the control surface supports deterministic dataset configuration only. Later reliability
campaigns may add source-side fault injection here without adding evidence switches to EOC.
