#!/bin/zsh
# Copy deploy/out/ (from stage.sh, plus .env with TUNNEL_TOKEN and PUBLIC_URL) to
# ~/hrf on the server, create the database on the first run, and (re)start the
# hrf compose project. Touches nothing outside ~/hrf.
set -euo pipefail

DEPLOY=${0:A:h}
OUT=$DEPLOY/out
[[ -f $DEPLOY/deploy.conf ]] || { echo "missing $DEPLOY/deploy.conf: HOST=user@server, KEY=path of the ssh key"; exit 1 }
source $DEPLOY/deploy.conf   # HOST, KEY (git-ignored, the server stays out of the repository)

[[ -f $OUT/.env ]] || { echo "missing $OUT/.env"; exit 1; }

srv() { ssh -o BatchMode=yes -i $KEY $HOST "$@" }

# a tar stream instead of rsync: macOS ships openrsync, which once exited 0 here without copying anything
echo "copying $(du -sh $OUT | cut -f1) to $HOST:~/hrf ..."
# empty app/ and site/ rather than deleting them: the running container's bind mounts
# would keep pointing at the deleted directories
srv 'mkdir -p ~/hrf/data ~/hrf/app ~/hrf/site && find ~/hrf/app ~/hrf/site -mindepth 1 -delete'
COPYFILE_DISABLE=1 tar -C $OUT --no-xattrs --no-mac-metadata -czf - --exclude ./data --exclude ./data-test . | srv 'tar -C ~/hrf -xzf -'
srv 'find ~/hrf -maxdepth 4 -name "._*" -delete'   # AppleDouble files from older deploys
srv 'test -f ~/hrf/.env && test -f ~/hrf/app/good-game.jar && test -f ~/hrf/site/index.html' || { echo "copy failed: files missing on the server"; exit 1; }
echo "copied"
srv 'set -e
cd ~/hrf
chmod 600 .env
. ./.env
docker compose pull -q
if [ ! -e data/good-game-database.properties ]; then
    docker compose run --rm hrf java -jar /app/good-game.jar create /data/good-game-database /site "$PUBLIC_URL" "$PUBLIC_URL/hrf/" 7070
fi
docker compose up -d
docker compose up -d --force-recreate hrf   # pick up a new jar and the refreshed files
sleep 10
docker compose ps
docker compose logs --tail 15'
