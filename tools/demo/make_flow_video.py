#!/usr/bin/env python3
"""차비스 사용 시연 영상: 조개섬 캐릭터(그림만 사용) + 새로 그린 배경·움직임 + 실제 앱 녹화(record_flow.py).

흐름: 앱에서 코스 계획 → "드라이브를 시작할까요?" 팝업 → 내비 안내 → 도착 → 다시 D면 다음 장소 자동 안내
      → 마지막 장소 후 귀가 → 집에서 리뷰(만족도) → 다음 추천에 반영.

사용: python make_flow_video.py <record_flow 폴더> <kids-animation 폴더> tools/demo <out.mp4>
(make_usage_video.py의 합성 도구를 재사용한다. Pillow·Chrome 필요)
"""
import json
import sys
from pathlib import Path

TOOLS = Path(sys.argv[3])
sys.path.insert(0, str(TOOLS))
import make_usage_video as mu  # noqa: E402
from make_promo import apartments, car_side, page, render, scene_dash, svg  # noqa: E402

W, H = mu.W, mu.H
mu.APP_CROP = (640, 90, 1280, 850)            # Pleos 화면에서 앱·내비가 뜨는 오른쪽 영역
mu.DISPLAY = (410, 290, 1100, 730)
CROP = mu.APP_CROP
DISP = mu.DISPLAY
CAR_X, CAR_Y, CAR_S = mu.CAR_X, mu.CAR_Y, mu.CAR_S

NARR = {
    "title": "차비스 사용법. 조개섬 친구들의 주말 나들이로 보여 드릴게요.",
    "cast": "어른 둘, 아이 둘. 오늘은 차를 타고 박물관과 맛집에 가요.",
    "plan": "첫째, 차비스에서 오늘 갈 코스를 고릅니다. 아이와 함께 테마에서 마음에 드는 코스를 골라 일정을 잡으면,",
    "popup": "드라이브를 시작할지 물어봐요. 출발하기를 누르면, 계획한 순서대로 첫 목적지를 안내합니다.",
    "navi": "목적지를 다시 검색할 필요 없이, 차량 내비게이션으로 바로 길 안내가 시작돼요.",
    "arrive": "둘째, 박물관에 도착해 차를 세우면 도착으로 처리하고, 다음 장소를 준비해요.",
    "next": "구경을 마치고 다시 시동을 걸어 출발하면, 화면을 누르지 않아도 다음 목적지를 음성으로 알려 주고, 내비 안내를 이어 가요.",
    "finish": "맛있는 점심까지 먹고, 마지막 장소에서 집으로 출발하면 오늘 코스가 끝나요.",
    "review": "셋째, 집에 도착해 차를 세우면, 장소마다 별점과 한 줄 소감으로 오늘 코스의 만족도를 남겨요. 이 리뷰는 다음 코스 추천에 반영됩니다.",
    "end": "계획은 한 번, 안내는 이어서, 만족도는 다음 추천으로. 차비스와 함께 떠나요.",
}


def bg_museum():
    cols = "".join(f'<rect x="{x}" y="260" width="34" height="300" fill="#E9E2D3"/>' for x in range(330, 900, 95))
    return page(svg(f"""<rect width="{W}" height="{H}" fill="#BFE3F7"/><circle cx="1700" cy="150" r="80" fill="#FFF3D6"/>
<rect x="260" y="230" width="700" height="340" fill="#F5EFE2"/><path d="M230 240 L610 110 L990 240 Z" fill="#D9CDB5"/>{cols}
<rect x="560" y="440" width="100" height="130" fill="#8A7A62"/>
<rect y="{H - 260}" width="{W}" height="260" fill="#8C96A3"/><rect y="570" width="{W}" height="250" fill="#A8D88A"/>
{car_side(CAR_X, CAR_Y, CAR_S)}"""), "#BFE3F7")


