# Notify Me link protocol (v1)

Phone ⇄ Minecraft, direct, no cloud. WebSocket text frames with JSON at `ws://<host>:25570/ws`.
LAN discovery (UDP 25571): the mod broadcasts every 2 s and answers a phone's probe `{"t":"notifyme-probe"}` immediately:
`{"t":"notifyme","v":1,"name":"<world>","id":"<serverId>","port":25570,"invite":["Odd Deer 68"]}`.
`invite` lists callsigns that a notifier links but that are not connected; those phones offer one-tap Accept.
A phone that is linked on a notifier is approved automatically on first connect.

## Identity
* Phone key: EC P-256 (Android Keystore). `key` = base64 X.509 SubjectPublicKeyInfo.
* Callsign = `adj[h0] noun[h1] NN` where h = SHA-256(key) and NN = h2 % 100 (two digits), e.g. `Odd Deer 68`.
  Word lists: `callsign_adjectives.txt`, `callsign_nouns.txt` (256 each, identical in mod and app).
  Reference: `callsign_ref.py`, vectors: `test_vectors.txt`.

## Handshake
| dir | message |
|---|---|
| → | `hello{callsign, key, device}` |
| ← | `challenge{nonce, serverId, server, v}` or `rejected{reason}` (`callsign_mismatch`, `callsign_taken`) |
| → | `auth{sig}`: SHA256withECDSA over `notifyme-auth|<nonce>|<serverId>` |
| ← | `pending{message}` (first time, until `/notifyme approve <callsign>`) then `welcome{serverId, server, callsign}` |
| ← | `snapshot{blocks[]}` then any queued `alert` with `missed:true` |

## After welcome
Server → phone: `block{block}`, `removed{uuid}`, `alert{id, uuid, name, tier, message, active, strength, ts, server, repeat, test}`,
`acked{id, by}`, `bye{reason}`, `error{message}`, `pong`.
Phone → server: `ack{id}`, `config{uuid, name?, message?, tier?, trigger?, threshold?, cooldown?, repeat?}`, `test{uuid}`,
`unlink{uuid}`, `snapshot`, `ping`.

Block JSON: `uuid, name, message, tier(0-4), trigger(rising|falling|both|while_on), threshold(1-15), cooldown(s), repeat(s),
alarmColor(0-5), showState, active, strength, lastChange(ms), loaded, dim, dimName, x, y, z, owner, links[]`.
Alert JSON also carries `color` and `showState` from its block.
