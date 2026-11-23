"""Build Excel workbooks and validate GVRP test-case CSV catalogues.

Usage (from the repository root):

    # Build GVRP_Test_Cases.xlsx from test-cases.csv (default, requires openpyxl)
    py GVRP_Document/04-test-design/tools/build_test_cases.py

    # Build GVRP_System_Test_Cases.xlsx from system-test-cases.csv (requires openpyxl)
    py GVRP_Document/04-test-design/tools/build_test_cases.py --system

    # Validate both CSV catalogues against schema, enums, and use-case IDs (no openpyxl needed)
    py GVRP_Document/04-test-design/tools/build_test_cases.py --validate-only

`openpyxl` is not a repository dependency; install it in your own Python environment
when generating `.xlsx` files. Outputs are deterministic: rows keep CSV order and
workbook timestamps are fixed.
"""
from __future__ import annotations

import argparse
import csv
import datetime
import sys
from collections import OrderedDict, defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
CSV_PATH = HERE.parent / "test-cases.csv"
XLSX_PATH = HERE.parent / "GVRP_Test_Cases.xlsx"
SYSTEM_CSV_PATH = HERE.parent / "system-test-cases.csv"
SYSTEM_XLSX_PATH = HERE.parent / "GVRP_System_Test_Cases.xlsx"
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

SYSTEM_COLUMNS = [
    "st_id", "pillar", "execution_mode", "domains", "use_case_ids", "requirement_ref",
    "title", "preconditions", "test_data", "steps", "expected_api_result",
    "expected_db_state", "expected_ui_state", "priority", "status",
]
SYSTEM_WIDTHS = {
    "st_id": 12, "pillar": 8, "execution_mode": 17, "domains": 18, "use_case_ids": 26,
    "requirement_ref": 18, "title": 40, "preconditions": 32, "test_data": 32,
    "steps": 38, "expected_api_result": 42, "expected_db_state": 36,
    "expected_ui_state": 36, "priority": 8, "status": 10,
}
PILLARS = ["E2E", "SEC", "RES", "LIM"]
EXECUTION_MODES = ["API_AUTO_TARGET", "BROWSER_E2E"]
PRIORITIES = ["H", "M", "L"]
VALID_STATUSES = {"NOT_RUN", "PASS", "FAIL", "BLOCKED"}
FIXED_TIME_SYSTEM = datetime.datetime(2026, 11, 23, 0, 0, 0)


def read_csv_rows(path: Path, expected_columns: list[str]) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as handle:
        reader = csv.DictReader(handle)
        if reader.fieldnames != expected_columns:
            raise SystemExit(
                f"Unexpected CSV header in {path.name}: {reader.fieldnames} "
                f"(expected {expected_columns})"
            )
        return list(reader)


def catalogue_use_cases() -> list[str]:
    ids = []
    for line in CATALOGUE.read_text(encoding="utf-8").splitlines():
        if line.startswith("### UC-"):
            ids.append(line.split()[1])
    return ids


def split_use_case_ids(raw: str) -> list[str]:
    return [part.strip() for part in raw.split(",") if part.strip()]


