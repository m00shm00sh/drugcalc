import { zodResolver } from '@hookform/resolvers/zod'
import classNames from 'classnames'
import { useState } from 'react'
import { FormProvider, useFieldArray, useForm } from 'react-hook-form'
import { useLocalCompounds } from '../../hooks/useLocalData'
import type { CompoundEditorDataContainer, CompoundEditorRow } from '../../types/Compounds'
import {
    CompoundEditorDataContainerSchema,
    compoundEditorFieldsToMap,
    compoundMapToEditorFields,
    compoundRowInit,
    loadCompoundDetailsFromRemote,
} from '../../types/Compounds'
import { getSelectedIndices } from '../../types/Selectable'
import {
    selectedItemsFromRemoteFormLoader,
    selectedItemsToRemoteSender,
} from '../../util/remote-load-store'
import { Centered } from '../../widgets/Centered'
import { Flex } from '../../widgets/RowCol'
import type { EditorProps } from '../EditorCommands'
import { EditorCommands } from '../EditorCommands'
import { FormInput, FormTextArea } from '../FormField'

export const CompoundsEditor = ({ isLoggedIn, loginToken }: EditorProps) => {
    const [storage, setStorage] = useLocalCompounds()

    const initData = () => {
        if (Object.keys(storage).length > 0) return compoundMapToEditorFields(storage)
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

    const loadFromRemote = selectedItemsFromRemoteFormLoader(
        methods,
        'compounds',
        loadCompoundDetailsFromRemote,
        (i: number) => [`compounds.${i}.compound`, `compounds.${i}.variant`],
        (r: CompoundEditorRow) => ({
            ...compoundRowInit(),
            compound: r.compound,
            variant: r.variant,
        }),
        update,
    )

    const doSubmit = selectedItemsToRemoteSender(
        methods,
        remove,
        'compounds',
        compoundEditorFieldsToMap,
        '/api/data/compounds',
        setStorage,
        allowCommit,
        loginToken,
    )

    const {
        watch,
        formState: { errors },
    } = methods
    const selecteds = getSelectedIndices(watch, `compounds`)
    return (
        <>
            <title>Compounds editor</title>
            <Centered>
                <h1 className="text-2xl">Data editor - compounds</h1>
            </Centered>
            <FormProvider {...methods}>
                <form onSubmit={methods.handleSubmit((e) => doSubmit(e.compounds))}>
                    {fields.map((field, index) => (
                        <fieldset
                            key={field.id}
                            className={classNames(
                                'border',
                                errors?.compounds?.[index] && 'invalid'
                            )}
                        >
                            <Flex dir="row" auxClasses={['gap-2', 'p-2']}>
                                <FormInput
                                    name={`compounds.${index}.selected`}
                                    type="checkbox"
                                    label=" "
                                />
                                <FormInput
                                    name={`compounds.${index}.compound`}
                                    type="text"
                                    placeholder="compound"
                                    label="compound"
                                />
                                <FormInput
                                    name={`compounds.${index}.variant`}
                                    type="text"
                                    placeholder="variant"
                                    label="variant"
                                />
                                <FormInput
                                    name={`compounds.${index}.halfLife`}
                                    type="text"
                                    placeholder="halflife (iso8601-ish)"
                                    label="halflife (duration)"
                                />
                                <FormInput
                                    name={`compounds.${index}.pctActive`}
                                    type="number"
                                    placeholder="% active"
                                    label="% active"
                                    min={0.0001}
                                    step={0.0001}
                                    max={100}
                                />
                                <FormTextArea name={`compounds.${index}.note`} label="note" />
                            </Flex>
                        </fieldset>
                    ))}
                    <EditorCommands
                        isLoggedIn={isLoggedIn}
                        appendRow={() => append(compoundRowInit())}
                        getSelected={() => selecteds}
                        removeRows={remove}
                        loadFromRemote={loadFromRemote}
                        allowCommit={[allowCommit, setAllowCommit]}
                    />
                </form>
            </FormProvider>
        </>
    )
}
