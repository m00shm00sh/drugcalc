import { z } from 'zod'
import {
    displayToIso8601,
    iso8601ToDisplay,
    zodDisplayDurationStringSchema,
    zodIsoDurationStringSchema,
} from './duration'
import { SelectableSchema } from './Selectable'

export const ConfigSchema = z.object({
    tickDuration: zodIsoDurationStringSchema.optional(),
    cutoffMilligrams: z.number().gt(0).optional(),
    doLambdaDoseCorrection: z.boolean().optional(),
})
export type Config = z.infer<typeof ConfigSchema>

const zConfigItem = (name: string, value: z.core.SomeType) =>
    z.object({ type: z.literal(name), value: value }).extend(SelectableSchema.shape)

export const ConfigEditorSchema = z.discriminatedUnion('type', [
    zConfigItem('tickDuration', zodDisplayDurationStringSchema),
    zConfigItem('cutoffMilligrams', z.number().gt(0)),
    zConfigItem('doLambdaDoseCorrection', z.literal(['true', 'false'])),
])
export type ConfigEditorRow = z.infer<typeof ConfigEditorSchema> & {
    type: keyof Config
}

export const configEditorRowInit = (): ConfigEditorRow => ({
    type: 'tickDuration',
    value: configEditorFields.tickDuration.default,
})

export const isConfigItem = (s: string): s is keyof Config =>
    ['tickDuration', 'cutoffMilligrams', 'doLambdaDoseCorrection'].indexOf(s) >= 0

export const ConfigEditorContainerSchema = z.object({
    config: z.array(ConfigEditorSchema).superRefine((data, ctx) => {
        const seen: (keyof Config)[] = []
        for (const [i, { type }] of data.entries()) {
            if (!isConfigItem(type)) throw Error('failed test for config key')
            const inter = seen.filterIntersecting([type])
            if (inter.length > 0) {
                ctx.addIssue({
                    code: 'custom',
                    path: [i, 'type'],
                    message: 'duplicate key',
                })
            }
            seen.push(type)
        }
    }),
})

export type ConfigEditorContainer = z.infer<typeof ConfigEditorContainerSchema>

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

const transformConfigItemFromField = (k: keyof Config, v: unknown): unknown => {
    switch (k) {
        case 'doLambdaDoseCorrection':
            if (v === undefined) return undefined
            return Boolean(['false', 'true'].indexOf(v as string))
        case 'tickDuration':
            return displayToIso8601(v as string)
    }
    return v
}

const transformConfigItemToField = (k: keyof Config, v: Config[keyof Config]): unknown => {
    switch (k) {
        case 'doLambdaDoseCorrection':
            return ['false', 'true'][Number(v)]
        case 'tickDuration':
            return iso8601ToDisplay(v as string)
    }
    return v as string | number
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
