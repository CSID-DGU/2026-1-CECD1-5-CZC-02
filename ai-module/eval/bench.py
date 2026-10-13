"""모델 비교 틀 (L-0). 설정 조합만 바꿔 같은 평가셋을 돌리고 결과를 한 표로 모은다.

사용법 (ai-module 폴더에서):
    python -m eval.bench --list                         # 설정 목록과 기본 실행 대상 보기
    python -m eval.bench                                # 기본 대상(무료)만 실행
    python -m eval.bench --only rule,ollama-qwen2.5-7b  # 골라서 실행
    python -m eval.bench --only openai-analysis --allow-paid --limit 10   # 유료는 옵션을 켜야만
    python -m eval.bench --write-md                     # eval/MODELS.md 자동 표에 결과 반영

- 설정 목록: eval/bench_configs.json. 모델 이름은 코드가 아니라 여기의 환경 변수로만 바꾼다.
  설정에 적지 않은 모델 변수는 app/config.py 기본값으로 고정한다 (로컬 .env·셸 값은 무시 → PC가 달라도 같은 기준).
- 설정마다 별도 프로세스로 과제 평가 스크립트(메일: eval.run_eval)를 실행한다. app/config.py가 import 시점에
  환경 변수를 읽기 때문에, 같은 프로세스에서 설정을 바꿔 가며 돌리면 이전 값이 남는다.
- 유료 설정(LLM_PROVIDER=openai)은 --allow-paid와 --limit이 있어야만 돌고, 나머지 설정의 자식 프로세스에는
  OPENAI_API_KEY를 빈 값으로 넘겨 .env에 키가 있어도 OpenAI를 부르지 않는다. 데이터는 가짜 평가셋만 쓴다.
- 원자료: eval/results/bench_<시각>/ (git에 올라가지 않음). 요약 표: --write-md로 eval/MODELS.md.
"""
import argparse
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional, Sequence, Tuple

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")

EVAL_DIR = Path(__file__).resolve().parent
AI_MODULE_DIR = EVAL_DIR.parent
DEFAULT_CONFIGS = EVAL_DIR / "bench_configs.json"
MODELS_MD = EVAL_DIR / "MODELS.md"
RESULTS_DIR = EVAL_DIR / "results"

# 설정 파일에서 바꿀 수 있는 환경 변수 (app/config.py 기준). 키·비밀번호는 여기 넣을 수 없다.
ALLOWED_ENV_KEYS = {
    "LLM_PROVIDER",
    "OLLAMA_MODEL",
    "OLLAMA_TIMEOUT_SECONDS",
    "OLLAMA_TEMPERATURE",
    "OPENAI_MODEL_ANALYSIS",
    "OPENAI_REASONING_EFFORT",
    "OPENAI_TIMEOUT_SECONDS",
    "EMBEDDING_CLASSIFIER_ENABLED",
    "EMBEDDING_MODEL_NAME",
    "EMBEDDING_ACTION_THRESHOLD",
    "EMBEDDING_BUSINESS_THRESHOLD",
    "GLINER_ENABLED",
    "GLINER_MODEL_NAME",
    "GLINER_THRESHOLD",
}

CONFIG_PY = AI_MODULE_DIR / "app" / "config.py"


def code_defaults(path: Path = CONFIG_PY) -> Dict[str, str]:
    """app/config.py의 os.getenv("KEY", "기본값")에서 모델 설정 기본값을 읽는다.

    설정 파일에 없는 모델 변수는 이 값으로 고정해, 로컬 .env·셸 값과 무관하게 '지금 기본값'이 기준이 되게 한다.
    """
    pairs = re.findall(r'os\.getenv\(\s*"(\w+)"\s*,\s*"([^"]*)"\s*\)', path.read_text(encoding="utf-8"))
    return {key: value for key, value in pairs if key in ALLOWED_ENV_KEYS}

DEFAULT_PAID_BUDGET_USD = 1.0
CHILD_TIMEOUT_SECONDS = 60 * 60


@dataclass(frozen=True)
class TaskSpec:
    """평가 과제 하나. module은 --engine/--limit/--output/--allow-paid를 받아 결과 JSON을 쓰는 스크립트."""
    label: str
    module: str
    columns: Tuple[Tuple[str, str], ...]   # (summary 키, 표 머리글)
    headline: str


