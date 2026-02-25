#!/usr/bin/env python3
"""Erzeugt ein Review-Bundle fuer Code-Review + Doku-Sync.

Das Skript ist bewusst konservativ:
- Es aendert keinen Produktivcode.
- Es erstellt nur Report-Dateien.
- Es kann Smoke-Checks ausfuehren und Ergebnisse sammeln.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import re
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


DEFAULT_SMOKES = [
  "mvn -DskipTests compile",
  "mvn -Dtest=CsvParserTest,CsvImportServiceTest,TransactionViewServiceTest test",
]

CODE_EXTENSIONS = {
  ".java",
  ".kt",
  ".groovy",
  ".py",
  ".js",
  ".ts",
  ".tsx",
  ".jsx",
  ".html",
  ".css",
  ".sql",
}


@dataclass
class CommandResult:
  command: str
  return_code: int
  output: str

  @property
  def status(self) -> str:
    return "pass" if self.return_code == 0 else "fail"


@dataclass
class NamedCheckResult:
  name: str
  result: CommandResult


def run_shell(command: str, cwd: Path) -> CommandResult:
  completed = subprocess.run(
    command,
    cwd=cwd,
    shell=True,
    text=True,
    capture_output=True,
    check=False,
  )
  output = (completed.stdout or "") + ("\n" + completed.stderr if completed.stderr else "")
  return CommandResult(command=command, return_code=completed.returncode, output=output.strip())


def run_git(args: list[str], cwd: Path) -> str:
  completed = subprocess.run(
    ["git", *args],
    cwd=cwd,
    text=True,
    capture_output=True,
    check=False,
  )
  if completed.returncode != 0:
    return ""
  return completed.stdout.strip()


def parse_status_porcelain(raw: str) -> tuple[list[str], list[str], list[str]]:
  staged: list[str] = []
  unstaged: list[str] = []
  untracked: list[str] = []

  for line in raw.splitlines():
    if not line.strip():
      continue
    if line.startswith("?? "):
      untracked.append(line[3:].strip())
      continue

    if len(line) < 4:
      continue
    index_status = line[0]
    worktree_status = line[1]
    path = line[3:].strip()

    if index_status != " ":
      staged.append(path)
    if worktree_status != " ":
      unstaged.append(path)

  return staged, unstaged, untracked


def collect_changed_files(cwd: Path) -> list[str]:
  files: set[str] = set()
  for args in (["diff", "--name-only"], ["diff", "--staged", "--name-only"]):
    raw = run_git(list(args), cwd)
    for line in raw.splitlines():
      line = line.strip()
      if line:
        files.add(line)
  return sorted(files)


def normalize_line(line: str) -> str:
  stripped = line.strip()
  if not stripped:
    return ""

  comment_prefixes = ("//", "#", "/*", "*", "<!--")
  if stripped.startswith(comment_prefixes):
    return ""

  stripped = re.sub(r"\s+", " ", stripped)
  return stripped


def detect_duplicate_windows(
  cwd: Path,
  files: Iterable[str],
  window_size: int,
  max_results: int,
) -> list[dict]:
  windows: dict[tuple[str, ...], list[tuple[str, int]]] = {}

  for rel_path in files:
    path = cwd / rel_path
    if not path.exists() or not path.is_file():
      continue
    if path.suffix.lower() not in CODE_EXTENSIONS:
      continue

    try:
      lines = path.read_text(encoding="utf-8", errors="ignore").splitlines()
    except OSError:
      continue

    normalized: list[tuple[int, str]] = []
    for index, raw_line in enumerate(lines, start=1):
      norm = normalize_line(raw_line)
      if norm:
        normalized.append((index, norm))

    if len(normalized) < window_size:
      continue

    for i in range(len(normalized) - window_size + 1):
      chunk = tuple(item[1] for item in normalized[i : i + window_size])
      start_line = normalized[i][0]
      windows.setdefault(chunk, []).append((rel_path, start_line))

  duplicates: list[dict] = []
  for snippet, occurrences in windows.items():
    unique_locs = sorted(set(occurrences))
    if len(unique_locs) < 2:
      continue
    duplicates.append(
      {
        "occurrences": len(unique_locs),
        "locations": [
          {"file": file_path, "line": line_no} for file_path, line_no in unique_locs[:8]
        ],
        "snippet": list(snippet[:4]),
      }
    )

  duplicates.sort(key=lambda entry: entry["occurrences"], reverse=True)
  return duplicates[:max_results]


def derive_module_hints(paths: Iterable[str]) -> list[str]:
  modules: set[str] = set()
  pattern = re.compile(r"src/main/java/com/example/finanzapp/([^/]+)/")
  for path in paths:
    match = pattern.search(path)
    if match:
      modules.add(match.group(1))
  return sorted(modules)


def write_text(path: Path, content: str) -> None:
  path.parent.mkdir(parents=True, exist_ok=True)
  path.write_text(content, encoding="utf-8")


def create_readme_draft(
  out_dir: Path,
  changed_files: list[str],
  modules: list[str],
  smoke_results: list[CommandResult],
  extra_checks: list[NamedCheckResult],
) -> None:
  lines = [
    "# README Update Draft",
    "",
    f"Stand: {dt.datetime.now().strftime('%Y-%m-%d %H:%M')}",
    "",
    "## Vorgeschlagene Ergaenzungen",
    "",
  ]

  if modules:
    lines.append("- Betroffene Module/Funktionsbereiche: " + ", ".join(modules) + ".")
  else:
    lines.append("- Betroffene Funktionsbereiche aus Diff ergaenzen.")

  lines.extend(
    [
      "- Fachliche oder technische Aenderungen in 3-5 Stichpunkten ergaenzen.",
      "- Falls Verhalten geaendert wurde: kurze Vorher/Nachher-Erklaerung hinzufuegen.",
      "",
      "## Verifikation",
      "",
    ]
  )

  for result in smoke_results:
    lines.append(f"- `{result.command}` -> {result.status}")

  for check in extra_checks:
    lines.append(f"- `{check.name}` (`{check.result.command}`) -> {check.result.status}")

  lines.extend(
    [
      "",
      "## Geaenderte Dateien (Auszug)",
      "",
    ]
  )

  for path in changed_files[:20]:
    lines.append(f"- `{path}`")

  write_text(out_dir / "README_UPDATE_DRAFT.md", "\n".join(lines) + "\n")


def create_changelog_draft(out_dir: Path, modules: list[str]) -> None:
  lines = [
    "# CHANGELOG Draft",
    "",
    "## [Unreleased]",
    "",
    "### Added",
    "",
    "-",
    "",
    "### Changed",
    "",
  ]

  if modules:
    lines.append("- Aktualisiert: " + ", ".join(modules) + ".")
  else:
    lines.append("- Technische und fachliche Aenderungen aus dem aktuellen Diff ergaenzen.")

  lines.extend(
    [
      "",
      "### Fixed",
      "",
      "- Fehlerbehebungen aus dem Review eintragen.",
      "",
      "### Notes",
      "",
      "- Eintraege kurz und user-zentriert formulieren.",
      "- Reine interne Refactorings nur aufnehmen, wenn sie Verhalten oder Stabilitaet beeinflussen.",
    ]
  )

  write_text(out_dir / "CHANGELOG_DRAFT.md", "\n".join(lines) + "\n")


def tail_text(text: str, max_lines: int = 20) -> str:
  lines = text.splitlines()
  if len(lines) <= max_lines:
    return text
  return "\n".join(lines[-max_lines:])


def main() -> None:
  parser = argparse.ArgumentParser(description="Build review bundle for approval-gated workflow")
  parser.add_argument("--base-branch", default="main", help="Base branch for context")
  parser.add_argument("--output-dir", default=".opencode/review/latest", help="Output directory")
  parser.add_argument(
    "--smoke",
    action="append",
    default=[],
    help="Smoke command (can be passed multiple times)",
  )
  parser.add_argument("--skip-smoke", action="store_true", help="Skip running smoke commands")
  parser.add_argument("--run-cpd", action="store_true", help="Run PMD CPD duplicate check")
  parser.add_argument(
    "--cpd-min-tokens",
    type=int,
    default=100,
    help="Minimum token threshold for PMD CPD",
  )
  parser.add_argument(
    "--cpd-command",
    default="",
    help="Custom CPD command (overrides --cpd-min-tokens)",
  )
  parser.add_argument("--run-owasp", action="store_true", help="Run OWASP dependency check")
  parser.add_argument(
    "--owasp-command",
    default="mvn org.owasp:dependency-check-maven:check",
    help="Custom OWASP command",
  )
  parser.add_argument("--draft-docs", action="store_true", help="Generate README/CHANGELOG drafts")
  parser.add_argument(
    "--duplicate-window",
    type=int,
    default=8,
    help="Window size in normalized lines for duplicate indicator",
  )
  parser.add_argument("--max-duplicates", type=int, default=10, help="Maximum duplicate clusters")
  args = parser.parse_args()

  cwd = Path.cwd()
  out_dir = (cwd / args.output_dir).resolve()
  out_dir.mkdir(parents=True, exist_ok=True)

  now = dt.datetime.now().isoformat(timespec="seconds")
  branch = run_git(["rev-parse", "--abbrev-ref", "HEAD"], cwd) or "unknown"
  status_raw = run_git(["status", "--short"], cwd)
  staged, unstaged, untracked = parse_status_porcelain(status_raw)
  changed_files = collect_changed_files(cwd)
  modules = derive_module_hints(changed_files)

  smoke_commands = args.smoke if args.smoke else DEFAULT_SMOKES
  smoke_results: list[CommandResult] = []
  if not args.skip_smoke:
    for command in smoke_commands:
      smoke_results.append(run_shell(command, cwd))

  extra_checks: list[NamedCheckResult] = []
  if args.run_cpd:
    minimum_tokens = max(30, args.cpd_min_tokens)
    cpd_command = (
      args.cpd_command.strip()
      if args.cpd_command.strip()
      else f"mvn org.apache.maven.plugins:maven-pmd-plugin:3.22.0:cpd-check -DminimumTokens={minimum_tokens}"
    )
    extra_checks.append(
      NamedCheckResult(name="pmd-cpd", result=run_shell(cpd_command, cwd))
    )

  if args.run_owasp:
    owasp_command = args.owasp_command.strip() or "mvn org.owasp:dependency-check-maven:check"
    extra_checks.append(
      NamedCheckResult(name="owasp-dependency-check", result=run_shell(owasp_command, cwd))
    )

  duplicate_candidates = detect_duplicate_windows(
    cwd=cwd,
    files=changed_files,
    window_size=max(4, args.duplicate_window),
    max_results=max(1, args.max_duplicates),
  )

  readme_path = cwd / "README.md"
  changelog_path = cwd / "CHANGELOG.md"
  readme_exists = readme_path.exists()
  changelog_exists = changelog_path.exists()
  readme_in_diff = "README.md" in changed_files
  changelog_in_diff = "CHANGELOG.md" in changed_files

  report = {
    "generated_at": now,
    "branch": branch,
    "base_branch": args.base_branch,
    "counts": {
      "changed": len(changed_files),
      "staged": len(staged),
      "unstaged": len(unstaged),
      "untracked": len(untracked),
      "duplicate_candidates": len(duplicate_candidates),
    },
    "changed_files": changed_files,
    "staged_files": staged,
    "unstaged_files": unstaged,
    "untracked_files": untracked,
    "modules": modules,
    "smoke_results": [
      {
        "command": item.command,
        "status": item.status,
        "return_code": item.return_code,
        "tail": tail_text(item.output, max_lines=20),
      }
      for item in smoke_results
    ],
    "extra_checks": [
      {
        "name": check.name,
        "command": check.result.command,
        "status": check.result.status,
        "return_code": check.result.return_code,
        "tail": tail_text(check.result.output, max_lines=20),
      }
      for check in extra_checks
    ],
    "docs": {
      "readme_exists": readme_exists,
      "changelog_exists": changelog_exists,
      "readme_in_diff": readme_in_diff,
      "changelog_in_diff": changelog_in_diff,
    },
    "duplicate_candidates": duplicate_candidates,
  }

  write_text(out_dir / "review-report.json", json.dumps(report, indent=2, ensure_ascii=True) + "\n")

  smoke_rows: list[str] = []
  if smoke_results:
    for item in smoke_results:
      smoke_rows.append(f"| `{item.command}` | {item.status} | {item.return_code} |")
  else:
    smoke_rows.append("| _skipped_ | warn | n/a |")

  extra_rows: list[str] = []
  if extra_checks:
    for check in extra_checks:
      extra_rows.append(
        f"| `{check.name}` | `{check.result.command}` | {check.result.status} | {check.result.return_code} |"
      )
  else:
    extra_rows.append("| _none_ | _not requested_ | warn | n/a |")

  md_lines = [
    "# Review Report",
    "",
    f"Erstellt am: {now}",
    f"Branch: `{branch}`",
    f"Base-Branch: `{args.base_branch}`",
    "",
    "## Scope",
    "",
    f"- Geaenderte Dateien: {len(changed_files)}",
    f"- Staged: {len(staged)}",
    f"- Unstaged: {len(unstaged)}",
    f"- Untracked: {len(untracked)}",
    "",
    "## Smoke Gates",
    "",
    "| Command | Status | Code |",
    "|---|---|---|",
    *smoke_rows,
    "",
    "## Zusatzchecks",
    "",
    "| Check | Command | Status | Code |",
    "|---|---|---|---|",
    *extra_rows,
    "",
    "## Duplicate Candidates (indikativ)",
    "",
  ]

  if duplicate_candidates:
    for idx, dup in enumerate(duplicate_candidates, start=1):
      loc = dup["locations"][0]
      md_lines.append(
        f"{idx}. Treffer={dup['occurrences']} erstfund bei `{loc['file']}:{loc['line']}`"
      )
  else:
    md_lines.append("- Keine auffaelligen Duplikat-Cluster im aktuellen Diff-Scope gefunden.")

  md_lines.extend(
    [
      "",
      "## Dokumentation",
      "",
      f"- README vorhanden: {'ja' if readme_exists else 'nein'}",
      f"- CHANGELOG vorhanden: {'ja' if changelog_exists else 'nein'}",
      f"- README im Diff: {'ja' if readme_in_diff else 'nein'}",
      f"- CHANGELOG im Diff: {'ja' if changelog_in_diff else 'nein'}",
      "",
      "## Freigabe-Gate",
      "",
      "Vor Anwendung von Fixes bestaetigen:",
      "",
      "`Genehmigt: Fixes + README + CHANGELOG wie vorgeschlagen umsetzen.`",
      "",
      "## Artefakte",
      "",
      "- `review-report.json`",
      "- `README_UPDATE_DRAFT.md` (falls --draft-docs)",
      "- `CHANGELOG_DRAFT.md` (falls --draft-docs)",
    ]
  )

  write_text(out_dir / "review-report.md", "\n".join(md_lines) + "\n")

  if args.draft_docs:
    create_readme_draft(out_dir, changed_files, modules, smoke_results, extra_checks)
    create_changelog_draft(out_dir, modules)

  print(f"Review-Bundle erstellt unter: {out_dir}")


if __name__ == "__main__":
  main()
