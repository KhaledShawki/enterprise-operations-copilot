import unittest

from eoc_lab.doctor import DoctorReport
from eoc_lab.evidence import CheckResult


class DoctorReportTest(unittest.TestCase):
    def test_passes_only_when_every_check_passes(self) -> None:
        self.assertTrue(DoctorReport((CheckResult("a", True, "ok"),)).passed)
        self.assertFalse(
            DoctorReport((CheckResult("a", True, "ok"), CheckResult("b", False, "bad"))).passed
        )


if __name__ == "__main__":
    unittest.main()
