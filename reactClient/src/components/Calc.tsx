import { zodResolver } from '@hookform/resolvers/zod'
import { useMemo, useState, type SyntheticEvent } from 'react'
import { FormProvider, useFieldArray, useForm, useFormContext } from 'react-hook-form'
import createPlotlyComponent from 'react-plotly.js/factory'
import { makeInvoker, useAsyncResult } from '../hooks/useAsyncFetch'
import {
    useLocalBlends,
    useLocalCompounds,
    useLocalConfig,
    useLocalFrequencies,
} from '../hooks/useLocalData'
import type { CalcRequestDataContainer, CalcRequestRow, PlotlyInvocation } from '../types/Calc'
import {
    calcRequestContainerToRequest,
    CalcRequestDataContainerSchema,
    calcRequestPrefixMapping,
    calcRequestRowInit,
    CycleResultSchema,
    exportRowsToQueryString,
    importRowsFromQueryString,
    plotlyEmptyInvocation,
    resultToPlotly,
} from '../types/Calc'
import type { ByCompoundByVariant } from '../types/Compounds'
import { reshapeCompoundKeys } from '../types/Compounds'
import { sortedFrequenciesWithWeights } from '../types/Frequencies'
import { getSelectedIndices } from '../types/Selectable'
import {
    fetchBlends,
    fetchCompounds,
    fetchFrequencies,
    fetchTransformerFrequencies,
    fetchTransformerNames,
    fetchVariants,
    postJson,
} from '../util/fetcher'
import memoize from '../util/memoize'
import { Centered } from '../widgets/Centered'
import { ErrorMessage } from '../widgets/ErrorMessage'
import { Flex, Grid } from '../widgets/RowCol'
import { EditorCommands } from './EditorCommands'
import { FormInput, FormSelectWithOptions } from './FormField'
import classNames from 'classnames'
import { useQueryString } from '../hooks/useQueryString'
// either this or a dummy typescript declaration file;
// use "regular" Plotly from react-plotly.js for development
// eslint-disable-next-line @typescript-eslint/no-require-imports
const Plotly = require('plotly.js-gl2d-dist-min/')
const Plot = createPlotlyComponent(Plotly)

type WithAuxDeps<T extends unknown[], U extends unknown[]> = [
    (...args: [...T]) => Promise<readonly string[]>,
    [...U],
]
type CycleDescriptionEditorRowProps = {
    index: number
    update: (row: CalcRequestRow) => void
    getCompounds: WithAuxDeps<[], [ByCompoundByVariant]>
    getVariants: WithAuxDeps<[string], [ByCompoundByVariant]>
    getBlends: WithAuxDeps<[], [string[]]>
    getFrequencies: WithAuxDeps<[], [Record<string, number>]>
}

const fetchEmptyList = async (): Promise<readonly string[]> => []

