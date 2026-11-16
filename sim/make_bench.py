"""Flux de banc d'essai « conduite reelle » pour SimTransport (APK de test).

    python3 make_bench.py video.h264 fk2bench.bin

- video 800x480 a 30 i/s, image cle toutes les 0,5 s (= delai de reponse d'un vrai dongle
  a une demande d'image cle ; le faux dongle ne sait pas en fabriquer) ;
- musique 48 kHz stereo par blocs de 20 ms, envoyee 0,5 % trop vite (horloges iPhone/autoradio
  jamais identiques) ;
- toutes les 10 s, coupure Wi-Fi de 400 ms suivie d'une rafale (CarPlay sans fil) ;
- appel telephonique de 40 a 50 s (commandes audio 4 et 5, musique coupee) ;
"""
import struct
import sys

from make_stream import (PHASE, audio_cmd, msg, split_access_units, video,
                         PLUGGED, SOFTWARE_VERSION, HEARTBEAT)

FPS = 30
DURATION_S = 68
CALL = (40.0, 50.0)
HICCUP_EVERY_S, HICCUP_MS = 10.0, 400
AUDIO_CHUNK_MS, AUDIO_DRIFT = 20, 0.005


def main(main_h264, out_path):
    aus = split_access_units(open(main_h264, "rb").read())
    n = min(len(aus), DURATION_S * FPS)
    events = []  # (instant ms, message)

    for k in range(n):
        events.append((k * 1000.0 / FPS, video(aus[k])))
        if k % 60 == 0:
            events.append((k * 1000.0 / FPS, msg(HEARTBEAT)))

    # Musique : blocs de 20 ms (3840 octets) produits 0,5 % trop vite, sauf pendant l'appel.
    pcm = bytes(3840)
    step = AUDIO_CHUNK_MS * (1 - AUDIO_DRIFT)
    t = 0.0
    events.append((0.0, audio_cmd(10)))
    while t < n * 1000.0 / FPS:
        if not (CALL[0] * 1000 <= t < CALL[1] * 1000):
            events.append((t, msg(0x07, struct.pack("<ifi", 4, 0.0, 1) + pcm)))
        t += step
    events.append((CALL[0] * 1000 - 1, audio_cmd(11)))
    events.append((CALL[0] * 1000, audio_cmd(4)))
    events.append((CALL[1] * 1000, audio_cmd(5)))
    events.append((CALL[1] * 1000 + 1, audio_cmd(10)))
    events.sort(key=lambda e: e[0])

    # Coupures Wi-Fi : tout ce qui devait arriver pendant la coupure arrive d'un coup a la fin.
    def deliver(ts):
        cycle = ts % (HICCUP_EVERY_S * 1000)
        start = HICCUP_EVERY_S * 1000 - HICCUP_MS
        if ts > HICCUP_EVERY_S * 1000 and cycle >= start:
            return ts - cycle + HICCUP_EVERY_S * 1000
        return ts

    recs = []

    def add(delay, m):
        recs.append(struct.pack(">ii", int(delay), len(m)) + m)

    add(300, msg(SOFTWARE_VERSION, b"2025.02.25.1521CAY\x00"))
    add(100, msg(PHASE, struct.pack("<i", 7)))
    add(1000, msg(PLUGGED, struct.pack("<ii", 3, 1)))
    sent_ms = 0
    for ts, m in events:
        d = deliver(ts)
        target = int(d)
        delay = max(0, target - sent_ms)
        sent_ms += delay
        add(delay, m)
    add(500, msg(0x04))  # iPhone deconnecte
    open(out_path, "wb").write(b"".join(recs))

    print(f"{n} images, {len(recs)} messages, {sum(map(len, recs)) / 1e6:.1f} Mo")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
