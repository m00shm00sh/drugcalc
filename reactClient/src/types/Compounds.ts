import { z } from 'zod'
import { zodDurationString } from './duration'
import quote from '../util/quote'
import { requireNotNull } from '../util/util'

export type CompoundInfo = {
    halfLife: string;
    pctActive?: number;
    note?: string;
};
export type CompoundName = [string] | [string, string];
export type CompoundsMap = Record<string, CompoundInfo>;

export function unpackCompoundName(cn: string): CompoundName {
    const [, b, v] = requireNotNull(
        cn.match(/^([^=]+)(?:=(.*))?$/),
        'regex failed',
    )
    requireNotNull(b, `bad compound name: ${cn}`)
    if (v) return [b, v]
    return [b]
}

export function packCompoundName(cn: CompoundName): string {
    const [b, v] = cn
    let s: string = b
    if (v ?? '') {
        s += '=' + v
    }
    return s
}

export function quoteCompoundName(cn: CompoundName): string {
    // eslint-disable-next-line prefer-const
    let [b, v] = cn.map(quote)
    v ??= ''
    if (v.indexOf(' ') != -1) v = `"${v.replace('"', '\\"')}"`
    let s = `[${b}`
    if (v) s += `,${v}`
    s += ']'
    return s
}

export type ByCompoundByVariant = Record<string, string[]>;

export function reshapeCompoundKeys(m: CompoundsMap): ByCompoundByVariant {
    const builder: ByCompoundByVariant = {}
    for (const k of Object.keys(m)) {
        const [b, v] = unpackCompoundName(k)
        let ov = builder[b]
        if (ov === undefined) {
            ov = []
            builder[b] = ov
        }
        if (v !== undefined) {
            ov.push(v)
        }
    }
    return builder
}

export const COMPOUND_BASE_PATTERN = '[^.](?!.*=).*'

type CompoundNameEntry = {
    compound: string;
    variant?: string;
};

export type CompoundEditorRow = CompoundNameEntry & {
    compound: string;
    variant?: string;
    halfLife: string;
    pctActive?: number;
    note?: string;
};

export const compoundRowInit = () =>
    ({
        compound: '',
        halfLife: '',
    }) as CompoundEditorRow

const CompoundEditorRowSchema = z.object({
    compound: z
        .string()
        .regex(RegExp(`^${COMPOUND_BASE_PATTERN}$`), 'invalid compound name'),
    variant: z.string().optional(),
    halfLife: zodDurationString,
    // react-hook-form coerces undefined to Nan when valueAsNumber is active on an input
    pctActive: z
        .number('NaN')
        .gt(0, 'invalid percent')
        .lte(100, 'invalid percent')
        .optional()
        .or(z.nan()),
    note: z.string().optional(),
})

export type CompoundEditorDataContainer = {
    compounds: CompoundEditorRow[];
};

export const CompoundEditorDataContainerSchema = z.object({
    compounds: z.array(CompoundEditorRowSchema).superRefine((data, ctx) => {
        const seen: string[] = []
        for (const [i, d] of data.entries()) {
            const cn = packCompoundName([d.compound, d.variant] as CompoundName)
            if (seen.includes(cn)) {
                ctx.addIssue({
                    code: 'custom',
                    path: [i, 'compound'],
                    message: 'Compound ' + (d.variant ? '...' : 'respecified'),
                })
                if (d.variant)
                    ctx.addIssue({
                        code: 'custom',
                        path: [i, 'variant'],
                        message: '... respecified',
                    })
            } else seen.push(cn)
        }
    }),
})

export const compoundNameOf = (r: CompoundNameEntry): CompoundName =>
    r.variant ? [r.compound, r.variant] : [r.compound]