# 회의록(B-1 평가셋)·개체명(L-3)·할 일 분류(C)는 각 평가 스크립트가 생기면 여기에 추가한다.
TASKS: Dict[str, TaskSpec] = {
    "mail": TaskSpec(
        label="메일 분석 (eval/dataset.jsonl, 가짜 메일 21통)",
        module="eval.run_eval",
        columns=(
            ("allFieldsCorrect", "무수정 통과"),
            ("businessType", "영업 분류"),
            ("actionType", "처리 유형"),
            ("customerName", "고객사"),
            ("productName", "제품"),
            ("contactName", "담당자"),
            ("attendees", "참석자 F1"),
            ("scheduleDate", "일정 날짜"),
        ),
        headline="allFieldsCorrect",
    ),
}


@dataclass
class BenchConfig:
    name: str
    task: str
    engine: str
    env: Dict[str, str] = field(default_factory=dict)
    note: str = ""
    default: bool = False

    @property
    def paid(self) -> bool:
        return self.engine != "rule" and self.env.get("LLM_PROVIDER", "").lower() == "openai"


def load_configs(path: Path = DEFAULT_CONFIGS) -> List[BenchConfig]:
    raw = json.loads(path.read_text(encoding="utf-8"))
    configs = [BenchConfig(**entry) for entry in raw["configs"]]
    validate_configs(configs)
    return configs


def validate_configs(configs: Sequence[BenchConfig]) -> None:
    names = [config.name for config in configs]
    duplicates = {name for name in names if names.count(name) > 1}
    if duplicates:
        raise ValueError(f"설정 이름이 겹칩니다: {sorted(duplicates)}")
    for config in configs:
        if config.task not in TASKS:
            raise ValueError(f"{config.name}: 알 수 없는 과제 {config.task!r} (가능: {sorted(TASKS)})")
        if config.engine not in ("rule", "ollama"):
            raise ValueError(f"{config.name}: engine은 rule 또는 ollama")
        unknown = set(config.env) - ALLOWED_ENV_KEYS
        if unknown:
            raise ValueError(f"{config.name}: 설정 파일에서 바꿀 수 없는 환경 변수 {sorted(unknown)} "
                             "(키·비밀번호는 환경 변수로만 넘긴다)")
        if not all(isinstance(value, str) for value in config.env.values()):
            raise ValueError(f"{config.name}: env 값은 문자열이어야 합니다")


def select_configs(configs: Sequence[BenchConfig], only: Optional[str]) -> List[BenchConfig]:
    if not only:
        return [config for config in configs if config.default]
    wanted = [name.strip() for name in only.split(",") if name.strip()]
    by_name = {config.name: config for config in configs}
    missing = [name for name in wanted if name not in by_name]
    if missing:
        raise ValueError(f"없는 설정: {missing} (가능: {sorted(by_name)})")
    return [by_name[name] for name in wanted]


def check_paid_options(configs: Sequence[BenchConfig], allow_paid: bool, limit: Optional[int]) -> List[str]:
    """유료 설정을 돌릴 수 있는지 확인. 못 돌리면 이유 목록을 돌려준다."""
    paid = [config.name for config in configs if config.paid]
    if not paid:
        return []
    problems = []
    if not allow_paid:
        problems.append(f"유료 설정 {paid}은(는) --allow-paid를 줘야 실행됩니다.")
    if limit is None:
        problems.append(f"유료 설정 {paid}은(는) --limit N(소량)이 필요합니다.")
    return problems


def build_child_env(config: BenchConfig, base_env: Dict[str, str], budget_usd: float) -> Dict[str, str]:
    env = dict(base_env)
    env.update(code_defaults())
    env.update(config.env)
    env["PYTHONIOENCODING"] = "utf-8"
    if config.paid:
        # 키는 사용자 환경 변수나 .env에서 자식 프로세스가 직접 읽는다. 이 실행만의 예산 상한을 건다.
        env["OPENAI_MONTHLY_BUDGET_USD"] = str(budget_usd)
    else:
        # 빈 값을 넣어 두면 load_dotenv()가 .env의 키로 덮어쓰지 않는다 → OpenAI 호출 불가.
        env["OPENAI_API_KEY"] = ""
        env["LLM_PROVIDER"] = "ollama"
    return env


def build_child_args(config: BenchConfig, limit: Optional[int], output: Path) -> List[str]:
    task = TASKS[config.task]
    args = [sys.executable, "-m", task.module, "--engine", config.engine, "--output", str(output)]
    if limit is not None:
        args += ["--limit", str(limit)]
    if config.paid:
        args.append("--allow-paid")
    return args


