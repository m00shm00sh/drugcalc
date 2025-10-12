import { z } from 'zod'
import type { BlendsMap } from './Blends'
import { packCompoundName } from './Compounds'
import type { CompoundName, CompoundsMap } from './Compounds'
import type { Config } from './Config'
import type { FrequenciesMap } from './Frequencies'
import {
    displayToIso8601,
    zodDisplayDurationStringSchema,
    zodIsoDurationStringSchema,
} from './duration'
import { zodNonemptyStringSchema } from './string'
import { SelectableSchema } from './Selectable'
import { stripIf } from '../util/filter-setif'

/* even though this is strictly an outgoing type, i'm still making a zod schema because
 * prettier's typescript handling butchers indentation of union types
 */

const CycleDescriptionSharedSchema = z
    .object({
        compoundOrBlend: zodNonemptyStringSchema('select a compound or blend'),
        start: zodDisplayDurationStringSchema,
        duration: zodDisplayDurationStringSchema,
        freqName: zodNonemptyStringSchema('select a frequency'),
    })
    .extend(SelectableSchema.shape)

export const CycleDescriptionSchema = z.union([
    CycleDescriptionSharedSchema.extend({
        // compound
        prefix: z.literal('').optional(),
        variantOrTransformer: zodNonemptyStringSchema(
            'select a variant or transformer',
        ).optional(),
        dose: z.number().gt(0),
    }),
    CycleDescriptionSharedSchema.extend({
        // blend
        prefix: z.literal('.b'),
        dose: z.number().gt(0, 'dose too low'),
    }),
    CycleDescriptionSharedSchema.extend({
        // transformer
        prefix: z.literal('.t'),
        compoundOrBlend: zodNonemptyStringSchema('select a compound'),
        variantOrTransformer: zodNonemptyStringSchema('select a transformer'),
    }),
])

export type CycleDescription = z.infer<typeof CycleDescriptionSchema>

const extractCompoundsFromCycle = (rows: CycleDescription[]): string[] =>
    rows
        .filter((e) => e.prefix === undefined)
        .map((e) => {
            const c: CompoundName = [e.compoundOrBlend]
            if (!e.prefix && e.variantOrTransformer)
                c.push(e.variantOrTransformer)
            return packCompoundName(c)
        })
        .filterDistinct()

const extractBlendsFromCycle = (rows: CycleDescription[]): string[] =>
    rows
        .filter((e) => e.prefix === '.b')
        .map((e) => e.compoundOrBlend)
        .filterDistinct()

const extractFrequenciesFromCycle = (rows: CycleDescription[]): string[] =>
    rows.map((e) => e.freqName).filterDistinct()

export const calcRequestPrefixMapping: Record<string, string> = {
    compound: '', // can't be undefined because of html option constraints
    blend: '.b',
    transformer: '.t',
}

const CalcRequestRowSchema = z.intersection(
    CycleDescriptionSchema,
    SelectableSchema,
)
export type CalcRequestRow = z.infer<typeof CalcRequestRowSchema>

export const calcRequestRowInit = (): CalcRequestRow => ({
    prefix: '',
    compoundOrBlend: '',
    dose: undefined,
    start: '',
    duration: '',
    freqName: '',
}) as Partial<CalcRequestRow> as CalcRequestRow

export const cycleFieldsToCycleDescription = (
    fields: CalcRequestRow[],
): CycleDescription[] =>
    fields.map((e) => {
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        const { selected, ...d } = {
            ...e,
            start: displayToIso8601(e.start),
            duration: displayToIso8601(e.duration),
        }
        stripIf(d.prefix === '.t', d, 'dose')
        stripIf(d.prefix === '.b', d, 'variantOrTransformer')
        stripIf(d.prefix === '', d, 'prefix')
        return d
    })

export type AuxData = {
    compounds?: CompoundsMap
    blends?: BlendsMap
    frequencies?: FrequenciesMap
}

