"""이력서 추출 채점. 정답(gold)과 엔진이 만든 resume_profile을 비교해 숫자만 낸다.

정답 형식 (값을 모르면 null, 목록은 빈 배열):
    {"format": "TABLE_CARD", "name": "가상인", "birthYear": 1991, "grade": "INTERMEDIATE" | null,
     "projectCount": 5, "skills": ["Java", ...통일한 이름], "industries": ["금융"],
     "totalCareerMonths": 57 | null, "availableFrom": "2027-02-01" | null}
지표: 칸별 정확도(이름·생년·등급·양식·투입 가능일), 프로젝트 수 일치율, 기술 재현율·정밀도(사전에 있는 기술만),
      산업 재현율, 총 경력 오차(개월), 근거가 원문에 있는 비율, 건당 시간.
"""
import statistics
from typing import Any, Dict, Iterable, List, Optional

SCALAR_FIELDS = ("format", "name", "birthYear", "grade", "availableFrom")


def predicted(profile: Dict[str, Any]) -> Dict[str, Any]:
    """프로필 → 정답과 같은 모양."""
    def value(key):
        return profile[key]["value"] if profile.get(key) else None

    return {
        "format": profile["format"],
        "name": value("name"),
        "birthYear": value("birthYear"),
        "grade": value("grade"),
        "projectCount": len(profile["projects"]),
        "skills": sorted({skill["name"] for skill in profile["skills"] if skill["known"]}),
        "industries": sorted({item["name"] for item in profile["industries"]}),
        "totalCareerMonths": profile["totalCareer"]["months"] if profile["totalCareer"] else None,
        "availableFrom": profile["availableFrom"]["date"] if profile["availableFrom"] else None,
    }


def evidence_items(profile: Dict[str, Any]) -> Iterable[str]:
    for key in ("name", "birthYear", "grade"):
        if profile.get(key):
            yield profile[key]["evidence"]
    for key in ("certifications", "educations", "careers", "projects", "skills", "industries"):
        for item in profile[key]:
            yield item["evidence"]


def score_case(profile: Dict[str, Any], gold: Optional[Dict[str, Any]], source: str) -> Dict[str, Any]:
    pred = predicted(profile)
    evidences = list(evidence_items(profile))
    row: Dict[str, Any] = {
        "evidenceTotal": len(evidences),
        "evidenceInSource": sum(1 for evidence in evidences if evidence in source),
        "filled": {key: pred[key] is not None for key in ("name", "birthYear", "grade", "totalCareerMonths")},
        "projectCount": pred["projectCount"],
        "skillCount": len(pred["skills"]),
        "format": pred["format"],
    }
    if gold is None:
        return row

    row["correct"] = {key: pred[key] == gold.get(key) for key in SCALAR_FIELDS if key in gold}
    row["projectCountExact"] = pred["projectCount"] == gold.get("projectCount")
    gold_skills, pred_skills = set(gold.get("skills") or []), set(pred["skills"])
    row["skillHit"], row["skillGold"], row["skillPred"] = len(gold_skills & pred_skills), len(gold_skills), len(pred_skills)
    gold_industries, pred_industries = set(gold.get("industries") or []), set(pred["industries"])
    row["industryHit"], row["industryGold"], row["industryPred"] = (len(gold_industries & pred_industries),
                                                                    len(gold_industries), len(pred_industries))
    if gold.get("totalCareerMonths") is not None:
        row["careerError"] = (abs(pred["totalCareerMonths"] - gold["totalCareerMonths"])
                              if pred["totalCareerMonths"] is not None else None)
    return row


def ratio(numerator: float, denominator: float) -> Optional[float]:
    return round(numerator / denominator, 3) if denominator else None


def summarize(rows: List[Dict[str, Any]], elapsed_ms: List[int]) -> Dict[str, Any]:
    summary: Dict[str, Any] = {
        "cases": len(rows),
        "evidenceInSourceRate": ratio(sum(r["evidenceInSource"] for r in rows), sum(r["evidenceTotal"] for r in rows)),
        "filledRate": {key: ratio(sum(r["filled"][key] for r in rows), len(rows))
                       for key in ("name", "birthYear", "grade", "totalCareerMonths")},
        "medianProjects": statistics.median([r["projectCount"] for r in rows]) if rows else None,
        "medianMs": statistics.median(elapsed_ms) if elapsed_ms else None,
        "meanMs": round(statistics.mean(elapsed_ms)) if elapsed_ms else None,
    }
    graded = [r for r in rows if "correct" in r]
    if graded:
        summary["graded"] = len(graded)
        summary["accuracy"] = {key: ratio(sum(r["correct"].get(key, False) for r in graded),
                                          sum(key in r["correct"] for r in graded)) for key in SCALAR_FIELDS}
        summary["projectCountExact"] = ratio(sum(r["projectCountExact"] for r in graded), len(graded))
        summary["skillRecall"] = ratio(sum(r["skillHit"] for r in graded), sum(r["skillGold"] for r in graded))
        summary["skillPrecision"] = ratio(sum(r["skillHit"] for r in graded), sum(r["skillPred"] for r in graded))
        summary["industryRecall"] = ratio(sum(r["industryHit"] for r in graded), sum(r["industryGold"] for r in graded))
        errors = [r["careerError"] for r in graded if "careerError" in r]
        known = [error for error in errors if error is not None]
        summary["careerMonthsMAE"] = round(statistics.mean(known), 1) if known else None
        summary["careerMissing"] = sum(1 for error in errors if error is None)
    return summary


def print_report(results: Dict[str, Dict[str, Any]]) -> None:
    keys = ["cases", "evidenceInSourceRate", "medianProjects", "medianMs"]
    for mode, summary in results.items():
        print(f"\n[{mode}]")
        for key in keys:
            print(f"  {key}: {summary.get(key)}")
        print(f"  filledRate: {summary['filledRate']}")
        if "accuracy" in summary:
            print(f"  accuracy: {summary['accuracy']}")
            for key in ("projectCountExact", "skillRecall", "skillPrecision", "industryRecall", "careerMonthsMAE",
                        "careerMissing"):
                print(f"  {key}: {summary.get(key)}")