Runner = Callable[[List[str], Dict[str, str]], Tuple[int, str]]


def subprocess_runner(args: List[str], env: Dict[str, str]) -> Tuple[int, str]:
    try:
        completed = subprocess.run(args, cwd=AI_MODULE_DIR, env=env, capture_output=True, text=True,
                                   encoding="utf-8", errors="replace", timeout=CHILD_TIMEOUT_SECONDS)
    except subprocess.TimeoutExpired:
        return 124, f"{CHILD_TIMEOUT_SECONDS}초 안에 끝나지 않아 중단했습니다."
    return completed.returncode, (completed.stdout or "") + (completed.stderr or "")


def probe_ollama_memory(model: Optional[str], base_url: str) -> Optional[Dict[str, Any]]:
    """로컬 Ollama에 올라간 모델 크기(/api/ps). 실패하면 None. 외부로 나가는 호출이 아니다."""
    if not model:
        return None
    try:
        with urllib.request.urlopen(f"{base_url.rstrip('/')}/api/ps", timeout=3) as response:
            loaded = json.loads(response.read().decode("utf-8")).get("models", [])
    except (urllib.error.URLError, TimeoutError, OSError, ValueError):
        return None
    for entry in loaded:
        if entry.get("name") == model or entry.get("model") == model:
            size, vram = int(entry.get("size") or 0), int(entry.get("size_vram") or 0)
            return {"sizeGb": round(size / 1024 ** 3, 2), "gpuShare": round(vram / size, 2) if size else None}
    return None


def run_bench(configs: Sequence[BenchConfig], limit: Optional[int], out_dir: Path,
              base_env: Optional[Dict[str, str]] = None, budget_usd: float = DEFAULT_PAID_BUDGET_USD,
              runner: Runner = subprocess_runner,
              probe: Callable[[Optional[str], str], Optional[Dict[str, Any]]] = probe_ollama_memory,
              ) -> List[Dict[str, Any]]:
    base_env = dict(os.environ if base_env is None else base_env)
    out_dir.mkdir(parents=True, exist_ok=True)
    rows = []
    for config in configs:
        output = out_dir / f"{safe_name(config.name)}.json"
        env = build_child_env(config, base_env, budget_usd)
        print(f"\n▶ {config.name} ({config.task}, engine={config.engine}"
              + (", 유료" if config.paid else "") + ") 실행 중…", flush=True)
        returncode, log = runner(build_child_args(config, limit, output), env)
        if returncode != 0 or not output.exists():
            print(f"  실패 (exit {returncode})\n" + indent_tail(log))
            rows.append({"config": config.name, "task": config.task, "status": "failed",
                         "error": tail(log), "note": config.note})
            continue

        result = json.loads(output.read_text(encoding="utf-8"))
        settings = result.get("settings", {})
        ollama_memory = None
        if config.engine != "rule" and not result.get("usage", {}).get("sentToExternalApi"):
            ollama_memory = probe(settings.get("ollamaModel"),
                                  env.get("OLLAMA_BASE_URL", "http://localhost:11434"))
        row = make_row(config, result, ollama_memory)
        print(f"  {TASKS[config.task].headline}={fmt_pct(row['scores'].get(TASKS[config.task].headline))} "
              f"· 건당 {fmt_ms(row['msPerItem'])}"
              + (f" · 경고: {'; '.join(row['warnings'])}" if row["warnings"] else ""))
        rows.append(row)

    (out_dir / "summary.json").write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
    return rows


def make_row(config: BenchConfig, result: Dict[str, Any], ollama_memory: Optional[Dict[str, Any]]) -> Dict[str, Any]:
    summary, runtime, usage = result.get("summary", {}), result.get("runtime", {}), result.get("usage", {})
    settings = result.get("settings", {})
    items = runtime.get("items") or 0
    cost = usage.get("estimatedCostUsd") or 0.0

    warnings = []
    coverage = runtime.get("llmCoverage")
    if coverage is not None and coverage < 1.0:
        warnings.append(f"LLM 성공률 {coverage * 100:.0f}% (실패한 건은 규칙 폴백 — LLM 성능이 아님)")
    if config.paid and not usage.get("sentToExternalApi"):
        warnings.append("유료 설정인데 OpenAI가 호출되지 않음 (키 없음·실패·예산 초과) — 결과 무효")

    return {
        "config": config.name,
        "task": config.task,
        "status": "ok",
        "note": config.note,
        "model": describe_model(config, settings, usage),
        "settings": settings,
        "items": items,
        "scores": {key: value.get("score") for key, value in summary.items()},
        "msPerItem": runtime.get("msPerItem"),
        "p50Ms": runtime.get("p50Ms"),
        "llmCoverage": coverage,
        "processMemoryMb": runtime.get("peakMemoryMb"),
        "ollamaMemory": ollama_memory,
        "costUsd": cost,
        "costPer1000Usd": round(cost / items * 1000, 4) if items else None,
        "sentToExternalApi": bool(usage.get("sentToExternalApi")),
        "finishedAt": result.get("finishedAt"),
        "warnings": warnings,
    }