export const CalcRequestDataContainerSchema = z.object({
    cycle: z.array(CalcRequestRowSchema).min(1),
})

export type CalcRequestDataContainer = z.infer<
    typeof CalcRequestDataContainerSchema
>

export const calcRequestDataContainerInit = (): CalcRequestDataContainer => ({
    cycle: [calcRequestRowInit()],
})

export type CalcRequest = {
    data?: AuxData
    config?: Config
    cycle: CycleDescription[] // TODO: assert nonempty
    decode: 'PT24H' // displayToIso8601('1d')
}

export const calcRequestContainerToRequest = (
    container: CalcRequestDataContainer,
    config: Config,
    localCompounds: CompoundsMap,
    localBlends: BlendsMap,
    localFrequencies: FrequenciesMap,
): CalcRequest => {
    const cycle = cycleFieldsToCycleDescription(container.cycle)
    const data: AuxData = {}
    const localCompoundNames = Object.keys(localCompounds)
    if (
        localCompoundNames.filterIntersecting(extractCompoundsFromCycle(cycle))
            .length > 0
    )
        data.compounds = localCompounds
    if (
        Object.keys(localBlends).filterIntersecting(
            extractBlendsFromCycle(cycle),
        ).length > 0
    )
        data.blends = localBlends
    if (
        Object.keys(localFrequencies).filterIntersecting(
            extractFrequenciesFromCycle(cycle),
        ).length > 0
    )
        data.frequencies = localFrequencies
    const req: CalcRequest = {
        cycle: cycle,
        decode: 'PT24H',
    }
    if (Object.keys(data).length > 0) req.data = data
    if (Object.keys(config).length > 0) req.config = config
    return req
}

const XYListSchema = z.union([
    z.object({
        xType: z.union([z.literal('raw'), z.literal('day')]),
        plotType: z.union([z.literal('point'), z.literal('bar')]),
        x: z.array(z.number()),
        y: z.array(z.number()),
    }),
    z.object({
        xType: z.literal('duration'),
        plotType: z.union([z.literal('point'), z.literal('bar')]),
        x: z.array(zodIsoDurationStringSchema),
        y: z.array(z.number()),
    }),
])
type XYList = z.infer<typeof XYListSchema>

export const CycleResultSchema = z.record(
    zodNonemptyStringSchema('invalid result item'),
    XYListSchema,
)
export type CycleResult = z.infer<typeof CycleResultSchema>

const plotlyLayout: Partial<Plotly.Layout> = {
    xaxis: {
        title: {
            text: 'time (days)',
        },
    },
    yaxis: {
        title: {
            text: 'release rate (mg/day)',
        },
        showgrid: true,
    },
    title: {
        text: 'mg/day release',
    },
    legend: {
        orientation: 'h',
        xanchor: 'center',
        yanchor: 'bottom',
        x: 0.5,
        y: -0.5,
    },
    hovermode: 'x unified',
}

const resultItemToPlotlyData = (
    name: string,
    xyl: XYList,
): Partial<Plotly.PlotData> => {
    if (xyl.xType === 'duration')
        throw Error(`unexpected xylist type: duration`)
    return {
        x: xyl.x,
        y: xyl.y,
        showlegend: true,
        type: 'scattergl',
        name: name,
        mode: 'lines',
        ...(xyl.plotType === 'bar' && {
            line: {
                shape: 'hv',
            },
        }),
    }
}

export type PlotlyInvocation = {
    data: Partial<Plotly.PlotData>[]
    layout: Partial<Plotly.Layout>
}

export const plotlyEmptyInvocation = (): PlotlyInvocation => ({
    data: [],
    layout: {},
})

export const resultToPlotly = (result: CycleResult): PlotlyInvocation => ({
    data: Object.entries(result).map(([k, v]) => resultItemToPlotlyData(k, v)),
    layout: plotlyLayout,
})
