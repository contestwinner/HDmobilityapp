#!/usr/bin/env python3
"""차비스 홍보 영상: 한 가족의 하루(장면 일러스트 + 내레이션) 사이에 실제 앱 녹화 화면을 넣는다.

- 앱 화면: record_demo.py가 녹화한 car.mp4·phone.mp4에서 차비스 앱 영역만 잘라 쓴다(손대지 않은 녹화본).
- 장면 일러스트·대시보드 틀: 이 스크립트가 HTML/SVG로 그려 headless Chrome으로 PNG를 만든다.
- 음성: macOS `say -v Yuna`. 내레이션과, 앱 안내 문장(Guidance.speech 앞부분)을 차 스피커 느낌으로 넣는다.
  (에뮬레이터 이미지에 TTS 엔진이 없어 차량 음성은 이렇게 재현한다)

사용: python make_promo.py <record_demo 폴더> <out.mp4>
"""
import json
import subprocess
import sys
from pathlib import Path

CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
W, H = 1920, 1080
APP_CROP = "1252:846:642:93"           # Pleos 화면에서 차비스 앱 창 영역 (w:h:x:y)
DISPLAY = (410, 290, 1100, 743)         # 대시보드 틀 안 디스플레이 위치 (x, y, w, h)
PHONE = (300, 70, 425, 944)             # 휴대폰 틀 안 화면 위치

FONT = "'Apple SD Gothic Neo', 'AppleSDGothicNeo', sans-serif"
BASE_CSS = f"""*{{margin:0;padding:0;box-sizing:border-box}}
body{{width:{W}px;height:{H}px;overflow:hidden;font-family:{FONT};}}
.t{{position:absolute;color:#fff;font-weight:800;letter-spacing:-1px}}
.s{{position:absolute;color:rgba(255,255,255,.85);font-weight:500}}"""

# --- SVG 조각 ---------------------------------------------------------------

def car_side(x, y, s=1.0, color="#4FC3A1", door_open=False):
    door = (f'<path d="M150 -10 L150 70 L95 95 L95 0 Z" fill="{color}" opacity=".85"/>' if door_open else "")
    return f"""<g transform="translate({x},{y}) scale({s})">
<path d="M0 60 Q0 30 40 25 L95 -20 Q110 -32 140 -32 L250 -32 Q275 -32 295 -12 L335 25 Q380 30 385 60 L385 85 Q385 95 375 95 L10 95 Q0 95 0 85 Z" fill="{color}"/>
<path d="M110 -10 L140 -22 L190 -22 L190 22 L85 22 Z" fill="#CFE9F5"/>
<path d="M200 -22 L250 -22 Q265 -22 280 -8 L310 22 L200 22 Z" fill="#CFE9F5"/>
{door}
<circle cx="80" cy="95" r="34" fill="#22262E"/><circle cx="80" cy="95" r="14" fill="#9AA4B2"/>
<circle cx="305" cy="95" r="34" fill="#22262E"/><circle cx="305" cy="95" r="14" fill="#9AA4B2"/>
<rect x="360" y="45" width="22" height="12" rx="4" fill="#FFE08A"/></g>"""


def person(x, y, s=1.0, shirt="#F28B66", hair="#2B2B2B"):
    return f"""<g transform="translate({x},{y}) scale({s})">
<circle cx="0" cy="0" r="22" fill="#F6C9A8"/><path d="M-22 -4 Q-20 -28 0 -28 Q22 -28 22 -4 Q10 -16 -22 -4 Z" fill="{hair}"/>
<rect x="-20" y="24" width="40" height="60" rx="16" fill="{shirt}"/>
<rect x="-16" y="80" width="12" height="44" rx="6" fill="#3A4150"/><rect x="4" y="80" width="12" height="44" rx="6" fill="#3A4150"/></g>"""


