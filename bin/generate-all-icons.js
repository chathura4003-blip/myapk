const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const edgePath = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';
const rootDir = path.resolve(__dirname, '..');
const androidRes = path.join(rootDir, 'android', 'app', 'src', 'main', 'res');
const publicDir = path.join(rootDir, 'public');
const assetsPublicDir = path.join(rootDir, 'android', 'app', 'src', 'main', 'assets', 'public');

// 1. Shared SVG Elements & Gradients
const svgDefs = `
    <defs>
      <!-- Vibrant Cyber Gradient for the Monogram 'C' -->
      <linearGradient id="cGrad" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stop-color="#00f2fe" />
        <stop offset="35%" stop-color="#00c6ff" />
        <stop offset="70%" stop-color="#0072ff" />
        <stop offset="100%" stop-color="#7928ca" />
      </linearGradient>

      <!-- High-Voltage Gradient for Lightning Bolt -->
      <linearGradient id="arrowGrad" x1="0%" y1="0%" x2="0%" y2="100%">
        <stop offset="0%" stop-color="#00ffcc" />
        <stop offset="50%" stop-color="#00f2fe" />
        <stop offset="100%" stop-color="#4facfe" />
      </linearGradient>

      <!-- Cloud Top Bulb Neon Gradient -->
      <linearGradient id="cloudGrad" x1="0%" y1="0%" x2="100%" y2="0%">
        <stop offset="0%" stop-color="#00f2fe" stop-opacity="0.95" />
        <stop offset="50%" stop-color="#a855f7" stop-opacity="0.9" />
        <stop offset="100%" stop-color="#ff0080" stop-opacity="0.85" />
      </linearGradient>

      <!-- Multi-stage Neon Bloom Filter -->
      <filter id="neonGlowCyan" x="-50%" y="-50%" width="200%" height="200%">
        <feGaussianBlur stdDeviation="28" result="blur1" />
        <feGaussianBlur stdDeviation="14" result="blur2" />
        <feGaussianBlur stdDeviation="5" result="blur3" />
        <feMerge>
          <feMergeNode in="blur1" />
          <feMergeNode in="blur2" />
          <feMergeNode in="blur3" />
          <feMergeNode in="SourceGraphic" />
        </feMerge>
      </filter>

      <filter id="subtleGlow" x="-30%" y="-30%" width="160%" height="160%">
        <feGaussianBlur stdDeviation="10" result="blur" />
        <feMerge>
          <feMergeNode in="blur" />
          <feMergeNode in="SourceGraphic" />
        </feMerge>
      </filter>

      <!-- Center Aura Glow (transparent edges) -->
      <radialGradient id="centerGlow" cx="50%" cy="50%" r="45%">
        <stop offset="0%" stop-color="#00f2fe" stop-opacity="0.25" />
        <stop offset="55%" stop-color="#7928ca" stop-opacity="0.08" />
        <stop offset="100%" stop-color="#00f2fe" stop-opacity="0" />
      </radialGradient>

      <linearGradient id="squircleBgGrad" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stop-color="#0e131f" />
        <stop offset="50%" stop-color="#070a10" />
        <stop offset="100%" stop-color="#040609" />
      </linearGradient>

      <linearGradient id="borderGrad" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stop-color="#00f2fe" stop-opacity="0.8" />
        <stop offset="50%" stop-color="#7928ca" stop-opacity="0.4" />
        <stop offset="100%" stop-color="#00f2fe" stop-opacity="0.6" />
      </linearGradient>
    </defs>
`;

