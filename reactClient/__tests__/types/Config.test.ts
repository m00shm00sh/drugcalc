import { describe, it } from '@jest/globals'
import { expectParseFail, expectParseOk } from './__parse_okfail'

import {
    ConfigEditorContainerSchema,
    configEditorFields,
    configEditorFieldsToConfig,
    ConfigEditorSchema,
    ConfigSchema,
    configToEditorFields
} from '../../src/types/Config'
import type {
    Config,
    ConfigEditorContainer,
    ConfigEditorRow
} from '../../src/types/Config'

describe('ConfigObject', () => {
    it('should accept empty object',
        expectParseOk(ConfigSchema, {})
    )
    const ok = (k: keyof Config, v: unknown) => expectParseOk(ConfigSchema, { [k]: v})()
    const f = (k: keyof Config, v: unknown) => expectParseFail(ConfigSchema, { [k]: v})()
    // the object has server schema so use iso not display duration
    describe('for tickDuration', () => {
        it('should accept valid iso duration', () => ok('tickDuration', 'PT1H1M1S'))
        it('should reject unexpected or missing duration', () => {
            f('tickDuration', '1d1m')
            f('tickDuration', '')
            f('tickDuration', 1)
        })
    })
    describe('for cutoffMilligrams', () => {
        it('should accept positive value', () => ok('cutoffMilligrams', 0.1))
        it('should reject unexpected value or type', () => {
            f('cutoffMilligrams', 0)
            f('cutoffMilligrams', '1')
        })
    })
    describe('for doLambdaDoseCorrection', () => {
        it('should accept booly', () => ok('doLambdaDoseCorrection', true))
        it('should reject string', () => f('doLambdaDoseCorrection', ''))
    })
})

