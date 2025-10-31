import { createDefaultEsmPreset } from "ts-jest";

const tsJestTransformCfg = createDefaultEsmPreset().transform;

/** @type {import("jest").Config} **/
export default {
  clearMocks: true,
  collectCoverage: true,
  testEnvironment: "jsdom",
  transform: {
    ...tsJestTransformCfg,
    '^.+\\.jsx?$': 'babel-jest',
  },
  "extensionsToTreatAsEsm": [".ts"],
  "transformIgnorePatterns": [
      "node_modules/(?!p-map)"
    ],
  automock: false,
  setupFiles: ["./src/setupTests.js"],
  testRegex: "__tests__/.*\\.test\\.ts$",
};
