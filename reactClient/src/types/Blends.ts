import { z } from 'zod'
import { packCompoundName } from './Compounds'
import type { CompoundName } from './Compounds'

export type BlendComponentsMap = Record<string, number>;

export type BlendEntry = {
    note?: string;
    components: BlendComponentsMap;
};

export type BlendsMap = Record<string, BlendEntry>;

export type BlendEditorComponentRow = {
    compound: string;
    variant?: string;
    dose: number;
};

export const blendComponentRowInit = () =>
    ({
        compound: '',
        dose: 0,
    }) as BlendEditorComponentRow

const BlendEditorComponentRowSchema = z.object({
    compound: z.string(),
    variant: z.string().optional(),
    dose: z.number().gt(0, 'dose too low'),
})

export type BlendEditorRow = {
    blend: string;
    note?: string;
    components: BlendEditorComponentRow[];
};

export const blendRowInit = () =>
    ({
        blend: '',
        components: [blendComponentRowInit()],
    }) as BlendEditorRow

const BlendEditorRowSchema = z.object({
    blend: z.string().nonempty('blend name cannot be empty'),
    note: z.string().optional(),
    components: z
        .array(BlendEditorComponentRowSchema)
        .superRefine((data, ctx) => {
            const seen: string[] = []
            for (const [i, d] of data.entries()) {
                const cn = packCompoundName([d.compound, d.variant] as CompoundName)
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

export type BlendEditorDataContainer = {
    blends: BlendEditorRow[];
};

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