describe('ConfigRowItem', () => {
    // NOTE: do not test that the new item is invalid because the default value is server default

    const ok = (t: keyof Config, v: unknown) => expectParseOk(ConfigEditorSchema,
        { type: t, value: v} as unknown as object)() // i hate how hacky this is
    const f = (t: keyof Config, v: unknown) => expectParseFail(ConfigEditorSchema, { type: t, value: v})()
    // the object has server schema so use iso not display duration
    describe('for tickDuration', () => {
        it('should accept valid display duration', () => ok('tickDuration', '10d21h33m45s'))
        it('should reject empty display duration', () => f('tickDuration', ''))
        it('should reject iso duration', () => f('tickDuration', 'PT1H23M45S'))
        it('should reject number', () => f('tickDuration', 1))
    })
    describe('for cutoffMilligrams', () => {
        it('should accept positive value', () => ok('cutoffMilligrams', 1))
        it('should reject nonpositive value', () => f('cutoffMilligrams', 0))
        it('should reject string', () => f('cutoffMilligrams', '1'))
    })
    describe('for doLambdaDoseCorrection', () => {
        it('should accept booly string', () => ok('doLambdaDoseCorrection', 'true'))
        it('should reject empty string', () => f('doLambdaDoseCorrection', ''))
        it('should reject non-string', () => f('doLambdaDoseCorrection', false))
    })
})
describe('ConfigRowsContainer', () => {
    it('should accept all config keys',
        expectParseOk(ConfigEditorContainerSchema, <ConfigEditorContainer>{config: [
            // TODO: is there a way to exhaustively generate this?
            {type: 'tickDuration', value: '1d'},
            {type: 'cutoffMilligrams', value: 1},
            {type: 'doLambdaDoseCorrection', value: 'false'}
        ]})
    )
    describe('reject duplicate keys',
        expectParseFail(ConfigEditorContainerSchema, {config: [
            {type: 'tickDuration', value: '1d'},
            {type: 'tickDuration', value: '2d'},
        ]})
    )
    describe('reject unknown keys',
        expectParseFail(ConfigEditorContainerSchema, {config: [
            {type: 'aaaaaaaa', value: ''},
        ]})
    )
})
describe('FormRows', () => {
    it('tickDuration produces input&&text', () => {
        const fields = configEditorFields.tickDuration as unknown as Record<string, string>
        expect(fields.type).toBe('input')
        expect(fields.valType).toBe('text')
    })
    it('cutoffMilligrams produces input&&num&&min>0', () => {
        const fields = configEditorFields.cutoffMilligrams as unknown as Record<string, string>
        expect(fields.type).toBe('input')
        expect(fields.valType).toBe('number')
        expect(fields.min).toBeGreaterThan(0)
    })
    it('doLambdaDoseCorrection produces select', () => {
        const fields = configEditorFields.doLambdaDoseCorrection as unknown as Record<string, string>
        expect(fields.type).toBe('select')
    })
})
describe('converter', () => {
    const m: Config = {
        tickDuration: 'PT25H11M11S',
        cutoffMilligrams: 0.1,
        doLambdaDoseCorrection: false
    }
    const r: ConfigEditorRow[] = [
        {type: 'tickDuration', value: '1d1h11m11s'},
        {type: 'cutoffMilligrams', value: 0.1},
        {type: 'doLambdaDoseCorrection', value: 'false'}
    ]
    const m2: Config = {
        doLambdaDoseCorrection: undefined
    }
    const r2: ConfigEditorRow[] = [
        {type: 'doLambdaDoseCorrection', value: undefined}
    ]
    const mBad = {...m,
        aaaa: 1
    }
    const rBad = [...r,
        {type: 'tickDuration', value: '111d'}
    ] as unknown as ConfigEditorRow[]
    const rBad2 = [...r,
        {type: 'aaaa', value: ''}
    ] as unknown as ConfigEditorRow[]
    it('should convert map to rows', () =>
        expect(configToEditorFields(m)).toEqual(r)
    )
    it('should convert rows to map', () =>
        expect(configEditorFieldsToConfig(r)).toEqual(m)
    )
    it('should handle explicit undefined doLambdaDoseCorrection', () =>
        expect(configEditorFieldsToConfig(r2)).toEqual(m2)
    )
    it('should reject unexpected fields of map', () =>
        expect(() => configToEditorFields(mBad)).toThrow()
    )
    it('should reject duplicate types in rows', () =>
        expect(() => configEditorFieldsToConfig(rBad)).toThrow()
    )
    it('should reject unexpected types in rows', () =>
        expect(() => configEditorFieldsToConfig(rBad2)).toThrow()
    )
})
/*

type ConfigEditorField =
    | {
        type: 'input'
        valType: 'text'
        minLength?: number
        maxLength?: number
        default: string
    }
    | {
        type: 'input'
        valType: 'number'
        min?: number
        max?: number
        step?: number
        default: number
    }
    | {
        type: 'select'
        options: readonly string[]
        default: string
    }

export const configEditorFields: Record<keyof Config, ConfigEditorField> = {
    tickDuration: {
        type: 'input',
        valType: 'text',
        minLength: 1,
        default: '1h30m',
    },
    cutoffMilligrams: {
        type: 'input',
        valType: 'number',
        min: 0.0001,
        step: 0.0001,
        default: 0,
    },
    doLambdaDoseCorrection: {
        type: 'select',
        options: ['true', 'false'],
        default: 'false',
    },
}

export const configEditorFieldsToConfig = (fields: ConfigEditorRow[]): Config => {
    const seen: (keyof Config)[] = []
    for (const [i, { type }] of fields.entries()) {
        if (!isConfigItem(type)) throw Error('failed test for config key')
        const inter = seen.filterIntersecting([type])
        if (inter.length > 0) throw Error(`overlap detected for ${i}: ${type}`)
        seen.push(type)
    }
    return Object.fromEntries(
        fields.map(({ type, value }) => [type, transformConfigItemFromField(type, value)]),
    )
}

export const configToEditorFields = (c: Config): ConfigEditorRow[] => {
    const rows: ConfigEditorRow[] = []
    for (const [k, v] of Object.entries(c)) {
        if (!isConfigItem(k)) throw Error('failed test for config key')
        rows.push({ type: k, value: transformConfigItemToField(k, v) })
    }
    return rows
}

*/