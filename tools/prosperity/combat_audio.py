"""Frozen v71 combat sound recipe: CC0 creature recordings + deterministic DSP.

Run twice: python tools/prosperity/combat_audio.py
Source hashes are pinned by sources.json; missing/changed inputs fail closed.
All production OGG pages use serial 0x474F544F and recomputed Ogg CRC.
Subjective audition and Forge/OpenAL playback are NOT_PERFORMED.
"""
from pathlib import Path
import hashlib, io, json, re, struct
import numpy as np
import soundfile as sf
from scipy import signal

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'plan/model/authored/audio/combat-v71'
OUT = ROOT / 'src/main/resources/assets/gtsr/sounds/combat'
EVIDENCE = ROOT / 'temp/combat-v71/audio'
SR = 32000

def sha(data):
    return hashlib.sha256(data).hexdigest()

def canonical(raw):
    data = bytearray(raw)
    offset = 0
    while offset < len(data):
        assert data[offset:offset + 4] == b'OggS'
        segments = data[offset + 26]
        size = 27 + segments + sum(data[offset + 27:offset + 27 + segments])
        struct.pack_into('<I', data, offset + 14, 0x474F544F)
        data[offset + 22:offset + 26] = b'\0' * 4
        crc = 0
        for value in data[offset:offset + size]:
            crc ^= value << 24
            for _ in range(8):
                crc = ((crc << 1) ^ (0x04C11DB7 if crc & 0x80000000 else 0)) & 0xffffffff
        struct.pack_into('<I', data, offset + 22, crc)
        offset += size
    assert offset == len(data)
    return bytes(data)

def encode(x, subtype, format):
    memory = io.BytesIO()
    sf.write(memory, x, SR, subtype=subtype, format=format)
    return memory.getvalue()

def filtered(x, lo, hi):
    return signal.sosfilt(signal.butter(3, [lo, hi], btype='bandpass', fs=SR, output='sos'), x)

