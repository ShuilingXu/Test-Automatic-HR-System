#!/usr/bin/env python3
import os
import sys
from pathlib import Path


project_root = Path(__file__).resolve().parent.parent
env_path = project_root / ".env"
jar_path = project_root / "backend" / "target" / "auto-hr-backend-1.0.0-SNAPSHOT.jar"

if not env_path.is_file():
    raise SystemExit(f"Missing environment file: {env_path}")
if not jar_path.is_file():
    raise SystemExit(f"Missing backend artifact: {jar_path}")

for raw_line in env_path.read_text(encoding="utf-8").splitlines():
    line = raw_line.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    key, value = line.split("=", 1)
    os.environ[key.strip()] = value.strip()

extra_args = sys.argv[1:]
os.chdir(project_root)
os.execvp("java", ["java", "-jar", str(jar_path), *extra_args])
