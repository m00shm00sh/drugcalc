import { jest } from '@jest/globals'

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


import mockFetch from 'jest-fetch-mock'
mockFetch.enableMocks()

// TODO: remove this once we implement logging of errors inside useAsyncResult
process.on("unhandledRejection",
    () => {

    }
)