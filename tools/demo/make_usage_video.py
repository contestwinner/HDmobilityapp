#!/usr/bin/env python3
"""차비스 사용 시연 영상 (조개섬 캐릭터 출연).

캐릭터 그림만 kids-animation에서 가져오고, 배경·상황·움직임은 이 스크립트가 새로 만든다.
앱 화면은 record_demo.py가 녹화한 실제 APK 화면(차비스 앱 창만 잘라냄)이다.
화면 위의 단계 번호·강조 표시로 "실제로 어떻게 쓰는지"를 보여 준다. 목소리는 내레이션 하나.

사용: python make_usage_video.py <record_demo 폴더> <kids-animation 폴더> <out.mp4>   (Pillow·Chrome 필요)
"""
import json
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageOps

sys.path.insert(0, str(Path(__file__).parent))
from make_promo import apartments, car_side, page, render, scene_dash, svg  # noqa: E402

W, H = 1920, 1080
APP_CROP = (642, 93, 1252, 846)               # Pleos 화면에서 차비스 앱 창 (x, y, w, h)
DISPLAY = (410, 290, 1100, 743)               # 대시보드 틀 안 디스플레이
PHONE = (700, 60, 432, 960)                   # 휴대폰 화면 위치
ACCENT = (79, 195, 161)
WARN = (255, 170, 60)
INK = (34, 38, 48)
GOTHIC = "/System/Library/Fonts/AppleSDGothicNeo.ttc"
CAR_X, CAR_Y, CAR_S = 1060, 560, 2.0          # 주차 장면 자동차 위치(옆모습 SVG)

NARR = {
    "title": "차비스 사용법. 조개섬 친구들의 나들이로 보여 드릴게요.",
    "cast": "오늘은 어른 둘, 아이 둘이 함께 국립중앙박물관에 가요.",
    "plan": "첫째, 전날 밤 휴대폰 앱에서 가족과 목적지, 출발 시각을 고릅니다. 차비스가 시간대별 혼잡을 보고 코스를 짜 주고, 차로 보낼 짧은 코드를 만들어요.",
    "board": "둘째, 아침에 모두 차에 타면, 좌석 센서가 몇 명이 탔는지 알아요.",
    "depart": "차 화면에 코드를 한 번 넣고 기어를 D로 바꾸면, 차비스가 오늘 갈 곳과 주차장, 챙길 물건을 음성으로 알려 줘요. 운전 중에는 큰 글씨와 음성만 보여 줘요.",
    "lunch": "셋째, 점심을 먹고 다시 탈 때예요. 그런데 콩콩이가 아직 놀이터에 있네요.",
    "reboard": "기어를 D로 바꾸는 순간, 차비스가 알려 줘요. 좌석이 넷 중 셋만 감지됐어요, 모두 탔나요? 집을 나선 지 네 시간, 기저귀 갈 시간과 연료 부족까지 함께 챙겨요.",
    "fixed": "콩콩이까지 타면 좌석 넷 중 넷. 이제 안심하고 출발해요.",
    "home": "집에 갈 때도, 다시 탈 때마다 지난 시간과 남은 길을 알려 줘요.",
    "end": "계획은 휴대폰에서, 다시 탈 때마다 확인은 차비스가. 차비스와 함께 떠나요.",
}


# --- 공통 -----------------------------------------------------------------

def ff(args):
    subprocess.run(["ffmpeg", "-v", "error", "-y", *args], check=True)


def dur_of(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)],
                         check=True, capture_output=True, text=True).stdout
    return float(out.strip())


def say(text, out):
    aiff = out.with_suffix(".aiff")
    subprocess.run(["say", "-v", "Yuna", "-r", "180", "-o", str(aiff), text], check=True)
    ff(["-i", str(aiff), "-ar", "48000", "-ac", "2", str(out)])


def font(size, jua=None):
    return ImageFont.truetype(jua, size) if jua else ImageFont.truetype(GOTHIC, size, index=6)


