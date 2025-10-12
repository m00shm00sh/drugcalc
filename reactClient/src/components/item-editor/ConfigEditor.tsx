import { zodResolver } from '@hookform/resolvers/zod'
import type { SyntheticEvent } from 'react'
import {
    FormProvider,
    useFieldArray,
    useForm,
    useFormContext,
} from 'react-hook-form'
import { useLocalConfig } from '../../hooks/useLocalData'
import type { ConfigEditorContainer, ConfigEditorRow } from '../../types/Config'
import {
    ConfigEditorContainerSchema,
    configEditorFields,
    configEditorFieldsToConfig,
    configEditorRowInit,
    configToEditorFields,
    isConfigItem,
} from '../../types/Config'
import { getSelectedIndices } from '../../types/Selectable'
import { Centered } from '../../widgets/Centered'
import { Grid, GridCol } from '../../widgets/RowCol'
import { EditorCommands } from '../EditorCommands'
import { FormInput, FormSelectWithOptions } from '../FormField'

type SetItemProps = {
    index: number
    type: string
    value: unknown
}
const SetItem = ({index, type, value}: SetItemProps) => {
    const name = type
    if (!isConfigItem(name)) throw Error('failed test for config key')
    const setItemField = configEditorFields[name]
    switch (setItemField?.type) {
        case 'input':
            return (
                <FormInput
                    name={`config.${index}.value`}
                    type={setItemField.valType}
                    {...(setItemField.valType === 'number' && {
                        valueAsNumber: true,
                        min: setItemField.min,
                        max: setItemField.max,
                        step: setItemField.step,
                    })}
                    {...(setItemField.valType === 'text' && {
                        minLength: setItemField.minLength,
                        maxLength: setItemField.maxLength,
                    })}
                />
            )
        case 'select':
            return (
                <FormSelectWithOptions
                    name={`config.${index}.value`}
                    defaultValue={value as string}
                    optionValues={setItemField.options}
                />
            )
    }
}


type ConfigItemProps = {
    index: number
    update: (row: ConfigEditorRow) => void
}
const ConfigItem = ({ index, update }: ConfigItemProps) => {
    const {
        formState: { errors },
        clearErrors,
        watch,
    } = useFormContext<ConfigEditorContainer>()
    const { type, value } = watch(`config.${index}`)
    const keyNames = Object.keys(configEditorFields)

    const componentError = errors?.config?.[index]
    return (
        <fieldset className={`border ${componentError ? 'invalid' : ''}`}>
            <Grid colSpec="3" auxClasses="gap-2 p-2">
                <FormSelectWithOptions
                    name={`config.${index}.type`}
                    optionValues={keyNames}
                    onChange={(e: SyntheticEvent<HTMLSelectElement>) => {
                        const type = e.currentTarget.value
                        if (!isConfigItem(type))
                            throw Error('failed test for config key')
                        const value = configEditorFields[type].default
                        clearErrors(`config.${index}.value`)
                        update({ type, value })
                    }}
                />
                <SetItem index={index} type={type} value={value} />
                <FormInput
                    type="checkbox"
                    name={`config.${index}.selected`}
                    inpClasses="pl-2"
                />
            </Grid>
        </fieldset>
    )
}

export const ConfigEditor = () => {
    const [storedConfig, setStoredConfig] = useLocalConfig()
    const initConfig = () => {
        if (Object.keys(storedConfig).length > 0)
            return configToEditorFields(storedConfig)
        return [configEditorRowInit()]
    }

    const methods = useForm<ConfigEditorContainer>({
        defaultValues: {
            config: initConfig(),
        },
        resolver: zodResolver(ConfigEditorContainerSchema),
    })

    const doSubmit = (c: ConfigEditorContainer) => {
        const r = c.config.map((r) => {
            if (!isConfigItem(r.type)) throw Error('failed test for config key')
            return { ...r, type: r.type }
        })
        setStoredConfig(configEditorFieldsToConfig(r))
    }

    const {
        control,
        formState: { errors },
        watch,
    } = methods
    const { fields, append, update, remove } = useFieldArray({
        control,
        name: `config`,
    })
    const componentError = errors?.config
    const selecteds = getSelectedIndices(watch, `config`)

    return (
        <>
            <title>Config editor</title>
            <Centered>
                <h1 className="text-2xl">Config editor</h1>
            </Centered>
            <FormProvider {...methods}>
                <form onSubmit={methods.handleSubmit((e) => doSubmit(e))}>
                    <fieldset
                        className={`border ${componentError && 'invalid'}`}
                    >
                        <GridCol>
                            {fields.map((field, index) => (
                                <ConfigItem
                                    key={field.id}
                                    index={index}
                                    update={(row: ConfigEditorRow) =>
                                        update(index, row)
                                    }
                                />
                            ))}
                        </GridCol>
                    </fieldset>
                    <EditorCommands
                        isLoggedIn={false}
                        appendRow={() => append(configEditorRowInit())}
                        getSelected={() => selecteds}
                        removeRows={remove}
                        allowCommit={false}
                    />
                </form>
            </FormProvider>
        </>
    )
}
