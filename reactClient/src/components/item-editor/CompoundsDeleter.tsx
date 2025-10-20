import { zodResolver } from '@hookform/resolvers/zod'
import classNames from 'classnames'
import { FormProvider, useFieldArray, useForm } from 'react-hook-form'
import { makeInvoker, useAsyncResult } from '../../hooks/useAsyncFetch'
import type { CompoundDeleterDataContainer, CompoundDeleterRow } from '../../types/Compounds'
import {
    CompoundDeleterDataContainerSchema,
    compoundDeleterRowInit,
    compoundNameOf,
    expandExpansionItems,
} from '../../types/Compounds'
import { getSelectedIndices } from '../../types/Selectable'
import { compoundPath, memoizedRemoteCompounds, memoizedRemoteVariants } from '../../util/fetcher'
import { selectedItemsOnRemoteDeleter } from '../../util/remote-load-store'
import type { Nullable } from '../../util/util'
import { Centered } from '../../widgets/Centered'
import { Flex } from '../../widgets/RowCol'
import type { EditorProps } from '../EditorCommands'
import { EditorCommands } from '../EditorCommands'
import { FormInput, FormSelectWithOptions } from '../FormField'

type SelectVariantProps = {
    compoundName: string
    initialVariant: Nullable<string>
    isExpandSelected: Nullable<boolean>
    index: number
}
const SelectVariant = ({
    compoundName,
    initialVariant,
    isExpandSelected,
    index,
}: SelectVariantProps) => {
    const variantNames = useAsyncResult(makeInvoker({
        func: memoizedRemoteVariants,
        args: [compoundName],
        initial: [],
        auxDeps: []
    }))
    if (isExpandSelected)
        return

    return <FormSelectWithOptions
        name={`compounds.${index}.variant`}
        type="text"
        optionValues={variantNames}
        label="variant"
        defaultValue={initialVariant}
    />
}

export const CompoundsDeleter = ({ loginToken }: EditorProps) => {
    if (!loginToken)
        throw Error('this component should not rendered without a login token')

    const initData = () => [compoundDeleterRowInit()]

    const methods = useForm<CompoundDeleterDataContainer>({
        defaultValues: { compounds: initData() },
        resolver: zodResolver(CompoundDeleterDataContainerSchema),
    })
    const arrayMethods = useFieldArray({
        control: methods.control,
        name: 'compounds',
    })
    const { fields, append, remove, update } = arrayMethods

    const doExpand = expandExpansionItems(methods, arrayMethods)

    const doSubmit = selectedItemsOnRemoteDeleter(
        methods,
        remove,
        'compounds',
        (i: number) => [`compounds.${i}`],
        '/api/data/compounds',
        (r: CompoundDeleterRow) => compoundPath(compoundNameOf(r), r.expand),
        [memoizedRemoteCompounds, memoizedRemoteVariants],
        loginToken,
    )

    const {
        getValues,
        watch,
        formState: { errors },
    } = methods

    const compoundNames = useAsyncResult(makeInvoker({
        func: memoizedRemoteCompounds,
        args: [],
        initial: [],
        auxDeps: []
    }))

    const selecteds = getSelectedIndices(watch, `compounds`)
    return (
        <>
            <title>Compounds deleter</title>
            <Centered>
                <h1 className="text-2xl">Compounds deleter</h1>
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
                                <FormSelectWithOptions
                                    name={`compounds.${index}.compound`}
                                    type="text"
                                    optionValues={compoundNames}
                                    label="compound"
                                    onChange={async (e) => {
                                        const newCompound = e.currentTarget.value
                                        const row = getValues(`compounds.${index}`)
                                        if (!row.expand)
                                            row.vx = await memoizedRemoteVariants(newCompound)
                                        update(index, {...row})
                                    }}
                                />
                                <SelectVariant
                                    compoundName={getValues(`compounds.${index}.compound`)}
                                    initialVariant={getValues(`compounds.${index}.variant`)}
                                    isExpandSelected={getValues(`compounds.${index}.expand`)}
                                    index={index}
                                />
                                <FormInput
                                    name={`compounds.${index}.expand`}
                                    type="checkbox"
                                    label="expand?"
                                />
                            </Flex>
                        </fieldset>
                    ))}
                    <EditorCommands
                        appendRow={() => append(compoundDeleterRowInit())}
                        getSelected={() => selecteds}
                        removeRows={remove}
                        loadFromRemote={['Expand select-all items', doExpand]}
                        submitStr='Delete from remote'
                        allowCommit={[true, () => {}]}
                    />
                </form>
            </FormProvider>
        </>
    )
}