def apartments(color="#2E3A4F", windows="#FFD27A", lit=0.5):
    out = []
    for i, (x, w, h) in enumerate([(40, 220, 520), (300, 180, 420), (1500, 200, 480), (1720, 180, 560)]):
        out.append(f'<rect x="{x}" y="{H - 260 - h}" width="{w}" height="{h}" fill="{color}"/>')
        for r in range(h // 70):
            for c in range(w // 60):
                if (r * 7 + c * 3 + i) % 10 < lit * 10:
                    out.append(f'<rect x="{x + 18 + c * 60}" y="{H - 240 - h + 20 + r * 70}" width="28" height="30" rx="3" fill="{windows}"/>')
    return "".join(out)


def page(body, bg):
    return f"<html><head><meta charset='utf-8'><style>{BASE_CSS}</style></head><body style='background:{bg}'>{body}</body></html>"


def svg(inner):
    return f'<svg width="{W}" height="{H}" style="position:absolute;left:0;top:0">{inner}</svg>'


# --- 장면 -------------------------------------------------------------------

def scene_hook():
    art = svg(f"""<defs><linearGradient id="sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFB88C"/><stop offset="1" stop-color="#FFE3C2"/></linearGradient></defs>
<rect width="{W}" height="{H}" fill="url(#sky)"/><circle cx="1500" cy="300" r="110" fill="#FFF3D6"/>
{apartments("#E39A7B", "#FFF3D6", 0.25)}<rect y="{H - 260}" width="{W}" height="260" fill="#7A8796"/>
<rect y="{H - 150}" width="{W}" height="8" fill="#E6E9EE" opacity=".6"/>
{car_side(760, H - 330, 1.25)}{person(700, H - 380, 1.1, "#5B8DEF")}{person(1300, H - 380, 1.1, "#F28B66", "#4A2E20")}
{person(1240, H - 300, .7, "#FFD15C")}""")
    txt = "<div class='t' style='left:120px;top:120px;font-size:84px;color:#3B2A24'>토요일 아침,<br>아이 둘과 떠나는 나들이</div>"
    return page(art + txt, "#FFE3C2")


def scene_worry():
    bubbles = [("다 탔나?", 260, 300, "#FF8A65"), ("기저귀 갈 때 됐나?", 760, 480, "#FFD15C"), ("기름은 충분한가?", 1200, 300, "#4FC3A1")]
    b = "".join(f"<div style='position:absolute;left:{x}px;top:{y}px;background:{c};color:#14181F;font-size:58px;font-weight:800;padding:34px 48px;border-radius:60px'>{t}</div>" for t, x, y, c in bubbles)
    sub = "<div class='s' style='left:0;width:100%;text-align:center;top:800px;font-size:44px'>운전하면서 챙길 게 너무 많아요</div>"
    return page(b + sub, "#14181F")


def scene_night():
    art = svg(f"""<rect width="{W}" height="{H}" fill="#1C2340"/>
<rect x="1420" y="60" width="400" height="240" rx="12" fill="#2B3561"/><circle cx="1720" cy="150" r="50" fill="#F5E6B8"/>
<circle cx="1500" cy="120" r="4" fill="#fff"/><circle cx="1580" cy="240" r="3" fill="#fff"/>
<rect x="1060" y="700" width="760" height="200" rx="40" fill="#5E4B8B"/><rect x="1060" y="640" width="760" height="110" rx="40" fill="#6D59A0"/>
<rect x="{PHONE[0] - 18}" y="{PHONE[1] - 18}" width="{PHONE[2] + 36}" height="{PHONE[3] + 36}" rx="48" fill="#0B0D12"/>""")
    txt = ("<div class='t' style='left:900px;top:180px;font-size:72px'>전날 밤,<br>휴대폰으로 3분</div>"
           "<div class='s' style='left:900px;top:420px;font-size:40px;line-height:1.7'>가족 · 목적지 · 출발 시각만 고르면<br>붐비는 시간을 피한 하루 코스<br>짧은 코드 하나로 차에 보내요</div>")
    return page(art + txt, "#1C2340")


def scene_dash(sky_top, sky_bottom, title, sub):
    x, y, w, h = DISPLAY
    art = svg(f"""<defs><linearGradient id="sky" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{sky_top}"/><stop offset="1" stop-color="{sky_bottom}"/></linearGradient></defs>
<rect width="{W}" height="{H}" fill="url(#sky)"/>
<path d="M760 420 L1160 420 L1900 {H} L20 {H} Z" fill="#5B6472" opacity=".55"/>
<path d="M0 250 Q960 200 1920 250 L1920 {H} L0 {H} Z" fill="#181B21"/>
<rect x="{x - 26}" y="{y - 26}" width="{w + 52}" height="{h + 52}" rx="34" fill="#0A0B0E"/>
<circle cx="150" cy="{H + 40}" r="300" fill="none" stroke="#2A2E36" stroke-width="56"/>
<rect x="1620" y="560" width="230" height="40" rx="14" fill="#262A32"/><rect x="1620" y="630" width="230" height="40" rx="14" fill="#262A32"/>""")
    txt = (f"<div class='t' style='left:{x}px;top:36px;font-size:52px;text-shadow:0 2px 10px rgba(0,0,0,.5)'>{title}</div>"
           f"<div class='s' style='left:{x}px;top:112px;font-size:32px;text-shadow:0 2px 8px rgba(0,0,0,.5)'>{sub}</div>")
    return page(art + txt, sky_bottom)


def scene_lunch():
    art = svg(f"""<rect width="{W}" height="{H}" fill="#BFE6F5"/><circle cx="1760" cy="150" r="90" fill="#FFF0B3"/>
<rect y="{H - 330}" width="{W}" height="330" fill="#9CCB7A"/><rect y="{H - 180}" width="{W}" height="180" fill="#8C96A3"/>
<rect x="1500" y="{H - 620}" width="40" height="300" fill="#7A5A3A"/><circle cx="1520" cy="{H - 640}" r="150" fill="#5FA35A"/>
<path d="M1150 {H - 330} L1250 {H - 520} L1290 {H - 520} L1360 {H - 330}" fill="none" stroke="#FF8A65" stroke-width="22"/>
<rect x="1240" y="{H - 540}" width="60" height="24" fill="#FF8A65"/>
{car_side(260, H - 260, 1.35, "#4FC3A1", True)}{person(1330, H - 470, .72, "#FFD15C")}""")
    txt = "<div class='t' style='left:120px;top:100px;font-size:70px;color:#1F2A36'>점심 먹고 1시 15분,<br>다시 출발하려는데…</div>"
    return page(art + txt, "#BFE6F5")


def scene_end():
    body = ("<div class='t' style='left:0;width:100%;text-align:center;top:300px;font-size:150px;color:#4FC3A1'>차비스</div>"
            "<div class='t' style='left:0;width:100%;text-align:center;top:500px;font-size:56px'>다시 탈 때마다 챙겨 주는 차 속 비서</div>"
            "<div class='s' style='left:0;width:100%;text-align:center;top:610px;font-size:34px'>Pleos Connect · 서울에서 먼저, 전국으로</div>"
            "<div class='s' style='left:0;width:100%;text-align:center;top:930px;font-size:24px;color:rgba(255,255,255,.55)'>"
            "앱 화면: 실제 APK를 Pleos Connect Emulator 2.1.0에서 녹화 · 기어·좌석은 Vehicle SDK 신호, 연료·시각은 데모 값 · "
            "차량 음성은 앱 안내 문장을 합성 음성으로 재현 · 장면 그림은 연출</div>")
    return page(body, "#14181F")


# --- 렌더·합성 -------------------------------------------------------------

def render(html, png):
    src = png.with_suffix(".html")
    src.write_text(html, encoding="utf-8")
    subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", f"--window-size={W},{H}",
                    f"--screenshot={png}", src.as_uri()], check=True, capture_output=True)