def validate_all() -> tuple[list[dict[str, str]], list[dict[str, str]]]:
    valid_ucs = set(catalogue_use_cases())
    errors: list[str] = []

    tc_rows = read_csv_rows(CSV_PATH, COLUMNS)
    seen_tc: set[str] = set()
    for idx, row in enumerate(tc_rows, start=2):
        tc_id = row["tc_id"].strip()
        if not tc_id:
            errors.append(f"{CSV_PATH.name}:{idx}: empty tc_id")
        elif tc_id in seen_tc:
            errors.append(f"{CSV_PATH.name}:{idx}: duplicate tc_id '{tc_id}'")
        seen_tc.add(tc_id)

        if row["level"] not in LEVELS:
            errors.append(f"{CSV_PATH.name}:{idx} ({tc_id}): invalid level '{row['level']}'")
        if row["priority"] not in PRIORITIES:
            errors.append(f"{CSV_PATH.name}:{idx} ({tc_id}): invalid priority '{row['priority']}'")
        if row["status"] not in VALID_STATUSES:
            errors.append(f"{CSV_PATH.name}:{idx} ({tc_id}): invalid status '{row['status']}'")
        if row["use_case_id"] not in valid_ucs:
            errors.append(
                f"{CSV_PATH.name}:{idx} ({tc_id}): unknown use_case_id '{row['use_case_id']}'"
            )

    st_rows = read_csv_rows(SYSTEM_CSV_PATH, SYSTEM_COLUMNS)
    seen_st: set[str] = set()
    for idx, row in enumerate(st_rows, start=2):
        st_id = row["st_id"].strip()
        if not st_id:
            errors.append(f"{SYSTEM_CSV_PATH.name}:{idx}: empty st_id")
        elif st_id in seen_st:
            errors.append(f"{SYSTEM_CSV_PATH.name}:{idx}: duplicate st_id '{st_id}'")
        seen_st.add(st_id)

        if row["pillar"] not in PILLARS:
            errors.append(f"{SYSTEM_CSV_PATH.name}:{idx} ({st_id}): invalid pillar '{row['pillar']}'")
        if row["execution_mode"] not in EXECUTION_MODES:
            errors.append(
                f"{SYSTEM_CSV_PATH.name}:{idx} ({st_id}): invalid execution_mode '{row['execution_mode']}'"
            )
        if row["priority"] not in PRIORITIES:
            errors.append(f"{SYSTEM_CSV_PATH.name}:{idx} ({st_id}): invalid priority '{row['priority']}'")
        if row["status"] not in VALID_STATUSES:
            errors.append(f"{SYSTEM_CSV_PATH.name}:{idx} ({st_id}): invalid status '{row['status']}'")

        uc_list = split_use_case_ids(row["use_case_ids"])
        if not uc_list:
            errors.append(f"{SYSTEM_CSV_PATH.name}:{idx} ({st_id}): empty use_case_ids")
        for uc in uc_list:
            if uc not in valid_ucs:
                errors.append(f"{SYSTEM_CSV_PATH.name}:{idx} ({st_id}): unknown use_case_id '{uc}'")

    if errors:
        for err in errors:
            print(f"ERROR: {err}", file=sys.stderr)
        raise SystemExit(1)

    return tc_rows, st_rows


def print_validation_summary(tc_rows: list[dict[str, str]], st_rows: list[dict[str, str]]) -> None:
    by_domain: "OrderedDict[str, int]" = OrderedDict()
    for r in sorted(tc_rows, key=lambda x: x["domain"]):
        by_domain[r["domain"]] = by_domain.get(r["domain"], 0) + 1
    automated = sum(1 for r in tc_rows if r["automated_by"].strip())
    manual = len(tc_rows) - automated
    print(
        f"test-cases.csv: OK ({len(tc_rows)} rows; automated={automated}, manual={manual}; "
        f"domains={dict(by_domain)})"
    )

    by_pillar = {p: sum(1 for r in st_rows if r["pillar"] == p) for p in PILLARS}
    by_mode = {m: sum(1 for r in st_rows if r["execution_mode"] == m) for m in EXECUTION_MODES}
    by_prio = {p: sum(1 for r in st_rows if r["priority"] == p) for p in PRIORITIES}
    print(
        f"system-test-cases.csv: OK ({len(st_rows)} rows; pillars={by_pillar}; "
        f"modes={by_mode}; priorities={by_prio})"
    )


def create_styled_workbook():
    from openpyxl import Workbook
    from openpyxl.styles import Alignment, Font, PatternFill
    from openpyxl.utils import get_column_letter

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

    return book, add_sheet


