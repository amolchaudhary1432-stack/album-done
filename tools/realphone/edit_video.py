"""The owner's recording of a showcase run: Test Lab's screen video, with a title, a caption for each
step (from showcase-marks.txt, written by JourneyDeviceTest), and only the checking part sped up.
Everything else plays at normal speed.
    python3 edit_video.py VIDEO.mp4 MARKS.txt "TITLE" OUT.mp4
Marks are "seconds since the test started<TAB>caption"; the last is "end". The video starts a little
before the test, so its clock is matched to the test's by the end: the recording stops when the test ends."""
import os, subprocess, sys, tempfile

video, marks_path, title, out = sys.argv[1:5]
FONT = next((f for f in ('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', '/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf') if os.path.exists(f)), None)
CHECKING = 'Checking every photo'
SPED_TO = 75.0  # seconds the checking part lasts in the video

dur = float(subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', video],
                           capture_output=True, text=True, check=True).stdout.strip())
marks = []
for line in open(marks_path, encoding='utf-8'):
    t, _, cap = line.rstrip('\n').partition('\t')
    if t: marks.append((float(t), cap))
end_t = next((t for t, c in marks if c == 'end'), marks[-1][0])
# The recording runs about 6 s past the test's last caption (Test Lab's own screen): measured on run 39.
shift = max(0.0, dur - end_t - 6.0)  # video seconds before the test's first second
marks = [(min(dur, t + shift), c) for t, c in marks]
# Sped up: the photos being copied onto the phone before the app opens (a still screen), and checking.
first = marks[0][0]
scan = next((i for i, (_, c) in enumerate(marks) if c.startswith(CHECKING)), None)
fast = [(0.0, first, 4.0)]
if scan is not None and scan + 1 < len(marks): fast.append((marks[scan][0], marks[scan + 1][0], SPED_TO))
segs, t0 = [], 0.0  # (start, end, factor)
for a, b, target in fast:
    if a > t0: segs.append((t0, a, 1.0))
    if b > a: segs.append((a, b, max(1.0, (b - a) / target)))
    t0 = max(t0, b)
stop = min(dur, next((t for t, c in marks if c == 'end'), dur) + 2.0)  # not Test Lab's screen after the test
segs.append((t0, stop, 1.0))
print(f'video {dur:.0f} s, test clock shifted {shift:.1f} s; ' + ', '.join(f'{a:.0f}-{b:.0f} s x{f:.0f}' for a, b, f in segs if f > 1))

def new_t(t):
    out = 0.0
    for a, b, f in segs:
        if t <= a: break
        out += (min(t, b) - a) / f
    return out

def wrap(s, n=30):
    lines, cur = [], ''
    for w in s.split():
        if cur and len(cur) + 1 + len(w) > n: lines.append(cur); cur = w
        else: cur = (cur + ' ' + w).strip()
    return '\n'.join(lines + [cur])

tmp = tempfile.mkdtemp()
draws = []
def text(s, i):
    p = os.path.join(tmp, f't{i}.txt'); open(p, 'w', encoding='utf-8').write(s); return p
if FONT:
    caps = [(new_t(t), c) for t, c in marks if c != 'end']
    caps.insert(0, (0.0, 'Putting the photos on the phone'))
    for i, (t, c) in enumerate(caps):
        t2 = caps[i + 1][0] if i + 1 < len(caps) else new_t(dur)
        sped = next((f for a, b, f in segs if abs(new_t(a) - t) < 0.5 and f > 1), None)
        if sped: c += f' (sped up {sped:.0f}x)'
        draws.append(f"drawtext=fontfile={FONT}:textfile={text(wrap(c), i)}:fontsize=h/48:line_spacing=8:fontcolor=white:box=1:"
                     f"boxcolor=black@0.7:boxborderw=16:x=(w-text_w)/2:y=h-text_h-h/12:enable='between(t,{t:.2f},{t2:.2f})'")
    draws.append(f"drawtext=fontfile={FONT}:textfile={text(wrap(title, 26), 'title')}:fontsize=h/40:line_spacing=10:fontcolor=white:"
                 f"box=1:boxcolor=0x1d1d1f@0.88:boxborderw=26:x=(w-text_w)/2:y=h/3:enable='lt(t,7)'")
# One pass, little memory: each frame's new time from a piecewise remap of its old time, then 30 fps
# (frames that land on the same moment of the sped-up parts are dropped).
expr, base = None, []
acc = 0.0
for a, b, f in segs: base.append(acc); acc += (b - a) / f
expr = f"{base[-1]:.3f}+(T-{segs[-1][0]:.3f})/{segs[-1][2]:.4f}"
for (a, b, f), b0 in reversed(list(zip(segs[:-1], base[:-1]))):
    expr = f"if(lt(T,{b:.3f}),{b0:.3f}+(T-{a:.3f})/{f:.4f},{expr})"
vf = f"trim=0:{stop:.2f},setpts='({expr})/TB',fps=30" + ''.join(',' + d for d in draws)
subprocess.run(['ffmpeg', '-y', '-v', 'error', '-i', video, '-vf', vf, '-an', '-threads', '2',
                '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '26', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', out], check=True)
print('written', out, f'{os.path.getsize(out) / 1e6:.0f} MB')
