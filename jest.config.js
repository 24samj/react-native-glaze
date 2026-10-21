// iOS and Android only: web renders a plain View and would need react-native-web.
const path = require('path');

// Not in expo-module-scripts' "exports", so load it by path
const createJestPreset = require(path.join(__dirname, 'node_modules/expo-module-scripts/createJestPreset.cjs'));

module.exports = {
  projects: [
    createJestPreset(require('jest-expo/ios/jest-preset')),
    createJestPreset(require('jest-expo/android/jest-preset')),
  ],
};
