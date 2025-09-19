import { z } from 'zod'
import type { CompoundName } from './Compounds'
import {
    compoundNameOf,
    packCompoundName,
    unpackCompoundName,
} from './Compounds'
import { fieldsToMap, mapToFields } from '../util/reshaper'
import { fetchBlendDetailsOrNull } from '../util/fetcher'
import { setIf } from '../util/filter-setif'
import memoize from '../util/memoize'
import type { Nullable } from '../util/util'
import { zodNonemptyStringSchema } from './string'
import { SelectableSchema } from './Selectable'

export const BlendNamesListSchema = z.readonly(z.array(z.string()))
export type BlendNamesList = z.infer<typeof BlendNamesListSchema>

const BlendComponentsMapSchema = z.record(z.string(), z.number().gt(0))
type BlendComponentsMap = z.infer<typeof BlendComponentsMapSchema>

export const BlendEntrySchema = z.object({
    note: z.string().optional(),
    components: BlendComponentsMapSchema,
})
export type BlendEntry = z.infer<typeof BlendEntrySchema>

export const BlendsMapSchema = z.record(
    zodNonemptyStringSchema('invalid blend name'),
    BlendEntrySchema,
)
export type BlendsMap = z.infer<typeof BlendsMapSchema>

const BlendEditorComponentRowSchema = z
    .object({
        compound: z.string(),
        variant: z.string().optional(),
        dose: z.number().gt(0, 'dose too low'),
    })
    .extend(SelectableSchema.shape)

export type BlendEditorComponentRow = z.infer<
    typeof BlendEditorComponentRowSchema
>

export const blendComponentRowInit = () =>
    ({
        compound: '',
        dose: 0,
    }) as BlendEditorComponentRow

const BlendEditorRowSchema = z
    .object({
        blend: zodNonemptyStringSchema('invalid blend name'),
        note: z.string().optional(),
        components: z
            .array(BlendEditorComponentRowSchema)
            .superRefine((data, ctx) => {
                const seen: string[] = []
                for (const [i, d] of data.entries()) {
                    const cn = packCompoundName([
                        d.compound,
                        d.variant,
                    ] as CompoundName)
                    if (seen.includes(cn)) {
                        ctx.addIssue({
                            code: 'custom',
                            path: [i, 'compound'],
                            message: 'Compound respecified',
                        })
                        ctx.addIssue({
                            code: 'custom',
                            path: [i, 'variant'],
                            message: ' ',
                        })
                    } else seen.push(cn)
                }
                if (seen.length < 2) {
                    ctx.addIssue({
                        code: 'custom',
                        message: 'not enough distinct components',
                    })
                }
            }),
    })
    .extend(SelectableSchema.shape)

export type BlendEditorRow = z.infer<typeof BlendEditorRowSchema>

export const blendRowInit = () =>
    ({
        blend: '',
        components: [blendComponentRowInit()],
    }) as BlendEditorRow

export const BlendEditorDataContainerSchema = z.object({
    blends: z.array(BlendEditorRowSchema).superRefine((data, ctx) => {
        const seen: string[] = []
        for (const [i, d] of data.entries()) {
            if (seen.includes(d.blend)) {
                ctx.addIssue({
                    code: 'custom',
                    path: [i, 'blend'],
                    message: 'Blend respecified',
                })
            } else seen.push(d.blend)
        }
    }),
})

export type BlendEditorDataContainer = z.infer<
    typeof BlendEditorDataContainerSchema
>

const componentFieldsToMap = (
    fields: BlendEditorComponentRow[],
): BlendComponentsMap =>
    fieldsToMap(
        fields,
        (r) => packCompoundName(compoundNameOf(r)),
        (r) => r.dose,
    )

export const blendEditorFieldsToMap = (fields: BlendEditorRow[]): BlendsMap =>
    fieldsToMap(
        fields,
        (r) => r.blend,
        (r) => {
            const e: BlendEntry = {
                components: componentFieldsToMap(r.components),
            }
            setIf(e, 'note', r.note)
            return e
        },
    )

export const componentMapToFields = (
    map: BlendComponentsMap,
): BlendEditorComponentRow[] =>
    mapToFields(
        map,
        (k) => {
            const o: Partial<BlendEditorComponentRow> = {}
            const [compound, variant] = unpackCompoundName(k)
            o.compound = compound
            setIf(o, 'variant', variant)
            return o
        },
        (v, o) => {
            o.dose = v
            return o as BlendEditorComponentRow
        },
    )

export const blendMapToEditorFields = (map: BlendsMap): BlendEditorRow[] =>
    mapToFields(
        map,
        (k) => ({ blend: k }) as Partial<BlendEditorRow>,
        (v, o) => {
            setIf(o, 'note', v.note)
            o.components = componentMapToFields(v.components)
            return o as BlendEditorRow
        },
    )

const fetchDetails = memoize(
    async (b: string) => await fetchBlendDetailsOrNull(b),
    (s) => s,
    60000,
)

export const loadBlendDetailsFromRemote = async (
    row: BlendEditorRow,
): Promise<Nullable<BlendEditorRow>> =>
    fetchDetails(row.blend).then((b?: BlendEntry): Nullable<BlendEditorRow> => {
        if (!b) return undefined
        const o: BlendEditorRow = { ...row }
        setIf(o, 'note', b.note)
        o.components = componentMapToFields(b.components)
        return o
    })