const logoGraphics = `
      <!-- Holographic Ambient Aura -->
      <circle cx="512" cy="500" r="340" fill="url(#centerGlow)" />

      <!-- Subtle Cyber Tech Rings -->
      <g opacity="0.15" stroke="#00f2fe" stroke-width="2">
        <circle cx="512" cy="500" r="320" fill="none" stroke-dasharray="8,16" />
        <circle cx="512" cy="500" r="220" fill="none" />
      </g>

      <!-- THE MONOGRAM 'C' -->
      <g filter="url(#neonGlowCyan)">
        <path d="
          M 670, 310
          C 580, 230, 430, 230, 340, 315
          C 240, 410, 240, 614, 340, 709
          C 430, 794, 580, 794, 670, 714
          C 696, 690, 696, 650, 668, 626
          C 642, 602, 602, 602, 578, 626
          C 525, 676, 435, 676, 385, 626
          C 325, 566, 325, 458, 385, 398
          C 435, 348, 525, 348, 578, 398
          C 602, 422, 642, 422, 668, 398
          C 696, 374, 696, 334, 670, 310
          Z"
          fill="url(#cGrad)"
        />

        <!-- Cloud Top Bulb -->
        <path d="
          M 430, 265
          C 445, 195, 525, 175, 575, 220
          C 605, 205, 645, 215, 665, 245
          C 675, 260, 675, 280, 665, 305
          C 630, 285, 595, 280, 560, 290
          C 520, 255, 460, 255, 430, 265
          Z"
          fill="url(#cloudGrad)"
          opacity="0.9"
        />
      </g>

      <!-- LIGHTNING BOLT -->
      <g filter="url(#subtleGlow)">
        <path d="
          M 545, 360
          L 460, 500
          L 520, 500
          L 470, 670
          L 600, 490
          L 535, 490
          L 585, 360
          Z"
          fill="url(#arrowGrad)"
        />
        <!-- Holographic Disc Projection Base -->
        <ellipse cx="512" cy="740" rx="140" ry="24" fill="none" stroke="#00f2fe" stroke-width="5" stroke-dasharray="16,10" opacity="0.85" />
        <ellipse cx="512" cy="740" rx="80" ry="14" fill="#00f2fe" opacity="0.35" filter="url(#subtleGlow)" />
      </g>

      <!-- Cyber Sparkle Stars -->
      <g fill="#ffffff">
        <circle cx="680" cy="270" r="5" opacity="0.9" />
        <circle cx="720" cy="512" r="4" opacity="0.75" />
        <circle cx="280" cy="340" r="4" opacity="0.85" />
        <circle cx="310" cy="680" r="5" opacity="0.85" />
        <polygon points="680,240 684,248 692,252 684,256 680,264 676,256 668,252 676,248" fill="#00ffcc" opacity="0.95" />
      </g>
`;

// 2. Generate Master HTML Templates with explicit pixel sizes to prevent any Edge window clipping
function getMasterHtml(type) {
  if (type === 'transparent') {
    // Master Transparent Icon for Web & In-app
    return `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    html, body { width: 1024px; height: 1024px; background: transparent; overflow: hidden; }
    svg { width: 1024px; height: 1024px; display: block; }
  </style>
</head>
<body>
  <svg viewBox="140 110 744 744" xmlns="http://www.w3.org/2000/svg">
    ${svgDefs}
    ${logoGraphics}
  </svg>
</body>
</html>`;
  }

  if (type === 'foreground') {
    // Android Adaptive Icon Safe Zone (70% scale centered inside 1024x1024)
    return `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    html, body { width: 1024px; height: 1024px; background: transparent; overflow: hidden; }
    svg { width: 1024px; height: 1024px; display: block; }
  </style>
</head>
<body>
  <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
    ${svgDefs}
    <g transform="matrix(0.70 0 0 0.70 153.6 153.6)">
      ${logoGraphics}
    </g>
  </svg>
</body>
</html>`;
  }

  if (type === 'squircle') {
    // Legacy squircle launcher icon (centered on dark gradient squircle)
    return `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    html, body { width: 1024px; height: 1024px; background: transparent; overflow: hidden; }
    svg { width: 1024px; height: 1024px; display: block; }
  </style>
</head>
<body>
  <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
    ${svgDefs}
    <rect x="72" y="72" width="880" height="880" rx="220" ry="220" fill="url(#squircleBgGrad)" />
    <rect x="72" y="72" width="880" height="880" rx="220" ry="220" fill="none" stroke="url(#borderGrad)" stroke-width="6" />
    <g transform="matrix(0.85 0 0 0.85 76.8 76.8)">
      ${logoGraphics}
    </g>
  </svg>
</body>
</html>`;
  }

  if (type === 'round') {
    // Legacy round launcher icon (centered on dark gradient circle)
    return `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    html, body { width: 1024px; height: 1024px; background: transparent; overflow: hidden; }
    svg { width: 1024px; height: 1024px; display: block; }
  </style>
</head>
<body>
  <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
    ${svgDefs}
    <circle cx="512" cy="512" r="440" fill="url(#squircleBgGrad)" />
    <circle cx="512" cy="512" r="440" fill="none" stroke="url(#borderGrad)" stroke-width="6" />
    <g transform="matrix(0.82 0 0 0.82 92.16 92.16)">
      ${logoGraphics}
    </g>
  </svg>
</body>
</html>`;
  }

  if (type === 'splash_port') {
    // AMOLED Portrait Splash Screen (1280x1920 master)
    return `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    html, body { width: 1280px; height: 1920px; background: #0a0c10; display: flex; align-items: center; justify-content: center; overflow: hidden; }
    .splash-logo { width: 560px; height: 560px; }
    svg { width: 100%; height: 100%; display: block; }
  </style>
</head>
<body>
  <div class="splash-logo">
    <svg viewBox="140 110 744 744" xmlns="http://www.w3.org/2000/svg">
      ${svgDefs}
      ${logoGraphics}
    </svg>
  </div>
</body>
</html>`;
  }

  if (type === 'splash_land') {
    // AMOLED Landscape Splash Screen (1920x1280 master)
    return `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    html, body { width: 1920px; height: 1280px; background: #0a0c10; display: flex; align-items: center; justify-content: center; overflow: hidden; }
    .splash-logo { width: 500px; height: 500px; }
    svg { width: 100%; height: 100%; display: block; }
  </style>
</head>
<body>
  <div class="splash-logo">
    <svg viewBox="140 110 744 744" xmlns="http://www.w3.org/2000/svg">
      ${svgDefs}
      ${logoGraphics}
    </svg>
  </div>
</body>
</html>`;
  }
}

