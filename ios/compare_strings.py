#!/usr/bin/env python3
"""Lists the user-visible text that differs between the Android and the iOS apps.

    python3 ios/compare_strings.py                      # Android from origin/develop, iOS as checked out
    python3 ios/compare_strings.py --ios-ref 306a5664   # iOS from a commit
    python3 ios/compare_strings.py --ignored ios/strings-ignored.txt

Neither app has string resources, the text is in the code, so this reads the string literals of both
and keeps the ones that look like text. Interpolations become {}, numbers too: Android's
"for ${minutes} min" and iOS's "for 10 min" are the same text. Strings glued with + count as one, and
the strings inside an interpolation count on their own.

It only knows about text. A layout, a colour or a state showing the wrong text isn't caught, and a
string can be in both apps yet shown at different times. Strings with the same words but other
placeholders count as the same: Android often builds a sentence from pieces, so "Clear all sensors
from" and "Clear all sensors from {}?" are too. Previews, logs, exceptions and the adb tools' messages
are left out. Exits with 1 when it finds a difference,
leaving out the lines of --ignored: one per string as this prints it, for text accepted as different.
"""
import argparse
import difflib
import re
import subprocess
import sys
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path

NBSP = "\u00a0"
PLACEHOLDER = "{}"
SIMILAR = 0.75

MIN_SIMILAR_LENGTH = 8

ANDROID_FILES = re.compile(r"^(app/phone|core/ui|core/common|data/\w+|feature/[\w-]+)/src/(main|debug)/kotlin/.*\.kt$")
# Only reached through adb, by whoever develops the app
ANDROID_DEV_TOOLS = re.compile(r"/(MockAdvertisements|MockSensor)\.kt$")
IOS_FILES = re.compile(r"^ios/(App/Sources|Packages/TPMSCore/Sources)/.*\.swift$")

# What comes right before a literal which isn't shown to the user
NOT_UI_CONTEXT = re.compile(
    r"(\b(error|require|check|requireNotNull|checkNotNull|fatalError|precondition|preconditionFailure|assert|"
    r"print|println|debugPrint|testTag|withTag|getSharedPreferences|intPreferencesKey|Suppress|OptIn|"
    r"Image|UIImage|NSPredicate|Regex|URL|Logger|Action|Intent|setAction|forKey|systemName|systemImage|named|"
    r"forResource|withExtension|key|tag|channelId|identifier|id|databaseName|name|prepare|execute|exec|query)"
    r"\s*[(:=]\s*(\w+\s*:\s*)?(\{\s*)?$"
    r"|\b(logger|Logger|log|Log)\b[.\w]*\s*[({]\s*(\w+\s*=\s*)?(\{\s*)?$"
    # A Kermit call, logger.w(error) { "..." }, or an exception
    r"|\.[vdiwe]\s*(\([^()]*\))?\s*\{\s*$"
    r"|\w*(Exception|Error)\(\s*(\w+\s*=\s*)?$"
    r"|\b(const\s+val\s+\w+\s*=|private\s+static\s+let\s+\w*[Kk]ey\w*\s*=|import\s+)\s*$)"
)
SQL = re.compile(r"^\s*(SELECT|INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|PRAGMA|BEGIN|COMMIT|ATTACH|DETACH)\b")



@dataclass(frozen=True)
class Literal:
    text: str
    file: str
    line: int
    before: str