def ff(args):
    subprocess.run(["ffmpeg", "-v", "error", "-y", *args], check=True)


def say(text, out, rate=185):
    aiff = out.with_suffix(".aiff")
    subprocess.run(["say", "-v", "Yuna", "-r", str(rate), "-o", str(aiff), text], check=True)
    ff(["-i", str(aiff), "-ar", "48000", "-ac", "2", str(out)])


def audio_mix(dur, items, first=1):
    """items: (wav, start초, car스피커여부). 반환: ffmpeg 입력 인자와 필터."""
    inputs, filt, labels = [], [], []
    for i, (wav, start, car) in enumerate(items):
        inputs += ["-i", str(wav)]
        ms = int(start * 1000)
        fx = "highpass=f=250,lowpass=f=5000,aecho=0.8:0.5:20:0.15," if car else ""
        filt.append(f"[{i + first}:a]{fx}aformat=sample_rates=48000:channel_layouts=stereo,adelay={ms}|{ms}[a{i}]")
        labels.append(f"[a{i}]")
    filt.append(f"{''.join(labels)}amix=inputs={len(items)}:duration=longest:normalize=0,apad,atrim=0:{dur}[aout]")
    return inputs, ";".join(filt)


def still_clip(png, dur, out, voices):
    vin = ["-loop", "1", "-framerate", "30", "-t", str(dur), "-i", str(png)]
    zoom = f"scale=2112:1188,zoompan=z='1+0.04*on/({dur}*30)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=1:s={W}x{H}:fps=30"
    a_in, a_f = audio_mix(dur, voices)
    ff(vin + a_in + ["-filter_complex", f"[0:v]{zoom},format=yuv420p[v];{a_f}", "-map", "[v]", "-map", "[aout]",
                     "-t", str(dur), "-c:v", "libx264", "-c:a", "aac", str(out)])


