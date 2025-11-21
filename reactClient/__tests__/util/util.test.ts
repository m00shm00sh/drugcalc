import { describe, expect, it } from '@jest/globals'

import { requireNotNull, requireTrue } from '../../src/util/util'

describe('requireNotNull', () => {
    it('should throw on null', () => {
        expect(() => requireNotNull(null)).toThrow('null check failed')
    })
    it('should throw on undef', () => {
        expect(() => requireNotNull(undefined)).toThrow('null check failed')
    })
    it('should use custom message when available', () => {
        expect(() => requireNotNull(undefined, 'aabbcc')).toThrow('aabbcc')
    })
    it('should not throw on empty string', () => {
        expect(() => requireNotNull('')).not.toThrow()
    })
    it('should not throw on zero', () => {
        expect(() => requireNotNull(0)).not.toThrow()
    })
    it('should not throw on truthy', () => {
        expect(() => requireNotNull('a')).not.toThrow()
    })
})

describe('require', () => {
    it('should throw on false', () => {
        expect(() => requireTrue(false)).toThrow('requirement failed')
    })
    it('should use custom message when available', () => {
        expect(() => requireTrue(false, 'aabb')).toThrow('aabb')
    })
    it('should not throw on true', () => {
        expect(() => requireTrue(true)).not.toThrow()
    })
})