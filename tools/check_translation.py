"""Check a Minecraft language contribution against en_us; no translation or network calls."""
import argparse
from collections import Counter
import json
from pathlib import Path
import re


def read_language(path):
    duplicates = []

    def pairs(items):
        data = {}
        for key, value in items:
            if key in data:
                duplicates.append(key)
            data[key] = value
        return data

    return json.loads(path.read_text(encoding="utf-8-sig"), object_pairs_hook=pairs), duplicates


def placeholders(value):
    # Minecraft permits numbered %s arguments to be reordered by translators.
    result = Counter()
    automatic = 0
    for match in re.finditer(r"%(?:(\d+)\$)?([A-Za-z%]|$)", value):
        if match[2] == "%":
            result[("literal_percent", "%")] += 1
        else:
            if match[1] is None:
                automatic += 1
            result[(int(match[1]) if match[1] else automatic, match[2])] += 1
    result.update(re.findall(r"\{(?:\d+|\w+)\}", value))
    return result


def check(source, target):
    reference, source_duplicates = read_language(source)
    translation, duplicates = read_language(target)
    errors, warnings = [], []
    for key in source_duplicates + duplicates:
        errors.append({"key": key, "type": "duplicate_key"})
    for key, value in translation.items():
        if not isinstance(value, str):
            errors.append({"key": key, "type": "non_string"})
        elif key not in reference:
            warnings.append({"key": key, "type": "unknown_key"})
        elif placeholders(value) != placeholders(reference[key]):
            errors.append({"key": key, "type": "placeholder_mismatch",
                           "source": reference[key], "translation": value})
        elif "\ufffd" in value:
            errors.append({"key": key, "type": "replacement_character"})
        else:
            if reference[key] and (len(value) > len(reference[key]) * 1.5 or len(value) < len(reference[key]) * .5):
                warnings.append({"key": key, "type": "length_variance"})
            def numbers(text):
                text = re.sub(r"%(?:(\d+)\$)?[A-Za-z%]", "", text)
                return Counter(re.findall(r"(?<!\w)\d+(?:[.,]\d+)?(?!\w)", text))
            if numbers(value) != numbers(reference[key]):
                warnings.append({"key": key, "type": "numeric_variance"})
    missing = sorted(reference.keys() - translation.keys())
    return {"source": str(source), "translation": str(target), "total_keys": len(translation),
            "critical_errors": len(errors), "missing_keys": missing, "warnings": len(warnings),
            "errors": errors, "warning_details": warnings}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("translation", type=Path)
    parser.add_argument("--report", type=Path)
    parser.add_argument("--require_complete", action="store_true")
    args = parser.parse_args()
    report = check(args.source, args.translation)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Keys: {report['total_keys']}; critical errors: {report['critical_errors']}; "
          f"missing: {len(report['missing_keys'])}; non-critical warnings: {report['warnings']}")
    raise SystemExit(bool(report["critical_errors"] or args.require_complete and report["missing_keys"]))