def describe_model(config: BenchConfig, settings: Dict[str, Any], usage: Dict[str, Any]) -> str:
    parts = []
    if config.engine == "rule":
        parts.append("규칙")
    elif usage.get("sentToExternalApi"):
        parts.append(f"OpenAI {settings.get('openaiModelAnalysis')}")
    else:
        parts.append(f"Ollama {settings.get('ollamaModel')}")
    if settings.get("glinerEnabled"):
        parts.append(f"GLiNER {settings.get('glinerModel')}")
    if settings.get("embeddingEnabled"):
        parts.append(f"임베딩 {settings.get('embeddingModel')}")
    return " + ".join(parts)


# ---- 표 만들기 ----

def table_header(task: TaskSpec) -> List[str]:
    metric_heads = [label for _, label in task.columns]
    heads = ["설정", "모델", *metric_heads, "건당 시간(평균·중앙)", "LLM 성공률", "메모리", "1,000건당 비용", "외부 전송", "n", "실행일"]
    return ["| " + " | ".join(heads) + " |", "|" + "---|" * len(heads)]


def table_row(task: TaskSpec, row: Dict[str, Any]) -> str:
    name = f"`{row['config']}`"
    if row.get("status") != "ok":
        cells = [name, "실행 실패", *["-"] * (len(task.columns) + 8)]
        return "| " + " | ".join(cells) + " |"
    metrics = [fmt_pct(row["scores"].get(key)) for key, _ in task.columns]
    cells = [
        name,
        row["model"] + (" ⚠" if row["warnings"] else ""),
        *metrics,
        f"{fmt_ms(row['msPerItem'])} · {fmt_ms(row.get('p50Ms'))}",
        "-" if row["llmCoverage"] is None else fmt_pct(row["llmCoverage"]),
        fmt_memory(row),
        "무료(로컬)" if not row["sentToExternalApi"] else f"${row['costPer1000Usd']:.2f}",
        "예" if row["sentToExternalApi"] else "아니오",
        str(row["items"]),
        (row.get("finishedAt") or "")[:10],
    ]
    return "| " + " | ".join(cells) + " |"


def render_table(task_name: str, rows: Sequence[Dict[str, Any]]) -> str:
    task = TASKS[task_name]
    lines = table_header(task) + [table_row(task, row) for row in rows if row["task"] == task_name]
    return "\n".join(lines)


def update_models_md(rows: Sequence[Dict[str, Any]], path: Path = MODELS_MD) -> None:
    """MODELS.md의 자동 표 구간에서 이번에 돌린 설정 행만 바꾸고(없으면 추가) 나머지 행은 그대로 둔다."""
    text = path.read_text(encoding="utf-8")
    for task_name in sorted({row["task"] for row in rows}):
        begin, end = f"<!-- bench:{task_name}:begin -->", f"<!-- bench:{task_name}:end -->"
        if begin not in text or end not in text:
            text = text.rstrip() + f"\n\n### {TASKS[task_name].label}\n\n{begin}\n{end}\n"
        head, rest = text.split(begin, 1)
        body, tail_text = rest.split(end, 1)

        task = TASKS[task_name]
        existing: Dict[str, str] = {}
        order: List[str] = []
        for line in body.splitlines():
            match = re.match(r"^\| `([^`]+)` \|", line)
            if match:
                existing[match.group(1)] = line
                order.append(match.group(1))
        for row in rows:
            if row["task"] != task_name:
                continue
            if row["config"] not in existing:
                order.append(row["config"])
            existing[row["config"]] = table_row(task, row)

        new_body = "\n".join(table_header(task) + [existing[name] for name in order])
        text = f"{head}{begin}\n{new_body}\n{end}{tail_text}"
    path.write_text(text, encoding="utf-8")