def normalize(x):
    x = np.nan_to_num(x - np.mean(x))
    ramp = min(640, len(x) // 4)
    x[:ramp] *= np.sin(np.linspace(0, np.pi / 2, ramp)) ** 2
    x[-ramp:] *= np.cos(np.linspace(0, np.pi / 2, ramp)) ** 2
    x *= 10 ** (-7 / 20) / max(np.max(np.abs(x)), 1e-9)
    x[0] = x[-1] = 0
    # Quantize before encoding, making WAV and OGG use the exact same PCM source.
    return (np.round(x * 32767).astype(np.int16).astype(np.float64) / 32768)

def layer(out, source, at=0, gain=1):
    start = int(at * SR)
    if start >= len(out):
        return
    count = min(len(source), len(out) - start)
    out[start:start + count] += gain * source[:count]

def voice(source, ratio, duration, rng):
    """Pitch-shift/respeed CC0 recording and assemble nonverbal syllabic grains."""
    shifted = signal.resample_poly(source, 100, max(25, int(ratio * 100)))
    shifted = shifted / max(np.max(np.abs(shifted)), 1e-9)
    n = int(duration * SR)
    out = np.zeros(n)
    grain = int(.32 * SR)
    for at in np.arange(0, duration - .15, .26):
        start = int(rng.integers(0, max(1, len(shifted) - grain)))
        chunk = shifted[start:start + grain].copy()
        chunk *= np.hanning(len(chunk))
        layer(out, chunk, at, float(rng.uniform(.55, 1)))
    return out

def recipe(key, sources):
    seed = int(sha(key.encode())[:8], 16)
    rng = np.random.default_rng(seed)
    event = key.rsplit('.', 1)[-1]
    king = key.startswith('king.')
    machine = key.startswith('colossus.') or key.startswith('spawner.')
    hive = key.startswith('hive.')
    death = event == 'death'
    duration = 4.2 if king and event in ('chant', 'phase', 'death') else 2.8 if event in ('phase', 'meteor', 'summon', 'recharge', 'unlock') else 2.4 if death else 1.6 if event == 'idle' else 1.2
    n = int(SR * duration)
    t = np.arange(n) / SR
    code = key.split('.')[1] if key.startswith('entity.') else ''
    idx = int(sha(code.encode())[:4], 16)
    names = ['monster_01.ogg', 'alien_02.ogg', 'bug_02.ogg', 'weird_02.ogg', 'roar_01.ogg', 'grunt_03.ogg']
    name = 'monster_01.ogg' if king else 'roar_01.ogg' if machine else 'weird_02.ogg' if hive else names[idx % len(names)]
    if event == 'hurt':
        name = 'hurt_02.ogg'
    if death and not king:
        name = 'scream_01.ogg'
    pitch = .42 if king else .56 if machine else .82 if hive else .72 + (idx % 43) / 50
    x = voice(sources[name], pitch, duration, rng)
    # Nonverbal voices retain breath/noise, with spectral styling by family.
    x = filtered(x, 45 if king else 90, 1100 if king else 4200)
    if machine:
        x = np.tanh(4 * x) * (.62 + .38 * signal.square(2 * np.pi * 31 * t, .35))
        x += .24 * x * np.sin(2 * np.pi * 173 * t)
    if hive:
        x += .45 * x[::-1] * (.6 + .4 * np.sin(2 * np.pi * 7 * t))
    noise = filtered(rng.normal(0, 1, n), 75, 6500)
    if event in ('attack', 'slam', 'stomp', 'crush', 'impact', 'explode', 'domain'):
        # Broad-band stone crack, low resonant body, granular debris and aftershock.
        x *= .28
        x += noise * np.exp(-t * 6) * .75
        body = signal.sawtooth(2 * np.pi * (64 * t - 14 * t * t))
        x += filtered(body, 38, 280) * np.exp(-t * 5)
        for at in (.17, .31, .49):
            layer(x, noise[:int(.19 * SR)] * np.exp(-np.arange(int(.19 * SR)) / SR * 18), at, .2)
    elif event in ('pull', 'charge', 'launch', 'meteor', 'summon', 'spawn', 'unlock', 'recharge'):
        # Reverse suction with detuned resonances, not a single notification tone.
        x += noise * np.sin(np.pi * t / duration) ** 2 * .17
        for f in (137, 229, 367, 613):
            x += .05 * np.sin(2 * np.pi * (f * t + 48 * t * t)) * np.sin(np.pi * t / duration) ** 2
        if event in ('unlock', 'recharge'):
            for at, freq in ((.12, 439), (.37, 661), (.64, 997)):
                tone_t = np.arange(int(.7 * SR)) / SR
                metallic = sum(np.sin(2 * np.pi * freq * r * tone_t) / (i + 1) for i, r in enumerate((1, 1.481, 2.071, 2.963)))
                layer(x, metallic * np.exp(-tone_t * 7), at, .13)
    elif event in ('silence', 'warning', 'phase'):
        x += .11 * noise * np.exp(-t * .7)
        x *= .6 + .4 * np.sin(2 * np.pi * 2.3 * t) ** 2
    if death:
        x *= np.exp(-t * .9)
        x += .16 * noise * np.sin(np.pi * t / duration) ** 2 * np.exp(-t)
    # A finite, nonlooping diffused tail; damped delays imply space without long files.
    dry = x.copy()
    for delay, gain in ((.079, .24), (.151, .18), (.233, .12), (.347, .08)):
        layer(x, filtered(dry, 80, 1700 if king else 3500), delay, gain)
    return normalize(x), name, seed

def keys():
    result = []
    families = {
        'king': 'idle hurt death attack chant warning phase crush pull silence domain meteor impact',
        'colossus': 'idle hurt death attack phase slam charge stomp',
        'hive': 'idle hurt death attack phase summon launch impact',
        'spawner': 'spawn unlock recharge',
        'oathguard': 'idle hurt death attack',
        'creature': 'idle hurt death attack',
    }
    for family, events in families.items():
        result.extend(family + '.' + event for event in events.split())
    enum = (ROOT / 'src/main/java/com/miaokatze/gtsr/common/dimension/prosperity/echo/EchoKind.java').read_text(encoding='utf-8')
    for code in re.findall(r'\("((?:dr|di|do)-\d+)"', enum):
        if code != 'do-01':
            result.extend('entity.' + code + '.' + event for event in ('idle', 'hurt', 'death', 'attack'))
    result.append('echo.projectile.explode')
    return sorted(set(result))

def run():
    manifest = json.loads((SOURCE / 'sources.json').read_text(encoding='utf-8'))
    sources = {}
    for row in manifest['sources']:
        path = SOURCE / 'original' / row['file']
        assert sha(path.read_bytes()) == row['sha256'], f'Source changed: {path}'
        x, rate = sf.read(path, dtype='float64', always_2d=True)
        sources[row['file']] = signal.resample_poly(np.mean(x, axis=1), SR, rate)
    OUT.mkdir(parents=True, exist_ok=True)
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    wavdir = SOURCE / 'processed'
    wavdir.mkdir(exist_ok=True)
    report = []
    fingerprints = {}
    registration = json.loads((ROOT / 'src/main/resources/assets/gtsr/sounds.json').read_text(encoding='utf-8'))
    for key in keys():
        x, source, seed = recipe(key, sources)
        wav = encode(x, 'PCM_16', 'WAV')
        ogg = canonical(encode(x, 'VORBIS', 'OGG'))
        assert ogg == canonical(encode(recipe(key, sources)[0], 'VORBIS', 'OGG')), key
        decoded, rate = sf.read(io.BytesIO(ogg))
        assert rate == SR and decoded.ndim == 1 and len(decoded) == len(x)
        assert np.isfinite(decoded).all() and np.max(np.abs(decoded)) < 10 ** (-1 / 20)
        info = sf.info(io.BytesIO(wav))
        assert info.samplerate == SR and info.channels == 1 and info.subtype == 'PCM_16'
        assert x[0] == x[-1] == 0 and np.max(np.abs(x)) > .05
        # Endpoint codec ringing is reported separately; PCM endpoints are exact zero.
        peak_rates = [float(np.max(np.abs(signal.resample_poly(decoded, p, q)))) for p, q in ((2,1),(1,1),(1,2))]
        assert max(peak_rates) < 10 ** (-1 / 20)
        filename = key.replace('.', '_')
        (OUT / (filename + '.ogg')).write_bytes(ogg)
        (wavdir / (filename + '.wav')).write_bytes(wav)
        fingerprints[key] = {'wav': sha(wav), 'ogg': sha(ogg)}
        registration[key] = {'category': 'hostile', 'sounds': [{'name': 'gtsr:combat/' + filename, 'stream': False}]}
        report.append({'key': key, 'source': source, 'seed': seed, 'seconds': len(x)/SR,
            'wavPeakDb': float(20*np.log10(np.max(np.abs(x)))),
            'decodedPeakDb': float(20*np.log10(np.max(np.abs(decoded)))),
            'resampledPeakDb': [float(20*np.log10(p)) for p in peak_rates],
            'decodedEndpoints': [float(decoded[0]), float(decoded[-1])],
            'tail20msRms': float(np.sqrt(np.mean(decoded[-640:]**2)))})
    sidecar = EVIDENCE / 'fingerprints.json'
    if sidecar.exists():
        assert json.loads(sidecar.read_text()) == fingerprints, 'Cross-execution determinism failed'
    sidecar.write_text(json.dumps(fingerprints, indent=2) + '\n')
    (ROOT / 'src/main/resources/assets/gtsr/sounds.json').write_text(json.dumps(registration, indent=2) + '\n')
    result = {'assets': len(report), 'S0_source_hash': 'PASS', 'S3a_memory_encode': 'PASS',
        'S3b_cross_execution': 'PASS' if (EVIDENCE / 'report.json').exists() else 'FIRST_RUN',
        'actual_decode': 'PASS', 'pcm_format': 'PCM16/mono/32000Hz', 'subjective_audio_audition': 'NOT_PERFORMED',
        'forge_openal': 'NOT_PERFORMED', 'entries': report}
    (EVIDENCE / 'report.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({k:v for k,v in result.items() if k != 'entries'}))

if __name__ == '__main__':
    run()