def video_clip(bg, src, ss, dur, box, crop, out, voices, speed=1.0):
    x, y, w, h = box
    pre = f"crop={crop}," if crop else ""
    v = (f"[1:v]{pre}setpts=(PTS-STARTPTS)/{speed},scale={w}:{h},fps=30,tpad=stop_mode=clone:stop_duration={dur}[app];"
         f"[0:v][app]overlay={x}:{y},format=yuv420p[v]")
    a_in, a_f = audio_mix(dur, voices, first=2)
    ff(["-loop", "1", "-framerate", "30", "-t", str(dur), "-i", str(bg), "-ss", str(ss), "-i", str(src)] + a_in +
       ["-filter_complex", f"{v};{a_f}", "-map", "[v]", "-map", "[aout]",
        "-t", str(dur), "-c:v", "libx264", "-c:a", "aac", str(out)])


def main(rec_dir, out):
    rec = Path(rec_dir)
    work = rec / "promo_work"
    work.mkdir(exist_ok=True)
    ev = json.loads((rec / "events.json").read_text())
    car, phone = rec / "car.mp4", rec / "phone.mp4"

    scenes = {
        "hook": scene_hook(), "worry": scene_worry(), "night": scene_night(), "lunch": scene_lunch(), "end": scene_end(),
        "dash_am": scene_dash("#8EC9F0", "#DDF0FA", "아침 9:00 · 출발", "휴대폰 코드를 넣고 기어를 D로"),
        "dash_pm": scene_dash("#7FB8E6", "#CFE6F5", "오후 1:15 · 다시 출발", "기어 D 순간, 좌석·시간·연료를 함께 확인"),
        "dash_eve": scene_dash("#F08A5D", "#FFD3A5", "오후 4:40 · 집으로", "모두 탑승, 귀갓길 안내"),
    }
    for k, html in scenes.items():
        render(html, work / f"{k}.png")

    n = {
        "hook": "주말 아침, 아이 둘과 떠나는 나들이.",
        "worry": "다 탔는지, 기저귀 갈 때는 됐는지, 기름은 충분한지. 운전하면서 챙길 게 너무 많죠.",
        "night": "전날 밤, 차비스 앱에서 가족과 목적지, 출발 시각만 고르세요. 붐비는 시간을 피한 하루 코스를 만들고, 짧은 코드 하나로 차에 보냅니다.",
        "am": "아침, 차에 코드를 넣고 기어를 D로 바꾸면,",
        "lunch": "점심을 먹고 다시 출발하려는 순간. 아이 한 명이 아직 타지 않았어요.",
        "eve": "해질녘, 집으로 가는 길까지.",
        "end": "아이와 떠나는 길. 다시 탈 때마다, 차비스가 챙길게요.",
        # 앱이 기어 D 순간 읽는 안내 문장(Guidance.speech)의 앞부분
        "c1": "오늘은 국립중앙박물관 나들이예요. 다음은 어린이박물관, 약 35분, 주차는 박물관 지하주차장. 기저귀 가방과 물티슈 챙기셨나요?",
        "c3": "다시 출발해요. 집을 나선 지 4시간 15분 지났어요. 좌석이 4석 중 3석만 감지됐어요. 모두 탔나요? 출발 4시간 15분, 기저귀 갈 시간이 됐을 수 있어요. 연료 12퍼센트, 약 72킬로미터. 남은 코스 18킬로미터라 가는 길에 주유를 권해요.",
        "c4": "다시 출발해요. 집을 나선 지 7시간 40분 지났어요. 출발 7시간 40분, 기저귀 갈 시간이 됐을 수 있어요. 이제 집으로 가요. 약 35분 걸려요.",
    }
    wav = {}
    for k, t in n.items():
        wav[k] = work / f"v_{k}.wav"
        say(t, wav[k], 190 if k.startswith("c") else 185)
    chime = work / "chime.wav"
    ff(["-f", "lavfi", "-i", "sine=frequency=880:duration=0.25,afade=t=out:st=0.1:d=0.15,volume=0.4", "-ar", "48000", "-ac", "2", str(chime)])

    parts = []
    p = work / "00_hook.mp4"; still_clip(work / "hook.png", 5, p, [(wav["hook"], 0.6, False)]); parts.append(p)
    p = work / "01_worry.mp4"; still_clip(work / "worry.png", 7.5, p, [(wav["worry"], 0.3, False)]); parts.append(p)
    p = work / "02_night.mp4"
    video_clip(work / "night.png", phone, 0, 17, PHONE, None, p, [(wav["night"], 0.5, False)]); parts.append(p)
    # 아침: 코드 입력 → D → 첫 탑승 안내
    s = ev["code"] - 0.5; d_voice = ev["depart"] + 3.3 - s
    p = work / "03_am.mp4"
    video_clip(work / "dash_am.png", car, s, round(d_voice + 11, 2), DISPLAY, APP_CROP, p,
               [(wav["am"], 0.3, False), (chime, d_voice - 0.4, True), (wav["c1"], d_voice, True)]); parts.append(p)
    p = work / "04_lunch.mp4"; still_clip(work / "lunch.png", 6.5, p, [(wav["lunch"], 0.3, False)]); parts.append(p)
    s = ev["reboard"] - 1.5; d_voice = ev["reboard"] + 3.3 - s
    p = work / "05_pm.mp4"
    video_clip(work / "dash_pm.png", car, s, round(d_voice + 20.5, 2), DISPLAY, APP_CROP, p,
               [(chime, d_voice - 0.4, True), (wav["c3"], d_voice, True)]); parts.append(p)
    s = ev["home"] - 2.0; d_voice = ev["home"] + 3.3 - s
    p = work / "06_eve.mp4"
    video_clip(work / "dash_eve.png", car, s, round(d_voice + 11, 2), DISPLAY, APP_CROP, p,
               [(wav["eve"], 0.2, False), (chime, d_voice - 0.4, True), (wav["c4"], d_voice, True)]); parts.append(p)
    p = work / "07_end.mp4"; still_clip(work / "end.png", 7, p, [(wav["end"], 0.5, False)]); parts.append(p)

    listing = work / "list.txt"
    listing.write_text("".join(f"file '{q}'\n" for q in parts))
    ff(["-f", "concat", "-safe", "0", "-i", str(listing), "-c", "copy", out])


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
