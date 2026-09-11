const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const args = process.argv.slice(2);
const newVersion = (args[0] || '').replace(/^v/i, '').trim();
const changelogText = args.slice(1).join(' ') || 'General improvements and bug fixes';

if (!newVersion) {
  console.log('Usage: npm run ota:publish <version> [changelog notes]');
  console.log('Example: npm run ota:publish 1.0.1 "Added fast movie search and UI speedup"');
  process.exit(1);
}

const rootDir = path.resolve(__dirname, '..');
const otaManifestPath = path.join(rootDir, 'data', 'ota_manifest.json');
const packageJsonPath = path.join(rootDir, 'package.json');
const otaJsPath = path.join(rootDir, 'public', 'js', 'ota.js');

console.log(`\n🚀 [OTA Publisher] Publishing Over-The-Air Update v${newVersion}...\n`);

try {
  // 1. Update ota_manifest.json
  let manifest = {
    version: newVersion,
    buildNumber: 1,
    releaseDate: new Date().toISOString().split('T')[0],
    title: `Cloud Drive Leech v${newVersion}`,
    changelog: [changelogText],
    apkUrl: '/api/ota/download/apk',
    apkSize: '4.2 MB',
    mandatory: false
  };

  if (fs.existsSync(otaManifestPath)) {
    try {
      manifest = JSON.parse(fs.readFileSync(otaManifestPath, 'utf8'));
      manifest.version = newVersion;
      manifest.buildNumber = (manifest.buildNumber || 1) + 1;
      manifest.releaseDate = new Date().toISOString().split('T')[0];
      manifest.title = `Cloud Drive Leech v${newVersion}`;
      manifest.changelog = [changelogText];
    } catch (_) {}
  }
  fs.writeFileSync(otaManifestPath, JSON.stringify(manifest, null, 2), 'utf8');

  // 2. Update package.json version
  if (fs.existsSync(packageJsonPath)) {
    const pkg = JSON.parse(fs.readFileSync(packageJsonPath, 'utf8'));
    pkg.version = newVersion;
    fs.writeFileSync(packageJsonPath, JSON.stringify(pkg, null, 2), 'utf8');
  }

  // 3. Update public/js/ota.js version
  if (fs.existsSync(otaJsPath)) {
    let otaJs = fs.readFileSync(otaJsPath, 'utf8');
    otaJs = otaJs.replace(/window\.APP_VERSION\s*=\s*['"][^'"]+['"]/, `window.APP_VERSION = '${newVersion}'`);
    fs.writeFileSync(otaJsPath, otaJs, 'utf8');
  }

  console.log(`✅ Manifest updated for v${newVersion}!`);
  console.log('🔨 Triggering APK build...');
  execSync('node bin/build-apk.js', { cwd: rootDir, stdio: 'inherit' });

  console.log(`\n🎉 [OTA Published] v${newVersion} is live! Connected devices will receive the update prompt.`);
} catch (err) {
  console.error('❌ [OTA Publish Error]:', err.message);
  process.exit(1);
}
