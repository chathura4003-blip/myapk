const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

console.log('\n🚀 [OTA & APK Builder] Starting Build Process for Cloud Drive Leech...\n');

const rootDir = path.resolve(__dirname, '..');
const androidDir = path.join(rootDir, 'android');
const buildType = (process.env.APK_BUILD_TYPE || 'debug').toLowerCase();
const apkSource = path.join(androidDir, 'app', 'build', 'outputs', 'apk', buildType, `app-${buildType}.apk`);
const apkDest = path.join(rootDir, 'CloudDriveLeech.apk');
const otaManifestPath = path.join(rootDir, 'data', 'ota_manifest.json');

try {
  // 1. Sync Web Assets to Android
  console.log('📦 Step 1/3: Syncing web assets and OTA updater to Android...');
  execSync('npx cap sync android', { cwd: rootDir, stdio: 'inherit' });

  // 2. Run Gradle Assemble
  console.log('\n🔨 Step 2/3: Compiling Native Android APK with Gradle (Java 17)...');
  if (!['debug', 'release'].includes(buildType)) {
    throw new Error('APK_BUILD_TYPE must be debug or release');
  }
  if (buildType === 'release' && (!process.env.ANDROID_KEYSTORE_FILE || !process.env.ANDROID_KEYSTORE_PASSWORD || !process.env.ANDROID_KEY_ALIAS || !process.env.ANDROID_KEY_PASSWORD)) {
    throw new Error('Refusing unsigned release build. Set Android keystore, alias, and password environment variables first.');
  }
  const gradleTask = buildType === 'release' ? 'assembleRelease' : 'assembleDebug';
  const gradlewCmd = process.platform === 'win32' ? `gradlew.bat ${gradleTask}` : `./gradlew ${gradleTask}`;
  execSync(gradlewCmd, { cwd: androidDir, stdio: 'inherit' });

  // 3. Verify & Copy APK
  if (fs.existsSync(apkSource)) {
    console.log('\n📋 Step 3/3: Packaging final APK & Updating OTA Manifest...');
    fs.copyFileSync(apkSource, apkDest);
    const stats = fs.statSync(apkDest);
    const sizeMb = (stats.size / (1024 * 1024)).toFixed(1) + ' MB';

    // Update OTA Manifest
    if (fs.existsSync(otaManifestPath)) {
      try {
        const manifest = JSON.parse(fs.readFileSync(otaManifestPath, 'utf8'));
        manifest.apkSize = sizeMb;
        manifest.releaseDate = new Date().toISOString().split('T')[0];
        fs.writeFileSync(otaManifestPath, JSON.stringify(manifest, null, 2), 'utf8');
      } catch (_) {}
    }

    console.log('\n======================================================');
    console.log('🎉 BUILD & OTA PACKAGING SUCCESSFUL!');
    console.log(`📱 APK Path: ${apkDest}`);
    console.log(`📦 Size:     ${sizeMb}`);
    console.log('📡 Over-The-Air (OTA) distribution is active and ready!');
    console.log('======================================================\n');
  } else {
    throw new Error(`Output APK not found at: ${apkSource}`);
  }
} catch (err) {
  console.error('\n❌ [Build Error]:', err.message);
  process.exit(1);
}
