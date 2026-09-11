'use strict';

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const adbPath = 'C:\\Users\\chathura\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe';
const configPath = path.resolve(__dirname, '../capacitor.config.json');

const action = process.argv[2] || 'on';

function run(cmd, cwd = path.resolve(__dirname, '..')) {
  console.log(`\x1b[36m> ${cmd}\x1b[0m`);
  execSync(cmd, { cwd, stdio: 'inherit' });
}

function adb(args) {
  try {
    return execSync(`"${adbPath}" ${args}`, { encoding: 'utf8' }).trim();
  } catch (e) {
    console.warn(`[ADB Warning] ${e.message}`);
    return '';
  }
}

if (action === 'on') {
  console.log('\x1b[32m=== 🚀 Enabling USB Live-Reload Mode ===\x1b[0m');
  
  // 1. Update capacitor.config.json with local live dev server
  const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
  config.server = {
    url: 'http://localhost:5000',
    cleartext: true,
    allowNavigation: ['*']
  };
  fs.writeFileSync(configPath, JSON.stringify(config, null, 2), 'utf8');

  // 2. Setup ADB reverse tethering
  adb('reverse tcp:5000 tcp:5000');
  console.log('✅ ADB Reverse tcp:5000 -> tcp:5000 connected');

  // 3. Sync & Build Live APK
  run('npx cap sync android');
  run('.\\gradlew.bat assembleDebug', path.resolve(__dirname, '../android'));

  const srcApk = path.resolve(__dirname, '../android/app/build/outputs/apk/debug/app-debug.apk');
  const destApk = path.resolve(__dirname, '../CloudDriveLeech.apk');
  fs.copyFileSync(srcApk, destApk);

  // 4. Install & Launch Live APK
  adb(`install -r "${destApk}"`);
  adb('shell am start -n com.clouddrive.leech/com.clouddrive.leech.MainActivity');

  console.log('\n\x1b[32m🎉 LIVE RELOAD IS NOW ACTIVE!\x1b[0m');
  console.log('👉 Live reload connected to port 5000');
  console.log('👉 Now whenever you push updates with `npm run sync`, changes reflect instantly!');
  console.log('👉 To inspect on PC Chrome: open \x1b[36mchrome://inspect/#devices\x1b[0m\n');
} else if (action === 'off') {
  console.log('\x1b[33m=== 📦 Restoring Standalone Offline Mode ===\x1b[0m');

  const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
  config.server = {
    cleartext: true,
    allowNavigation: ['*']
  };
  fs.writeFileSync(configPath, JSON.stringify(config, null, 2), 'utf8');

  run('npx cap sync android');
  run('.\\gradlew.bat assembleDebug', path.resolve(__dirname, '../android'));

  const srcApk = path.resolve(__dirname, '../android/app/build/outputs/apk/debug/app-debug.apk');
  const destApk = path.resolve(__dirname, '../CloudDriveLeech.apk');
  fs.copyFileSync(srcApk, destApk);

  adb(`install -r "${destApk}"`);
  adb('shell am start -n com.clouddrive.leech/com.clouddrive.leech.MainActivity');

  console.log('\n\x1b[32m✅ Standalone Offline APK Installed and Running!\x1b[0m\n');
} else if (action === 'push') {
  console.log('\x1b[36m=== ⚡ 1-Click Fast Sync & Push ===\x1b[0m');
  run('npx cap sync android');
  run('.\\gradlew.bat assembleDebug', path.resolve(__dirname, '../android'));

  const srcApk = path.resolve(__dirname, '../android/app/build/outputs/apk/debug/app-debug.apk');
  const destApk = path.resolve(__dirname, '../CloudDriveLeech.apk');
  fs.copyFileSync(srcApk, destApk);

  adb(`install -r "${destApk}"`);
  adb('shell am start -n com.clouddrive.leech/com.clouddrive.leech.MainActivity');

  console.log('\n\x1b[32m✅ Latest Build Pushed to Phone!\x1b[0m\n');
}
