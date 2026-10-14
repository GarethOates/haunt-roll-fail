#!/bin/zsh
# Copy an online game from hrf.im into the hrf.botc.games server and print its new links:
#     deploy/import-game.sh 'https://hrf.im/play/<game>/<16 letters>[?version=...]'
# Any player's or the spectator's link of the game will do. Runs
# haunt-roll-fail/tools/import-game.py on the server (piped over ssh, nothing is left
# there), which reads hrf.im and writes through good-game's own API in the hrf container.
set -euo pipefail

DEPLOY=${0:A:h}
[[ -f $DEPLOY/deploy.conf ]] || { echo "missing $DEPLOY/deploy.conf: HOST=user@server, KEY=path of the ssh key"; exit 1 }
source $DEPLOY/deploy.conf   # HOST, KEY (git-ignored, the server stays out of the repository)

[[ $# -eq 1 && $1 =~ '^https://hrf\.im/play/[a-z-]+/[a-z]{16}(\?version=[0-9.]+)?$' ]] || {
    echo "usage: $0 'https://hrf.im/play/<game>/<16 letters>[?version=...]'"; exit 1
}
ssh -o BatchMode=yes -i $KEY $HOST "python3 - '$1'" < ${DEPLOY:h}/haunt-roll-fail/tools/import-game.py