def scan(source: str, kotlin: bool) -> list[Literal]:
    """Every string literal of [source], with its interpolations as {} and the file left to the caller"""
    found: list[tuple[int, str]] = []

    def skip_comment(i: int) -> int | None:
        if source.startswith("//", i):
            end = source.find("\n", i)
            return len(source) if end < 0 else end
        if source.startswith("/*", i):
            depth, i = 1, i + 2
            while i < len(source) and depth:
                if source.startswith("/*", i):
                    depth, i = depth + 1, i + 2
                elif source.startswith("*/", i):
                    depth, i = depth - 1, i + 2
                else:
                    i += 1
            return i
        return None

    def code(i: int, closing: str | None) -> int:
        """Reads code up to the bracket closing it, nil for the whole source, recording its literals"""
        depth = 0
        while i < len(source):
            char = source[i]
            if (end := skip_comment(i)) is not None:
                i = end
            elif char == '"':
                i = literal(i)
            elif kotlin and char == "'":
                # A char literal, '"' being what matters
                match = re.compile(r"'(\\u[0-9a-fA-F]{4}|\\.|[^\\'])'").match(source, i)
                i = match.end() if match else i + 1
            elif closing and char in "({":
                depth, i = depth + 1, i + 1
            elif closing and char == closing and depth == 0:
                return i + 1
            elif closing and char in ")}":
                depth, i = depth - 1, i + 1
            else:
                i += 1
        return i

    def string(i: int) -> tuple[str, int]:
        """The string starting at [i], one of its quotes, and where it ends"""
        triple = source.startswith('"""', i)
        quote = '"""' if triple else '"'
        i += len(quote)
        text = []
        while i < len(source):
            if source.startswith(quote, i):
                return "".join(text), i + len(quote)
            char = source[i]
            if char == "\\" and not (kotlin and triple):
                next_char = source[i + 1] if i + 1 < len(source) else ""
                if next_char == "u":
                    match = re.compile(r"\\u(?:\{([0-9a-fA-F]+)\}|([0-9a-fA-F]{4}))").match(source, i)
                    if match:
                        text.append(chr(int(match.group(1) or match.group(2), 16)))
                        i = match.end()
                        continue
                if not kotlin and next_char == "(":
                    text.append(PLACEHOLDER)
                    i = code(i + 2, ")")
                    continue
                text.append({"n": "\n", "t": "\t"}.get(next_char, next_char))
                i += 2
            elif kotlin and source.startswith("${", i):
                text.append(PLACEHOLDER)
                i = code(i + 2, "}")
            elif kotlin and char == "$" and (match := re.compile(r"\$[A-Za-z_]\w*").match(source, i)):
                text.append(PLACEHOLDER)
                i = match.end()
            else:
                text.append(char)
                i += 1
        return "".join(text), i

    def literal(start: int) -> int:
        """The string at [start] with the ones glued to it by +"""
        slot = len(found)
        found.append((start, ""))
        text, end = string(start)
        while match := re.compile(r'\s*\+\s*(?=")').match(source, end):
            more, end = string(match.end())
            text += more
        found[slot] = (start, text)
        return end

    code(0, None)
    previews = [match.span() for match in re.finditer(r"^(@Preview|#Preview)\b.*?(\n\n(?=\S)|\Z)", source, re.DOTALL | re.MULTILINE)]
    return [
        Literal(text, "", source.count("\n", 0, start) + 1, source[max(0, start - 80):start])
        for start, text in found
        if not any(first <= start < last for first, last in previews)
    ]


def looks_like_text(literal: Literal) -> bool:
    text = literal.text.strip()
    letters = text.replace(PLACEHOLDER, "")
    if sum(char.isalpha() for char in letters) < 2:
        return False
    if SQL.match(text) or NOT_UI_CONTEXT.search(literal.before):
        return False
    if re.search(r"[ " + NBSP + r"]", text):
        # Words, not a format nor a path
        return not re.fullmatch(r"[^a-zA-Z]*", text) and "://" not in text
    # A single word: capitalised and nothing else, "Done", rather than an identifier
    return bool(re.fullmatch(r"[A-Z][a-z]+\W*", text))


def files(ref: str | None, pattern: re.Pattern[str]) -> dict[str, str]:
    def run(*command: str) -> str:
        return subprocess.run(command, capture_output=True, text=True, check=True).stdout

    if ref is None:
        return {
            path: Path(path).read_text()
            for path in run("git", "ls-files").splitlines()
            if pattern.match(path) and Path(path).exists() and not ANDROID_DEV_TOOLS.search(path)
        }
    # All in one process, a git per file takes minutes
    paths = [path for path in run("git", "ls-tree", "-r", "--name-only", ref).splitlines() if pattern.match(path) and not ANDROID_DEV_TOOLS.search(path)]
    output = subprocess.run(
        ["git", "cat-file", "--batch"],
        input="".join(f"{ref}:{path}\n" for path in paths).encode(),
        capture_output=True,
        check=True,
    ).stdout
    contents = {}
    position = 0
    for path in paths:
        # Each is a line "<sha> blob <size>", its bytes and a line break
        header_end = output.index(b"\n", position)
        size = int(output[position:header_end].split()[2])
        contents[path] = output[header_end + 1:header_end + 1 + size].decode()
        position = header_end + 1 + size + 1
    return contents


def texts(sources: dict[str, str], kotlin: bool) -> dict[str, list[str]]:
    """The places of each text, by the text with its numbers made placeholders"""
    places = defaultdict(list)
    for path, source in sources.items():
        for literal in scan(source, kotlin):
            if looks_like_text(literal):
                key = re.sub(r"(?<![\w.])\d+(\.\d+)?(?![\w.])", PLACEHOLDER, literal.text.strip())
                places[key].append(f"{path}:{literal.line}")
    return places