const CycleDescriptionEditorRow = ({
    index,
    update,
    getCompounds,
    getVariants,
    getBlends,
    getFrequencies,
}: CycleDescriptionEditorRowProps) => {
    const {
        watch,
        formState: { errors },
    } = useFormContext<CalcRequestDataContainer>()

    const row = `cycle.${index}`
    const watchRow = watch(`cycle.${index}`)
    const selectPrefix = calcRequestPrefixMapping
    const prefixValue = watch(`cycle.${index}.prefix`)

    const [getCompoundsInvocation, selectCompoundOrBlendLabel] = (() => {
        switch (prefixValue) {
            case calcRequestPrefixMapping.compound:
            case calcRequestPrefixMapping.transformer:
                return [
                    makeInvoker({
                        func: getCompounds[0],
                        args: [],
                        initial: [],
                        auxDeps: getCompounds[1] as unknown[],
                    }),
                    'compound',
                ]
            case calcRequestPrefixMapping.blend:
                return [
                    makeInvoker({
                        func: getBlends[0],
                        args: [],
                        initial: [],
                        auxDeps: getBlends[1],
                    }),
                    'blend',
                ]
        }
        throw Error(`unhandled prefix for compound selection: ${prefixValue}`)
    })()
    const selectCompoundOrBlend = useAsyncResult(getCompoundsInvocation)

    const compoundOrBlendValue = watch(`cycle.${index}.compoundOrBlend`)

    const [getVariantsInvocation, selectVariantsLabel] = (() => {
        switch (prefixValue) {
            case calcRequestPrefixMapping.compound:
                return [
                    makeInvoker({
                        func: getVariants[0],
                        args: [compoundOrBlendValue ?? ''],
                        initial: [],
                        auxDeps: getVariants[1] as unknown[],
                    }),
                    'variant',
                ]
            case calcRequestPrefixMapping.transformer:
                return [
                    makeInvoker({
                        func: fetchTransformerNames,
                        args: [],
                        initial: [],
                    }),
                    'transformer',
                ]
            case calcRequestPrefixMapping.blend:
                return [
                    makeInvoker({
                        func: fetchEmptyList,
                        args: [],
                        initial: [],
                    }),
                    'blend',
                ]
        }
        throw Error(`unhandled prefix for compound selection: ${prefixValue}`)
    })()
    const selectVariantOrTransformer = useAsyncResult(getVariantsInvocation)

    const selectFrequency = useAsyncResult(
        makeInvoker({
            func: async () => {
                const all = await getFrequencies[0]()
                const xform =
                    prefixValue === calcRequestPrefixMapping.transformer
                        ? await fetchTransformerFrequencies()
                        : []
                return [...all, ...xform]
            },
            args: [],
            initial: [],
            auxDeps: [getFrequencies[1], fetchTransformerFrequencies],
        }),
    )

    const rowError = errors?.cycle?.[index]
    return (
        <fieldset
            className={classNames(
                'border',
                rowError && 'invalid'
            )}
        >
            <Grid colSpec="nc-4" auxClasses={['gap-2', 'justify-items-center']}>
                <FormInput
                    type="checkbox"
                    label=" "
                    name={`cycle.${index}.selected`}
                    inpClasses={['pl-2']}
                />
                <FormSelectWithOptions
                    label="prefix"
                    name={`${row}.prefix`}
                    optionValues={selectPrefix}
                    onChange={(e: SyntheticEvent<HTMLSelectElement>) => {
                        const newPrefix = e.currentTarget.value
                        // if previous state was non-blend, we have to clear it
                        if ('variantOrTransformer' in watchRow)
                            delete watchRow.variantOrTransformer
                        update({
                            ...watchRow,
                            compoundOrBlend: '',
                            ...(newPrefix !== calcRequestPrefixMapping.blend && {
                                variantOrTransformer: undefined,
                            }),
                            ...(newPrefix === calcRequestPrefixMapping.transformer && {
                                dose: undefined,
                            }),
                        })
                    }}
                />
                <FormSelectWithOptions
                    label={selectCompoundOrBlendLabel}
                    name={`${row}.compoundOrBlend`}
                    optionValues={selectCompoundOrBlend}
                />
                <FormSelectWithOptions
                    label={selectVariantsLabel}
                    name={`${row}.variantOrTransformer`}
                    optionValues={selectVariantOrTransformer}
                />
                {prefixValue !== calcRequestPrefixMapping.transformer && (
                    <FormInput
                        label="dose"
                        placeholder="dose (mg)"
                        blockClasses={['row-2', 'col-1']}
                        name={`${row}.dose`}
                        type="number"
                        min={0.0001}
                        step={0.0001}
                    />
                )}
                <FormInput
                    blockClasses={['row-2', 'col-2']}
                    placeholder="iso8601-ish"
                    label="start"
                    type="text"
                    name={`${row}.start`}
                />
                <FormInput
                    blockClasses={['row-2', 'col-3']}
                    placeholder="iso8601-ish"
                    label="duration"
                    type="text"
                    name={`${row}.duration`}
                />
                <FormSelectWithOptions
                    blockClasses={['row-2', 'col-4']}
                    label="frequency"
                    name={`${row}.freqName`}
                    optionValues={selectFrequency}
                />
            </Grid>
        </fieldset>
    )
}