def app_xy(x, y):
    """원본 Pleos 화면 좌표 → 대시보드 틀 안 좌표."""
    sx = DISPLAY[2] / APP_CROP[2]
    return int(DISPLAY[0] + (x - APP_CROP[0]) * sx), int(DISPLAY[1] + (y - APP_CROP[1]) * sx)


# --- 캐릭터 ---------------------------------------------------------------

def cut_view(sheet, col, out, height, mirror=False):
    """turnaround 시트(4칸)에서 한 칸을 잘라 흰 배경을 투명하게 만든다."""
    img = Image.open(sheet).convert("RGBA")
    w, h = img.size
    img = img.crop((col * w // 4, 0, (col + 1) * w // 4, h))
    for seed in [(0, 0), (img.width - 1, 0), (0, h - 1), (img.width - 1, h - 1), (img.width // 2, 2), (img.width // 2, h - 2)]:
        ImageDraw.floodfill(img, seed, (255, 255, 255, 0), thresh=30)
    img = img.crop(img.getbbox())
    img = img.resize((int(img.width * height / img.height), height), Image.LANCZOS)
    if mirror:
        img = ImageOps.mirror(img)
    img.save(out)
    return img


CAST = [  # id, 이름, 역할, 키(px), 옆모습 칸
    ("pelican", "펠리", "어른 · 운전", 330, 2),
    ("turtle", "꼬부", "어른", 240, 2),
    ("otter", "달이", "5살", 250, 2),
    ("crab", "콩콩", "1살", 170, 0),
]


def prep_cast(chars, work):
    faces = {}
    for cid, _, _, hgt, side in CAST:
        front = cut_view(chars / f"{cid}/turnaround.png", 0, work / f"{cid}_front.png", hgt)
        cut_view(chars / f"{cid}/turnaround.png", side, work / f"{cid}_walk.png", hgt, mirror=(side == 2))
        faces[cid] = front.crop((0, 0, front.width, int(front.height * (0.62 if cid == "crab" else 0.5))))
    return faces


# --- 그림 레이어 -----------------------------------------------------------

def label_png(out, text, size=40, bg=(255, 255, 255, 240), fg=INK, jua=None, pad=(30, 16)):
    f = font(size, jua)
    d0 = ImageDraw.Draw(Image.new("RGBA", (10, 10)))
    tw = int(d0.textlength(text, font=f))
    img = Image.new("RGBA", (tw + pad[0] * 2, size + pad[1] * 2 + 8), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, img.width - 1, img.height - 1], radius=(size + pad[1] * 2) // 2, fill=bg)
    d.text((pad[0], pad[1]), text, font=f, fill=fg)
    img.save(out)
    return img.size


def step_png(out, num, text, jua):
    f1, f2 = font(44, jua), font(52, jua)
    img = Image.new("RGBA", (W, 130), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    tw = d.textlength(text, font=f2)
    d.rounded_rectangle([40, 24, 40 + 150 + tw + 60, 116], radius=46, fill=(255, 255, 255, 240))
    d.rounded_rectangle([50, 32, 180, 108], radius=38, fill=ACCENT + (255,))
    d.text((72, 42), f"{num}단계", font=font(34, jua), fill=(255, 255, 255))
    d.text((210, 38), text, font=f2, fill=INK)
    img.save(out)


def box_png(out, rect, label, color=ACCENT, below=False):
    """강조 테두리 + 설명 말풍선 (화면 전체 크기 투명 레이어)."""
    x0, y0, x1, y1 = rect
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([x0 - 8, y0 - 8, x1 + 8, y1 + 8], radius=14, outline=color + (255,), width=7)
    f = font(36)
    tw = d.textlength(label, font=f)
    lx = min(max(x0, 30), W - tw - 90)
    ly = y1 + 22 if below else y0 - 84
    d.rounded_rectangle([lx, ly, lx + tw + 48, ly + 62], radius=30, fill=color + (255,))
    d.text((lx + 24, ly + 9), label, font=f, fill=(20, 24, 31))
    img.save(out)


def seat_map(out, faces, seated, missing=None):
    """위에서 본 차 좌석 그림. seated: 앉은 캐릭터 id 목록(앞좌 운전석, 조수석, 뒤 왼, 뒤 오 순서)."""
    img = Image.new("RGBA", (380, 560), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([20, 60, 360, 540], radius=110, fill=(245, 247, 250, 245), outline=(60, 66, 80), width=6)
    d.rounded_rectangle([70, 100, 310, 150], radius=20, fill=(190, 225, 245))
    seats = [(85, 190), (215, 190), (85, 360), (215, 360)]
    order = ["pelican", "turtle", "otter", "crab"]
    for (sx, sy), cid in zip(seats, order):
        empty = cid not in seated
        col = (255, 120, 100) if (empty and cid == missing) else ((200, 205, 215) if empty else ACCENT)
        d.rounded_rectangle([sx, sy, sx + 90, sy + 120], radius=22, fill=col)
        if not empty:
            fc = faces[cid].copy()
            fc.thumbnail((84, 84))
            img.paste(fc, (sx + 45 - fc.width // 2, sy + 60 - fc.height // 2), fc)
        elif cid == missing:
            d.text((sx + 30, sy + 30), "?", font=font(56), fill=(255, 255, 255))
    n = len(seated)
    ok = n == 4
    d.rounded_rectangle([60, 0, 320, 52], radius=26, fill=(ACCENT if ok else (WARN if missing else (90, 96, 110))))
    txt = f"좌석 센서 {n}/4"
    f = font(32)
    d.text((190 - d.textlength(txt, font=f) / 2, 6), txt, font=f, fill=(255, 255, 255))
    img.save(out)


# --- 배경 (새로 그림) -------------------------------------------------------

def bg_cast():
    return page(svg(f"""<defs><radialGradient id="g" cx=".5" cy=".45" r=".7"><stop offset="0" stop-color="#FFF6E5"/><stop offset="1" stop-color="#FFD9B0"/></radialGradient></defs>
<rect width="{W}" height="{H}" fill="url(#g)"/><ellipse cx="960" cy="860" rx="820" ry="70" fill="#F2C49A"/>"""), "#FFF6E5")


def bg_night():
    return page(svg(f"""<rect width="{W}" height="{H}" fill="#232B4D"/>
<rect x="1360" y="110" width="430" height="300" rx="16" fill="#33406E"/><circle cx="1680" cy="200" r="54" fill="#F5E6B8"/>
<line x1="1575" y1="110" x2="1575" y2="410" stroke="#232B4D" stroke-width="10"/>
<rect x="0" y="860" width="{W}" height="220" fill="#2D365E"/>
<rect x="1240" y="640" width="640" height="220" rx="46" fill="#6D59A0"/><rect x="1240" y="580" width="640" height="120" rx="46" fill="#7D69B0"/>
<rect x="160" y="520" width="20" height="340" fill="#C9A06A"/><path d="M90 520 L250 520 L210 420 L130 420 Z" fill="#FFD27A"/>
<ellipse cx="170" cy="640" rx="220" ry="220" fill="#FFD27A" opacity=".10"/>
<rect x="{PHONE[0] - 20}" y="{PHONE[1] - 20}" width="{PHONE[2] + 40}" height="{PHONE[3] + 40}" rx="54" fill="#0B0D12"/>"""), "#232B4D")


def bg_parking(sky_top, sky_bottom, sun):
    return page(svg(f"""<defs><linearGradient id="sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{sky_top}"/><stop offset="1" stop-color="{sky_bottom}"/></linearGradient></defs>
<rect width="{W}" height="{H}" fill="url(#sky)"/><circle cx="{sun[0]}" cy="{sun[1]}" r="90" fill="#FFF3D6"/>
{apartments("#9FB3C8", "#FFF3D6", 0.3)}<rect y="{H - 260}" width="{W}" height="260" fill="#8C96A3"/>
<rect x="980" y="{H - 250}" width="10" height="200" fill="#F2F4F7"/><rect x="1900" y="{H - 250}" width="10" height="200" fill="#F2F4F7"/>
{car_side(CAR_X, CAR_Y, CAR_S)}"""), sky_bottom)


def bg_park():
    return page(svg(f"""<rect width="{W}" height="{H}" fill="#BFE6F5"/><circle cx="1720" cy="150" r="90" fill="#FFF0B3"/>
<path d="M0 520 Q400 440 800 520 T1920 500 L1920 {H} L0 {H} Z" fill="#A8D88A"/>
<rect y="{H - 260}" width="{W}" height="260" fill="#8C96A3"/>
<rect x="160" y="300" width="36" height="300" fill="#7A5A3A"/><circle cx="178" cy="280" r="130" fill="#5FA35A"/>
<path d="M330 820 L450 560 L500 560 L640 820" fill="none" stroke="#FF8A65" stroke-width="24"/><rect x="440" y="540" width="70" height="26" fill="#FF8A65"/>
<rect x="620" y="560" width="14" height="260" fill="#6C7A89"/><rect x="820" y="560" width="14" height="260" fill="#6C7A89"/><rect x="610" y="550" width="236" height="16" fill="#6C7A89"/>
<line x1="680" y1="566" x2="680" y2="720" stroke="#555" stroke-width="4"/><line x1="770" y1="566" x2="770" y2="720" stroke="#555" stroke-width="4"/><rect x="664" y="716" width="124" height="14" rx="6" fill="#4FC3A1"/>
<rect x="0" y="{H - 270}" width="900" height="14" fill="#C9B18A"/>
{car_side(CAR_X, CAR_Y, CAR_S)}"""), "#BFE6F5")


def bg_road(sky_top, sky_bottom):
    hills = "".join(f'<ellipse cx="{x}" cy="640" rx="360" ry="200" fill="#7FB77E" opacity=".9"/>' for x in range(0, 3840 + 1, 640))
    dashes = "".join(f'<rect x="{x}" y="900" width="120" height="14" rx="6" fill="#F2F4F7"/>' for x in range(0, 3840, 240))
    trees = "".join(f'<rect x="{x + 80}" y="620" width="24" height="120" fill="#7A5A3A"/><circle cx="{x + 92}" cy="600" r="64" fill="#5FA35A"/>' for x in range(0, 3840, 480))
    return f"""<html><head><meta charset='utf-8'><style>*{{margin:0}}body{{width:3840px;height:{H}px;overflow:hidden}}</style></head><body>
<svg width="3840" height="{H}"><defs><linearGradient id="sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{sky_top}"/><stop offset="1" stop-color="{sky_bottom}"/></linearGradient></defs>
<rect width="3840" height="{H}" fill="url(#sky)"/>{hills}{trees}<rect y="780" width="3840" height="300" fill="#6E7784"/>{dashes}</svg></body></html>"""


def car_only():
    return f"""<html><head><meta charset='utf-8'><style>*{{margin:0}}body{{width:820px;height:360px;background:transparent}}</style></head><body>
<svg width="820" height="360"><g transform="translate(20,80)">{car_side(0, 0, 2.0)}</g></svg></body></html>"""


def render_wide(html, png, w, h, transparent=False):
    src = png.with_suffix(".html")
    src.write_text(html, encoding="utf-8")
    extra = ["--default-background-color=00000000"] if transparent else []
    subprocess.run(["/Applications/Google Chrome.app/Contents/MacOS/Google Chrome", "--headless=new", "--disable-gpu",
                    "--hide-scrollbars", f"--window-size={w},{h}", *extra, f"--screenshot={png}", src.as_uri()],
                   check=True, capture_output=True)


# --- 합성 -----------------------------------------------------------------

class Scene:
    """배경 1장(또는 영상) 위에 레이어를 시간·위치 식으로 겹친다."""

    def __init__(self, bg, dur, bg_video=None):
        self.inputs = ["-loop", "1", "-framerate", "30", "-t", str(dur), "-i", str(bg)]
        self.chain = ["[0:v]fps=30,format=rgba[b0]"]
        self.n, self.last, self.dur = 1, "b0", dur

    def add(self, src, x, y, start=0.0, end=None, video=None):
        """x, y: ffmpeg overlay 식(t 사용 가능). video: (ss, crop, w, h) 이면 영상 레이어."""
        end = self.dur if end is None else end
        if video:
            ss, crop, w, h = video
            self.inputs += ["-ss", str(ss), "-i", str(src)]
            pre = f"crop={crop[2]}:{crop[3]}:{crop[0]}:{crop[1]}," if crop else ""
            self.chain.append(f"[{self.n}:v]{pre}scale={w}:{h},fps=30,setpts=PTS-STARTPTS+{start}/TB,"
                              f"tpad=stop_mode=clone:stop_duration={self.dur}[l{self.n}]")
        else:
            self.inputs += ["-loop", "1", "-framerate", "30", "-t", str(self.dur), "-i", str(src)]
            self.chain.append(f"[{self.n}:v]fps=30,format=rgba[l{self.n}]")
        out = f"o{self.n}"
        self.chain.append(f"[{self.last}][l{self.n}]overlay=x='{x}':y='{y}':eval=frame:"
                          f"enable='between(t,{start},{end})'[{out}]")
        self.last, self.n = out, self.n + 1

    def render(self, voice, out, bgm, bgm_start):
        a = (f"[{self.n}:a]aformat=sample_rates=48000:channel_layouts=stereo,adelay=400|400[nv];"
             f"[{self.n + 1}:a]aformat=sample_rates=48000:channel_layouts=stereo,volume=0.10[m];"
             f"[nv][m]amix=inputs=2:duration=longest:normalize=0,apad,atrim=0:{self.dur}[aout]")
        ff(self.inputs + ["-i", str(voice), "-ss", str(bgm_start), "-i", str(bgm), "-filter_complex",
                          ";".join(self.chain) + f";[{self.last}]format=yuv420p[v];" + a,
                          "-map", "[v]", "-map", "[aout]", "-t", str(self.dur), "-c:v", "libx264", "-c:a", "aac", str(out)])
        return self.dur


def walk(sc, png, x0, x1, y, t0, t1, hide_at_end=True):
    """t0→t1 동안 x0→x1 로 걸어가며 통통 튄다."""
    speed = (x1 - x0) / (t1 - t0)
    x = f"if(lt(t,{t0}),{x0},if(lt(t,{t1}),{x0}+(t-{t0})*{speed:.1f},{x1}))"
    yy = f"{y}-if(between(t,{t0},{t1}),abs(sin((t-{t0})*9))*16,0)"
    sc.add(png, x, yy, 0, t1 if hide_at_end else None)


def hop(sc, png, x, y, start=0, end=None, amp=26, speed=7):
    sc.add(png, x, f"{y}-abs(sin(t*{speed}))*{amp}", start, end)


def main(rec_dir, kids_dir, out):
    rec, kids = Path(rec_dir), Path(kids_dir)
    work = rec / "usage_work"
    work.mkdir(exist_ok=True)
    bgm = kids / "episodes/ep00/audio/bgm.mp3"
    jua = str(kids / "assets/fonts/Jua-Regular.ttf")
    ev = json.loads((rec / "events.json").read_text())
    car, phone = rec / "car.mp4", rec / "phone.mp4"

    voice = {}
    for k, t in NARR.items():
        voice[k] = work / f"n_{k}.wav"
        say(t, voice[k])
    vd = {k: dur_of(v) + 0.4 for k, v in voice.items()}
    faces = prep_cast(kids / "assets/characters", work)
    P = lambda name: work / name

    # 배경
    for name, html in [("bg_cast", bg_cast()), ("bg_night", bg_night()),
                       ("bg_am", bg_parking("#8EC9F0", "#E4F3FB", (300, 180))), ("bg_park", bg_park()),
                       ("dash_am", scene_dash("#8EC9F0", "#DDF0FA", "", "")),
                       ("dash_pm", scene_dash("#7FB8E6", "#CFE6F5", "", "")),
                       ("dash_eve", scene_dash("#F08A5D", "#FFD3A5", "", ""))]:
        render(html, P(f"{name}.png"))
    render_wide(bg_road("#F08A5D", "#FFD3A5"), P("road_eve.png"), 3840, H)
    render_wide(car_only(), P("car.png"), 820, 360, transparent=True)

    # 글자·표시 레이어
    step_png(P("s1.png"), 1, "전날 밤, 휴대폰으로 계획", jua)
    step_png(P("s2.png"), 2, "아침, 타고 기어 D", jua)
    step_png(P("s3.png"), 3, "다시 탈 때마다 자동 확인", jua)
    step_png(P("s4.png"), 4, "집으로 가는 길까지", jua)
    for i, (text, y) in enumerate([("가족: 어른 2 · 아이 2(5살, 1살)", 220), ("목적지 고르기", 420), ("출발 시각 고르기", 560),
                                   ("혼잡을 피한 하루 코스", 380), ("차로 보낼 코드", 600)]):
        label_png(P(f"pl{i}.png"), text, 40)
    seat_map(P("seat0.png"), faces, [])
    for i, who in enumerate([["pelican"], ["pelican", "turtle"], ["pelican", "turtle", "otter"], ["pelican", "turtle", "otter", "crab"]]):
        seat_map(P(f"seat{i + 1}.png"), faces, who)
    seat_map(P("seat_miss.png"), faces, ["pelican", "turtle", "otter"], missing="crab")
    seat_map(P("seat_ok.png"), faces, ["pelican", "turtle", "otter", "crab"])
    fx, fy = app_xy(1436, 384); fx1, fy1 = app_xy(1820, 440)
    box_png(P("c_code.png"), (fx, fy, fx1, fy1), "① 휴대폰 코드 입력 → 불러오기")
    gx, gy = app_xy(1440, 118); gx1, gy1 = app_xy(1500, 156)
    box_png(P("c_gear.png"), (gx, gy, gx1, gy1), "② 기어 D = 출발 감지 (Pleos Vehicle SDK)", below=True)
    hx, hy = app_xy(660, 225); hx1, hy1 = app_xy(1880, 460)
    box_png(P("c_guide.png"), (hx, hy, hx1, hy1), "③ 다음 장소 · 주차장 · 챙길 것을 음성으로")
    wx, wy = app_xy(660, 470); wx1, wy1 = app_xy(1880, 620)
    box_png(P("c_warn.png"), (wx, wy, wx1, wy1), "좌석 3/4 · 기저귀 · 주유 경고", color=WARN, below=True)
    sx, sy = app_xy(1515, 118); sx1, sy1 = app_xy(1600, 156)
    box_png(P("c_seat.png"), (sx, sy, sx1, sy1), "좌석 센서 3/4 (Pleos Vehicle SDK)", color=WARN, below=True)
    label_png(P("drive_tag.png"), "주행 중: 큰 글씨 + 음성만", 40, bg=(20, 24, 31, 230), fg=(255, 255, 255))

    parts, t = [], 0.0
    def done(sc, key, name):
        nonlocal t
        p = work / f"{len(parts):02d}_{name}.mp4"
        t += sc.render(voice[key], p, bgm, t)
        parts.append(p)

    # 0. 제목
    sc = Scene(P("bg_cast.png"), max(vd["title"] + 1.0, 5))
    label_png(P("title.png"), "차비스 사용법", 110, bg=(0, 0, 0, 0), fg=ACCENT, jua=jua)
    label_png(P("title2.png"), "조개섬 친구들의 나들이", 56, bg=(0, 0, 0, 0), fg=INK, jua=jua)
    sc.add(P("title.png"), "(W-w)/2", 260); sc.add(P("title2.png"), "(W-w)/2", 420)
    for i, (cid, *_rest) in enumerate(CAST):
        hop(sc, P(f"{cid}_front.png"), 520 + i * 240, f"860-h", start=0.3 + i * 0.25, amp=14, speed=5 + i)
    done(sc, "title", "title")

    # 1. 등장
    sc = Scene(P("bg_cast.png"), max(vd["cast"] + 1.0, 6))
    for i, (cid, name, role, _h, _s) in enumerate(CAST):
        x = 300 + i * 360
        label_png(P(f"name_{cid}.png"), f"{name} · {role}", 40)
        walk(sc, P(f"{cid}_walk.png"), -300, x, f"860-h", 0.2 + i * 0.3, 1.6 + i * 0.3, hide_at_end=True)
        sc.add(P(f"{cid}_front.png"), x, "860-h", 1.6 + i * 0.3)
        sc.add(P(f"name_{cid}.png"), x - 40, 880, 1.8 + i * 0.3)
    done(sc, "cast", "cast")

    # 2. 1단계: 전날 밤 휴대폰
    sc = Scene(P("bg_night.png"), max(vd["plan"] + 1.0, 18))
    sc.add(phone, PHONE[0], PHONE[1], 0, video=(0, None, PHONE[2], PHONE[3]))
    for i, (s0, s1) in enumerate([(0.5, 3.0), (3.0, 7.0), (7.0, 10.5), (10.5, 14.4), (14.4, sc.dur)]):
        sc.add(P(f"pl{i}.png"), 1190, [220, 420, 560, 380, 600][i], s0, s1)
    hop(sc, P("turtle_front.png"), 340, "900-h", amp=6, speed=2)
    sc.add(P("s1.png"), 0, 0)
    done(sc, "plan", "plan")

    # 3. 2단계: 아침 탑승 (좌석 센서)
    sc = Scene(P("bg_am.png"), max(vd["board"] + 1.0, 8))
    door = CAR_X + 230
    for i, (cid, *_rest) in enumerate(CAST):
        walk(sc, P(f"{cid}_walk.png"), -250 - i * 160, door, f"{H - 120}-h", 0.3, 2.4 + i * 0.9)
    for i in range(5):
        sc.add(P(f"seat{i}.png"), 120, 330, 0 if i == 0 else 2.4 + (i - 1) * 0.9, 2.4 + i * 0.9 if i < 4 else None)
    sc.add(P("s2.png"), 0, 0)
    done(sc, "board", "board")

    # 4. 차 화면: 코드 입력 → 기어 D → 안내
    s = ev["code"] - 0.5
    d_on = ev["depart"] + 2.6 - s
    sc = Scene(P("dash_am.png"), max(vd["depart"] + 1.0, d_on + 9))
    sc.add(car, DISPLAY[0], DISPLAY[1], 0, video=(s, APP_CROP, DISPLAY[2], DISPLAY[3]))
    sc.add(P("c_code.png"), 0, 0, 0.8, d_on - 1.0)
    sc.add(P("c_gear.png"), 0, 0, d_on - 0.6, d_on + 3.0)
    sc.add(P("c_guide.png"), 0, 0, d_on + 1.0, sc.dur)
    sc.add(P("drive_tag.png"), "W-w-60", 180, d_on + 4.0, sc.dur)
    sc.add(P("seat4.png"), 10, 400, 0)
    sc.add(P("s2.png"), 0, 0)
    done(sc, "depart", "depart")

    # 5. 3단계: 점심 후, 콩콩이만 놀이터에
    sc = Scene(P("bg_park.png"), max(vd["lunch"] + 1.0, 8))
    door = CAR_X + 230
    for i, cid in enumerate(["pelican", "turtle", "otter"]):
        walk(sc, P(f"{cid}_walk.png"), 900 - i * 140, door, f"{H - 120}-h", 0.3, 2.0 + i * 0.6)
    hop(sc, P("crab_front.png"), 470, "830-h", amp=40, speed=6)
    sc.add(P("seat3.png"), 1560, 60, 0, 3.2)
    sc.add(P("seat_miss.png"), 1560, 60, 3.2)
    sc.add(P("s3.png"), 0, 0)
    done(sc, "lunch", "lunch")

    # 6. 차 화면: 다시 D → 경고
    s = ev["reboard"] - 1.0
    d_on = ev["reboard"] + 2.6 - s
    sc = Scene(P("dash_pm.png"), max(vd["reboard"] + 1.0, d_on + 10))
    sc.add(car, DISPLAY[0], DISPLAY[1], 0, video=(s, APP_CROP, DISPLAY[2], DISPLAY[3]))
    sc.add(P("c_seat.png"), 0, 0, d_on + 0.6, d_on + 4.5)
    sc.add(P("c_warn.png"), 0, 0, d_on + 4.5, sc.dur)
    sc.add(P("seat_miss.png"), 10, 400, 0)
    sc.add(P("s3.png"), 0, 0)
    done(sc, "reboard", "reboard")

    # 7. 콩콩이 탑승 → 4/4
    sc = Scene(P("bg_park.png"), max(vd["fixed"] + 1.0, 5))
    walk(sc, P("crab_front.png"), 470, CAR_X + 230, "830-h", 0.3, 2.6)
    sc.add(P("seat_miss.png"), 1560, 60, 0, 2.6)
    sc.add(P("seat_ok.png"), 1560, 60, 2.6)
    sc.add(P("s3.png"), 0, 0)
    done(sc, "fixed", "fixed")

    # 8. 4단계: 귀갓길 (달리는 차 + 차 화면)
    s = ev["home"] - 1.0
    d_on = ev["home"] + 2.6 - s
    dur = max(vd["home"] + 1.0, d_on + 8)
    sc = Scene(P("dash_eve.png"), dur)
    sc.add(car, DISPLAY[0], DISPLAY[1], 0, video=(s, APP_CROP, DISPLAY[2], DISPLAY[3]))
    sc.add(P("seat_ok.png"), 10, 400, 0)
    sc.add(P("s4.png"), 0, 0)
    done(sc, "home", "home")

    # 9. 마무리: 달리는 차 + 요약
    sc = Scene(P("bg_cast.png"), max(vd["end"] + 1.5, 8))
    sc.inputs[sc.inputs.index(str(P("bg_cast.png")))] = str(P("road_eve.png"))
    sc.chain[0] = f"[0:v]fps=30,crop={W}:{H}:'mod(t*500,{W})':0,format=rgba[b0]"
    sc.add(P("car.png"), 560, "560-abs(sin(t*10))*5")
    for i, txt in enumerate(["1  전날 밤, 휴대폰으로 계획", "2  타고 기어 D, 자동 안내", "3  다시 탈 때마다 좌석, 시간, 연료 확인"]):
        label_png(P(f"sum{i}.png"), txt, 46, jua=jua)
        sc.add(P(f"sum{i}.png"), 120, 90 + i * 110, 0.4 + i * 0.6)
    label_png(P("logo.png"), "차비스", 120, bg=(255, 255, 255, 235), fg=ACCENT, jua=jua)
    sc.add(P("logo.png"), "W-w-120", 120, 2.4)
    note = ("앱 화면: 실제 APK를 Pleos Connect Emulator 2.1.0에서 녹화 · 기어·좌석은 Vehicle SDK 신호, 연료·시각은 데모 값 · "
            "캐릭터: 조개섬 친구들(자체 제작)")
    label_png(P("note.png"), note, 22, bg=(20, 24, 31, 200), fg=(220, 224, 230), pad=(20, 8))
    sc.add(P("note.png"), "(W-w)/2", 1030)
    done(sc, "end", "end")

    listing = work / "list.txt"
    listing.write_text("".join(f"file '{q}'\n" for q in parts))
    ff(["-f", "concat", "-safe", "0", "-i", str(listing), "-c", "copy", out])


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3])
