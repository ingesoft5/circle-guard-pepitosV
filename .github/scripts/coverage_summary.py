"""Prints a Markdown table with test results and JaCoCo line coverage per service (HU-18)."""
import glob
import os
import sys
import xml.etree.ElementTree as ET

GATED = {
    "circleguard-auth-service",
    "circleguard-identity-service",
    "circleguard-promotion-service",
    "circleguard-form-service",
    "circleguard-gateway-service",
}
MINIMUM = 60.0


def line_coverage(report):
    root = ET.parse(report).getroot()
    for counter in root.findall("counter"):
        if counter.get("type") == "LINE":
            missed, covered = int(counter.get("missed")), int(counter.get("covered"))
            total = missed + covered
            return (100.0 * covered / total if total else 100.0), covered, total
    return None, 0, 0


def test_results(service_dir):
    tests = failures = skipped = 0
    for path in glob.glob(os.path.join(service_dir, "build/test-results/test/*.xml")):
        suite = ET.parse(path).getroot()
        tests += int(suite.get("tests", 0))
        failures += int(suite.get("failures", 0)) + int(suite.get("errors", 0))
        skipped += int(suite.get("skipped", 0))
    return tests, failures, skipped


def main(services_root):
    print("### Unit tests and line coverage\n")
    print("| Service | Tests | Failed | Lines covered | Line coverage | Gate |")
    print("|:---|---:|---:|---:|---:|:---|")
    for service_dir in sorted(glob.glob(os.path.join(services_root, "*"))):
        name = os.path.basename(service_dir)
        tests, failures, skipped = test_results(service_dir)
        report = os.path.join(service_dir, "build/reports/jacoco/test/jacocoTestReport.xml")
        if os.path.exists(report):
            pct, covered, total = line_coverage(report)
            coverage = f"{pct:.1f} %"
            lines = f"{covered}/{total}"
        else:
            pct, coverage, lines = None, "no report", "-"
        if name in GATED:
            gate = "✅ ≥ 60 %" if pct is not None and pct >= MINIMUM else "❌ < 60 %"
        else:
            gate = "tests only"
        print(f"| {name.replace('circleguard-', '')} | {tests} | {failures} | {lines} | {coverage} | {gate} |")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "services")