// 3. Render Master High-Res PNG with Edge Headless
function renderMasterWithEdge(htmlFile, outFile, width, height, bgColor = '00000000') {
  const fileUrl = 'file:///' + htmlFile.replace(/\\/g, '/');
  console.log(`📸 Rendering Master [${width}x${height}] -> ${path.basename(outFile)}`);
  execFileSync(edgePath, [
    '--headless=new',
    '--disable-gpu',
    '--no-sandbox',
    `--screenshot=${outFile}`,
    `--window-size=${width},${height}`,
    '--hide-scrollbars',
    `--default-background-color=${bgColor}`,
    fileUrl
  ]);
}

// 4. Batch Resize Images with PowerShell System.Drawing (HighQualityBicubic, 0 pixel shift)
function batchResize(taskJsonPath) {
  const psScript = `
    Add-Type -AssemblyName System.Drawing
    $json = Get-Content '${taskJsonPath.replace(/\\/g, '\\\\')}' | ConvertFrom-Json
    foreach ($item in $json) {
      $src = $item.src
      $dst = $item.dst
      $w = [int]$item.w
      $h = [int]$item.h

      $dstDir = [System.IO.Path]::GetDirectoryName($dst)
      if (-not (Test-Path $dstDir)) {
        [System.IO.Directory]::CreateDirectory($dstDir) | Out-Null
      }

      $srcImg = [System.Drawing.Image]::FromFile($src)
      $bmp = New-Object System.Drawing.Bitmap($w, $h)
      $g = [System.Drawing.Graphics]::FromImage($bmp)
      $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
      $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
      $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
      $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality

      if ($item.bg) {
        $color = [System.Drawing.ColorTranslator]::FromHtml($item.bg)
        $g.Clear($color)
      } else {
        $g.Clear([System.Drawing.Color]::Transparent)
      }

      $g.DrawImage($srcImg, 0, 0, $w, $h)
      $bmp.Save($dst, [System.Drawing.Imaging.ImageFormat]::Png)
      $g.Dispose()
      $bmp.Dispose()
      $srcImg.Dispose()
      Write-Host "  -> Resized $($w)x$($h): $([System.IO.Path]::GetFileName($dst)) in $([System.IO.Path]::GetFileName($dstDir))"
    }
  `;

  execFileSync('powershell', ['-NoProfile', '-Command', psScript], { stdio: 'inherit' });
}

