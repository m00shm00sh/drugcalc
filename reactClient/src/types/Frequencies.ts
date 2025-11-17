import { z } from 'zod'
import {
    displayToIso8601,
    displayToNumber,
    iso8601ToDisplay,
    zodDisplayDurationStringSchema,
    zodIsoDurationStringSchema,
} from './duration'
import { fetchFrequencyDetailsOrNull } from '../util/data-fetcher'
import cache from '../util/cache'
import { fieldsToMap, mapToFields } from '../util/reshaper'
import type { Nullable } from '../util/util'
import { zodNonemptyStringSchema } from './string'
import { SelectableSchema } from './Selectable'
import { zodSuperRefinerForUniqueArray } from './uniqueArray'

export const FrequencyEntrySchema = z.object({
    values: z.array(zodIsoDurationStringSchema),
})
export type FrequencyEntry = z.infer<typeof FrequencyEntrySchema>

export const FrequenciesMapSchema = z.record(
    zodNonemptyStringSchema('invalid frequency name'),
    FrequencyEntrySchema,
)
export type FrequenciesMap = z.infer<typeof FrequenciesMapSchema>

export const FrequencyEditorComponentItemSchema = z
    .object({
        value: zodDisplayDurationStringSchema,
    })
    .extend(SelectableSchema.shape)
export type FrequencyEditorComponentItem = z.infer<typeof FrequencyEditorComponentItemSchema>

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
    frequencies: z.array(FrequencyEditorRowSchema).superRefine(
        zodSuperRefinerForUniqueArray(
            (r: FrequencyEditorRow) => r.frequency,
            () => [['frequency', 'Frequency respecified']],
        ),
    ),
})

export type FrequencyEditorDataContainer = z.infer<typeof FrequencyEditorDataContainerSchema>

const componentFieldsToEntry = (fields: FrequencyEditorComponentItem[]): FrequencyEntry => ({
    values: fields.map((e) => displayToIso8601(e.value)),
})

export const frequencyEditorFieldsToMap = (fields: FrequencyEditorRow[]): FrequenciesMap =>
    fieldsToMap(
        fields,
        (r) => r.frequency,
        (r) => componentFieldsToEntry(r.values),
    )

const entryToComponentFields = (entry: FrequencyEntry): FrequencyEditorComponentItem[] =>
    entry.values.map((e) => ({ value: iso8601ToDisplay(e) }))

export const frequencyMapToEditorFields = (map: FrequenciesMap): FrequencyEditorRow[] =>
    mapToFields(
        map,
        (k) => ({ frequency: k }) as Partial<FrequencyEditorRow>,
        (v, o) => {
            o.values = entryToComponentFields(v)
            return o as FrequencyEditorRow
        },
    )

const fetchDetails = cache(
    async (f: string) => await fetchFrequencyDetailsOrNull(f),
    (s) => s,
    60000,
)

export const loadFrequencyDetailsFromRemote = async (
    row: FrequencyEditorRow,
): Promise<Nullable<FrequencyEditorRow>> =>
    fetchDetails(row.frequency).then((f?: FrequencyEntry): Nullable<FrequencyEditorRow> => {
        if (!f) return undefined
        const o: FrequencyEditorRow = {
            ...row,
            values: entryToComponentFields(f),
        }
        return o
    })

export const FrequencyNamesWithWeightsSchema = z.record(
    zodNonemptyStringSchema('invalid frequency name'),
    zodIsoDurationStringSchema,
)
export type FrequencyNamesWithWeights = z.infer<typeof FrequencyNamesWithWeightsSchema>

export const sortedFrequenciesWithWeights = (m: FrequenciesMap) =>
    Object.fromEntries(
        Object.entries(m)
            .map(
                ([k, v]) =>
                    [k, v.values.map((e) => displayToNumber(e)).reduce((a, x) => a + x)] as
                    [ string, number, ],
            )
            .sort((a, b) => a[1] - b[1]),
    )

export const FrequencyDeleterRowSchema = z.object({
    frequency: zodNonemptyStringSchema()
})
    .extend(SelectableSchema.shape)

export type FrequencyDeleterRow = z.infer<typeof FrequencyDeleterRowSchema>

export const frequencyDeleterRowInit = (): FrequencyDeleterRow => ({
    frequency: '',
})

export const FrequencyDeleterDataContainerSchema = z.object({
    frequencies: z.array(FrequencyDeleterRowSchema)
        .superRefine(zodSuperRefinerForUniqueArray(
            (b: FrequencyDeleterRow) => b.frequency,
            () => [['frequency', 'Frequency respecified']],
        ))
})
export type FrequencyDeleterDataContainer = z.infer<typeof FrequencyDeleterDataContainerSchema>
