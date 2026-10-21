// Resolve react-native-glaze from the repo root (one level up), with one copy of react,
// react-native and the Expo packages: the root's dev copies are blocked so they come from here.
const { getDefaultConfig } = require('expo/metro-config');
const path = require('path');

const root = path.resolve(__dirname, '..');
const config = getDefaultConfig(__dirname);

const shared = ['react', 'react-native', 'expo', 'expo-modules-core', 'expo-glass-effect', 'expo-blur'];
config.watchFolders = [root];
config.resolver.blockList = [
  ...[].concat(config.resolver.blockList ?? []),
  ...shared.map((name) => new RegExp(`^${path.join(root, 'node_modules', name).replace(/[/\\]/g, '[/\\\\]')}[/\\\\].*`)),
];
config.resolver.nodeModulesPaths = [path.resolve(__dirname, 'node_modules'), path.join(root, 'node_modules')];
config.resolver.extraNodeModules = { 'react-native-glaze': root };

module.exports = config;
