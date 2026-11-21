"""원 평가 자료를 재학습 없이 재생성한다. 해시가 다르거나 출력이 있으면 중단한다."""
import argparse
import json
from pathlib import Path

from ml.identity.controlled_data import digest
from ml.identity.fresh_validation import make_fresh

ROOT = Path(__file__).resolve().parent / "results/fresh_validation_v1"


def reproduce(output):
    frozen = json.loads((ROOT / "frozen_protocol.json").read_text(encoding="utf-8"))
    data = make_fresh(seed=frozen["seed"])
    if digest(data) != frozen["datasetSha256"]:
        raise ValueError("고정 자료 해시가 다릅니다. 기존 정답을 덮어쓰지 않습니다.")
    with output.open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(json.dumps(data, ensure_ascii=False, indent=2, allow_nan=False) + "\n")
    return {"output": str(output), "datasetSha256": digest(data), "persons": len(data["persons"]), "queries": len(data["queries"])}


def main():
    parser = argparse.ArgumentParser(description="고정된 새 평가 자료만 재생성 (모델 학습 없음)")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = reproduce(args.output)
    except (FileExistsError, ValueError) as error:
        parser.error(str(error))
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