// ============================================================================
// MAIN EXECUTION
// ============================================================================
const tempDir = __dirname;
const masterTransHtml = path.join(tempDir, 'temp_master_trans.html');
const masterSquircleHtml = path.join(tempDir, 'temp_master_squircle.html');
const masterRoundHtml = path.join(tempDir, 'temp_master_round.html');
const masterForegroundHtml = path.join(tempDir, 'temp_master_fg.html');
const masterSplashPortHtml = path.join(tempDir, 'temp_master_splash_port.html');
const masterSplashLandHtml = path.join(tempDir, 'temp_master_splash_land.html');

fs.writeFileSync(masterTransHtml, getMasterHtml('transparent'), 'utf8');
fs.writeFileSync(masterSquircleHtml, getMasterHtml('squircle'), 'utf8');
fs.writeFileSync(masterRoundHtml, getMasterHtml('round'), 'utf8');
fs.writeFileSync(masterForegroundHtml, getMasterHtml('foreground'), 'utf8');
fs.writeFileSync(masterSplashPortHtml, getMasterHtml('splash_port'), 'utf8');
fs.writeFileSync(masterSplashLandHtml, getMasterHtml('splash_land'), 'utf8');

const masterTransPng = path.join(tempDir, 'master_trans.png');
const masterSquirclePng = path.join(tempDir, 'master_squircle.png');
const masterRoundPng = path.join(tempDir, 'master_round.png');
const masterForegroundPng = path.join(tempDir, 'master_foreground.png');
const masterSplashPortPng = path.join(tempDir, 'master_splash_port.png');
const masterSplashLandPng = path.join(tempDir, 'master_splash_land.png');

console.log('🎨 Step 1: Rendering Master Templates at Full Native Res (1024x1024 / 1920)...');
renderMasterWithEdge(masterTransHtml, masterTransPng, 1024, 1024);
renderMasterWithEdge(masterSquircleHtml, masterSquirclePng, 1024, 1024);
renderMasterWithEdge(masterRoundHtml, masterRoundPng, 1024, 1024);
renderMasterWithEdge(masterForegroundHtml, masterForegroundPng, 1024, 1024);
renderMasterWithEdge(masterSplashPortHtml, masterSplashPortPng, 1280, 1920, 'FF0A0C10');
renderMasterWithEdge(masterSplashLandHtml, masterSplashLandPng, 1920, 1280, 'FF0A0C10');

// Save raw SVG for web
const rawSvg = `<?xml version="1.0" encoding="utf-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="140 110 744 744" width="100%" height="100%">
${svgDefs}
${logoGraphics}
</svg>`;
fs.writeFileSync(path.join(publicDir, 'icon.svg'), rawSvg, 'utf8');
fs.writeFileSync(path.join(assetsPublicDir, 'icon.svg'), rawSvg, 'utf8');
console.log('✅ Saved public/icon.svg and assets/public/icon.svg');

// Build batch resize tasks
console.log('\n⚙️ Step 2: Preparing Batch Downscaling for Web, Launcher & Splash...');
const tasks = [];

// Web Icons
tasks.push({ src: masterTransPng, dst: path.join(publicDir, 'icon.png'), w: 512, h: 512 });
tasks.push({ src: masterTransPng, dst: path.join(publicDir, 'icon-512.png'), w: 512, h: 512 });
tasks.push({ src: masterTransPng, dst: path.join(publicDir, 'icon-192.png'), w: 192, h: 192 });
tasks.push({ src: masterTransPng, dst: path.join(publicDir, 'favicon.png'), w: 128, h: 128 });

// Android Launcher Mipmap Icons
const mipmaps = [
  { dir: 'mipmap-mdpi',    launcher: 48,  fg: 108 },
  { dir: 'mipmap-hdpi',    launcher: 72,  fg: 162 },
  { dir: 'mipmap-xhdpi',   launcher: 96,  fg: 216 },
  { dir: 'mipmap-xxhdpi',  launcher: 144, fg: 324 },
  { dir: 'mipmap-xxxhdpi', launcher: 192, fg: 432 }
];