def loose(text: str) -> str:
    return re.sub(r"\s+", " ", text.replace(NBSP, " ")).strip().casefold()


def words(text: str) -> str:
    return " ".join(re.findall(r"\w+", loose(text).replace(PLACEHOLDER, " ")))


def shown(text: str) -> str:
    """With what can't be seen made visible"""
    return text.replace(NBSP, "\u237d").replace("\n", "\u23ce")


@dataclass
class Comparison:
    same: int
    spacing_or_case: list[tuple[str, str]]
    worded_differently: list[tuple[str, str]]
    android_only: list[str]
    ios_only: list[str]


def compare(android: dict[str, list[str]], ios: dict[str, list[str]]) -> Comparison:
    same = android.keys() & ios.keys()
    android_left = set(android) - same
    ios_left = set(ios) - same

    by_loose = {loose(text): text for text in sorted(ios_left)}
    spacing_or_case = []
    for text in sorted(android_left):
        if (other := by_loose.get(loose(text))) in ios_left:
            spacing_or_case.append((text, other))
            android_left.discard(text)
            ios_left.discard(other)

    by_words = {words(text): text for text in sorted(ios_left)}
    same_words = 0
    for text in sorted(android_left):
        if (other := by_words.get(words(text))) in ios_left:
            same_words += 1
            android_left.discard(text)
            ios_left.discard(other)

    # The closest pairs first, so each text gets its best match
    candidates = []
    for a in (text for text in android_left if len(loose(text)) >= MIN_SIMILAR_LENGTH):
        # Compares each with many: set_seq2 keeps what it learned of a, the quick ratios rule most out
        matcher = difflib.SequenceMatcher(None, autojunk=False)
        matcher.set_seq2(loose(a))
        for b in (text for text in ios_left if len(loose(text)) >= MIN_SIMILAR_LENGTH):
            matcher.set_seq1(loose(b))
            if matcher.real_quick_ratio() >= SIMILAR and matcher.quick_ratio() >= SIMILAR:
                candidates.append((matcher.ratio(), a, b))
    pairs = sorted(candidates, key=lambda pair: (-pair[0], pair[1], pair[2]))
    worded_differently = []
    for ratio, a, b in pairs:
        if ratio >= SIMILAR and a in android_left and b in ios_left:
            worded_differently.append((a, b))
            android_left.discard(a)
            ios_left.discard(b)

    return Comparison(len(same) + same_words, spacing_or_case, sorted(worded_differently), sorted(android_left), sorted(ios_left))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--android-ref", default="origin/develop")
    parser.add_argument("--ios-ref", help="The checked out files by default")
    parser.add_argument("--ignored", type=Path, help="A file of strings accepted as different, one per line")
    args = parser.parse_args()

    android = texts(files(args.android_ref, ANDROID_FILES), kotlin=True)
    ios = texts(files(args.ios_ref, IOS_FILES), kotlin=False)
    if not android or not ios:
        sys.exit("No text found: run from the repository's root, on a commit having ios/")
    result = compare(android, ios)

    ignored = set(args.ignored.read_text().splitlines()) if args.ignored else set()
    differences = 0

    def pairs(title: str, entries: list[tuple[str, str]]) -> None:
        nonlocal differences
        entries = [e for e in entries if shown(e[0]) not in ignored and shown(e[1]) not in ignored]
        differences += len(entries)
        print(f"\n## {title} ({len(entries)})")
        for a, b in entries:
            print(f"Android: {shown(a)}\n         {android[a][0]}\n    iOS: {shown(b)}\n         {ios[b][0]}\n")

    def only(title: str, entries: list[str], places: dict[str, list[str]]) -> None:
        nonlocal differences
        entries = [e for e in entries if shown(e) not in ignored]
        differences += len(entries)
        print(f"\n## {title} ({len(entries)})")
        by_file = defaultdict(list)
        for text in entries:
            by_file[places[text][0].split(":")[0]].append(text)
        for path in sorted(by_file):
            print(f"\n{path}")
            for text in by_file[path]:
                print(f"  {places[text][0].split(':')[1]}: {shown(text)}")

    print(f"{result.same} strings are the same in both apps. {chr(0x237d)} is a non-breaking space, {chr(0x23ce)} a line break.")
    pairs("Differ in spacing or case", result.spacing_or_case)
    pairs("Worded differently", result.worded_differently)
    only("Only on Android", result.android_only, android)
    only("Only on iOS", result.ios_only, ios)
    return 1 if differences else 0


if __name__ == "__main__":
    sys.exit(main())
