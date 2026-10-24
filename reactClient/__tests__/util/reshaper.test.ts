import { describe, expect, it } from '@jest/globals'
import { fieldsToMap, mapToFields } from '../../src/util/reshaper'

const theMap: Record<string, Record<string, number>> = {
    a: { b: 1},
    b: { b: 2}
}

type Field = {
    a: string
    b: number
}

const theFields: readonly Field[] = [
    { a: 'a', b: 1 },
    { a: 'b', b: 2 }
]

describe('fieldsToMap', () => {
    it('should convert', () => {
        const m = fieldsToMap(
            theFields,
            (row) => row.a,
            (row) => ({b: row.b})
        )
        expect(m).toStrictEqual(theMap)
    })
    it('should use same incoming ref for key and value', () => {
        let ref: Record<string, string|number>|undefined
        fieldsToMap(
            theFields,
            (row) => { ref = row; return row.a},
            (row) => {
                if (ref !== row)
                    throw Error('unexpected ref')
                return {b: row.b}
            }
        )
    })
})

describe('mapToFields', () => {
    it('should convert', () => {
        const f = mapToFields(
            theMap,
            (k): Partial<Field> => ({ a: k }),
            (v, a) => ({...a, b: v.b} as Field)
        )
        expect(f).toStrictEqual(theFields)
    })
    it('should use same incoming ref for key and value', () => {
        let ref: Partial<Field>
        mapToFields(
            theMap,
            (k): Partial<Field> => {
                ref = { a: k }
                return ref
            },
            (v, a) => {
                if (a !== ref)
                    throw Error('unexpected ref')
                return {...a, b: v.b} as Field
            }
        )
    })
})