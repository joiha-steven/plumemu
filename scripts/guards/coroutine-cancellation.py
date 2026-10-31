#!/usr/bin/env python3
"""A broad `catch` in coroutine code must let `CancellationException` through (Kotlin main sources).

Cancelling a coroutine throws `CancellationException` at its next suspension point. A `catch`
that also matches it and carries on turns "cancelled" into "failed, go on": a scan that the person
stopped keeps running, a screen that left keeps loading. 
A catch of `Exception`, `Throwable`, `RuntimeException` or `IllegalStateException` (also
`kotlin.`/`java.lang.` qualified, also with `_` as the name; `CancellationException` is an
`IllegalStateException`) inside coroutine code passes only when one of these holds:
- an earlier `catch (e: CancellationException) { ... }` of the same `try` (it takes the
  cancellation before the broad one can; GameLauncher's `e.rethrowIfCallerCancelled()` and
  GraphRestore's `rethrow = e` handle it there);
- its own body contains `throw <the caught name>` (`if (e is CancellationException) throw e`);
- its own body calls `ensureActive()` (also `currentCoroutineContext().ensureActive()`).

Coroutine code, kept simple on purpose, is the body of
- a `suspend fun`, block or expression body (a nested plain `fun` inside it is not), or
- a lambda whose call is one of `launch`, `async`, `withContext`, `flow`, `channelFlow`,
  `callbackFlow`, `produce`, `runBlocking`, `coroutineScope`, `supervisorScope`, `withTimeout`,
  `withTimeoutOrNull`, `LaunchedEffect`, `produceState`, `runInterruptible`, or a lambda written
  `suspend { ... }`.
Known limits: a lambda of a function that takes a `suspend` parameter under another name is not
recognised; a `catch` around code that can never suspend (pure parsing) in a suspend fun is still
reported (move it into a plain function, or rethrow: it costs one line). A `catch` of a specific
exception (`IOException`, `SecurityException`, ...) never matches `CancellationException` and
passes. Test sources are exempt.
"""

import re
import sys

sys.dont_write_bytecode = True  # no __pycache__ in the working tree
import guardlib  # noqa: E402

NAME = "coroutine-cancellation"

BROAD = r"(?:kotlin\.|java\.lang\.)?(?:Exception|Throwable|RuntimeException|IllegalStateException)"
CATCH = re.compile(r"\bcatch\s*\(\s*(\w+)\s*:\s*([\w.]+)\s*\)\s*\{")
SUSPEND_FUN = re.compile(r"\bsuspend\s+fun\b")
PLAIN_FUN = re.compile(r"\bfun\b")
COROUTINE_CALL = re.compile(
    r"\b(?:launch|async|withContext|flow|channelFlow|callbackFlow|produce|runBlocking|coroutineScope|"
    r"supervisorScope|withTimeout|withTimeoutOrNull|LaunchedEffect|produceState|runInterruptible)\b"
    r"|\bsuspend\s*$"
)
STATEMENT_START = re.compile(r"[;{}]")


def matching_braces(bare):
    """(open -> close, close -> open) offsets of every `{` `}` pair."""
    opens, close_of, open_of = [], {}, {}
    for i, ch in enumerate(bare):
        if ch == "{":
            opens.append(i)
        elif ch == "}" and opens:
            start = opens.pop()
            close_of[start] = i
            open_of[i] = start
    return close_of, open_of


def header(bare, brace):
    """The text between the previous statement boundary and the `{` at `brace`."""
    start = brace
    while start > 0 and not STATEMENT_START.match(bare[start - 1]):
        start -= 1
    return bare[start:brace]


def try_start(bare, catch_start, open_of):
    """Offset of the `try` that the catch at `catch_start` belongs to."""
    i = catch_start - 1
    while i >= 0 and bare[i].isspace():
        i -= 1
    while i >= 0 and bare[i] == "}":
        opening = open_of.get(i)
        if opening is None:
            return catch_start
        head = bare[:opening].rstrip()
        if head.endswith("try"):
            return len(head) - 3
        j = head.rfind("catch")
        if j < 0:
            return catch_start
        i = j - 1
        while i >= 0 and bare[i].isspace():
            i -= 1
    return catch_start


def expression_body_head(bare, start):
    """The line of `start` and the lines before it that end in `=`: `suspend fun f() =\n try {`."""
    lines = bare[:start].split("\n")
    text = lines[-1]
    for earlier in reversed(lines[:-1]):
        if earlier.rstrip().endswith("="):
            text = earlier + "\n" + text
        elif earlier.strip():
            break
    return text


def in_coroutine(bare, offset, close_of, open_of):
    """True when the innermost enclosing function or coroutine lambda of `offset` is coroutine code."""
    head = expression_body_head(bare, try_start(bare, offset, open_of))
    if SUSPEND_FUN.search(head):
        return True
    if PLAIN_FUN.search(head):
        return False
    enclosing = [b for b, c in close_of.items() if b < offset < c]
    for brace in sorted(enclosing, reverse=True):  # innermost first
        head = header(bare, brace)
        if SUSPEND_FUN.search(head):
            return True
        fun = PLAIN_FUN.search(head)
        if fun:
            # `fun f() = withContext {` : the lambda is what encloses; `fun f() {` : the function does.
            equals = head.find("=", fun.end())
            if equals < 0:
                return False
            head = head[equals + 1:]
        if COROUTINE_CALL.search(head):
            return True
    return False


def earlier_cancellation_rethrow(bare, catch_start, close_of, open_of):
    """True when an earlier catch of the same try catches CancellationException and throws."""
    i = catch_start - 1
    while i >= 0 and bare[i].isspace():
        i -= 1
    while i >= 0 and bare[i] == "}":
        opening = open_of.get(i)
        if opening is None:
            return False
        head = bare[:opening].rstrip()
        if head.endswith("try"):
            return False
        m = re.search(r"\bcatch\s*\(\s*(\w+)\s*:\s*([\w.]+)\s*\)$", head)
        if not m:
            return False
        if m.group(2).split(".")[-1] == "CancellationException":
            return True  # it takes the cancellation first; what it does with it is its own business
        i = head.rfind("catch") - 1
        while i >= 0 and bare[i].isspace():
            i -= 1
    return False


def main(argv):
    report = guardlib.Reporter(NAME)
    broad = re.compile(rf"^{BROAD}$")
    for full, rel in guardlib.files_from_args(argv):
        if not guardlib.is_kotlin_main_source(rel):
            continue
        code, bare = guardlib.strip_kotlin(guardlib.read_text(full))
        if "catch" not in bare:
            continue
        close_of, open_of = matching_braces(bare)
        for m in CATCH.finditer(bare):
            name, kind = m.group(1), m.group(2)
            if not broad.match(kind) or not in_coroutine(bare, m.start(), close_of, open_of):
                continue
            body = bare[m.end() - 1:close_of.get(m.end() - 1, len(bare))]
            if re.search(rf"\bthrow\s+{re.escape(name)}\b", body) and name != "_":
                continue
            if re.search(r"\bensureActive\s*\(", body):
                continue
            if earlier_cancellation_rethrow(bare, m.start(), close_of, open_of):
                continue
            report.fail(
                rel,
                guardlib.line_of(code, m.start()),
                f"catch ({name}: {kind}) in coroutine code swallows CancellationException: add "
                "`catch (e: CancellationException) { throw e }` before it, or `if (e is "
                "CancellationException) throw e` / `ensureActive()` in it",
            )
    report.finish()


if __name__ == "__main__":
    main(sys.argv[1:])
