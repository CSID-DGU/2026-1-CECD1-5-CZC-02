"""시연용 가짜 후보 풀 이력서(.docx 6개)를 만든다. 원하면 로컬 백엔드에 올리기까지 한다.

    python -m eval.resumes.demo_pool --out <폴더>
    python -m eval.resumes.demo_pool --out <폴더> --api http://localhost:8090 --token-file <JWT 파일>

- 이름·회사는 전부 가짜다 (app/resumes/demo_pool.py 표 참고).
- 올리기만 한다. **확정은 사람이 인력 메뉴의 확인 화면에서** 한다 (AI 결과 자동 확정 금지).
- 토큰은 파일로만 받는다 (명령줄·로그에 남기지 않는다).
"""
import argparse
import base64
import json
import urllib.request
from pathlib import Path

from app.resumes.demo_pool import demo_pool_docx


def upload(api: str, token: str, file_name: str, content: bytes) -> dict:
    body = json.dumps({"fileName": file_name, "contentBase64": base64.b64encode(content).decode("ascii")}).encode("utf-8")
    request = urllib.request.Request(f"{api.rstrip('/')}/api/talents/resumes", data=body, method="POST",
                                     headers={"Content-Type": "application/json", "Authorization": f"Bearer {token}"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.loads(response.read().decode("utf-8"))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--api", help="예: http://localhost:8090 (없으면 파일만 만든다)")
    parser.add_argument("--token-file", type=Path)
    args = parser.parse_args()

    args.out.mkdir(parents=True, exist_ok=True)
    files = demo_pool_docx()
    for name, content in files:
        (args.out / name).write_bytes(content)
    print(f"가짜 이력서 {len(files)}개를 만들었습니다: {args.out}")

    if args.api:
        if not args.token_file:
            parser.error("--api를 쓰려면 --token-file이 필요합니다")
        token = args.token_file.read_text(encoding="utf-8").strip()
        for name, content in files:
            result = upload(args.api, token, name, content)
            print(f"접수: {name} → {result.get('data', {}).get('profileId')}")
        print("인력 메뉴에서 하나씩 확인하고 확정하세요.")


if __name__ == "__main__":
    main()
