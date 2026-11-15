#!/usr/bin/env python3
"""차비스(com.peyya.app) "평일 계획 → 주말 시동" 흐름을 녹화한다.

휴대폰(세로): 테마 코스로 토요일 일정 잡기 → "계획을 저장했어요" 알림.
차(Pleos): 같은 코스를 미리 잡아 둔 뒤(녹화 밖) 앱을 다시 켜면(시동) "계획한 드라이브가 있어요" 팝업 → 출발하기 → 시스템 내비 안내(NaviHelper)
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
PHONE = os.environ.get("PHONE_SERIAL", "emulator-5556")
VC = str(Path(__file__).with_name("vc.py"))
PKG = "com.peyya.app"


def adb(*args, dev=None):
    return subprocess.run([ADB, "-s", dev or CAR, *args], check=True, capture_output=True)


def tap(x, y, wait=1.5, dev=None):
    adb("shell", "input", "tap", str(x), str(y), dev=dev)
    time.sleep(wait)


def find(text, dev):
    """화면에서 text가 처음 나오는 요소의 가운데 좌표."""
    import re
    xml = subprocess.run([ADB, "-s", dev, "exec-out", "uiautomator", "dump", "/dev/tty"],
                         capture_output=True).stdout.decode()
    for node in re.findall(r"<node [^>]*>", xml):
        if f'text="{text}"' in node:
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", re.search(r'bounds="([^"]*)"', node).group(1)))
            return (x1 + x2) // 2, (y1 + y2) // 2
    raise RuntimeError(f"not found: {text}")


def record_phone(out):
    """평일: 휴대폰에서 토요일 코스를 계획한다."""
    p = dict(dev=PHONE)
    adb("shell", "pm", "clear", PKG, **p)
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity", **p)
    time.sleep(3)
    rec = subprocess.Popen([ADB, "-s", PHONE, "shell", "screenrecord", "--bit-rate", "8000000",
                            "--time-limit", "60", "/sdcard/phone.mp4"])
    t0 = time.time()
    ev = {}
    mark = lambda k: ev.__setitem__(k, round(time.time() - t0, 2))
    time.sleep(2)
    mark("demo"); tap(540, 2033, 3, **p)         # 바로 체험하기
    mark("plan"); tap(197, 2066, 2.5, **p)       # 새 계획 세우기
    tap(715, 1623, 2, **p)                       # 테마: 아이와 함께
    adb("shell", "input", "swipe", "540", "1900", "540", "1300", "600", **p); time.sleep(2)
    mark("saved"); tap(*find("이 코스로 일정 잡기", PHONE), 5, **p)   # 토요일 10시 저장 알림
    tap(*find("확인", PHONE), 2.5, **p)
    mark("end")
    adb("shell", "pkill", "-INT", "screenrecord", **p)
    rec.wait(timeout=15)
    time.sleep(2)
    adb("pull", "/sdcard/phone.mp4", str(out / "phone.mp4"), **p)
    (out / "phone_events.json").write_text(json.dumps(ev, indent=1))
    print("phone", json.dumps(ev))


def prepare_car():
    """녹화 밖: 차에도 같은 코스를 잡아 두고 앱을 끈다(계정 연동 대신)."""
    adb("shell", "pm", "clear", PKG)
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")
    time.sleep(3)
    tap(1267, 799, 3); tap(719, 824, 2.5); tap(906, 642, 2)
    adb("shell", "input", "swipe", "1260", "750", "1260", "450", "600"); time.sleep(1.5)
    tap(1796, 494, 3); tap(1331, 569, 2)         # 일정 잡기 → 나중에
    adb("shell", "am", "force-stop", PKG)
    adb("shell", "input", "keyevent", "HOME")
    time.sleep(2)


def gear(g):
    subprocess.run([sys.executable, VC, "gear", g], check=True, capture_output=True)


def front():
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(2)


def main(out):
    out.mkdir(parents=True, exist_ok=True)
    adb("forward", "tcp:8088", "tcp:8088")
    gear("P")
    record_phone(out)
    prepare_car()
    rec = subprocess.Popen([ADB, "-s", CAR, "shell", "screenrecord", "--size", "1920x1080", "--bit-rate", "8000000",
                            "--time-limit", "180", "/sdcard/flow.mp4"])
    t0 = time.time()
    ev = {}
    mark = lambda k: ev.__setitem__(k, round(time.time() - t0, 2))
    time.sleep(2.5)
    mark("ignition")                             # 시동 ON = 앱이 켜짐
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(2)
    mark("popup"); time.sleep(4)                 # 계획한 드라이브가 있어요
    mark("go"); tap(1409, 578, 3)                # 출발하기
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
