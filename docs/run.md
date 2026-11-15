# 시연 재현

## 준비

- Pleos Connect Emulator 2.1.0 실행(emulator-5554), Virtual Console(:8088) 동작
- Android 14 휴대폰 에뮬레이터 실행(emulator-5556, 1080×2400)
- 두 에뮬레이터 모두 `adb install -r -g chavis-0.2.0.apk`

## 녹화

```bash
python3 tools/demo/record_weekly.py <폴더>   # 휴대폰 계획 + 차 시동 흐름 녹화, 장면 시각(phone_events.json, events.json)
```

1. 휴대폰(평일): 바로 체험하기 → 새 계획 세우기 → 테마 「아이와 함께」 → 이 코스로 일정 잡기 → **계획을 저장했어요(토요일 오전 10시)** → 확인
2. 차(녹화 밖 준비): 같은 코스를 일정으로 잡아 두고(나중에) 앱을 끈다. 휴대폰↔차 계획 동기화(Pleos 계정 연동)는 개발 예정이라 이렇게 대신한다.
3. 차(주말): 앱 실행(시동 ON) → **계획한 드라이브가 있어요 → 출발하기**
→ 오늘 루트 시작(시스템 내비 안내) → 기어 D → P(도착 처리) → **다시 D: 다음 목적지 자동 안내** → P(도착 처리)
→ 다시 D(루트 완료) → P → 리뷰(별점) 저장.

차에서 바로 계획하는 흐름("드라이브를 시작할까요?" 팝업)은 `record_flow.py` + `make_flow_video.py`로 녹화·합성한다.

기어는 Virtual Console HTTP API로 바꾼다(`tools/demo/vc.py gear D`). 내비 도착 이벤트는 에뮬레이터에서 실제 주행이 없어 **도착 처리** 버튼으로 대신한다.

## 영상 합성

```bash
python tools/demo/make_weekly_video.py <녹화 폴더> <kids-animation 폴더> tools/demo demo/chavis-usage.mp4
```

- 캐릭터 그림: 자체 제작 「조개섬 친구들」(kids-animation 프로젝트). 배경·움직임은 스크립트가 새로 그린다.
- 앱 화면: 녹화본에서 앱·내비 영역만 잘라 그대로 쓴다.
- 내레이션: macOS `say -v Yuna`.
