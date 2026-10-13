"""모델 비교 틀(eval/bench.py, eval/run_eval.py) 테스트. 네트워크·유료 API 호출 없음."""
import asyncio
import json
import sys

import pytest

from app.llm import router as router_module
from app.llm.base import LLMResponse
from app.llm.router import LLMRouter
from eval import bench, run_eval

PATCH = {"customerName": "대성물류", "contactName": "김현우", "productName": None,
         "attendees": ["김현우"], "scheduleTitle": None, "summary": "요약"}


class FakeProvider:
    def __init__(self, name, cost=0.0):
        self.name = name
        self.cost = cost

    def model_for(self, task):
        return f"{self.name}-fake"

    def generate(self, request):
        return LLMResponse(data=dict(PATCH), provider=self.name, model=self.model_for(request.task),
                           input_tokens=100, output_tokens=20, estimated_cost_usd=self.cost)


def config(name="c", engine="rule", env=None, default=False):
    return bench.BenchConfig(name=name, task="mail", engine=engine, env=env or {}, default=default)


# ---- 설정 목록 ----

def test_shipped_configs_are_valid_and_free_by_default():
    configs = bench.load_configs()
    defaults = bench.select_configs(configs, None)

    assert "rule" in [c.name for c in defaults]
    assert not any(c.paid for c in defaults)
    assert any(c.paid for c in configs)  # 유료 설정은 목록에 있지만 기본 실행 대상은 아님


def test_api_key_cannot_be_put_in_config_file():
    with pytest.raises(ValueError, match="OPENAI_API_KEY"):
        bench.validate_configs([config(env={"OPENAI_API_KEY": "x"})])


def test_unknown_config_name_is_rejected():
    with pytest.raises(ValueError, match="없는 설정"):
        bench.select_configs([config("rule")], "rule,nope")


# ---- 유료 차단 ----

def test_paid_config_requires_allow_paid_and_limit():
    paid = config("openai", engine="ollama", env={"LLM_PROVIDER": "openai"})

    assert len(bench.check_paid_options([paid], allow_paid=False, limit=None)) == 2
    assert bench.check_paid_options([paid], allow_paid=True, limit=None)
    assert bench.check_paid_options([paid], allow_paid=True, limit=5) == []
    assert bench.check_paid_options([config()], allow_paid=False, limit=None) == []


def test_main_refuses_paid_config_without_option(tmp_path, monkeypatch):
    path = tmp_path / "configs.json"
    path.write_text(json.dumps({"configs": [
        {"name": "openai", "task": "mail", "engine": "ollama", "env": {"LLM_PROVIDER": "openai"}}]}),
        encoding="utf-8")
    monkeypatch.setattr(bench, "run_bench", lambda *a, **k: pytest.fail("유료 설정이 실행되면 안 됨"))

    with pytest.raises(SystemExit):
        bench.main(["--configs", str(path), "--only", "openai"])
    with pytest.raises(SystemExit):
        bench.main(["--configs", str(path), "--only", "openai", "--allow-paid"])


def test_free_config_child_cannot_reach_openai_even_if_env_has_key():
    base = {"OPENAI_API_KEY": "sk-local", "LLM_PROVIDER": "openai", "GLINER_ENABLED": "true"}

    env = bench.build_child_env(config(engine="ollama"), base, budget_usd=1.0)

    assert env["OPENAI_API_KEY"] == ""  # 빈 값이라 load_dotenv가 .env 키로 덮어쓰지 않음
    assert env["LLM_PROVIDER"] == "ollama"
    assert env["GLINER_ENABLED"] == "false"


def test_unset_model_variables_are_pinned_to_code_defaults():
    defaults = bench.code_defaults()
    base = {"OLLAMA_MODEL": "local-override:99b", "EMBEDDING_MODEL_NAME": "local/other"}

    plain = bench.build_child_env(config(engine="ollama"), base, budget_usd=1.0)
    candidate = bench.build_child_env(config(engine="ollama", env={"OLLAMA_MODEL": "cand:7b"}), base, budget_usd=1.0)

    assert {"LLM_PROVIDER", "OLLAMA_MODEL", "GLINER_ENABLED", "EMBEDDING_MODEL_NAME"} <= set(defaults)
    assert plain["OLLAMA_MODEL"] == defaults["OLLAMA_MODEL"] != "local-override:99b"
    assert plain["EMBEDDING_MODEL_NAME"] == defaults["EMBEDDING_MODEL_NAME"]
    assert candidate["OLLAMA_MODEL"] == "cand:7b"