def bg_food():
    return page(svg(f"""<rect width="{W}" height="{H}" fill="#CBE7F5"/>
<rect x="200" y="280" width="760" height="300" fill="#F2D6B3"/><rect x="180" y="250" width="800" height="60" fill="#C8553D"/>
<path d="M180 310 Q230 350 280 310 Q330 350 380 310 Q430 350 480 310 Q530 350 580 310 Q630 350 680 310 Q730 350 780 310 Q830 350 880 310 Q930 350 980 310" fill="#E07A5F"/>
<rect x="260" y="380" width="200" height="140" fill="#9ED0E6"/><rect x="520" y="380" width="120" height="200" fill="#8A5A3A"/><rect x="700" y="380" width="200" height="140" fill="#9ED0E6"/>
<rect y="{H - 260}" width="{W}" height="260" fill="#8C96A3"/><rect y="580" width="{W}" height="240" fill="#B9C3CE"/>
{car_side(CAR_X, CAR_Y, CAR_S)}"""), "#CBE7F5")


def bg_home():
    return page(svg(f"""<defs><linearGradient id="sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#F08A5D"/><stop offset="1" stop-color="#FFD3A5"/></linearGradient></defs>
<rect width="{W}" height="{H}" fill="url(#sky)"/>{apartments("#B5838D", "#FFE08A", 0.55)}
<rect y="{H - 260}" width="{W}" height="260" fill="#7E8794"/>{car_side(CAR_X, CAR_Y, CAR_S)}"""), "#FFD3A5")


