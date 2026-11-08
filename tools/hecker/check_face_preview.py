#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Run the production camera geometry against multiple display sizes without Android."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix="hecker-face-preview-") as output:
    subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-d", output,
        str(ROOT / "src/com/android/settings/biometrics/face/FacePreviewGeometry.java"),
        str(ROOT / "tools/hecker/tests/FacePreviewGeometryTest.java")], check=True)
    subprocess.run(["java", "-cp", output,
        "com.android.settings.biometrics.face.FacePreviewGeometryTest"], check=True)
