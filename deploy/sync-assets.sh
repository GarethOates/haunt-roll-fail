#!/bin/zsh
# Copy the client (index.html and the built hrf-fastopt.js), hrf.im's compiled
# clients (vendor/<game>: Arcs, The Old King's Crown) and the game images, fonts,
# sounds and shared images (from tools/fetch-assets.py) into ~/hrf/site on the
# server, next to what deploy.sh put there. Additive: nothing on the server is
# deleted and nothing restarts, good-game serves /site from disk.
# Touches nothing outside ~/hrf/site.
set -euo pipefail

DEPLOY=${0:A:h}
HRF=${DEPLOY:h}/haunt-roll-fail
[[ -f $DEPLOY/deploy.conf ]] || { echo "missing $DEPLOY/deploy.conf: HOST=user@server, KEY=path of the ssh key"; exit 1 }
source $DEPLOY/deploy.conf   # HOST, KEY (git-ignored, the server stays out of the repository)

srv() { ssh -o BatchMode=yes -i $KEY $HOST "$@" }

cd $HRF
files=(index.html target/scala-2.13/hrf-fastopt.js webp2 fonts sounds question-mark.png external-link.png omen.png background.png)
vendored=(vendor/*(/:t))                 # arcs tokc
assets=(${^vendored}/assets)
clients=(arcs/target/scala-2.13/hrf-arcs-fastopt-0.8.161.js tokc/target/scala-2.13/hrf-tokc-fastopt-0.8.155.js)
for f in $files vendor/$^clients; do [[ -e $f ]] || { echo "missing $HRF/$f, run tools/fetch-assets.py"; exit 1 }; done

local_count=$( (find webp2 fonts sounds -type f; cd vendor && find $vendored -type f) | grep -v -e '/\._' -e '\.DS_Store' | wc -l | tr -d ' ')
echo "copying $local_count files ($(du -shc $files vendor/$^vendored | tail -1 | cut -f1)) to $HOST:~/hrf/site ..."
# a tar stream instead of rsync: macOS ships openrsync, which once exited 0 here without copying anything
srv 'test -d ~/hrf/site' || { echo "no ~/hrf/site on the server, run deploy.sh first"; exit 1; }
COPYFILE_DISABLE=1 tar --no-xattrs --no-mac-metadata --exclude '._*' --exclude .DS_Store -czf - $files -C vendor $vendored | srv 'tar -C ~/hrf/site -xzf -'

remote_count=$(srv "cd ~/hrf/site && find webp2 fonts sounds $vendored -type f ! -name '._*' | wc -l")
echo "server has $remote_count files under webp2, fonts, sounds and $vendored (local $local_count)"
srv "cd ~/hrf/site && for g in webp2/* $assets; do echo \"  \$g: \$(find \$g -type f | wc -l)\"; done"
(( remote_count >= local_count )) || { echo "copy incomplete"; exit 1; }
srv "cd ~/hrf/site && grep -q menuOpen target/scala-2.13/hrf-fastopt.js && ls $clients >/dev/null" || { echo "clients missing on the server"; exit 1; }
echo "clients in place: homepage and other games from this build, Arcs and The Old King's Crown from hrf.im"
echo "done, no restart needed"
