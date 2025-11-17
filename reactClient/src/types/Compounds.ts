import { z } from 'zod'
import { fetchCompoundDetailsOrNull } from '../util/data-fetcher'
import { setIf } from '../util/filter-setif'
import cache from '../util/cache'
import { fieldsToMap, mapToFields } from '../util/reshaper'
import type { Nullable } from '../util/util'
import { requireNotNull } from '../util/util'
import {
    displayToIso8601,
    iso8601ToDisplay,
    zodDisplayDurationStringSchema,
    zodIsoDurationStringSchema,
} from './duration'
import { SelectableSchema, zodOptBool } from './Selectable'
import { AvailableVXsCacheSchema, zodNonemptyStringSchema } from './string'
import { zodSuperRefinerForUniqueArray } from './uniqueArray'

export const CompoundNamesListSchema = z.readonly(z.array(z.string().regex(/^([^=]+)(?:=(.*))?$/)))
export type CompoundNamesList = z.infer<typeof CompoundNamesListSchema>

export const VariantNamesListSchema = z.readonly(z.array(z.string()))
export type VariantNamesList = z.infer<typeof VariantNamesListSchema>

export const CompoundInfoSchema = z.object({
    halfLife: zodIsoDurationStringSchema,
    pctActive: z.number().gt(0).lte(1).optional(),
    note: z.string().optional(),
})

export type CompoundInfo = z.infer<typeof CompoundInfoSchema>

export const CompoundsMapSchema = z.record(
    zodNonemptyStringSchema('invalid compound name'),
    CompoundInfoSchema,
)
export type CompoundsMap = z.infer<typeof CompoundsMapSchema>

export type CompoundName = [string] | [string, string]

export const unpackCompoundName = (cn: string): CompoundName => {
    const [, b, v] = requireNotNull(cn.match(/^([^=]+)(?:=(.*))?$/), 'regex failed')
    requireNotNull(b, `bad compound name: ${cn}`)
    if (v) return [b, v]
    return [b]
}

export const packCompoundName = (cn: CompoundName): string => {
    const [b, v] = cn
    let s: string = b
    if (v ?? '') s += `=${v}`
    return s
}

export type ByCompoundByVariant = Record<string, string[]>

export const reshapeCompoundKeys = (m: CompoundsMap): ByCompoundByVariant => {
    const builder: ByCompoundByVariant = {}
    for (const k of Object.keys(m)) {
        const [b, v] = unpackCompoundName(k)
        let ov = builder[b]
        if (ov === undefined) {
            ov = []
            builder[b] = ov
        }
        if (v !== undefined) ov.push(v)
    }
    return builder
}

export const COMPOUND_BASE_PATTERN = '[^.](?!.*=).*'

const CompoundNameEntrySchema = z.object({
    compound: z.string().regex(RegExp(`^${COMPOUND_BASE_PATTERN}$`), 'invalid compound name'),
    variant: z.string().optional(),
})

type CompoundNameEntry = z.infer<typeof CompoundNameEntrySchema>

export const CompoundEditorRowSchema = CompoundNameEntrySchema.extend({
    halfLife: zodDisplayDurationStringSchema,
    // react-hook-form coerces undefined to Nan when valueAsNumber is active on an input
    pctActive: z
        .number('NaN')
        .gt(0, 'invalid percent')
        .lte(100, 'invalid percent')
        .or(z.nan()),
    note: z.string().optional(),
}).extend(SelectableSchema.shape)

export type CompoundEditorRow = z.infer<typeof CompoundEditorRowSchema>

export const compoundEditorRowInit = () =>
    ({
        compound: '',
        halfLife: '',
        pctActive: 100
    }) as CompoundEditorRow

export const uniqueCompoundNamesRefiner = (post: (names: readonly string[]) => string = () => '') =>
    zodSuperRefinerForUniqueArray(
        (e: CompoundNameEntry) => packCompoundName([e.compound, e.variant] as CompoundName),
        (e: CompoundNameEntry) => {
            const a: [string, string][] = []
            a.push(['compound', `Compound ${e.variant ? '...' : 'respecified'}`])
            if (e.variant) a.push(['variant', '... respecified'])
            return a
        },
        post,
    )