export const Calc = () => {
    const [storedConfig] = useLocalConfig()
    const [localBlends] = useLocalBlends()
    const [localCompounds] = useLocalCompounds()
    const [localFrequencies] = useLocalFrequencies()
    const [result, setResult] = useState<PlotlyInvocation>(plotlyEmptyInvocation)
    const [localCompoundsFromStorage] = useLocalCompounds()
    const localCompoundNames = useMemo(
        () => reshapeCompoundKeys(localCompoundsFromStorage),
        [localCompoundsFromStorage],
    )

    // for useAsyncFetch, auxDeps = [localCompoundNames]
    const getCompounds = memoize(
        async () => await fetchCompounds(localCompoundNames),
        () => '',
        60000,
    )
    // for useAsyncFetch, auxDeps = [localCompoundNames]
    const getVariants = memoize(
        async (c: string) => await fetchVariants(localCompoundNames, c),
        (s) => s,
        60000,
    )

    const [localBlendsFromStorage] = useLocalBlends()
    const localBlendNames = useMemo(
        () => Object.keys(localBlendsFromStorage),
        [localBlendsFromStorage],
    )
    // for useAsyncFetch, auxDeps = [localBlendNames]
    const getBlends = memoize(
        async () => await fetchBlends(localBlendNames),
        () => '',
        60000,
    )

    const [localFrequenciesFromStorage] = useLocalFrequencies()
    const localFrequencyItems = useMemo(
        () => sortedFrequenciesWithWeights(localFrequenciesFromStorage),
        [localFrequenciesFromStorage],
    )
    // for useAsyncFetch, auxDeps = [localFrequencyNames]
    const getFrequencies = memoize(
        async () => await fetchFrequencies(localFrequencyItems),
        () => '',
    )

    const [searchParams, setSearchParams] = useQueryString()

    const initValues = () => {
        try {
            const rows = importRowsFromQueryString(searchParams)
            return rows.length !== 0 ? rows : [calcRequestRowInit()]
        } catch (e) {
            console.log(`query string import failed: ${e}`)
            return [calcRequestRowInit()]
        }
    }

    const methods = useForm<CalcRequestDataContainer>({
        defaultValues: {
            cycle: initValues(),
        },
        resolver: zodResolver(CalcRequestDataContainerSchema),
    })
    const {
        control,
        formState: { errors },
        getValues,
        setError,
        watch,
    } = methods
    const { append, update, remove, fields } = useFieldArray({
        control,
        name: `cycle`,
    })

    const saveFormToQueryString = () => {
        const r = getValues('cycle')
        setSearchParams(exportRowsToQueryString(r))
    }

    const doSubmit = async (r: CalcRequestDataContainer) => {
        try {
            const plot = await postJson(
                '/api/calc',
                CycleResultSchema,
                calcRequestContainerToRequest(
                    r,
                    storedConfig,
                    localCompounds,
                    localBlends,
                    localFrequencies,
                ),
            )
            setResult(resultToPlotly(plot))
        } catch (e) {
            if (e instanceof Error) {
                const resultErr = e.message.substring(
                    e.message.indexOf(':', e.message.indexOf(':') + 1) + 1,
                )
                setError('cycle', { message: resultErr })
            } else {
                alert(e)
            }
        }
    }

    const selecteds = getSelectedIndices(watch, `cycle`)

    return (
        <>
            <Centered>
                <h1 className="text-2xl">Cycle</h1>
            </Centered>
            <Flex dir="col">
                <FormProvider {...methods}>
                    <Centered>
                        <form onSubmit={methods.handleSubmit((e) => doSubmit(e))}>
                            {fields.map((field, index) => (
                                <CycleDescriptionEditorRow
                                    key={field.id}
                                    index={index}
                                    update={(e) => update(index, e)}
                                    getCompounds={[getCompounds, [localCompoundNames]]}
                                    getVariants={[getVariants, [localCompoundNames]]}
                                    getBlends={[getBlends, [localBlendNames]]}
                                    getFrequencies={[getFrequencies, [localFrequencyItems]]}
                                />
                            ))}
                            <ErrorMessage text={errors?.cycle?.message} />
                            <EditorCommands
                                isLoggedIn={false}
                                appendRow={() => append([calcRequestRowInit()])}
                                getSelected={() => selecteds}
                                removeRows={remove}
                                submitStr="Evaluate"
                                save2={[saveFormToQueryString, 'Save form to URL']}
                            />
                        </form>
                    </Centered>
                </FormProvider>
                <Centered>
                    {result.data.length > 0 && <Plot data={result.data} layout={result.layout} />}
                </Centered>
            </Flex>
        </>
    )
}
