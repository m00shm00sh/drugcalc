import { z } from 'zod'
import {
    displayToIso8601,
    displayToNumber,
    iso8601ToDisplay,
    zodDisplayDurationStringSchema,
    zodIsoDurationStringSchema,
} from './duration'
import { fetchFrequencyDetailsOrNull } from '../util/fetcher'
import memoize from '../util/memoize'
import { fieldsToMap, mapToFields } from '../util/reshaper'
import type { Nullable } from '../util/util'
import { zodNonemptyStringSchema } from './string'
import { SelectableSchema } from './Selectable'

export const FrequencyEntrySchema = z.object({
    values: z.array(zodIsoDurationStringSchema),
})
export type FrequencyEntry = z.infer<typeof FrequencyEntrySchema>

export const FrequenciesMapSchema = z.record(
    zodNonemptyStringSchema('invalid frequency name'),
    FrequencyEntrySchema,
)
export type FrequenciesMap = z.infer<typeof FrequenciesMapSchema>

const FrequencyEditorComponentItemSchema = z
    .object({
        value: zodDisplayDurationStringSchema,
    })
    .extend(SelectableSchema.shape)
export type FrequencyEditorComponentItem = z.infer<
    typeof FrequencyEditorComponentItemSchema
>

export const frequencyEditorComponentItemInit = () =>
    ({
        value: '',
    }) as FrequencyEditorComponentItem

const FrequencyEditorRowSchema = z
    .object({
        frequency: zodNonemptyStringSchema('invalid frequency name'),
        values: z.array(FrequencyEditorComponentItemSchema),
    })
    .extend(SelectableSchema.shape)

export type FrequencyEditorRow = z.infer<typeof FrequencyEditorRowSchema>

export const frequencyEditorRowInit = () =>
    ({
        frequency: '',
        values: [frequencyEditorComponentItemInit()],
    }) as FrequencyEditorRow

export const FrequencyEditorDataContainerSchema = z.object({
    frequencies: z.array(FrequencyEditorRowSchema).superRefine((data, ctx) => {
        const seen: string[] = []
        for (const [i, d] of data.entries()) {
            if (seen.includes(d.frequency)) {
                ctx.addIssue({
                    code: 'custom',
                    path: [i, 'frequency'],
                    message: 'Frequency respecified',
                })
            } else seen.push(d.frequency)
        }
    }),
})

export type FrequencyEditorDataContainer = z.infer<
    typeof FrequencyEditorDataContainerSchema
>

const componentFieldsToEntry = (
    fields: FrequencyEditorComponentItem[],
): FrequencyEntry => ({ values: fields.map((e) => displayToIso8601(e.value)) })

export const frequencyEditorFieldsToMap = (
    fields: FrequencyEditorRow[],
): FrequenciesMap =>
    fieldsToMap(
        fields,
        (r) => r.frequency,
        (r) => componentFieldsToEntry(r.values),
    )

const entryToComponentFields = (
    entry: FrequencyEntry,
): FrequencyEditorComponentItem[] =>
    entry.values.map((e) => ({ value: iso8601ToDisplay(e) }))

export const frequencyMapToEditorFields = (
    map: FrequenciesMap,
): FrequencyEditorRow[] =>
    mapToFields(
        map,
        (k) => ({ frequency: k }) as Partial<FrequencyEditorRow>,
        (v, o) => {
            o.values = entryToComponentFields(v)
            return o as FrequencyEditorRow
        },
    )

const fetchDetails = memoize(
    async (f: string) => await fetchFrequencyDetailsOrNull(f),
    (s) => s,
    60000,
)

export const loadFrequencyDetailsFromRemote = async (
    row: FrequencyEditorRow,
): Promise<Nullable<FrequencyEditorRow>> =>
    fetchDetails(row.frequency).then(
        (f?: FrequencyEntry): Nullable<FrequencyEditorRow> => {
            if (!f) return undefined
            const o: FrequencyEditorRow = {
                ...row,
                values: entryToComponentFields(f),
            }
            return o
        },
    )

export const FrequencyNamesWithWeightsSchema = z.record(
    zodNonemptyStringSchema('invalid frequency name'),
    zodIsoDurationStringSchema,
)
export type FrequencyNamesWithWeights = z.infer<
    typeof FrequencyNamesWithWeightsSchema
>

export const sortedFrequenciesWithWeights = (m: FrequenciesMap) =>
    Object.fromEntries(
        Object.entries(m)
            .map(
                ([k, v]) =>
                    [
                        k,
                        v.values
                            .map((e) => displayToNumber(e))
                            .reduce((a, x) => a + x),
                    ] as [string, number],
            )
            .sort((a, b) => a[1] - b[1]),
    )