export const CompoundEditorDataContainerSchema = z.object({
    compounds: z.array(CompoundEditorRowSchema).superRefine(uniqueCompoundNamesRefiner()),
})

export type CompoundEditorDataContainer = z.infer<typeof CompoundEditorDataContainerSchema>

export const compoundNameOf = (r: CompoundNameEntry): CompoundName =>
    r.variant ? [r.compound, r.variant] : [r.compound]

export const compoundEditorFieldsToMap = (fields: CompoundEditorRow[]): CompoundsMap =>
    fieldsToMap(
        fields,
        (r) => packCompoundName(compoundNameOf(r)),
        (r) => {
            const v: CompoundInfo = { halfLife: displayToIso8601(r.halfLife) }
            // react-hook-form coerces undefined to Nan when valueAsNumber is active on an input
            if (r.pctActive && !Number.isNaN(r.pctActive) && r.pctActive < 100)
                v.pctActive = r.pctActive / 100
            setIf(v, 'note', r.note)
            return v
        },
    )

export const compoundMapToEditorFields = (map: CompoundsMap): CompoundEditorRow[] =>
    mapToFields(
        map,
        (k) => {
            const o: Partial<CompoundEditorRow> = {}
            const [compound, variant] = unpackCompoundName(k)
            o.compound = compound
            setIf(o, 'variant', variant)
            return o
        },
        (v, o) => {
            o.halfLife = iso8601ToDisplay(v.halfLife)
            o.pctActive = (v.pctActive ?? 1) * 100
            setIf(o, 'note', v.note)
            return o as CompoundEditorRow
        },
    )

const fetchDetails = cache(fetchCompoundDetailsOrNull, packCompoundName, 60000)

export const loadCompoundDetailsFromRemote = async (
    row: CompoundEditorRow,
): Promise<Nullable<CompoundEditorRow>> =>
    fetchDetails(compoundNameOf(row)).then((i?: CompoundInfo): Nullable<CompoundEditorRow> => {
        if (!i) return undefined
        const o: CompoundEditorRow = { ...row, ...i }
        o.halfLife = iso8601ToDisplay(o.halfLife)
        if (o.pctActive === undefined)
            throw Error('unexpected: fetchCompoundDetailsOrNull should\'ve set {}.pctActive')
        o.pctActive *= 100
        return o
    })

const CompoundDeleterExpandAllItemsSchema = z.object({ 'expand': zodOptBool })
export const CompoundDeleterRowSchema = CompoundNameEntrySchema
    .extend(SelectableSchema.shape)
    .extend(AvailableVXsCacheSchema.shape)
    .extend(CompoundDeleterExpandAllItemsSchema.shape)
    .refine((r) => !(r.expand && r.variant), {error: "do not specify both variant and expand"})


export type CompoundDeleterRow = z.infer<typeof CompoundDeleterRowSchema>

export const compoundDeleterRowInit = (): CompoundDeleterRow => ({
    compound: '',
})

export const CompoundDeleterDataContainerSchema = z.object({
    // this is a mess to superRefine because we have to match expanders
    compounds: z.array(CompoundDeleterRowSchema).superRefine(zodSuperRefinerForUniqueArray(
        (e: CompoundDeleterRow) => e,
        (e: CompoundDeleterRow) => {
            const a: [string, string][] = []
            a.push(['compound', `Compound ${e.variant ? '...' : 'respecified'}`])
            if (e.variant) a.push(['variant', '... respecified'])
            if (e.expand) a.push(['expand', 'conflict'])
            return a
        },
        () => '', // post = noop
        (seen: readonly CompoundDeleterRow[], cur: CompoundDeleterRow) => {
            const emptyToUndef = (s: Nullable<string>) => s ? s : undefined

            for (const cmp of seen) {
                if ((cur.expand || cmp.expand) && cur.compound === cmp.compound)
                    return true
                if (cur.compound === cmp.compound && emptyToUndef(cur.variant) === emptyToUndef(cmp.variant))
                    return true
            }
            return false
        }
    ))
})

export type CompoundDeleterDataContainer = z.infer<typeof CompoundDeleterDataContainerSchema>
