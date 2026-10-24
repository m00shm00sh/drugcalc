import { describe, expect, it } from '@jest/globals'
import '../../src/util/filter-setif'
import { fieldsFilterer, getOrElse, setIf, stripIf } from '../../src/util/filter-setif'

describe('Array.filterDistinct', () => {
    // we only care that it behaves as expected for numbers and strings because
    // === under the hood (Array.indexOf) makes objects and arrays cautious to deal with
    describe('should not emit duplicates', () => {
        it('for numbers', () => {
            const input = [1, 2, 1, 2, 3, 4, 6, 5, 1]
            const exp = [1, 2, 3, 4, 6, 5]
            const got = input.filterDistinct()
            expect(got).toEqual(exp)
        })
        it('for strings', () => {
            const input = ['a', 'bb', 'bb', 'a', 'ddd', 'c', 'ee']
            const exp = ['a', 'bb', 'ddd', 'c', 'ee']
            const got = input.filterDistinct()
            expect(got).toEqual(exp)
        })
    })
})

describe('Array.filterIntersecting', () => {
    // we only care that it behaves as expected for numbers and strings because
    // === under the hood (Array.indexOf) makes objects and arrays cautious to deal with
    describe('should emit intersections including duplicates', () => {
        it('for numbers', () => {
            const a = [1, 2, 3, 4, 5, 4]
            const b = [2, 4, 5]
            const exp = [2, 4, 5, 4]
            const got = a.filterIntersecting(b)
            expect(got).toEqual(exp)
        })
        it('for strings', () => {
            const a = ['a', 'bb', 'bb', 'a', 'ddd', 'c', 'ee']
            const b = ['a', 'c', 'ddd']
            const exp = ['a', 'a', 'ddd', 'c']
            const got = a.filterIntersecting(b)
            expect(got).toEqual(exp)
        })
    })
})

describe('filterFields', () => {
    const input = {
        'a': 1,
        'b': 2
    }
    const filterer = fieldsFilterer(['a'])
    const got = filterer(input)
    it('should include specified fields', () => {
        expect(Object.keys(got)).toContain('a')
    })
    it('should exclude unspecied keys', () => {
        expect(Object.keys(got)).not.toContain('b')
    })
})

describe('setIf', () => {
    type T = {
        a?: object
        b?: string
        c?: number
        d?: number
    }
    describe('should not set', () => {
        it('for undefined', () => {
            const o: T = {}
            setIf(o, 'a', undefined)
            expect(Object.keys(o)).not.toContain('a')
        })
        it('for empty string', () => {
            const o: T = {}
            setIf(o, 'b', '')
            expect(Object.keys(o)).not.toContain('b')
        })
        it('for 0', () => {
            const o: T = {}
            setIf(o, 'c', 0)
            expect(Object.keys(o)).not.toContain('c')
        })
        it('for NaN', () => {
            const o: T = {}
            setIf(o, 'd', NaN)
            expect(Object.keys(o)).not.toContain('d')
        })
    })
    describe('should set', () => {
        it('for usable string', () =>{
            const o: T = {}
            setIf(o, 'b', 'a')
            expect(o.b).toBe('a')
        })
        it('for usable number', () =>{
            const o: T = {}
            setIf(o, 'c', 1)
            expect(o.c).toBe(1)
        })
    })
})

describe('stripIf', () => {
    describe('should strip', () => {
        it('for contains check', () => {
            const o = { a: 1, b: 2 }
            stripIf('a' in o, o, 'a')
            expect(Object.keys(o)).not.toContain('a')
        })
        it('for equals check', () => {
            const o = { a: 1, b: 2 }
            stripIf(o.b === 2, o, 'b')
            expect(Object.keys(o)).not.toContain('b')
        })
    })
    describe('should not strip', () => {
        it('for not contains check', () => {
            const o = { a: 1, b: 2 }
            stripIf(!('a' in o), o, 'a')
            expect(Object.keys(o)).toContain('a')
        })
        it('for not equals check', () => {
            const o = { a: 1, b: 2 }
            stripIf(o.b !== 2, o, 'b')
            expect(Object.keys(o)).toContain('b')
        })

    })
})
describe('getOrelse', () => {
    it('handles missing with replacement value', () => {
        const v = getOrElse({}, 'a', 1)
        expect(v).toBe(1)
    })
    it('handles undef with replacement value', () => {
        const v = getOrElse({'a': undefined}, 'a', 1)
        expect(v).toBe(1)
    })
    it('handles other falsy with replacement value', () => {
        const v = getOrElse({a: ''}, 'a', 'b')
        expect(v).toBe('b')
    })
    it('handles usable value without replacement', () => {
        const v = getOrElse({a: 1}, 'a', 2)
        expect(v).toBe(1)
    })
    it('handles with replacement supplier', () => {
        const v = getOrElse({}, 'a', () => (1))
        expect(v).toBe(1)
    })
})
