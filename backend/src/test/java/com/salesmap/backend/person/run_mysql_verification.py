"""새 로컬 MySQL 검증 DB·한정 계정만 만들고 finally에서 자기 자원만 정리한다."""
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys


def main():
    backend = next(path for path in Path(__file__).resolve().parents if (path / "gradlew").is_file())
    mysql = shutil.which("mysql")
    if mysql is None:
        raise RuntimeError("로컬 mysql 클라이언트가 필요합니다.")
    admin_env = os.environ.copy()
    admin_password = admin_env.pop("A_PERSON_MYSQL_ADMIN_PASSWORD", None)
    if admin_password is not None:
        admin_env["MYSQL_PWD"] = admin_password
    command = [mysql, "--no-defaults", "--batch", "--skip-column-names", "--user=" + os.environ.get("A_PERSON_MYSQL_ADMIN_USER", "root")]
    socket = os.environ.get("A_PERSON_MYSQL_SOCKET", "/private/tmp/mysql.sock")
    if os.name != "nt" and Path(socket).exists():
        command += ["--protocol=socket", "--socket=" + socket]
    else:
        command += ["--protocol=tcp", "--host=127.0.0.1", "--port=" + os.environ.get("A_PERSON_MYSQL_PORT", "3306")]

    def sql(statement):
        result = subprocess.run(command, input=statement, text=True, encoding="utf-8", capture_output=True, env=admin_env)
        if result.returncode:
            # CREATE USER 실패의 SQL 본문에 임시 암호가 들어 있을 수 있어 stderr를 그대로 출력하지 않는다.
            raise RuntimeError("로컬 MySQL 준비·정리 명령이 실패했습니다. 접속 권한과 서버 상태를 확인하세요.")
        return result.stdout.strip()

    run_id = secrets.token_hex(8)
    database, username = "a_identity_verify_" + run_id, "a_verify_" + run_id
    assert re.fullmatch(r"a_identity_verify_[0-9a-f]{16}", database)
    assert re.fullmatch(r"a_verify_[0-9a-f]{16}", username)
    password = secrets.token_urlsafe(32)
    created_database = created_user = False
    try:
        port = sql("select @@port;")
        if not port.isdecimal() or not 0 < int(port) < 65536:
            raise RuntimeError("로컬 MySQL 포트를 확인할 수 없습니다.")
        sql(f"CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;")
        created_database = True
        sql(f"CREATE USER '{username}'@'127.0.0.1' IDENTIFIED BY '{password}';")
        created_user = True
        sql(f"GRANT ALL PRIVILEGES ON `{database}`.* TO '{username}'@'127.0.0.1';")
        test_env = os.environ.copy()
        test_env.pop("A_PERSON_MYSQL_ADMIN_PASSWORD", None)
        test_env.pop("MYSQL_PWD", None)
        test_env.update(A_PERSON_MYSQL_TEST="true", A_PERSON_MYSQL_USER=username, A_PERSON_MYSQL_PASSWORD=password,
                        A_PERSON_MYSQL_URL=f"jdbc:mysql://127.0.0.1:{port}/{database}?serverTimezone=Asia/Seoul&characterEncoding=UTF-8")
        jdk = Path("/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home")
        if not test_env.get("JAVA_HOME") and jdk.is_dir():
            test_env["JAVA_HOME"] = str(jdk)
        wrapper = ["cmd", "/c", "gradlew.bat"] if os.name == "nt" else ["bash", "./gradlew"]
        print("격리된 새 MySQL DB에서 A 검증을 실행합니다. 임시 암호는 저장·출력하지 않습니다.", flush=True)
        result = subprocess.run(wrapper + ["--offline", "test", "--tests", "com.salesmap.backend.person.PersonMysqlIntegrationTests",
                                          "--no-daemon", "--rerun-tasks"], cwd=backend, env=test_env)
        return result.returncode
    finally:
        errors = []
        # CREATE 성공 여부를 기록한, 이번 실행의 무작위 자원만 지운다. 다른 DB 이름을 입력받지 않는다.
        if created_database:
            try:
                sql(f"DROP DATABASE `{database}`;")
            except RuntimeError:
                errors.append("검증 DB: " + database)
        if created_user:
            try:
                sql(f"DROP USER '{username}'@'127.0.0.1';")
            except RuntimeError:
                errors.append("검증 계정: " + username)
        if errors:
            print("임시 자원 정리가 완료되지 않았습니다: " + ", ".join(errors), file=sys.stderr)
            raise RuntimeError("이번 실행의 임시 자원 정리가 필요합니다.")
        if created_database:
            print("이번 실행의 검증 DB·계정을 정리했습니다. 기존 DB는 변경하지 않았습니다.", flush=True)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except RuntimeError as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
