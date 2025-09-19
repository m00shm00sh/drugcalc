import { useState, createContext, useContext, useMemo } from 'react'
import useAsyncResult from '../hooks/useAsyncFetch'
import { useLocalBlends, useLocalCompounds } from '../hooks/useLocalData'
import { Button } from '../widgets/Button'
import { Column, Row } from '../widgets/RowCol'
import memoize from '../util/memoize'
import {
    fetchCompounds,
    fetchBlendDetailsOrNull,
    fetchVariants,
    memoizedRemoteVariants,
} from '../util/fetcher'
import { itemsFromRemoteFormLoader } from '../util/load-from-remote'
import { fieldsToMap, mapToFields } from '../util/reshaper'
import { type Nullable } from '../util/util'
import type {
    BlendComponentsMap,
    BlendEntry,
    BlendsMap,
    BlendEditorComponentRow,
    BlendEditorRow,
    BlendEditorDataContainer,
} from '../types/Blends'
import {
    blendComponentRowInit,
    blendRowInit,
    BlendEditorDataContainerSchema,
} from '../types/Blends'
import type { ByCompoundByVariant } from '../types/Compounds'
import {
    compoundNameOf,
    reshapeCompoundKeys,
    packCompoundName,
    unpackCompoundName,
} from '../types/Compounds'
import { setIf } from '../util/filter-setif'
import {
    useForm,
    useFormContext,
    useFieldArray,
    FormProvider,
} from 'react-hook-form'
import type { UseFieldArrayRemove } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { EditorCommands } from './EditorCommands'
import type { EditorProps } from './EditorProps'
import { FormInput, FormSelectWithOptions, FormTextArea } from './FormField'

const componentFieldsToEntry = (
    fields: BlendEditorComponentRow[],
): BlendComponentsMap =>
    fieldsToMap(
        fields,
        (r) => packCompoundName(compoundNameOf(r)),
        (r) => r.dose,
    )

const bFieldsToMap = (fields: BlendEditorRow[]): BlendsMap =>
    fieldsToMap(
        fields,
        (r) => r.blend,
        (r) => {
            const e: BlendEntry = {
                components: componentFieldsToEntry(r.components),
            }
            setIf(e, 'note', r.note)
            return e
        },
    )

