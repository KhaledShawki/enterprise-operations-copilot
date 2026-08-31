import importlib.util
import sys
from pathlib import Path
import unittest

from eoc_lab.baseline_dataset import expected_dataset


REPO_ROOT = Path(__file__).resolve().parents[3]
MOCK_DATASET = REPO_ROOT / "tools/mock-erp/mock_erp/dataset.py"


class MockErpDatasetContractTest(unittest.TestCase):
    def test_external_source_and_independent_oracle_match_golden_vectors(self) -> None:
        spec = importlib.util.spec_from_file_location("external_mock_erp_dataset", MOCK_DATASET)
        assert spec is not None and spec.loader is not None
        module = importlib.util.module_from_spec(spec)
        sys.modules[spec.name] = module
        try:
            spec.loader.exec_module(module)
        finally:
            sys.modules.pop(spec.name, None)

        for seed, records in ((42, 3), (42, 137), (0, 1), (2_147_483_647, 10)):
            with self.subTest(seed=seed, records=records):
                expected = expected_dataset(seed, records)
                external = module.build_dataset(seed, records)
                self.assertEqual(expected.sha256, external.sha256)


if __name__ == "__main__":
    unittest.main()
