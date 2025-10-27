import { zodResolver } from '@hookform/resolvers/zod'
import classNames from 'classnames'
import { createContext, useContext, useMemo, useState } from 'react'
import type { UseFieldArrayRemove } from 'react-hook-form'
import { FormProvider, useFieldArray, useForm, useFormContext } from 'react-hook-form'
import { makeInvoker, useAsyncResult } from '../../hooks/useAsyncFetch'
import { useLocalBlends, useLocalCompounds } from '../../hooks/useLocalData'
import type {
    BlendEditorComponentRow,
    BlendEditorDataContainer,
    BlendEditorRow,
} from '../../types/Blends'
import {
    blendComponentRowInit,
    BlendEditorDataContainerSchema,
    blendEditorFieldsToMap,
    blendMapToEditorFields,
    blendRowInit,
    loadBlendDetailsFromRemote,
} from '../../types/Blends'
import { reshapeCompoundKeys } from '../../types/Compounds'
import { getSelectedIndices } from '../../types/Selectable'
import { fetchCompounds, fetchVariants, memoizedRemoteVariants } from '../../util/data-fetcher'
import { getOrElse } from '../../util/filter-setif'
import cache from '../../util/cache'
import {
    selectedItemsFromRemoteFormLoader,
    selectedItemsToRemoteSender,
} from './util/remote-load-store'
import { awaitAllWithBackpressure } from '../../util/awaitAllWithBackpressure'
import type { EditorProps } from '../EditorCommands'
import { EditorCommands } from '../EditorCommands'
import { FormInput, FormSelectWithOptions, FormTextArea } from '../FormField'
import { Centered } from '../widgets/Centered'
import { ErrorMessage } from '../widgets/ErrorMessage'
import { RequireContent } from '../widgets/RequireContent'
import { Flex } from '../widgets/RowCol'
import { ItemEditor } from './ItemEditorCommands'

const MergedCompoundNamesContext = createContext<string[]>([])
const VariantsFetcherContext = createContext(memoizedRemoteVariants)

type BlendComponentProps = {
    remove: UseFieldArrayRemove
    update: (row: BlendEditorComponentRow) => void
    blendIndex: number
    componentIndex: number
}
const BlendComponent = ({ blendIndex, componentIndex, update }: BlendComponentProps) => {
    const {
        getValues,
        formState: { errors },
    } = useFormContext<BlendEditorDataContainer>()
    const compounds = useContext(MergedCompoundNamesContext)
    const component = `blends.${blendIndex}.components.${componentIndex}`
    const variantsFetcher = useContext(VariantsFetcherContext)
    const curRow = getValues(`blends.${blendIndex}.components.${componentIndex}`)

    const componentError = errors?.blends?.[blendIndex]?.components?.[componentIndex]
    return (
        <fieldset
            className={classNames(
                'border',
                componentError && 'invalid'
            )}
        >
            <Flex dir="row" auxClasses={['gap-2', 'p-2']}>
                <FormInput type="checkbox" name={`${component}.selected`} />
                <FormInput
                    placeholder="dose (mg)"
                    type="number"
                    name={`${component}.dose`}
                    min={0.0001}
                    step={0.0001}
                />
                <FormSelectWithOptions
                    name={`${component}.compound`}
                    optionValues={compounds}
                    onChange={async (e) => {
                        const newCompound = e.currentTarget.value
                        const vx = await variantsFetcher(newCompound)
                        update({
                            ...curRow,
                            compound: newCompound,
                            variant: undefined,
                            vx: vx
                        })
                    }}
                />
                <FormSelectWithOptions name={`${component}.variant`} optionValues={getOrElse(curRow, 'vx', [])} />
            </Flex>
        </fieldset>
    )
}

type BlendComponentsProps = {
    parentIndex: number
}
const BlendComponents = ({ parentIndex }: BlendComponentsProps) => {
    const {
        control,
        watch,
        formState: { errors },
    } = useFormContext<BlendEditorDataContainer>()
    const { fields, append, remove, update } = useFieldArray({
        control,
        name: `blends.${parentIndex}.components`,
    })
    const componentContainerError = errors?.blends?.[parentIndex]?.components
    const selecteds = getSelectedIndices(watch, `blends.${parentIndex}.components`)
    return (
        <fieldset
            className={classNames(
                'border',
                componentContainerError && 'invalid'
            )}
        >
            <legend>components</legend>
            {fields.map((field, index) => (
                <BlendComponent
                    key={field.id}
                    blendIndex={parentIndex}
                    componentIndex={index}
                    remove={() => remove(index)}
                    update={(c: BlendEditorComponentRow) => update(index, c)}
                />
            ))}
            <ItemEditor
                addRow={() => append(blendComponentRowInit())}
                getSelected={() => selecteds}
                removeRows={remove}
            />
            <ErrorMessage text={componentContainerError?.root?.message} />
        </fieldset>
    )
}

