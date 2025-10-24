// Comprehensive Jest setup file for React 16 with Enzyme support
import { configure } from 'enzyme';
import Adapter from 'enzyme-adapter-react-16';

// Configure Enzyme adapter for React 16
configure({ adapter: new Adapter() });

// TextEncoder/TextDecoder polyfills for Node.js
if (typeof global.TextEncoder === 'undefined') {
  try {
    const { TextEncoder, TextDecoder } = require('util');
    global.TextEncoder = TextEncoder;
    global.TextDecoder = TextDecoder;
  } catch (error) {
    // Fallback polyfill if util is not available
    global.TextEncoder = class TextEncoder {
      encode(input = '') {
        const arr = new Uint8Array(input.length);
        for (let i = 0; i < input.length; i++) {
          arr[i] = input.charCodeAt(i);
        }
        return arr;
      }
    };
    global.TextDecoder = class TextDecoder {
      decode(arr) {
        return String.fromCharCode(...arr);
      }
    };
  }
}

// Web API polyfills
if (typeof global.ReadableStream === 'undefined') {
  global.ReadableStream = class ReadableStream {};
}

if (typeof global.WritableStream === 'undefined') {
  global.WritableStream = class WritableStream {};
}

if (typeof global.TransformStream === 'undefined') {
  global.TransformStream = class TransformStream {};
}

// Mock window.matchMedia
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: jest.fn().mockImplementation(query => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: jest.fn(),
    removeListener: jest.fn(),
    addEventListener: jest.fn(),
    removeEventListener: jest.fn(),
    dispatchEvent: jest.fn(),
  })),
});

// Mock IntersectionObserver
global.IntersectionObserver = class IntersectionObserver {
  constructor() {}
  observe() { return null; }
  disconnect() { return null; }
  unobserve() { return null; }
};

// Mock ResizeObserver
global.ResizeObserver = class ResizeObserver {
  constructor() {}
  observe() { return null; }
  disconnect() { return null; }
  unobserve() { return null; }
};

// Mock requestAnimationFrame and cancelAnimationFrame
global.requestAnimationFrame = function(callback) {
  return setTimeout(callback, 0);
};
global.cancelAnimationFrame = function(id) {
  clearTimeout(id);
};

// Mock window.getComputedStyle
Object.defineProperty(window, 'getComputedStyle', {
  value: jest.fn(() => ({
    getPropertyValue: jest.fn(() => ''),
    setProperty: jest.fn(),
    removeProperty: jest.fn(),
  })),
});

// Mock document.elementFromPoint
document.elementFromPoint = jest.fn(() => null);

// Mock window.scrollTo
window.scrollTo = jest.fn();

// Mock window.scroll
Object.defineProperty(window, 'scroll', {
  writable: true,
  value: jest.fn(),
});

// Mock navigator.clipboard
Object.defineProperty(navigator, 'clipboard', {
  value: {
    writeText: jest.fn(() => Promise.resolve()),
    readText: jest.fn(() => Promise.resolve('')),
  },
});

// Mock localStorage and sessionStorage
const createMockStorage = () => ({
  getItem: jest.fn(() => null),
  setItem: jest.fn(),
  removeItem: jest.fn(),
  clear: jest.fn(),
  key: jest.fn(() => null),
  length: 0,
});

Object.defineProperty(window, 'localStorage', {
  value: createMockStorage(),
});

Object.defineProperty(window, 'sessionStorage', {
  value: createMockStorage(),
});

// Suppress specific React warnings in tests
const originalConsoleError = console.error;
console.error = (...args) => {
  if (
    typeof args[0] === 'string' &&
    args[0].includes('Warning: ReactDOM.render is no longer supported')
  ) {
    return;
  }
  originalConsoleError(...args);
};

// Suppress specific React warnings in tests
const originalConsoleWarn = console.warn;
console.warn = (...args) => {
  if (
    typeof args[0] === 'string' &&
    (args[0].includes('Warning:') || args[0].includes('was not wrapped in act'))
  ) {
    return;
  }
  originalConsoleWarn(...args);
};
