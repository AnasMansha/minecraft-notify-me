"""Reference callsign implementation (mirrors mod Callsign.java and app Callsign.kt).
python -I callsign_ref.py  -> prints test vectors for key bytes 0..N"""
import hashlib, os
HERE = os.path.dirname(os.path.abspath(__file__))
ADJ = open(os.path.join(HERE, "callsign_adjectives.txt")).read().split()
NOUN = open(os.path.join(HERE, "callsign_nouns.txt")).read().split()
def callsign(key: bytes) -> str:
    h = hashlib.sha256(key).digest()
    return f"{ADJ[h[0]]} {NOUN[h[1]]} {h[2] % 100:02d}"
if __name__ == "__main__":
    for s in ["", "notifyme", "hello world", "0123456789"]:
        print(f"{s!r} -> {callsign(s.encode())}")