type BlendsEditorBodyProps = {
    initData: () => Promise<BlendEditorRow[]>,
    loginProps: EditorProps,
    setStorage: ReturnType<typeof useLocalBlends>[1]
}
const BlendsEditorBody = ({ initData, loginProps: {isLoggedIn, loginToken}, setStorage} : BlendsEditorBodyProps) => {
    const [loadError, setLoadError] = useState('')
    const methods = useForm<BlendEditorDataContainer>({
        defaultValues: async () => {
            try {
                return ({ blends: await initData() })
            } catch {
                setLoadError('could not load initial variants')
                return { blends: [] }
            }},
        resolver: zodResolver(BlendEditorDataContainerSchema),
    })
    const { fields, append, remove, update } = useFieldArray({
        control: methods.control,
        name: 'blends',
    })

    const [allowCommit, setAllowCommit] = useState(false)

    const {
        getValues,
        formState: { errors },
        watch,
    } = methods

    const selecteds = getSelectedIndices(watch, `blends`)

    const doseForRow = (row: number) => {
        const result =
            getValues(`blends.${row}`)
                .components
                .map((e) => e.dose)
                .reduce((a,x) => a+x, 0.0)
        // undefined -> there was nothing to reduce
        // NaN -> RHF valueAsNumber converted an empty value into a NaN
        return (Number.isNaN(result) || !result) ? '???' : result.toString()
    }

    const loadFromRemote = selectedItemsFromRemoteFormLoader(
        methods,
        'blends',
        loadBlendDetailsFromRemote,
        (i: number) => [`blends.${i}.blend`],
        (r: BlendEditorRow) => ({ ...blendRowInit(), blend: r.blend }),
        update,
    )

    const doSubmit = selectedItemsToRemoteSender(
        methods,
        remove,
        'blends',
        blendEditorFieldsToMap,
        '/api/data/blends',
        setStorage,
        allowCommit,
        loginToken,
    )
    return (
        <FormProvider {...methods}>
            <form onSubmit={methods.handleSubmit((e) => doSubmit(e.blends))}>
                {fields.map((field, index) => (
                    <fieldset
                        key={field.id}
                        className={
                            `border ${errors?.blends?.[index] && 'invalid'}`
                        }
                    >
                        <Flex dir="row">
                            <fieldset className="gap-2 p-2">
                                <FormInput
                                    type="checkbox"
                                    name={`blends.${index}.selected`}
                                />
                                <FormInput
                                    name={`blends.${index}.blend`}
                                    type="text"
                                    label="name"
                                    placeholder="name"
                                />
                                <FormInput
                                    name=''
                                    type="text"
                                    placeholder=''
                                    roValue={doseForRow(index).toString()}
                                    label="total dose (calculated)"
                                />
                                <FormTextArea
                                    name={`blends.${index}.note`}
                                    label="note"
                                    placeholder="note"
                                />
                            </fieldset>
                            <BlendComponents parentIndex={index} />
                        </Flex>
                    </fieldset>
                ))}
                {/* a bit hacky to conditionalize but it takes care of no network on load with init data */}
                <RequireContent predicate={!loadError} errorText={loadError}>
                    <EditorCommands
                        isLoggedIn={isLoggedIn}
                        appendRow={() => append(blendRowInit())}
                        getSelected={() => selecteds}
                        removeRows={remove}
                        loadFromRemote={loadFromRemote}
                        allowCommit={[allowCommit, setAllowCommit]}
                    />
                </RequireContent>
            </form>
        </FormProvider>
    )
}


export const BlendsEditor = (loginProps: EditorProps) => {
    const [localCompoundsFromStorage] = useLocalCompounds()
    const localCompoundNames = useMemo(
        () => reshapeCompoundKeys(localCompoundsFromStorage),
        [localCompoundsFromStorage],
    )

    // for useAsyncFetch, auxDeps = [localCompoundNames]
    const getCompounds = cache(
        async () => await fetchCompounds(localCompoundNames),
        () => '',
        60000,
    )
    const getVariants = cache(
        async (c: string) => await fetchVariants(localCompoundNames, c),
        (s) => s,
        60000,
    )

    const [storage, setStorage] = useLocalBlends()

    const initData = async () => {
        if (Object.keys(storage).length > 0) {
            const rows = blendMapToEditorFields(storage)
            const variantsToFetch = rows
                .flatMap((b) =>
                    b.components
                        .map(c => c.compound)
                ).filterDistinct()
            const variantsFetchers = variantsToFetch.map((c) => getVariants(c))
            const fetchedVariants = await awaitAllWithBackpressure(variantsFetchers)
            const variants = Object.fromEntries(
                variantsToFetch.map((v, i) => [v, fetchedVariants[i]])
            )

            for (const row of rows)
                for (const c of row.components)
                    c.vx = variants[c.compound]
            return rows
        }
        return [blendRowInit()]
    }


    const compoundNames = useAsyncResult(
        makeInvoker({
            func: getCompounds,
            args: [],
            initial: [], // do not use local compounds so we can catch network failure earlier
            auxDeps: [localCompoundNames],
        }),
    )

    return <>
        <title>Blends editor</title>
        <Centered>
            <h1 className="text-2xl">Data editor - blends</h1>
        </Centered>
        <RequireContent predicate={compoundNames.length > 0} errorText='could not load compounds'>
            <MergedCompoundNamesContext value={compoundNames}>
                <VariantsFetcherContext value={getVariants}>
                    <BlendsEditorBody
                        initData={initData}
                        loginProps={loginProps}
                        setStorage={setStorage}
                    />
                </VariantsFetcherContext>
            </MergedCompoundNamesContext>
        </RequireContent>
    </>
}
