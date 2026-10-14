#!/bin/zsh
# Collect the built client, the Arcs assets and the good-game jar into deploy/out/,
# laid out as the server expects. Run after `sbt fastOptJS` (haunt-roll-fail),
# `sbt assembly` (good-game) and haunt-roll-fail/tools/fetch-assets.py. deploy.sh ships
# it all; sync-assets.sh copies only the images, fonts and sounds, without a restart.
set -euo pipefail

DEPLOY=${0:A:h}
ROOT=${DEPLOY:h}
HRF=$ROOT/haunt-roll-fail
OUT=$DEPLOY/out

mkdir -p $OUT/site/target/scala-2.13 $OUT/app
rsync -a $HRF/index.html $HRF/question-mark.png $HRF/external-link.png $HRF/omen.png $HRF/background.png $OUT/site/
rsync -a $HRF/target/scala-2.13/hrf-fastopt.js $OUT/site/target/scala-2.13/
rsync -a --delete $HRF/fonts/ $OUT/site/fonts/
rsync -a --delete $HRF/sounds/ $OUT/site/sounds/
rsync -a --delete $HRF/webp2/ $OUT/site/webp2/
for g in $HRF/vendor/*(/); do rsync -a --delete $g/ $OUT/site/${g:t}/; done   # hrf.im's compiled clients and their images
rsync -a "$ROOT/good-game/target/scala-2.13/HRF Good Game-assembly-17.0.jar" $OUT/app/good-game.jar
cp $DEPLOY/compose.yaml $OUT/compose.yaml

du -sh $OUT/site $OUT/app
