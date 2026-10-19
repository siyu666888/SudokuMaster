#!/usr/bin/env bash
# 重新生成 README 中的界面截图与演示动图。
#
# 不需要模拟器或真机：Robolectric 在 JVM 上用真实 Skia + 系统字体渲染线上 Composable，
# 数据来自测试里预置的真实 Room 记录。界面改动后重跑本脚本即可同步文档配图。
#
# 依赖：JDK 17、Android SDK（platform 34）、Gradle 8.x、ffmpeg
# 用法：ANDROID_HOME=/path/to/sdk ./tools/export_screenshots.sh
set -euo pipefail
cd "$(dirname "$0")/.."

: "${ANDROID_HOME:?请先设置 ANDROID_HOME 环境变量}"
GRADLE=${GRADLE:-gradle}

echo "==> 渲染静态截图与交互帧"
"$GRADLE" testDebugUnitTest \
  --tests "com.example.sudokumaster.screenshot.ScreenshotTest" \
  --tests "com.example.sudokumaster.screenshot.GifFramesTest"

echo "==> 同步静态截图到 docs/screenshots"
mkdir -p docs/screenshots
cp app/build/screenshots/*.png docs/screenshots/

echo "==> 合成演示动图"
ffmpeg -y -loglevel error -framerate 1.1 -i app/build/gifframes/f%03d.png \
  -vf "scale=330:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=160[p];[b][p]paletteuse=dither=bayer" \
  -loop 0 docs/screenshots/demo.gif

echo "==> 完成"
ls -la docs/screenshots
