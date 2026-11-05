"""Construit un flux « dongle Carlinkit -> autoradio » rejouable par SimTransport.

Format : [delai ms int32 BE][longueur int32 BE][message]. Chaque message suit le protocole
Carlinkit (en-tete 16 octets little-endian). Scenario de 20 s, proche d'un usage reel.
"""
import math
import struct
import sys

MAGIC = 0x55AA55AA
PLUGGED, PHASE, UNPLUGGED, VIDEO, AUDIO, SOFTWARE_VERSION, HEARTBEAT = 0x02, 0x03, 0x04, 0x06, 0x07, 0xCC, 0xAA


def msg(t, payload=b""):
    return struct.pack("<IIii", MAGIC, len(payload), t, ~t) + payload


def split_access_units(data):
    """Decoupe un flux Annex-B en unites d'acces (une par image), SPS/PPS rattaches a l'IDR."""
    starts = []
    i = 0
    while True:
        j = data.find(b"\x00\x00\x01", i)
        if j < 0:
            break
        s = j - 1 if j > 0 and data[j - 1] == 0 else j
        starts.append((s, data[j + 3] & 0x1F))
        i = j + 3
    aus, cur = [], None
    for k, (s, t) in enumerate(starts):
        e = starts[k + 1][0] if k + 1 < len(starts) else len(data)
        nal = data[s:e]
        if t in (7, 8, 6):  # SPS, PPS, SEI : debut d'une nouvelle image
            if cur is not None and cur["slice"]:
                aus.append(cur["d"])
                cur = None
            cur = cur or {"d": b"", "slice": False}
            cur["d"] += nal
        elif t in (1, 5):
            if cur is not None and cur["slice"]:
                aus.append(cur["d"])
                cur = None
            cur = cur or {"d": b"", "slice": False}
            cur["d"] += nal
            cur["slice"] = True
    if cur and cur["slice"]:
        aus.append(cur["d"])
    return aus


def video(au, w=800, h=480):
    return msg(VIDEO, struct.pack("<iiiii", w, h, 0, len(au), 0) + au)


def audio_cmd(cmd, decode_type=4):
    return msg(AUDIO, struct.pack("<ifi", decode_type, 0.0, 1) + bytes([cmd]))


def audio_pcm(decode_type, pcm):
    return msg(AUDIO, struct.pack("<ifi", decode_type, 0.0, 1) + pcm)


def sine(rate, ch, ms, freq, phase):
    n = rate * ms // 1000
    out = bytearray()
    for i in range(n):
        v = int(8000 * math.sin(2 * math.pi * freq * (phase + i) / rate))
        out += struct.pack("<h", v) * ch
    return bytes(out), phase + n


def main(h264_path, out_path):
    aus = split_access_units(open(h264_path, "rb").read())
    recs = []

    def add(delay, m):
        recs.append(struct.pack(">ii", int(delay), len(m)) + m)

    add(300, msg(SOFTWARE_VERSION, b"2025.02.25.1521CAY\x00"))
    add(100, msg(PHASE, struct.pack("<i", 7)))
    add(1000, msg(PLUGGED, struct.pack("<ii", 3, 1)))  # CarPlay sans fil
    add(50, audio_cmd(10))  # debut media
    phase = 0
    t_ms = 0
    for k, au in enumerate(aus[:600]):  # 20 s a 30 i/s
        t = k * 1000 / 30
        if k == 240:  # 8 s : Siri (micro de la voiture)
            add(0, audio_cmd(8, 5))
        if k == 330:  # 11 s : fin Siri
            add(0, audio_cmd(9, 5))
        if 360 <= k < 400:  # 12 s : rafale de 40 images sans delai (test de surcharge)
            add(0, video(au))
            continue
        add(33, video(au))
        if k % 2 == 0:  # audio 48 kHz stereo par blocs de 66 ms
            pcm, phase = sine(48000, 2, 66, 440, phase)
            add(0, audio_pcm(4, pcm))
        if k % 60 == 0:
            add(0, msg(HEARTBEAT))
    add(50, audio_cmd(11))  # fin media
    add(500, msg(UNPLUGGED))
    add(1000, b"\xde\xad\xbe\xef" * 4)  # trame corrompue : doit relancer proprement la session
    open(out_path, "wb").write(b"".join(recs))
    print(f"{len(aus)} images dans le H.264, {len(recs)} messages, {sum(map(len, recs))} octets")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