def test_paid_config_child_gets_budget_cap():
    paid = config("openai", engine="ollama", env={"LLM_PROVIDER": "openai"})

    env = bench.build_child_env(paid, {"OPENAI_MONTHLY_BUDGET_USD": "10"}, budget_usd=0.5)
    args = bench.build_child_args(paid, limit=3, output=bench.EVAL_DIR / "x.json")

    assert env["LLM_PROVIDER"] == "openai"
    assert env["OPENAI_MONTHLY_BUDGET_USD"] == "0.5"
    assert "--allow-paid" in args and args[args.index("--limit") + 1] == "3"


def test_run_eval_refuses_paid_provider_without_option(monkeypatch):
    monkeypatch.setattr(run_eval.config, "LLM_PROVIDER", "openai")
    monkeypatch.setattr(run_eval.config, "OPENAI_API_KEY", "sk-test")
    monkeypatch.setattr(sys, "argv", ["run_eval", "--engine", "ollama", "--limit", "1"])
    monkeypatch.setattr(run_eval, "evaluate", lambda *a, **k: pytest.fail("유료 호출이 실행되면 안 됨"))

    assert run_eval.paid_provider_active("ollama")
    assert not run_eval.paid_provider_active("rule")
    with pytest.raises(SystemExit):
        asyncio.run(run_eval.main())


# ---- 평가 실행 (가짜 LLM) ----

def test_evaluate_records_coverage_and_usage_with_fake_llm(monkeypatch):
    monkeypatch.setattr(router_module, "_router", LLMRouter([FakeProvider("ollama")]))
    items = run_eval.load_dataset(run_eval.EVAL_DIR / "dataset.jsonl")[:3]

    result = asyncio.run(run_eval.evaluate(items, "ollama"))

    assert result["runtime"]["items"] == 3
    assert result["runtime"]["llmAttempted"] == 3
    assert result["runtime"]["llmCoverage"] == 1.0
    assert result["usage"]["llmCalls"] == 3
    assert result["usage"]["providers"] == {"ollama:ollama-fake": 3}
    assert result["usage"]["sentToExternalApi"] is False
    assert "OPENAI_API_KEY" not in json.dumps(result["settings"])
    assert all(row["llmProvider"] == "ollama" for row in result["items"])


def test_evaluate_marks_external_api_and_cost(monkeypatch):
    monkeypatch.setattr(router_module, "_router", LLMRouter([FakeProvider("openai", cost=0.001)]))
    items = run_eval.load_dataset(run_eval.EVAL_DIR / "dataset.jsonl")[:2]

    usage = asyncio.run(run_eval.evaluate(items, "ollama"))["usage"]

    assert usage["sentToExternalApi"] is True
    assert usage["estimatedCostUsd"] == pytest.approx(0.002)


def test_coverage_ignores_noise_mail_skipped_by_design_but_counts_failures(monkeypatch):
    dataset = run_eval.load_dataset(run_eval.EVAL_DIR / "dataset.jsonl")
    sales = next(item for item in dataset if item["gold"]["businessType"] == "SALES_ACTIVITY")
    noise = next(item for item in dataset if item["id"] == "noise-1")  # 규칙이 비영업 판정 → LLM 건너뜀

    monkeypatch.setattr(router_module, "_router", LLMRouter([FakeProvider("ollama")]))
    ok = asyncio.run(run_eval.evaluate([sales, noise], "ollama"))["runtime"]

    class Down(FakeProvider):
        def generate(self, request):
            from app.llm.base import LLMError
            raise LLMError("connection refused")

    monkeypatch.setattr(router_module, "_router", LLMRouter([Down("ollama")]))
    down = asyncio.run(run_eval.evaluate([sales, noise], "ollama"))["runtime"]

    assert (ok["llmAttempted"], ok["llmCoverage"]) == (1, 1.0)
    assert (down["llmAttempted"], down["llmCoverage"]) == (1, 0.0)