# ---- 출력 도우미 ----

def fmt_pct(value: Optional[float]) -> str:
    return "-" if value is None else f"{value * 100:.1f}%"


def fmt_ms(value: Optional[float]) -> str:
    if value is None:
        return "-"
    return f"{value / 1000:.1f}s" if value >= 1000 else f"{value:.0f}ms"


def fmt_memory(row: Dict[str, Any]) -> str:
    parts = []
    if row.get("processMemoryMb") is not None:
        parts.append(f"프로세스 {row['processMemoryMb']:.0f}MB")
    ollama = row.get("ollamaMemory")
    if ollama:
        gpu = ollama.get("gpuShare")
        parts.append(f"모델 {ollama['sizeGb']:.1f}GB" + (f"(GPU {gpu * 100:.0f}%)" if gpu is not None else ""))
    return " · ".join(parts) or "-"


def safe_name(name: str) -> str:
    return re.sub(r"[^A-Za-z0-9._-]+", "_", name)


def tail(log: str, lines: int = 15) -> str:
    return "\n".join(log.strip().splitlines()[-lines:])


def indent_tail(log: str) -> str:
    return "\n".join(f"    {line}" for line in tail(log).splitlines())


def print_plan(configs: Sequence[BenchConfig], selected: Sequence[BenchConfig]) -> None:
    chosen = {config.name for config in selected}
    print("설정 목록 (* = 이번 실행 대상, $ = 유료):")
    for config in configs:
        mark = ("*" if config.name in chosen else " ") + ("$" if config.paid else " ")
        env = ", ".join(f"{key}={value}" for key, value in config.env.items()) or "(기본값)"
        print(f" {mark} {config.name:<24} {config.task}/{config.engine:<7} {env}  — {config.note}")


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="설정만 바꿔 같은 평가셋을 돌리고 결과를 한 표로 모은다")
    parser.add_argument("--configs", type=Path, default=DEFAULT_CONFIGS, help="설정 목록 JSON")
    parser.add_argument("--only", help="쉼표로 구분한 설정 이름 (없으면 default=true인 설정)")
    parser.add_argument("--limit", type=int, default=None, help="과제마다 앞에서부터 N건만 (유료 설정은 필수)")
    parser.add_argument("--allow-paid", action="store_true", help="유료 API(OpenAI) 설정 실행 허용")
    parser.add_argument("--budget-usd", type=float, default=DEFAULT_PAID_BUDGET_USD,
                        help=f"유료 설정 한 개당 예상 비용 상한 (기본 ${DEFAULT_PAID_BUDGET_USD})")
    parser.add_argument("--out", type=Path, default=None, help="원자료 폴더 (기본 eval/results/bench_<시각>)")
    parser.add_argument("--write-md", action="store_true", help="eval/MODELS.md 자동 표에 결과 반영")
    parser.add_argument("--list", action="store_true", help="설정 목록만 보고 실행하지 않음")
    args = parser.parse_args(argv)

    try:
        configs = load_configs(args.configs)
        selected = select_configs(configs, args.only)
    except (ValueError, KeyError, TypeError) as exception:
        parser.error(str(exception))

    if args.list:
        print_plan(configs, selected)
        return 0
    if not selected:
        parser.error("실행할 설정이 없습니다. --only로 고르거나 설정 파일에 default=true를 두세요.")

    problems = check_paid_options(selected, args.allow_paid, args.limit)
    if problems:
        parser.error(" ".join(problems))
    if args.limit is not None and args.limit <= 0:
        parser.error("--limit은 1 이상이어야 합니다.")

    out_dir = args.out or RESULTS_DIR / f"bench_{datetime.now().strftime('%Y%m%d_%H%M%S')}"
    print_plan(configs, selected)
    rows = run_bench(selected, args.limit, out_dir, budget_usd=args.budget_usd)

    for task_name in sorted({row["task"] for row in rows}):
        print(f"\n=== {TASKS[task_name].label} ===\n{render_table(task_name, rows)}")
    for row in rows:
        for warning in row.get("warnings", []):
            print(f"[경고] {row['config']}: {warning}")
    print(f"\n원자료: {out_dir}")

    if args.write_md:
        update_models_md(rows)
        print(f"표 반영: {MODELS_MD}")
    return 1 if any(row["status"] != "ok" for row in rows) else 0


if __name__ == "__main__":
    sys.exit(main())
