# Kiro watch party relay

A tiny WebSocket server. The host's player state is relayed to every guest the instant it changes.
Rooms live in memory (a restart ends open parties).

## Run

```
cd server/party
npm install
npm start          # listens on $PORT (default 8080)
```

## Deploy

Any host that keeps a Node process running and supports WebSockets works (Render, Fly.io,
Railway, a VPS). Vercel serverless does not. Use the `wss://` address it gives you.

## Point the app at it

Set a GitHub **repository variable** named `PARTY_WS_URL` (Settings > Secrets and variables >
Actions > Variables), for example `wss://kiro-party.onrender.com`. The next build uses it. For a
local build, set the `PARTY_WS_URL` environment variable.

With no URL set the app keeps using Firestore for watch parties, as before.

## Protocol (JSON)

Client to server: `host`, `join`, `lookup`, `state` (host only), `end`, `leave`, `ping`.
Server to client: `joined`, `state`, `members`, `ended`, `info`, `error`, `pong`.