const componentMapToComponentFields = (
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

const bMapToFields = (map: BlendsMap): BlendEditorRow[] =>
    mapToFields(
        map,
        (k) => ({ blend: k }) as Partial<BlendEditorRow>,
        (v, o) => {
            setIf(o, 'note', v.note)
            o.components = componentMapToComponentFields(v.components)
            return o as BlendEditorRow
        },
    )

const fetchDetails = memoize(
    async (b: string) => await fetchBlendDetailsOrNull(b),
    (s) => s,
    60000,
)

const loadDetailsFromRemote = async (
    row: BlendEditorRow,
): Promise<Nullable<BlendEditorRow>> =>
    fetchDetails(row.blend).then((b?: BlendEntry): Nullable<BlendEditorRow> => {
        if (!b) return undefined
        const o: BlendEditorRow = { ...row }
        setIf(o, 'note', b.note)
        o.components = componentMapToComponentFields(b.components)
        return o
    })

const LocalCompoundsBcbvContext = createContext<ByCompoundByVariant>({})
const MergedCompoundNamesContext = createContext<string[]>([])
const VariantsFetcherContext = createContext(memoizedRemoteVariants)

type BlendComponentProps = {
    remove: UseFieldArrayRemove;
    blendIndex: number;
    componentIndex: number;
};
const BlendComponent = ({
    blendIndex,
    componentIndex,
    remove,
}: BlendComponentProps) => {
    const {
        watch,
        formState: { errors },
    } = useFormContext<BlendEditorDataContainer>()
    const localBcbv = useContext(LocalCompoundsBcbvContext)
    const compounds = useContext(MergedCompoundNamesContext)
    const component = `blends.${blendIndex}.components.${componentIndex}`
    const variantsFetcher = useContext(VariantsFetcherContext)
    const watchCompound: string = watch(
        `blends.${blendIndex}.components.${componentIndex}.compound`,
    )
    const variants: readonly string[] = useAsyncResult(
        variantsFetcher,
        [watchCompound],
        [],
        [localBcbv],
    )
    return (
        <Row grid>
            <fieldset
                className={
                    errors?.blends?.[blendIndex]?.components?.[componentIndex] &&
          'invalid'
                }
            >
                <FormSelectWithOptions
                    placeholder="compound"
                    name={`${component}.compound`}
                    optionValues={compounds}
                />
                <FormSelectWithOptions
                    placeholder="variant"
                    name={`${component}.variant`}
                    optionValues={variants}
                />
                <FormInput
                    placeholder="dose"
                    type="number"
                    name={`${component}.dose`}
                    valueAsNumber
                />
                <Button type="button" onClick={() => remove(componentIndex)}>
                    Remove component
                </Button>
            </fieldset>
        </Row>
    )
}

type BlendComponentsProps = {
    parentIndex: number;
};
const BlendComponents = ({ parentIndex }: BlendComponentsProps) => {
    const {
        control,
        formState: { errors },
    } = useFormContext<BlendEditorDataContainer>()
    const { fields, append, remove } = useFieldArray({
        control,
        name: `blends.${parentIndex}.components`,
    })
    const componentContainerError = errors?.blends?.[parentIndex]?.components
    return (
        <Column grid>
            <fieldset className={componentContainerError && 'invalid'}>
                <legend>components</legend>
                {fields.map((field, index) => (
                    <BlendComponent
                        key={field.id}
                        blendIndex={parentIndex}
                        componentIndex={index}
                        remove={remove}
                    />
                ))}
                <Button type="button" onClick={() => append(blendComponentRowInit())}>
                    Add component
                </Button>
            </fieldset>
            {componentContainerError?.root?.message && (
                <div className="error-msg">
                    {componentContainerError?.root?.message}
                </div>
            )}
        </Column>
    )
}

export const BlendsEditor = ({ isLoggedIn }: EditorProps) => {
    const [localCompoundsFromStorage] = useLocalCompounds()
    const localCompoundNames = useMemo(
        () => reshapeCompoundKeys(localCompoundsFromStorage),
        [localCompoundsFromStorage],
    )

    // for useAsyncFetch, auxDeps = [localCompoundsFromStorage]
    const getCompounds = memoize(
        async () => await fetchCompounds(localCompoundNames),
        () => '',
        60000,
    )
    // for useAsyncFetch, auxDeps = [localCompoundsFromStorage]
    const getVariants = memoize(
        async (c: string) => await fetchVariants(localCompoundNames, c),
        (s) => s,
        60000,
    )
    const [storage, setStorage] = useLocalBlends()

    const initData = () => {
        if (Object.keys(storage).length > 0) return bMapToFields(storage)
        return [blendRowInit()]
    }

    const methods = useForm<BlendEditorDataContainer>({
        defaultValues: { blends: initData() },
        resolver: zodResolver(BlendEditorDataContainerSchema),
    })
    const { fields, append, remove, update } = useFieldArray({
        control: methods.control,
        name: 'blends',
    })

    const [allowCommit, setAllowCommit] = useState(false)

    const compoundNames = useAsyncResult(
        getCompounds,
        [],
        [],
        [localCompoundNames],
    )

    const loadFromRemote = itemsFromRemoteFormLoader(
        methods,
        'blends',
        loadDetailsFromRemote,
        (b: BlendEditorRow) => !b.blend,
        (i: number) => [`blends.${i}.blend`],
        (r: BlendEditorRow) => ({ ...blendRowInit(), blend: r.blend }),
        update,
    )

    const doSubmit = async (data: BlendEditorRow[]) => {
        console.log(data)
        const map = bFieldsToMap(data)
        if (allowCommit) throw Error('unimplemented')
        else {
            setStorage(map)
        }
    }

    const {
        formState: { errors },
    } = methods
    return (
        <>
            <title>Blends editor</title>
            {compoundNames.length > 0 ? (
                <FormProvider {...methods}>
                    <LocalCompoundsBcbvContext value={localCompoundNames}>
                        <MergedCompoundNamesContext value={compoundNames}>
                            <VariantsFetcherContext value={getVariants}>
                                <form
                                    onSubmit={methods.handleSubmit((e) => doSubmit(e.blends))}
                                >
                                    <h1>Data editor - blends</h1>
                                    <Column grid>
                                        {fields.map((field, index) => (
                                            <fieldset
                                                key={field.id}
                                                className={errors?.blends?.[index] && 'invalid'}
                                            >
                                                <Row grid>
                                                    <Column grid>
                                                        <FormInput
                                                            name={`blends.${index}.blend`}
                                                            placeholder="name"
                                                        />
                                                        <FormTextArea
                                                            name={`blends.${index}.note`}
                                                            placeholder="note"
                                                        />
                                                        <Button type="button" onClick={() => remove(index)}>
                                                            Remove blend
                                                        </Button>
                                                    </Column>
                                                    <BlendComponents parentIndex={index} />
                                                </Row>
                                            </fieldset>
                                        ))}
                                    </Column>
                                    <EditorCommands
                                        isLoggedIn={isLoggedIn}
                                        appendRow={() => append(blendRowInit())}
                                        removeAll={remove}
                                        loadFromRemote={loadFromRemote}
                                        allowCommit={allowCommit}
                                        setAllowCommit={setAllowCommit}
                                    />
                                </form>
                            </VariantsFetcherContext>
                        </MergedCompoundNamesContext>
                    </LocalCompoundsBcbvContext>
                </FormProvider>
            ) : (
                <div className="content error-msg">
                    <h2>Could not load compounds</h2>
                </div>
            )}
        </>
    )
}