def build_unit_workbook(rows: list[dict[str, str]]) -> int:
    book, add_sheet = create_styled_workbook()
    by_domain: "OrderedDict[str, list[dict[str, str]]]" = OrderedDict()
    for row in sorted(rows, key=lambda r: r["domain"]):
        by_domain.setdefault(row["domain"], []).append(row)

    for domain, domain_rows in by_domain.items():
        add_sheet(
            domain,
            COLUMNS,
            [[r[c] for c in COLUMNS] for r in domain_rows],
            [WIDTHS[c] for c in COLUMNS],
        )

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
    add_sheet(
        "Summary",
        ["domain", "total", *LEVELS, "automated", "not_automated"],
        summary,
        [10, 8, 8, 8, 8, 8, 11, 14],
    )

    book.properties.created = FIXED_TIME
    book.properties.modified = FIXED_TIME
    book.properties.creator = "build_test_cases.py"
    book.save(XLSX_PATH)
    print(f"Wrote {XLSX_PATH} with {len(rows)} test cases")
    return 0


def build_system_workbook(rows: list[dict[str, str]]) -> int:
    book, add_sheet = create_styled_workbook()
    by_pillar: "OrderedDict[str, list[dict[str, str]]]" = OrderedDict(
        (pillar, []) for pillar in PILLARS
    )
    for row in rows:
        by_pillar[row["pillar"]].append(row)

    for pillar, pillar_rows in by_pillar.items():
        add_sheet(
            pillar,
            SYSTEM_COLUMNS,
            [[r[c] for c in SYSTEM_COLUMNS] for r in pillar_rows],
            [SYSTEM_WIDTHS[c] for c in SYSTEM_COLUMNS],
        )

    tests_by_uc: dict[str, list[str]] = defaultdict(list)
    for row in rows:
        for uc in split_use_case_ids(row["use_case_ids"]):
            tests_by_uc[uc].append(row["st_id"])
    trace = []
    for uc in catalogue_use_cases():
        ids = tests_by_uc.get(uc, [])
        trace.append([uc, len(ids), ", ".join(ids)])
    add_sheet("Traceability", ["use_case_id", "test_count", "system_test_cases"], trace, [14, 12, 90])

    summary = []
    for pillar, pillar_rows in by_pillar.items():
        mode_counts = [
            sum(1 for r in pillar_rows if r["execution_mode"] == mode) for mode in EXECUTION_MODES
        ]
        prio_counts = [
            sum(1 for r in pillar_rows if r["priority"] == prio) for prio in PRIORITIES
        ]
        summary.append([pillar, len(pillar_rows), *mode_counts, *prio_counts])
    totals = ["Total", len(rows)] + [
        sum(r[i] for r in summary) for i in range(2, 2 + len(EXECUTION_MODES) + len(PRIORITIES))
    ]
    summary.append(totals)
    add_sheet(
        "Summary",
        ["pillar", "total", *EXECUTION_MODES, *PRIORITIES],
        summary,
        [10, 8, 17, 14, 6, 6, 6],
    )

    book.properties.created = FIXED_TIME_SYSTEM
    book.properties.modified = FIXED_TIME_SYSTEM
    book.properties.creator = "build_test_cases.py"
    book.save(SYSTEM_XLSX_PATH)
    print(f"Wrote {SYSTEM_XLSX_PATH} with {len(rows)} system test cases")
    return 0


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--system",
        action="store_true",
        help="Generate GVRP_System_Test_Cases.xlsx from system-test-cases.csv instead of test-cases.csv.",
    )
    parser.add_argument(
        "--validate-only",
        action="store_true",
        help="Validate both test-cases.csv and system-test-cases.csv without requiring openpyxl.",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    tc_rows, st_rows = validate_all()

    if args.validate_only:
        print_validation_summary(tc_rows, st_rows)
        return 0

    try:
        import openpyxl  # noqa: F401
    except ImportError:
        print(
            "openpyxl is not installed; the CSV remains the deliverable. "
            "Install openpyxl in your own environment and re-run.",
            file=sys.stderr,
        )
        return 2

    if args.system:
        return build_system_workbook(st_rows)
    return build_unit_workbook(tc_rows)


if __name__ == "__main__":
    sys.exit(main())
