"""고정 로지스틱 모델의 JSON 추론. sklearn·네트워크·학습 자료 없이 실행한다."""
import hashlib
import json
import math
from functools import lru_cache
from pathlib import Path

FEATURES = (
    "name_exact", "name_similarity", "name_unknown", "name_different",
    "email_exact", "email_unknown", "email_different", "phone_exact", "phone_unknown", "phone_different",
    "company_exact", "company_unknown", "company_different", "contact_warning",
)
ARTIFACT = Path(__file__).with_name("logistic_v1.json")


def features(comparison):
    values = [int(comparison.name.status == "MATCH"), comparison.name_similarity or 0.0,
              int(comparison.name.status == "UNKNOWN"), int(comparison.name.status == "DIFFERENT")]
    for field in ("email", "phone", "company"):
        values.extend(int(getattr(comparison, field).status == status) for status in ("MATCH", "UNKNOWN", "DIFFERENT"))
    return values + [int(bool(comparison.warnings))]


def fingerprint(payload):
    return hashlib.sha256(json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")).hexdigest()


class LogisticModel:
    def __init__(self, bundle):
        p = bundle["payload"]
        if bundle["sha256"] != fingerprint(p) or p["features"] != list(FEATURES) or p["format"] != "identity-logistic-json-v1":
            raise ValueError("모델 무결성·특징 형식 불일치")
        for key in ("mean", "scale", "coefficients"):
            if len(p[key]) != len(FEATURES) or any(type(v) not in (int, float) or not math.isfinite(v) for v in p[key]):
                raise ValueError("모델 계수 형식 오류")
        if any(v <= 0 for v in p["scale"]):
            raise ValueError("모델 scale 오류")
        for key in ("intercept", "calibrationCoefficient", "calibrationIntercept", "threshold"):
            if type(p[key]) not in (int, float) or not math.isfinite(p[key]):
                raise ValueError("모델 상수 형식 오류")
        if not 0 < p["threshold"] < 1 or not isinstance(p["version"], str) or not p["version"]:
            raise ValueError("모델 버전·기준값 오류")
        self.payload = p
        self.version, self.threshold = p["version"], p["threshold"]

    def score(self, comparison):
        p = self.payload
        margin = p["intercept"] + sum((x-m)/s*c for x, m, s, c in zip(features(comparison), p["mean"], p["scale"], p["coefficients"]))
        value = p["calibrationCoefficient"] * margin + p["calibrationIntercept"]
        if not math.isfinite(value):
            raise ValueError("모델 추론 값 오류")
        if value >= 0:
            return 1 / (1 + math.exp(-value))
        exp = math.exp(value)
        return exp / (1 + exp)


@lru_cache(maxsize=1)
def load_model():
    return LogisticModel(json.loads(ARTIFACT.read_text(encoding="utf-8")))
