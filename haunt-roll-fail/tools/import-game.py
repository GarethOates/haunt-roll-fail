#!/usr/bin/env python3 -I
"""
Copy an online game from hrf.im into our own good-game server, and print the new
player links. hrf.im keeps its copy; play continues on whichever server the links
point at, so share only the new links.

Usage, on the server (deploy/import-game.sh pipes this file there over ssh):
    python3 - <hrf.im game link> [--target URL] [--public URL] < import-game.py

The link is any player's or the spectator's link of the game, e.g.
https://hrf.im/play/arcs-br/abcdefghijklmnop?version=0.8.155. Its version must be
one our site has a client for (tools/fetch-assets.py ARCS_CLIENTS for Arcs).

How (as hrf.scala arrangeOnlineGame creates a game):
  1. Open the link on hrf.im: the page carries that player's user id and secret,
     and the game's lobby journal. Read the lobby ("meta", "version", "title",
     "user <faction> <user id>", "name <user id> <name>", ..., "server <game journal>")
     and every game journal it names, with the player's rights.
  2. On the target: a host user, a lobby journal and a journal per game journal,
     a spectator play, and a play per player (one user may hold several factions)
     with read and append rights on the game journals.
  3. Append the game journals, then the lobby with hrf.im's user and journal ids
     replaced by the new ones.
"""

import argparse
import os
import re
import subprocess
import sys
import urllib.parse
import urllib.request

USER_AGENT = "haunt-roll-fail-import/1.0"


def http(method, url, body=None, referer=None):
    headers = {"User-Agent": USER_AGENT}
    if referer:
        headers["Referer"] = referer
    data = None
    if body is not None:
        data = body.encode("utf-8")
        headers["Content-Type"] = "text/plain; charset=UTF-8"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read().decode("utf-8")


def default_target():
    """good-game inside the hrf compose project, reached from the host by container ip."""
    out = subprocess.run(["docker", "inspect", "-f", "{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}", "hrf-hrf-1"],
                         capture_output=True, text=True, check=True).stdout.split()
    return "http://%s:7070" % out[0]


def default_public():
    with open(os.path.expanduser("~/hrf/.env")) as f:
        for l in f:
            if l.startswith("PUBLIC_URL="):
                return l.split("=", 1)[1].strip().strip('"')
    sys.exit("no PUBLIC_URL in ~/hrf/.env, pass --public")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("link", help="a player's or the spectator's hrf.im link to the game")
    ap.add_argument("--target", help="good-game to import into (default: the hrf container on this host)")
    ap.add_argument("--public", help="public url of the target for the printed links (default: PUBLIC_URL of ~/hrf/.env)")
    args = ap.parse_args()

    link = urllib.parse.urlsplit(args.link)
    m = re.fullmatch(r"/play/([\w-]+)/([a-z]{16})", link.path)
    if not m:
        sys.exit("not a game link: %s" % args.link)
    source = "%s://%s" % (link.scheme, link.netloc)
    target = (args.target or default_target()).rstrip("/")
    public = (args.public or default_public()).rstrip("/")

    # 1. read the game from the source
    page = http("GET", args.link)
    settings = dict(re.findall(r'data-(\w+)="([^"]*)"', re.search(r'<title id="settings"[^>]*>', page).group(0)))
    user, secret, lobby = settings["user"], settings["secret"], settings["lobby"]
    if not (user and secret and lobby):
        sys.exit("the page has no player credentials, is the link complete?")
    read = lambda journal: http("GET", "%s/read/%s/%s/%s/0" % (source, user, secret, journal), referer=args.link)

    lobby_lines = read(lobby).split("\n")
    field = lambda key: [l[len(key) + 1:] for l in lobby_lines if l.startswith(key + " ")]
    meta, version, title = field("meta")[0], field("version")[0], field("title")[0]
    games = field("server")
    players = {}                                  # old user id -> factions
    for l in field("user"):
        f, u = l.split(" ", 1)
        players.setdefault(u, []).append(f)
    names = dict(l.split(" ", 1) for l in field("name"))
    journals = {g: read(g) for g in games}
    print("%s: %s %s, %d players, %s moves" % (title, meta, version, len(players),
                                                 " + ".join(str(len(j.split("\n")) if j else 0) for j in journals.values())))

    # 2. the game on the target, as arrangeOnlineGame makes it
    post = lambda path, body="": http("POST", target + path, body)
    host_id, host_secret = post("/new-user", title + " HOST").split("\n")
    host = host_id + "/" + host_secret
    new_lobby = post("/new-journal/" + host, title + " LOBBY")
    ids = {lobby: new_lobby}
    for g in games:
        ids[g] = post("/new-journal/" + host, title)

    spectator, spectator_play = post("/new-play/%s/%s" % (host, new_lobby), "Spectator").split("\n")
    for g in games:
        post("/grant-read/%s/%s/%s" % (host, ids[g], spectator))
    plays = {}
    for u, factions in players.items():
        nu, play = post("/new-play/%s/%s" % (host, new_lobby), names.get(u, " ".join(factions))).split("\n")
        ids[u] = nu
        plays[u] = play
        for g in games:
            post("/grant-read-append/%s/%s/%s" % (host, ids[g], nu))

    # 3. the moves, then the lobby that points at them
    for g in games:
        if journals[g]:
            post("/append/%s/%s/0" % (host, ids[g]), journals[g])
    rename = re.compile("|".join(map(re.escape, sorted(ids, key=len, reverse=True))))
    post("/append/%s/%s/0" % (host, new_lobby), "\n".join(rename.sub(lambda m: ids[m.group(0)], l) for l in lobby_lines))

    query = "?version=" + version
    print("\nlinks:")
    for u, factions in players.items():
        print("  %-12s %-6s %s/play/%s/%s%s" % (names.get(u, ""), " ".join(factions), public, meta, plays[u], query))
    print("  %-12s %-6s %s/play/%s/%s%s" % ("spectator", "", public, meta, spectator_play, query))


if __name__ == "__main__":
    main()
