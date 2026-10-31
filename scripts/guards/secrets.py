#!/usr/bin/env python3
"""No secrets or forbidden files in git (docs/conventions/git.md).

Fails on files named like signing keys or local settings (`*.jks`, `*.keystore`,
`keystore.properties`, `secrets.properties`, `local.properties`, `.env*`), on private-key PEM
blocks and on well-known API key and token formats in any text file.
"""

import os
import re
import sys

sys.dont_write_bytecode = True  # no __pycache__ in the working tree
import guardlib  # noqa: E402

NAME = "secrets"

FORBIDDEN_NAMES = [
    (re.compile(r"\.(jks|keystore)$"), "signing key"),
    (re.compile(r"^(keystore|secrets|local)\.properties$"), "local settings or secrets"),
    (re.compile(r"^\.env"), "environment file"),
]

CONTENT_PATTERNS = [
    (re.compile(r"-----BEGIN (?:[A-Z0-9]+ )*PRIVATE KEY(?: BLOCK)?-----"), "private key"),
    (re.compile(r"\bAKIA[0-9A-Z]{16}\b"), "AWS access key id"),
    (re.compile(r"\bAIza[0-9A-Za-z_\-]{35}"), "Google API key"),
    (re.compile(r"\bgh[pousr]_[A-Za-z0-9]{36,}"), "GitHub token"),
    (re.compile(r"\bgithub_pat_[A-Za-z0-9_]{40,}"), "GitHub token"),
    (re.compile(r"\bxox[abprs]-[A-Za-z0-9-]{10,}"), "Slack token"),
    (re.compile(r"\b[rs]k_live_[0-9A-Za-z]{20,}"), "Stripe live key"),
    (re.compile(r"\bsk-ant-[A-Za-z0-9_\-]{20,}"), "Anthropic API key"),
    (re.compile(r"\bsk-(?:proj-)?[A-Za-z0-9_\-]{32,}"), "OpenAI-style API key"),
    (re.compile(r"\bglpat-[A-Za-z0-9_\-]{20,}"), "GitLab token"),
    (re.compile(r"\bhttps?://[^/\s:@]+:[^/\s:@]{6,}@"), "password in a URL"),
    # A long random-looking value assigned to something named like a secret.
    (re.compile(r"(?i)\b[\w.-]*(?:api[_-]?key|secret|passw(?:or)?d|token)[\w.-]*\s*[:=]\s*"
                r"[\"']?(?=[A-Za-z0-9+/_\-]*[0-9])(?=[A-Za-z0-9+/_\-]*[A-Za-z])[A-Za-z0-9+/_\-]{24,}"),
     "secret-like value"),
]


def main(argv):
    report = guardlib.Reporter(NAME)
    for full, rel in guardlib.files_from_args(argv):
        base = os.path.basename(rel)
        for pattern, kind in FORBIDDEN_NAMES:
            if pattern.search(base):
                report.fail(rel, 0, f"{kind} must never be in git: remove it and add it to .gitignore")
        if guardlib.is_binary(full):
            continue
        try:
            text = guardlib.read_text(full)
        except UnicodeDecodeError:
            continue  # the english-only guard reports files that are not UTF-8
        for number, line in enumerate(text.splitlines(), start=1):
            for pattern, kind in CONTENT_PATTERNS:
                if pattern.search(line):
                    report.fail(rel, number, f"looks like a {kind}: move it out of git "
                                             f"(local.properties or an environment variable)")
                    break
    report.finish()


if __name__ == "__main__":
    main(sys.argv[1:])
