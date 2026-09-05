#!/usr/bin/env python3

"""
GVRP Experiment Validator

Commands:

    python tools/experiment_validator.py validate <experiment-dir>
    python tools/experiment_validator.py promote-check <experiment-dir>
    python tools/experiment_validator.py summary <experiment-dir>

Example:

    python tools/experiment_validator.py validate experiments/runs/EXP-2026-001
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

try:
    from jsonschema import Draft202012Validator, FormatChecker
except ImportError:
    print(
        "ERROR: Missing dependency 'jsonschema'.\n"
        "Install it with:\n"
        "  python -m pip install jsonschema",
        file=sys.stderr,
    )
    sys.exit(2)

PROJECT_ROOT = Path(__file__).resolve().parents[1]

SCHEMA_DIR = PROJECT_ROOT / "experiments" / "schema"
EXPERIMENT_SCHEMA_PATH = SCHEMA_DIR / "experiment.schema.json"
REVIEW_SCHEMA_PATH = SCHEMA_DIR / "review.schema.json"


@dataclass
class CheckResult:
    name: str
    passed: bool
    message: str


class ValidationFailure(Exception):
    pass


def load_json(path: Path) -> dict[str, Any]:
    if not path.exists():
        raise ValidationFailure(f"Missing file: {path}")

    if not path.is_file():
        raise ValidationFailure(f"Not a file: {path}")

    try:
        with path.open("r", encoding="utf-8") as file:
            data = json.load(file)
    except json.JSONDecodeError as exc:
        raise ValidationFailure(
            f"Invalid JSON in {path}: "
            f"line {exc.lineno}, column {exc.colno}: {exc.msg}"
        ) from exc

    if not isinstance(data, dict):
        raise ValidationFailure(
            f"Expected top-level JSON object in {path}"
        )

    return data


def load_schema(path: Path) -> dict[str, Any]:
    return load_json(path)


def format_json_path(parts: Any) -> str:
    path = "$"

    for part in parts:
        if isinstance(part, int):
            path += f"[{part}]"
        else:
            path += f".{part}"

    return path


def validate_against_schema(
    instance: dict[str, Any],
    schema: dict[str, Any],
    label: str,
) -> list[str]:
    validator = Draft202012Validator(
        schema,
        format_checker=FormatChecker(),
    )

    errors = sorted(
        validator.iter_errors(instance),
        key=lambda error: list(error.absolute_path),
    )

    messages: list[str] = []

    for error in errors:
        location = format_json_path(error.absolute_path)
        messages.append(
            f"{label} {location}: {error.message}"
        )

    return messages


def normalize_experiment_dir(raw_path: str) -> Path:
    path = Path(raw_path)

    if not path.is_absolute():
        path = (Path.cwd() / path).resolve()
    else:
        path = path.resolve()

    if not path.exists():
        raise ValidationFailure(
            f"Experiment directory does not exist: {path}"
        )

    if not path.is_dir():
        raise ValidationFailure(
            f"Experiment path is not a directory: {path}"
        )

    return path


def resolve_artifact_path(
    experiment_dir: Path,
    raw_reference: str,
) -> Path:
    candidate = Path(raw_reference)

    if candidate.is_absolute():
        return candidate.resolve()

    return (experiment_dir / candidate).resolve()


def path_is_within(parent: Path, child: Path) -> bool:
    try:
        child.relative_to(parent)
        return True
    except ValueError:
        return False


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()

    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(chunk)

    return digest.hexdigest()


def collect_schema_checks(
    experiment_dir: Path,
) -> tuple[list[CheckResult], dict[str, Any] | None, dict[str, Any] | None]:
    checks: list[CheckResult] = []

    metadata_path = experiment_dir / "metadata.json"
    review_path = experiment_dir / "review.json"

    metadata: dict[str, Any] | None = None
    review: dict[str, Any] | None = None

    try:
        experiment_schema = load_schema(EXPERIMENT_SCHEMA_PATH)
        checks.append(
            CheckResult(
                "Experiment schema available",
                True,
                str(EXPERIMENT_SCHEMA_PATH),
            )
        )
    except ValidationFailure as exc:
        checks.append(
            CheckResult(
                "Experiment schema available",
                False,
                str(exc),
            )
        )
        experiment_schema = None

    try:
        review_schema = load_schema(REVIEW_SCHEMA_PATH)
        checks.append(
            CheckResult(
                "Review schema available",
                True,
                str(REVIEW_SCHEMA_PATH),
            )
        )
    except ValidationFailure as exc:
        checks.append(
            CheckResult(
                "Review schema available",
                False,
                str(exc),
            )
        )
        review_schema = None

    try:
        metadata = load_json(metadata_path)

        checks.append(
            CheckResult(
                "metadata.json readable",
                True,
                str(metadata_path),
            )
        )

        if experiment_schema is not None:
            errors = validate_against_schema(
                metadata,
                experiment_schema,
                "metadata.json",
            )

            if errors:
                checks.append(
                    CheckResult(
                        "metadata.json schema",
                        False,
                        "\n".join(errors),
                    )
                )
            else:
                checks.append(
                    CheckResult(
                        "metadata.json schema",
                        True,
                        "Valid against experiment.schema.json",
                    )
                )

    except ValidationFailure as exc:
        checks.append(
            CheckResult(
                "metadata.json readable",
                False,
                str(exc),
            )
        )

    if review_path.exists():
        try:
            review = load_json(review_path)

            checks.append(
                CheckResult(
                    "review.json readable",
                    True,
                    str(review_path),
                )
            )

            if review_schema is not None:
                errors = validate_against_schema(
                    review,
                    review_schema,
                    "review.json",
                )

                if errors:
                    checks.append(
                        CheckResult(
                            "review.json schema",
                            False,
                            "\n".join(errors),
                        )
                    )
                else:
                    checks.append(
                        CheckResult(
                            "review.json schema",
                            True,
                            "Valid against review.schema.json",
                        )
                    )

        except ValidationFailure as exc:
            checks.append(
                CheckResult(
                    "review.json readable",
                    False,
                    str(exc),
                )
            )
    else:
        checks.append(
            CheckResult(
                "review.json present",
                False,
                f"Missing file: {review_path}",
            )
        )

    return checks, metadata, review


def validate_dataset_hash(
    experiment_dir: Path,
    metadata: dict[str, Any],
) -> CheckResult:
    dataset = metadata.get("dataset", {})

    dataset_path_value = dataset.get("path")
    hash_record = dataset.get("hash")

    if not dataset_path_value:
        return CheckResult(
            "Dataset hash verification",
            True,
            "Dataset path is not recorded; format validation only.",
        )

    if not isinstance(hash_record, dict):
        return CheckResult(
            "Dataset hash verification",
            False,
            "Dataset path exists but dataset.hash is missing.",
        )

    algorithm = hash_record.get("algorithm")
    expected_hash = hash_record.get("value")

    if algorithm != "SHA-256":
        return CheckResult(
            "Dataset hash verification",
            False,
            f"Unsupported hash algorithm: {algorithm}",
        )

    dataset_path = Path(dataset_path_value)

    if not dataset_path.is_absolute():
        dataset_path = (PROJECT_ROOT / dataset_path).resolve()

    if not dataset_path.exists():
        return CheckResult(
            "Dataset hash verification",
            False,
            f"Dataset file does not exist: {dataset_path}",
        )

    if not dataset_path.is_file():
        return CheckResult(
            "Dataset hash verification",
            False,
            f"Dataset path is not a file: {dataset_path}",
        )

    actual_hash = sha256_file(dataset_path)

    if actual_hash.lower() != str(expected_hash).lower():
        return CheckResult(
            "Dataset hash verification",
            False,
            (
                "Dataset SHA-256 mismatch.\n"
                f"Expected: {expected_hash}\n"
                f"Actual:   {actual_hash}"
            ),
        )

    return CheckResult(
        "Dataset hash verification",
        True,
        f"SHA-256 verified: {actual_hash}",
    )


def validate_raw_artifacts(
    experiment_dir: Path,
    metadata: dict[str, Any],
    require_non_empty: bool,
) -> CheckResult:
    artifacts = metadata.get("artifacts", {})
    raw_artifacts = artifacts.get("raw", [])

    if not isinstance(raw_artifacts, list):
        return CheckResult(
            "Raw artifacts",
            False,
            "artifacts.raw must be an array.",
        )

    if require_non_empty and not raw_artifacts:
        return CheckResult(
            "Raw artifacts",
            False,
            "Completed experiment has no raw artifact references.",
        )

    missing: list[str] = []
    outside_experiment: list[str] = []

    for raw_reference in raw_artifacts:
        artifact_path = resolve_artifact_path(
            experiment_dir,
            raw_reference,
        )

        if not path_is_within(experiment_dir, artifact_path):
            outside_experiment.append(raw_reference)
            continue

        if not artifact_path.exists():
            missing.append(raw_reference)

    problems: list[str] = []

    if missing:
        problems.append(
            "Missing: " + ", ".join(missing)
        )

    if outside_experiment:
        problems.append(
            "Outside experiment directory: "
            + ", ".join(outside_experiment)
        )

    if problems:
        return CheckResult(
            "Raw artifacts",
            False,
            "\n".join(problems),
        )

    return CheckResult(
        "Raw artifacts",
        True,
        f"{len(raw_artifacts)} raw artifact reference(s) verified.",
    )


def validate_config(
    experiment_dir: Path,
    metadata: dict[str, Any],
) -> CheckResult:
    comparison = metadata.get("comparison", {})

    baseline = comparison.get("baseline", {})
    treatment = comparison.get("treatment", {})

    refs = [
        ("baseline", baseline.get("config_ref")),
        ("treatment", treatment.get("config_ref")),
    ]

    missing_refs: list[str] = []
    missing_files: list[str] = []
    outside_files: list[str] = []

    for name, config_ref in refs:
        if not config_ref:
            missing_refs.append(name)
            continue

        path = resolve_artifact_path(
            experiment_dir,
            config_ref,
        )

        if not path_is_within(experiment_dir, path):
            outside_files.append(
                f"{name}: {config_ref}"
            )
            continue

        if not path.exists():
            missing_files.append(
                f"{name}: {config_ref}"
            )

    problems: list[str] = []

    if missing_refs:
        problems.append(
            "Missing config_ref for: "
            + ", ".join(missing_refs)
        )

    if missing_files:
        problems.append(
            "Config file not found: "
            + ", ".join(missing_files)
        )

    if outside_files:
        problems.append(
            "Config outside experiment directory: "
            + ", ".join(outside_files)
        )

    if problems:
        return CheckResult(
            "Configuration artifacts",
            False,
            "\n".join(problems),
        )

    return CheckResult(
        "Configuration artifacts",
        True,
        "Baseline and treatment configuration references are present.",
    )


def validate_procedure(
    experiment_dir: Path,
    metadata: dict[str, Any],
) -> CheckResult:
    proc = metadata.get("procedure")

    if not isinstance(proc, dict) or not proc.get("commands"):
        return CheckResult(
            "Command invocation",
            False,
            "metadata.procedure.commands is missing or empty; "
            "record exact shell command(s) and raw mapping.",
        )

    commands = proc.get("commands")

    if not isinstance(commands, list) or not commands:
        return CheckResult(
            "Command invocation",
            False,
            "metadata.procedure.commands must be a non-empty array.",
        )

    raw_refs = set(metadata.get("artifacts", {}).get("raw", []))
    covered: set[str] = set()

    for entry in commands:
        if not isinstance(entry, dict) or not entry.get("command"):
            return CheckResult(
                "Command invocation",
                False,
                "Each procedure.commands entry needs a non-empty "
                "command and produces list.",
            )

        produces = entry.get("produces", [])

        if not isinstance(produces, list) or not produces:
            return CheckResult(
                "Command invocation",
                False,
                "Each procedure.commands entry needs a non-empty "
                "produces list.",
            )

        for ref in produces:
            covered.add(ref)

    uncovered = sorted(raw_refs - covered)

    if uncovered:
        return CheckResult(
            "Command invocation",
            False,
            "Raw artifacts without producing command: "
            + ", ".join(uncovered),
        )

    dangling = sorted(covered - raw_refs)

    if dangling:
        return CheckResult(
            "Command invocation",
            False,
            "Commands produce unlisted raw artifacts: "
            + ", ".join(dangling),
        )

    return CheckResult(
        "Command invocation",
        True,
        f"{len(commands)} command(s) cover {len(raw_refs)} "
        "raw artifact(s).",
    )


def validate_experiment_id_consistency(
    experiment_dir: Path,
    metadata: dict[str, Any],
    review: dict[str, Any] | None,
) -> CheckResult:
    metadata_id = metadata.get("experiment_id")

    if metadata_id != experiment_dir.name:
        return CheckResult(
            "Experiment ID consistency",
            False,
            (
                f"Directory name is '{experiment_dir.name}' "
                f"but metadata experiment_id is '{metadata_id}'."
            ),
        )

    if review is not None:
        review_id = review.get("experiment_id")

        if review_id != metadata_id:
            return CheckResult(
                "Experiment ID consistency",
                False,
                (
                    f"metadata experiment_id is '{metadata_id}' "
                    f"but review experiment_id is '{review_id}'."
                ),
            )

    return CheckResult(
        "Experiment ID consistency",
        True,
        str(metadata_id),
    )


def validate_review_revision(
    metadata: dict[str, Any],
    review: dict[str, Any],
) -> CheckResult:
    reviewed_revision = review.get("reviewed_revision")

    if reviewed_revision is None:
        return CheckResult(
            "Reviewed revision",
            False,
            "review.json does not record reviewed_revision.",
        )

    metadata_revision = metadata.get("revision")

    if reviewed_revision != metadata_revision:
        return CheckResult(
            "Reviewed revision",
            False,
            (
                "reviewed_revision does not match "
                "metadata revision."
            ),
        )

    return CheckResult(
        "Reviewed revision",
        True,
        "Reviewer inspected the recorded experiment revision.",
    )


def validate_review_artifact_claims(
    review: dict[str, Any],
) -> CheckResult:
    artifact_checks = review.get("artifact_checks", {})

    required_true = {
        "metadata_valid",
        "raw_artifacts_present",
        "config_present",
        "dataset_identity_verified",
    }

    failed = [
        name
        for name in required_true
        if artifact_checks.get(name) is not True
    ]

    if failed:
        return CheckResult(
            "Review artifact checks",
            False,
            (
                "Reviewer did not confirm: "
                + ", ".join(sorted(failed))
            ),
        )

    return CheckResult(
        "Review artifact checks",
        True,
        "Required artifact checks confirmed.",
    )


def validate_review_semantics(
    review: dict[str, Any],
) -> list[CheckResult]:
    results: list[CheckResult] = []

    review_status = review.get("review_status")
    promotion_allowed = review.get(
        "knowledge_promotion_allowed"
    )

    protocol = review.get("protocol_compliance", {})
    evidence = review.get("evidence_assessment", {})

    if review_status in {
        "ACCEPTED",
        "ACCEPTED_WITH_LIMITATIONS",
    }:
        if protocol.get("compliant") is not True:
            results.append(
                CheckResult(
                    "Accepted review protocol compliance",
                    False,
                    (
                        "Accepted evidence must have "
                        "protocol_compliance.compliant=true."
                    ),
                )
            )
        else:
            results.append(
                CheckResult(
                    "Accepted review protocol compliance",
                    True,
                    "Protocol marked compliant.",
                )
            )

        if evidence.get("conclusion_supported") is not True:
            results.append(
                CheckResult(
                    "Accepted review conclusion support",
                    False,
                    (
                        "Accepted evidence must have "
                        "conclusion_supported=true."
                    ),
                )
            )
        else:
            results.append(
                CheckResult(
                    "Accepted review conclusion support",
                    True,
                    "Conclusion marked supported.",
                )
            )

        if evidence.get("scope_appropriate") is not True:
            results.append(
                CheckResult(
                    "Accepted review scope",
                    False,
                    (
                        "Accepted evidence must have "
                        "scope_appropriate=true."
                    ),
                )
            )
        else:
            results.append(
                CheckResult(
                    "Accepted review scope",
                    True,
                    "Conclusion scope marked appropriate.",
                )
            )

        if promotion_allowed is not True:
            results.append(
                CheckResult(
                    "Knowledge promotion flag",
                    False,
                    (
                        "Accepted review does not allow "
                        "knowledge promotion."
                    ),
                )
            )
        else:
            results.append(
                CheckResult(
                    "Knowledge promotion flag",
                    True,
                    "Promotion allowed by review.",
                )
            )

    return results


def run_validate(
    experiment_dir: Path,
) -> tuple[list[CheckResult], dict[str, Any] | None, dict[str, Any] | None]:
    checks, metadata, review = collect_schema_checks(
        experiment_dir
    )

    if metadata is not None:
        checks.append(
            validate_experiment_id_consistency(
                experiment_dir,
                metadata,
                review,
            )
        )

    return checks, metadata, review


def run_promote_check(
    experiment_dir: Path,
) -> list[CheckResult]:
    checks, metadata, review = run_validate(
        experiment_dir
    )

    if metadata is None:
        checks.append(
            CheckResult(
                "Promotion gate",
                False,
                "metadata.json is unavailable or invalid JSON.",
            )
        )
        return checks

    execution_status = metadata.get("execution_status")

    checks.append(
        CheckResult(
            "Execution completed",
            execution_status == "COMPLETED",
            f"execution_status={execution_status}",
        )
    )

    checks.append(
        validate_dataset_hash(
            experiment_dir,
            metadata,
        )
    )

    checks.append(
        validate_raw_artifacts(
            experiment_dir,
            metadata,
            require_non_empty=True,
        )
    )

    checks.append(
        validate_config(
            experiment_dir,
            metadata,
        )
    )

    checks.append(
        validate_procedure(
            experiment_dir,
            metadata,
        )
    )

    if review is None:
        checks.append(
            CheckResult(
                "Review available",
                False,
                "review.json is required for promotion.",
            )
        )
        return checks

    checks.append(
        validate_review_revision(
            metadata,
            review,
        )
    )

    checks.append(
        validate_review_artifact_claims(review)
    )

    checks.extend(
        validate_review_semantics(review)
    )

    review_status = review.get("review_status")

    review_accepted = review_status in {
        "ACCEPTED",
        "ACCEPTED_WITH_LIMITATIONS",
    }

    checks.append(
        CheckResult(
            "Review accepted",
            review_accepted,
            f"review_status={review_status}",
        )
    )

    return checks


def print_checks(
    checks: list[CheckResult],
) -> bool:
    all_passed = True

    for check in checks:
        status = "PASS" if check.passed else "FAIL"

        print(f"[{status}] {check.name}")

        if check.message:
            for line in check.message.splitlines():
                print(f"       {line}")

        if not check.passed:
            all_passed = False

    return all_passed


def command_validate(
    experiment_dir: Path,
) -> int:
    checks, _, _ = run_validate(
        experiment_dir
    )

    print(f"Experiment: {experiment_dir.name}")
    print("Mode: validate")
    print()

    passed = print_checks(checks)

    print()
    print(
        "VALID"
        if passed
        else "INVALID"
    )

    return 0 if passed else 1


def command_promote_check(
    experiment_dir: Path,
) -> int:
    checks = run_promote_check(
        experiment_dir
    )

    print(f"Experiment: {experiment_dir.name}")
    print("Mode: promote-check")
    print()

    passed = print_checks(checks)

    print()
    print(
        "PROMOTION: ALLOWED"
        if passed
        else "PROMOTION: DENIED"
    )

    return 0 if passed else 1


def command_summary(
    experiment_dir: Path,
) -> int:
    try:
        metadata = load_json(
            experiment_dir / "metadata.json"
        )
    except ValidationFailure as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    review_path = experiment_dir / "review.json"
    review: dict[str, Any] | None = None

    if review_path.exists():
        try:
            review = load_json(review_path)
        except ValidationFailure:
            review = None

    print(f"Experiment: {metadata.get('experiment_id', 'UNKNOWN')}")
    print(f"Title: {metadata.get('title', 'UNKNOWN')}")
    print(
        "Execution: "
        f"{metadata.get('execution_status', 'UNKNOWN')}"
    )

    verification_types = metadata.get(
        "verification_type",
        [],
    )

    print(
        "Verification: "
        + (
            ", ".join(verification_types)
            if verification_types
            else "UNKNOWN"
        )
    )

    revision = metadata.get("revision", {})

    print(
        "Revision: "
        f"{revision.get('commit', 'UNKNOWN')} "
        f"({revision.get('branch', 'UNKNOWN')})"
    )

    dataset = metadata.get("dataset", {})

    print(
        "Dataset: "
        f"{dataset.get('dataset_id', 'UNKNOWN')}"
    )

    if review is None:
        print("Review: NOT_AVAILABLE")
        print("Promotion: DENIED")
        return 0

    review_status = review.get(
        "review_status",
        "UNKNOWN",
    )

    promotion_allowed = review.get(
        "knowledge_promotion_allowed",
        False,
    )

    print(f"Review: {review_status}")
    print(
        "Promotion requested by review: "
        + (
            "YES"
            if promotion_allowed
            else "NO"
        )
    )

    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Validate GVRP experiment artifacts."
    )

    subparsers = parser.add_subparsers(
        dest="command",
        required=True,
    )

    for name in (
        "validate",
        "promote-check",
        "summary",
    ):
        subparser = subparsers.add_parser(name)
        subparser.add_argument(
            "experiment_dir",
            help="Path to EXP-YYYY-NNN directory",
        )

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()

    try:
        experiment_dir = normalize_experiment_dir(
            args.experiment_dir
        )
    except ValidationFailure as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2

    if args.command == "validate":
        return command_validate(experiment_dir)

    if args.command == "promote-check":
        return command_promote_check(
            experiment_dir
        )

    if args.command == "summary":
        return command_summary(experiment_dir)

    parser.error(
        f"Unsupported command: {args.command}"
    )

    return 2


if __name__ == "__main__":
    sys.exit(main())