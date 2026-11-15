#!/usr/bin/env python3
"""차비스(com.peyya.app) 실제 사용 흐름을 Pleos 에뮬레이터에서 녹화한다.

계획(테마 코스) → "드라이브를 시작할까요?" 팝업 → 출발하기 → 시스템 내비 안내(NaviHelper)
→ 도착(P, 도착 처리) → 다시 D면 다음 장소 자동 안내 → 마지막 장소 후 출발 → 루트 완료 → 리뷰(별점) 저장.
기어는 Virtual Console(vc.py)로 바꾼다. 장면 시각은 events.json에 남긴다.

사용: python3 record_flow.py <outdir>   (사전: APK 설치, Virtual Console 동작)
"""
import json
import os
import subprocess
import sys
import time
from pathlib import Path

ADB = os.environ.get("ADB", "/opt/homebrew/share/android-commandlinetools/platform-tools/adb")
CAR = os.environ.get("CAR_SERIAL", "emulator-5554")
VC = str(Path(__file__).with_name("vc.py"))
PKG = "com.peyya.app"


def adb(*args):
    return subprocess.run([ADB, "-s", CAR, *args], check=True, capture_output=True)


def tap(x, y, wait=1.5):
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(wait)


def gear(g):
    subprocess.run([sys.executable, VC, "gear", g], check=True, capture_output=True)


def front():
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(2)


def main(out):
    out.mkdir(parents=True, exist_ok=True)
    adb("forward", "tcp:8088", "tcp:8088")
    gear("P")
    adb("shell", "pm", "clear", PKG)
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")
    time.sleep(3)
    rec = subprocess.Popen([ADB, "-s", CAR, "shell", "screenrecord", "--size", "1920x1080", "--bit-rate", "8000000",
                            "--time-limit", "180", "/sdcard/flow.mp4"])
    t0 = time.time()
    ev = {}
    mark = lambda k: ev.__setitem__(k, round(time.time() - t0, 2))
    time.sleep(2.5)
    mark("demo"); tap(1267, 799, 3)              # 바로 체험하기
    mark("plan"); tap(719, 824, 2.5)             # 새 계획 세우기
    tap(906, 642, 2)                             # 테마: 아이와 함께
    adb("shell", "input", "swipe", "1260", "750", "1260", "450", "600"); time.sleep(1.5)
    mark("popup"); tap(1796, 526, 4)             # 이 코스로 일정 잡기 → 팝업
    mark("go"); tap(1408, 568, 3)                # 출발하기
    mark("navi"); tap(1645, 896, 3)              # 오늘 루트 시작 → 시스템 내비
    tap(994, 866, 2)                             # 내비: 안내 시작
    mark("drive1"); gear("D"); time.sleep(4)
    mark("arrive1"); gear("P"); front(); tap(1645, 840, 4)   # 도착 처리
    mark("next"); gear("D"); time.sleep(7)       # 다시 D → 다음 장소 자동 안내
    mark("arrive2"); gear("P"); front(); tap(1645, 840, 4)
    mark("finish"); gear("D"); time.sleep(4)     # 마지막 장소에서 출발 → 루트 완료
    gear("P"); time.sleep(1.5)
    mark("review"); tap(1645, 456, 2.5)          # 리뷰 시작
    tap(1747, 464, 1.5); tap(1645, 583, 2.5)     # 별점 4 → 다음 장소
    tap(1645, 583, 4)                            # 저장
    mark("end")
    adb("shell", "pkill", "-INT", "screenrecord")
    rec.wait(timeout=15)
    time.sleep(2)
    adb("pull", "/sdcard/flow.mp4", str(out / "flow.mp4"))
    (out / "events.json").write_text(json.dumps(ev, indent=1))
    print(json.dumps(ev))


if __name__ == "__main__":
    main(Path(sys.argv[1]))