def test_rule_engine_has_no_llm_coverage():
    items = run_eval.load_dataset(run_eval.EVAL_DIR / "dataset.jsonl")[:2]

    result = asyncio.run(run_eval.evaluate(items, "rule"))

    assert result["runtime"]["llmCoverage"] is None
    assert result["usage"]["llmCalls"] == 0


# ---- 결과 모으기 ----

def fake_result(score, coverage=1.0, external=False, cost=0.0, items=4):
    return {
        "summary": {"allFieldsCorrect": {"score": score, "n": items}, "businessType": {"score": 1.0, "n": items}},
        "runtime": {"items": items, "msPerItem": 1500.0, "llmCoverage": coverage, "peakMemoryMb": 120.0},
        "usage": {"estimatedCostUsd": cost, "sentToExternalApi": external},
        "settings": {"ollamaModel": "fake:1b", "openaiModelAnalysis": "fake-gpt" if external else None},
        "finishedAt": "2026-10-13T10:00:00",
    }


def fake_runner(results):
    calls = []

    def runner(args, env):
        calls.append((args, env))
        name = args[args.index("--output") + 1]
        key = next(k for k in results if name.endswith(f"{bench.safe_name(k)}.json"))
        if results[key] is None:
            return 1, "Traceback: boom"
        with open(name, "w", encoding="utf-8") as file:
            json.dump(results[key], file)
        return 0, "ok"

    runner.calls = calls
    return runner


def test_run_bench_collects_rows_and_flags_problems(tmp_path):
    configs = [config("local", engine="ollama"), config("broken"),
               config("paid", engine="ollama", env={"LLM_PROVIDER": "openai"})]
    runner = fake_runner({"local": fake_result(0.75, coverage=0.5), "broken": None,
                          "paid": fake_result(1.0, external=True, cost=0.004)})

    rows = bench.run_bench(configs, limit=4, out_dir=tmp_path, base_env={}, runner=runner,
                           probe=lambda model, url: {"sizeGb": 2.0, "gpuShare": 1.0})
    by_name = {row["config"]: row for row in rows}

    assert by_name["local"]["warnings"] and "폴백" in by_name["local"]["warnings"][0]
    assert by_name["local"]["ollamaMemory"] == {"sizeGb": 2.0, "gpuShare": 1.0}
    assert by_name["broken"]["status"] == "failed"
    assert by_name["paid"]["costPer1000Usd"] == pytest.approx(1.0)
    assert by_name["paid"]["model"] == "OpenAI fake-gpt"
    assert by_name["paid"]["ollamaMemory"] is None
    assert json.loads((tmp_path / "summary.json").read_text(encoding="utf-8"))[0]["config"] == "local"

    table = bench.render_table("mail", rows)
    assert "| `local` | Ollama fake:1b ⚠ | 75.0% |" in table
    assert "실행 실패" in table and "$1.00" in table


def test_update_models_md_replaces_only_rerun_rows(tmp_path):
    md = tmp_path / "MODELS.md"
    md.write_text("# 표\n\n<!-- bench:mail:begin -->\n<!-- bench:mail:end -->\n\n## 판단 기록\n", encoding="utf-8")
    first = [dict(bench.make_row(config("a"), fake_result(0.5), None)),
             dict(bench.make_row(config("b"), fake_result(0.6), None))]
    bench.update_models_md(first, md)

    bench.update_models_md([bench.make_row(config("a"), fake_result(0.9), None)], md)
    text = md.read_text(encoding="utf-8")

    assert "| `a` | 규칙 | 90.0% |" in text
    assert "| `b` | 규칙 | 60.0% |" in text
    assert "50.0%" not in text
    assert text.index("`a`") < text.index("`b`")
    assert text.count("| 설정 |") == 1
    assert text.rstrip().endswith("## 판단 기록")


def test_bench_runs_rule_engine_end_to_end_in_subprocess(tmp_path):
    rows = bench.run_bench([config("rule")], limit=2, out_dir=tmp_path,
                           probe=lambda model, url: pytest.fail("규칙 엔진은 Ollama를 조회하지 않음"))

    assert rows[0]["status"] == "ok", rows[0].get("error")
    assert rows[0]["items"] == 2
    assert rows[0]["sentToExternalApi"] is False