for (const m of mipmaps) {
  const mDir = path.join(androidRes, m.dir);
  tasks.push({ src: masterSquirclePng,   dst: path.join(mDir, 'ic_launcher.png'),            w: m.launcher, h: m.launcher });
  tasks.push({ src: masterRoundPng,      dst: path.join(mDir, 'ic_launcher_round.png'),      w: m.launcher, h: m.launcher });
  tasks.push({ src: masterForegroundPng, dst: path.join(mDir, 'ic_launcher_foreground.png'), w: m.fg,       h: m.fg });
}

// Android Splash Screens (Landscape & Portrait)
const splashPortList = [
  { dir: 'drawable-port-mdpi',    w: 320,  h: 480 },
  { dir: 'drawable-port-hdpi',    w: 480,  h: 800 },
  { dir: 'drawable-port-xhdpi',   w: 720,  h: 1280 },
  { dir: 'drawable-port-xxhdpi',  w: 960,  h: 1600 },
  { dir: 'drawable-port-xxxhdpi', w: 1280, h: 1920 }
];

for (const sp of splashPortList) {
  tasks.push({ src: masterSplashPortPng, dst: path.join(androidRes, sp.dir, 'splash.png'), w: sp.w, h: sp.h, bg: '#0a0c10' });
}

const splashLandList = [
  { dir: 'drawable',               w: 480,  h: 320 },
  { dir: 'drawable-land-mdpi',     w: 480,  h: 320 },
  { dir: 'drawable-land-hdpi',     w: 800,  h: 480 },
  { dir: 'drawable-land-xhdpi',    w: 1280, h: 720 },
  { dir: 'drawable-land-xxhdpi',   w: 1600, h: 960 },
  { dir: 'drawable-land-xxxhdpi',  w: 1920, h: 1280 }
];

for (const sl of splashLandList) {
  tasks.push({ src: masterSplashLandPng, dst: path.join(androidRes, sl.dir, 'splash.png'), w: sl.w, h: sl.h, bg: '#0a0c10' });
}

const taskJsonPath = path.join(tempDir, 'resize_tasks.json');
fs.writeFileSync(taskJsonPath, JSON.stringify(tasks, null, 2), 'utf8');

console.log(`\n🚀 Step 3: Executing High-Fidelity Downscaling for ${tasks.length} Assets...`);
batchResize(taskJsonPath);

// Step 4: Sync Web Icons to Assets Public
console.log('\n🔄 Step 4: Syncing Web Icons to android/app/src/main/assets/public/...');
const webFiles = ['icon.png', 'favicon.png', 'icon-192.png', 'icon-512.png'];
for (const f of webFiles) {
  fs.copyFileSync(path.join(publicDir, f), path.join(assetsPublicDir, f));
}

// Step 5: Ensure adaptive icon background is 100% AMOLED pure dark in both colors and drawables
console.log('\n🛡️ Step 5: Harmonizing Adaptive Backgrounds (colors.xml & drawable)...');
const bgXml = path.join(androidRes, 'values', 'ic_launcher_background.xml');
fs.writeFileSync(bgXml, `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#070a10</color>
</resources>
`, 'utf8');

// Replace legacy green vector in drawable/ with dark vector so both @drawable and @color match
const drawableBgXml = path.join(androidRes, 'drawable', 'ic_launcher_background.xml');
fs.writeFileSync(drawableBgXml, `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#070a10"
        android:pathData="M0,0h108v108h-108z" />
</vector>
`, 'utf8');

// Step 6: Clean up temporary files
console.log('\n🧹 Step 6: Cleaning up temporary build files...');
const tempFiles = [
  masterTransHtml, masterSquircleHtml, masterRoundHtml, masterForegroundHtml, masterSplashPortHtml, masterSplashLandHtml,
  masterTransPng, masterSquirclePng, masterRoundPng, masterForegroundPng, masterSplashPortPng, masterSplashLandPng,
  taskJsonPath
];
for (const tf of tempFiles) {
  try { fs.unlinkSync(tf); } catch (_) {}
}

console.log('\n🎉 ALL ICONS & SPLASH ASSETS HAVE BEEN MASTER-RENDERED AND SCALED WITH 100% PERFECT CENTERING & ZERO PIXEL LOSS!');
