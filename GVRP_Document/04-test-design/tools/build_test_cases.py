"""Build GVRP_Test_Cases.xlsx from test-cases.csv (the source of truth).

Usage (from the repository root):

    py GVRP_Document/04-test-design/tools/build_test_cases.py

Requires `openpyxl` (not a repository dependency; install it in your own Python
environment). Output is deterministic: rows keep the CSV order, sheets are sorted
by domain, and the workbook's creation/modification times are fixed.

Sheets:
  * one sheet per domain (D01 ... D09): all columns, frozen header, wrapped text;
  * Traceability: use case -> test case ids, plus the use cases without any test;
  * Summary: counts per domain and level, and automated vs manual.
"""
from __future__ import annotations

import csv
import datetime
import sys
from collections import OrderedDict, defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
CSV_PATH = HERE.parent / "test-cases.csv"
XLSX_PATH = HERE.parent / "GVRP_Test_Cases.xlsx"
CATALOGUE = HERE.parent.parent / "01-requirements" / "02-use-case-catalogue.md"

COLUMNS = [
    "tc_id", "domain", "use_case_id", "requirement_ref", "title", "level",
    "preconditions", "test_data", "steps", "expected_result", "priority",
    "automated_by", "status",
]
WIDTHS = {
    "tc_id": 12, "domain": 8, "use_case_id": 12, "requirement_ref": 16, "title": 40,
    "level": 9, "preconditions": 30, "test_data": 30, "steps": 34,
    "expected_result": 46, "priority": 8, "automated_by": 48, "status": 10,
}
LEVELS = ["UNIT", "WEB", "CLIENT", "MANUAL"]
FIXED_TIME = datetime.datetime(2026, 10, 30, 0, 0, 0)


def read_rows() -> list[dict[str, str]]:
    with CSV_PATH.open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames != COLUMNS:
            raise SystemExit(f"Unexpected CSV header: {reader.fieldnames}")
        return list(reader)


def catalogue_use_cases() -> list[str]:
    ids = []
    for line in CATALOGUE.read_text(encoding="utf-8").splitlines():
        if line.startswith("### UC-"):
            ids.append(line.split()[1])
    return ids


def main() -> int:
    try:
        from openpyxl import Workbook
        from openpyxl.styles import Alignment, Font, PatternFill
        from openpyxl.utils import get_column_letter
    except ImportError:
        print("openpyxl is not installed; the CSV remains the deliverable. "
              "Install openpyxl in your own environment and re-run.", file=sys.stderr)
        return 2

    rows = read_rows()
    by_domain: "OrderedDict[str, list[dict[str, str]]]" = OrderedDict()
    for row in sorted(rows, key=lambda r: r["domain"]):
        by_domain.setdefault(row["domain"], []).append(row)

    book = Workbook()
    book.remove(book.active)
    header_font = Font(bold=True, color="FFFFFF")
    header_fill = PatternFill("solid", fgColor="305496")
    wrap = Alignment(wrap_text=True, vertical="top")

    def add_sheet(title: str, header: list[str], body: list[list[object]], widths: list[int]) -> None:
        sheet = book.create_sheet(title)
        sheet.append(header)
        for cell in sheet[1]:
            cell.font = header_font
            cell.fill = header_fill
            cell.alignment = wrap
        for values in body:
            sheet.append(values)
        for row_cells in sheet.iter_rows(min_row=2):
            for cell in row_cells:
                cell.alignment = wrap
        for index, width in enumerate(widths, start=1):
            sheet.column_dimensions[get_column_letter(index)].width = width
        sheet.freeze_panes = "A2"

    for domain, domain_rows in by_domain.items():
        add_sheet(domain, COLUMNS, [[r[c] for c in COLUMNS] for r in domain_rows],
                  [WIDTHS[c] for c in COLUMNS])

    tests_by_uc: dict[str, list[str]] = defaultdict(list)
    for row in rows:
        tests_by_uc[row["use_case_id"]].append(row["tc_id"])
    trace = []
    for uc in catalogue_use_cases():
        ids = tests_by_uc.get(uc, [])
        trace.append([uc, len(ids), ", ".join(ids)])
    add_sheet("Traceability", ["use_case_id", "test_count", "test_cases"], trace, [14, 12, 90])

    summary = []
    for domain, domain_rows in by_domain.items():
        counts = [sum(1 for r in domain_rows if r["level"] == level) for level in LEVELS]
        automated = sum(1 for r in domain_rows if r["automated_by"].strip())
        summary.append([domain, len(domain_rows), *counts, automated, len(domain_rows) - automated])
    totals = ["Total", len(rows)] + [sum(r[i] for r in summary) for i in range(2, 2 + len(LEVELS) + 2)]
    summary.append(totals)
    add_sheet("Summary", ["domain", "total", *LEVELS, "automated", "not_automated"], summary,
              [10, 8, 8, 8, 8, 8, 11, 14])

    book.properties.created = FIXED_TIME
    book.properties.modified = FIXED_TIME
    book.properties.creator = "build_test_cases.py"
    book.save(XLSX_PATH)
    print(f"Wrote {XLSX_PATH} with {len(rows)} test cases")
    return 0


if __name__ == "__main__":
    sys.exit(main())