def main(rec_dir, kids_dir, out):
    rec, kids = Path(rec_dir), Path(kids_dir)
    work = rec / "flow_work"
    work.mkdir(exist_ok=True)
    bgm = kids / "episodes/ep00/audio/bgm.mp3"
    jua = str(kids / "assets/fonts/Jua-Regular.ttf")
    ev = json.loads((rec / "events.json").read_text())
    flow = rec / "flow.mp4"
    P = lambda n: work / n

    voice = {}
    for k, t in NARR.items():
        voice[k] = P(f"n_{k}.wav")
        mu.say(t, voice[k])
    vd = {k: mu.dur_of(v) + 0.4 for k, v in voice.items()}
    mu.prep_cast(kids / "assets/characters", work)

    for name, html in [("bg_cast", mu.bg_cast()), ("bg_am", mu.bg_parking("#8EC9F0", "#E4F3FB", (300, 180))),
                       ("bg_museum", bg_museum()), ("bg_food", bg_food()), ("bg_home", bg_home()),
                       ("dash_am", scene_dash("#8EC9F0", "#DDF0FA", "", "")),
                       ("dash_pm", scene_dash("#7FB8E6", "#CFE6F5", "", "")),
                       ("dash_eve", scene_dash("#F08A5D", "#FFD3A5", "", ""))]:
        render(html, P(f"{name}.png"))

    mu.step_png(P("s1.png"), 1, "앱에서 코스 계획, 드라이브 시작", jua)
    mu.step_png(P("s2.png"), 2, "다시 출발하면 다음 장소 자동 안내", jua)
    mu.step_png(P("s3.png"), 3, "집에 와서 만족도 리뷰", jua)
    X = mu.app_xy
    def box(name, r, text, color=mu.ACCENT, below=False):
        (x0, y0), (x1, y1) = X(r[0], r[1]), X(r[2], r[3])
        mu.box_png(P(name), (x0, y0, x1, y1), text, color=color, below=below)
    box("c_theme.png", (658, 620, 1060, 665), "테마 고르기: 아이와 함께", below=True)
    box("c_course.png", (658, 340, 1878, 545), "코스 골라 일정 잡기")
    box("c_popup.png", (1064, 413, 1470, 615), "드라이브를 시작할까요?  →  출발하기", below=True)
    box("c_start.png", (1416, 150, 1874, 240), "계획 순서대로 첫 목적지 안내", below=True)
    box("c_navi.png", (664, 115, 1244, 915), "차량 내비 자동 연결 (Pleos NaviHelper)", color=(91, 141, 239), below=False)
    box("c_arrive.png", (1416, 150, 1874, 275), "도착 처리 → 다음 장소 준비", below=True)
    box("c_next.png", (680, 130, 1230, 900), "다시 D → 다음 목적지 음성 안내 + 내비", color=(91, 141, 239))
    box("c_done.png", (1416, 150, 1874, 240), "오늘 코스 완료", below=True)
    box("c_review.png", (1416, 365, 1874, 660), "장소별 별점 + 한 줄 소감", below=False)
    box("c_saved.png", (1416, 150, 1874, 330), "리뷰 저장 → 다음 코스 추천에 반영", below=True)

    parts, t = [], 0.0
    def done(sc, key, name):
        nonlocal t
        p = work / f"{len(parts):02d}_{name}.mp4"
        t += sc.render(voice[key], p, bgm, t)
        parts.append(p)

    def screen(bg, key, name, ss, step, boxes, min_dur=0, chars=()):
        sc = mu.Scene(P(bg), max(vd[key] + 1.0, min_dur))
        sc.add(flow, DISP[0], DISP[1], 0, video=(ss, CROP, DISP[2], DISP[3]))
        for png, a, b in boxes:
            sc.add(P(png), 0, 0, a, b if b is not None else sc.dur)
        for png, x, y in chars:
            mu.hop(sc, P(png), x, y, amp=8, speed=3)
        sc.add(P(step), 0, 0)
        done(sc, key, name)

    # 0. 제목
    sc = mu.Scene(P("bg_cast.png"), max(vd["title"] + 1.0, 5))
    mu.label_png(P("title.png"), "차비스 사용법", 110, bg=(0, 0, 0, 0), fg=mu.ACCENT, jua=jua)
    mu.label_png(P("title2.png"), "조개섬 친구들의 주말 나들이", 56, bg=(0, 0, 0, 0), fg=mu.INK, jua=jua)
    sc.add(P("title.png"), "(W-w)/2", 260); sc.add(P("title2.png"), "(W-w)/2", 420)
    for i, (cid, *_r) in enumerate(mu.CAST):
        mu.hop(sc, P(f"{cid}_front.png"), 520 + i * 240, "860-h", start=0.3 + i * 0.25, amp=14, speed=5 + i)
    done(sc, "title", "title")

    # 1. 등장
    sc = mu.Scene(P("bg_cast.png"), max(vd["cast"] + 1.0, 6))
    for i, (cid, name, role, _h, _s) in enumerate(mu.CAST):
        x = 300 + i * 360
        role = "어른" if i < 2 else role
        mu.label_png(P(f"name_{cid}.png"), f"{name} · {role}", 40)
        mu.walk(sc, P(f"{cid}_walk.png"), -300, x, "860-h", 0.2 + i * 0.3, 1.6 + i * 0.3)
        sc.add(P(f"{cid}_front.png"), x, "860-h", 1.6 + i * 0.3)
        sc.add(P(f"name_{cid}.png"), x - 40, 880, 1.8 + i * 0.3)
    done(sc, "cast", "cast")

    # 2. 계획: 테마 → 코스
    s = ev["plan"] - 0.3
    rel = lambda k: ev[k] - s
    screen("dash_am.png", "plan", "plan", s, "s1.png",
           [("c_theme.png", rel("plan") + 2.6, rel("popup") - 1.5), ("c_course.png", rel("popup") - 1.5, None)],
           min_dur=rel("popup") + 0.3, chars=[("otter_front.png", 1560, "1060-h")])
    # 3. 팝업 → 출발하기 → 첫 목적지
    s = ev["popup"] + 0.2
    rel = lambda k: ev[k] - s
    screen("dash_am.png", "popup", "popup", s, "s1.png",
           [("c_popup.png", 0.3, rel("go") + 0.2), ("c_start.png", rel("go") + 0.8, None)],
           min_dur=rel("navi") + 0.2, chars=[("turtle_front.png", 1580, "1060-h")])
    # 4. 내비 안내
    s = ev["navi"] + 0.5
    screen("dash_am.png", "navi", "navi", s, "s1.png", [("c_navi.png", 3.0, None)], min_dur=ev["arrive1"] - s)

    # 5. 박물관 도착: 내려서 구경
    door = CAR_X + 230
    sc = mu.Scene(P("bg_museum.png"), max(vd["arrive"] + 1.0, 5.5))
    for i, (cid, *_r) in enumerate(mu.CAST):
        mu.walk(sc, P(f"{cid}_front.png"), door, 420 + i * 120, f"{H - 240}-h", 0.3 + i * 0.35, 3.5 + i * 0.35,
                hide_at_end=False)
    sc.add(P("s2.png"), 0, 0)
    done(sc, "arrive", "museum")
    # 6. 다시 D → 다음 장소 (도착 화면부터 이어서)
    s = ev["arrive1"] + 3.0
    rel = lambda k: ev[k] - s
    screen("dash_pm.png", "next", "next", s, "s2.png",
           [("c_arrive.png", 0.3, rel("next") + 0.5), ("c_next.png", rel("next") + 4.0, None)],
           min_dur=rel("arrive2"))
    # 7. 맛집 → 마지막 출발
    sc = mu.Scene(P("bg_food.png"), max(vd["finish"] + 1.0, 6))
    for i, (cid, *_r) in enumerate(mu.CAST):
        mu.walk(sc, P(f"{cid}_walk.png"), 300 + i * 110, door, f"{H - 240}-h", 0.3 + i * 0.4, 2.6 + i * 0.4)
    sc.add(P("s2.png"), 0, 0)
    done(sc, "finish", "food")
    # 8. 집 도착 → 리뷰
    sc = mu.Scene(P("bg_home.png"), 4.5)
    for i, (cid, *_r) in enumerate(mu.CAST):
        mu.walk(sc, P(f"{cid}_front.png"), door, 300 + i * 130, f"{H - 240}-h", 0.2 + i * 0.3, 2.4 + i * 0.3, hide_at_end=False)
    sc.add(P("s3.png"), 0, 0)
    s = ev["review"] - 1.0
    rel = lambda k: ev[k] - s
    sc2 = mu.Scene(P("dash_eve.png"), max(vd["review"] + 1.0, rel("end") + 1.5))
    sc2.add(flow, DISP[0], DISP[1], 0, video=(s, CROP, DISP[2], DISP[3]))
    sc2.add(P("c_review.png"), 0, 0, 1.5, rel("end") - 3.5)
    sc2.add(P("c_saved.png"), 0, 0, rel("end") - 3.0, sc2.dur)
    mu.hop(sc2, P("crab_front.png"), 1600, "1060-h", amp=10, speed=4)
    sc2.add(P("s3.png"), 0, 0)
    silent = P("silence.wav")
    mu.ff(["-f", "lavfi", "-i", "anullsrc=r=48000:cl=stereo", "-t", "0.5", str(silent)])
    p = work / f"{len(parts):02d}_home.mp4"; t += sc.render(silent, p, bgm, t); parts.append(p)
    done(sc2, "review", "review")

    # 9. 마무리
    sc = mu.Scene(P("bg_cast.png"), max(vd["end"] + 1.5, 8))
    for i, txt in enumerate(["1  앱에서 코스 계획, 드라이브 시작 팝업", "2  다시 출발하면 다음 장소 자동 안내", "3  집에 와서 만족도 리뷰, 다음 추천"]):
        mu.label_png(P(f"sum{i}.png"), txt, 46, jua=jua)
        sc.add(P(f"sum{i}.png"), 120, 90 + i * 110, 0.4 + i * 0.6)
    mu.label_png(P("logo.png"), "차비스", 120, bg=(255, 255, 255, 235), fg=mu.ACCENT, jua=jua)
    sc.add(P("logo.png"), "W-w-120", 120, 2.4)
    for i, (cid, *_r) in enumerate(mu.CAST):
        mu.hop(sc, P(f"{cid}_front.png"), 560 + i * 240, "980-h", amp=12, speed=5 + i)
    note = "앱 화면: 실제 APK를 Pleos Connect Emulator 2.1.0에서 녹화 · 기어는 Virtual Console · 캐릭터: 조개섬 친구들(자체 제작)"
    mu.label_png(P("note.png"), note, 22, bg=(20, 24, 31, 200), fg=(220, 224, 230), pad=(20, 8))
    sc.add(P("note.png"), "(W-w)/2", 1030)
    done(sc, "end", "end")

    listing = work / "list.txt"
    listing.write_text("".join(f"file '{q}'\n" for q in parts))
    mu.ff(["-f", "concat", "-safe", "0", "-i", str(listing), "-c", "copy", out])


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[4])
