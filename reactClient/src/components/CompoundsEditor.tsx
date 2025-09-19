import { useState } from 'react'
import { useForm, useFieldArray, FormProvider } from 'react-hook-form'
import { useLocalCompounds } from '../hooks/useLocalData'
import { Button } from '../widgets/Button'
import { Column, Row } from '../widgets/RowCol'
import { fetchCompoundDetailsOrNull } from '../util/fetcher'
import { setIf } from '../util/filter-setif'
import { itemsFromRemoteFormLoader } from '../util/load-from-remote'
import { fieldsToMap, mapToFields } from '../util/reshaper'
import type { Nullable } from '../util/util'
import memoize from '../util/memoize'
import type {
    CompoundEditorDataContainer,
    CompoundEditorRow,
    CompoundInfo,
    CompoundsMap,
} from '../types/Compounds'
import {
    CompoundEditorDataContainerSchema,
    compoundRowInit,
    compoundNameOf,
    packCompoundName,
    unpackCompoundName,
} from '../types/Compounds'
import { displayToIso8601, iso8601ToDisplay } from '../types/duration'
import { zodResolver } from '@hookform/resolvers/zod'
import type { EditorProps } from './EditorProps'
import { FormInput, FormTextArea } from './FormField'
import { EditorCommands } from './EditorCommands'

const cFieldsToMap = (fields: CompoundEditorRow[]): CompoundsMap =>
    fieldsToMap(
        fields,
        (r) => packCompoundName(compoundNameOf(r)),
        (r) => {
            const v: CompoundInfo = { halfLife: displayToIso8601(r.halfLife) }
            // react-hook-form coerces undefined to Nan when valueAsNumber is active on an input
            if (r.pctActive && !isNaN(r.pctActive)) v.pctActive = r.pctActive
            setIf(v, 'note', r.note)
            return v
        },
    )

const cMapToFields = (map: CompoundsMap): CompoundEditorRow[] =>
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
            setIf(o, 'pctActive', v.pctActive)
            setIf(o, 'note', v.note)
            return o as CompoundEditorRow
        },
    )

const fetchDetails = memoize(
    fetchCompoundDetailsOrNull,
    packCompoundName,
    60000,
)

const loadDetailsFromRemote = async (
    row: CompoundEditorRow,
): Promise<Nullable<CompoundEditorRow>> =>
    fetchDetails(compoundNameOf(row)).then(
        (i?: CompoundInfo): Nullable<CompoundEditorRow> => {
            if (!i) return undefined
            const o: CompoundEditorRow = { ...row, ...i }
            if (o.pctActive) o.pctActive *= 100
            return o
        },
    )

export const CompoundsEditor = ({ isLoggedIn }: EditorProps) => {
    const [storage, setStorage] = useLocalCompounds()

    const initData = () => {
        if (Object.keys(storage).length > 0) return cMapToFields(storage)
        return [compoundRowInit()]
    }
    const methods = useForm<CompoundEditorDataContainer>({
        defaultValues: { compounds: initData() },
        resolver: zodResolver(CompoundEditorDataContainerSchema),
    })
    const { fields, append, remove, update } = useFieldArray({
        control: methods.control,
        name: 'compounds',
    })
    const [allowCommit, setAllowCommit] = useState(false)

    const loadFromRemote = itemsFromRemoteFormLoader(
        methods,
        'compounds',
        loadDetailsFromRemote,
        (r: CompoundEditorRow) => !r.compound,
        (i: number) => [`compounds.${i}.compound`, `compounds.${i}.variant`],
        (r: CompoundEditorRow) => ({
            ...compoundRowInit(),
            compound: r.compound,
            variant: r.variant,
        }),
        update,
    )

    const doSubmit = async (data: CompoundEditorRow[]) => {
        console.log(data)
        const map = cFieldsToMap(data)
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
            <title>Compounds editor</title>
            <FormProvider {...methods}>
                <form onSubmit={methods.handleSubmit((e) => doSubmit(e.compounds))}>
                    <h1>Data editor - compounds</h1>
                    <Column grid>
                        {fields.map((field, index) => (
                            <fieldset
                                key={field.id}
                                className={errors?.compounds?.[index] && 'invalid'}
                            >
                                <Row grid>
                                    <FormInput
                                        name={`compounds.${index}.compound`}
                                        placeholder="compound"
                                    />
                                    <FormInput
                                        name={`compounds.${index}.variant`}
                                        placeholder="variant"
                                    />
                                    <FormInput
                                        name={`compounds.${index}.halfLife`}
                                        placeholder="halflife (iso8601-ish)"
                                    />
                                    <FormInput
                                        name={`compounds.${index}.pctActive`}
                                        placeholder="% active"
                                        type="number"
                                        valueAsNumber
                                    />
                                    <FormTextArea
                                        name={`compounds.${index}.note`}
                                        placeholder="note"
                                    />
                                    <Button type="button" onClick={() => remove(index)}>
                                        Remove
                                    </Button>
                                </Row>
                            </fieldset>
                        ))}
                    </Column>
                    <EditorCommands
                        isLoggedIn={isLoggedIn}
                        appendRow={() => append(compoundRowInit())}
                        removeAll={remove}
                        loadFromRemote={loadFromRemote}
                        allowCommit={allowCommit}
                        setAllowCommit={setAllowCommit}
                    />
                </form>
            </FormProvider>
        </>
    )
}
