#!/usr/bin/env bash
# Synthetic media only; these fixtures contain no user titles, settings or credentials.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
command -v ffmpeg >/dev/null
out=composeApp/build/qa/quality
mkdir -p "$out"
ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i testsrc2=size=1280x720:rate=24 \
  -f lavfi -i sine=frequency=440:sample_rate=48000 \
  -t 20 -c:v libx264 -preset ultrafast -pix_fmt yuv420p -c:a aac \
  -metadata:s:a:0 language=eng -metadata:s:a:0 title=Main -movflags +faststart "$out/720p.mp4"
ffmpeg -hide_banner -loglevel error -y -i "$out/720p.mp4" \
  -vf scale=640:360 -c:v libx264 -preset ultrafast -c:a copy -movflags +faststart "$out/360p.mp4"
for height in 720 360; do
  ffmpeg -hide_banner -loglevel error -y -i "$out/${height}p.mp4" \
    -map 0 -c copy -metadata:s:a:0 title=Main "$out/${height}p.mkv"
done
