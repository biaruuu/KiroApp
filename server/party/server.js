// Kiro watch party relay: rooms live in memory, the host's player state is pushed to every guest
// over a WebSocket the moment it changes. Run it anywhere that keeps a process alive.
const http = require('http');
const { WebSocketServer } = require('ws');

const PORT = process.env.PORT || 8080;
const MAX_MEMBERS = 30;
const HOST_GRACE_MS = 30000;
const CODE = /^[A-Z2-9]{6}$/;

const rooms = new Map();

const server = http.createServer((req, res) => {
  res.writeHead(200, { 'content-type': 'text/plain' });
  res.end(`kiro party relay ok, ${rooms.size} rooms\n`);
});
const wss = new WebSocketServer({ server, maxPayload: 4096 });

const send = (ws, o) => { if (ws.readyState === 1) ws.send(JSON.stringify(o)); };
const str = (v, n) => (typeof v === 'string' ? v.slice(0, n) : '');
const num = (v) => (Number.isFinite(v) ? v : 0);

function stateOf(r) {
  return { t: 'state', animeId: r.animeId, episode: r.episode, title: r.title, hostName: r.hostName,
    positionMs: r.positionMs, playing: r.playing, sentAt: r.sentAt };
}
function membersOf(r) {
  return { t: 'members', list: [...r.members.values()].map((m) => ({ uid: m.uid, host: m.uid === r.hostUid, ...m.info })) };
}
function broadcast(r, o, except) {
  for (const m of r.members.values()) if (m.ws !== except && m.ws) send(m.ws, o);
}
function endRoom(code, r) {
  broadcast(r, { t: 'ended' });
  for (const m of r.members.values()) if (m.ws) m.ws.close(1000, 'ended');
  rooms.delete(code);
}
function info(m) {
  m = m || {};
  return { name: str(m.name, 40) || 'Guest', avatar: str(m.avatar, 300), decoration: str(m.decoration, 60), nameFont: str(m.nameFont, 60) };
}

wss.on('connection', (ws) => {
  ws.alive = true;
  ws.on('pong', () => { ws.alive = true; });
  ws.bucket = 20; // simple rate limit: refills 10 per second, burst 20
  const refill = setInterval(() => { ws.bucket = Math.min(20, ws.bucket + 10); }, 1000);

  ws.on('message', (raw) => {
    if (--ws.bucket < 0) return;
    let m;
    try { m = JSON.parse(raw); } catch { return; }
    if (!m || typeof m.t !== 'string') return;

    if (m.t === 'ping') return send(ws, { t: 'pong' });

    if (m.t === 'lookup') {
      const r = rooms.get(str(m.code, 6).toUpperCase());
      send(ws, r ? { ...stateOf(r), t: 'info' } : { t: 'error', msg: 'No watch party with that code' });
      return ws.close(1000);
    }

    if (m.t === 'host') {
      const code = str(m.code, 6).toUpperCase();
      const uid = str(m.uid, 128);
      if (!CODE.test(code) || !uid) return send(ws, { t: 'error', msg: 'Bad request' });
      let r = rooms.get(code);
      if (r && r.hostUid !== uid) return send(ws, { t: 'error', msg: 'That code is taken, try again' });
      if (!r) {
        r = { hostUid: uid, hostName: str(m.hostName, 40) || 'Host', title: str(m.title, 200), animeId: num(m.animeId),
          episode: num(m.episode), positionMs: num(m.positionMs), playing: false, sentAt: Date.now(), members: new Map() };
        rooms.set(code, r);
      }
      clearTimeout(r.graceTimer);
      const old = r.members.get(uid);
      if (old && old.ws && old.ws !== ws) old.ws.close(1000, 'replaced');
      r.members.set(uid, { uid, ws, info: info(m.member) });
      ws.room = code; ws.uid = uid;
      send(ws, { t: 'joined', code });
      broadcast(r, membersOf(r));
      return;
    }

    if (m.t === 'join') {
      const code = str(m.code, 6).toUpperCase();
      const uid = str(m.uid, 128);
      const r = rooms.get(code);
      if (!r) return send(ws, { t: 'error', msg: 'No watch party with that code' });
      if (!uid) return send(ws, { t: 'error', msg: 'Bad request' });
      if (r.members.size >= MAX_MEMBERS && !r.members.has(uid)) return send(ws, { t: 'error', msg: 'This party is full' });
      const old = r.members.get(uid);
      if (old && old.ws && old.ws !== ws) old.ws.close(1000, 'replaced');
      r.members.set(uid, { uid, ws, info: info(m.member) });
      ws.room = code; ws.uid = uid;
      send(ws, { t: 'joined', code });
      send(ws, stateOf(r));
      broadcast(r, membersOf(r));
      return;
    }

    const r = rooms.get(ws.room);
    if (!r) return;
    if (m.t === 'state' && ws.uid === r.hostUid) {
      r.animeId = num(m.animeId); r.episode = num(m.episode); r.positionMs = num(m.positionMs);
      r.playing = m.playing === true; r.sentAt = Date.now();
      broadcast(r, stateOf(r), ws);
    } else if (m.t === 'end' && ws.uid === r.hostUid) {
      endRoom(ws.room, r);
    } else if (m.t === 'leave') {
      ws.close(1000);
    }
  });

  ws.on('close', () => {
    clearInterval(refill);
    const code = ws.room;
    const r = rooms.get(code);
    if (!r) return;
    const mem = r.members.get(ws.uid);
    if (!mem || mem.ws !== ws) return; // replaced by a newer connection
    if (ws.uid === r.hostUid) {
      mem.ws = null; // keep the room for a moment so the host can reconnect
      r.graceTimer = setTimeout(() => { if (rooms.get(code) === r) endRoom(code, r); }, HOST_GRACE_MS);
    } else {
      r.members.delete(ws.uid);
      broadcast(r, membersOf(r));
    }
  });
  ws.on('error', () => {});
});

setInterval(() => {
  for (const ws of wss.clients) {
    if (!ws.alive) { ws.terminate(); continue; }
    ws.alive = false;
    ws.ping();
  }
}, 30000);

server.listen(PORT, () => console.log(`kiro party relay on :${PORT}`));
