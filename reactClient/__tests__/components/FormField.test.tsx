import { cleanup, render } from '@testing-library/react'
import {jest} from '@jest/globals'
import '@testing-library/jest-dom'
import './__class_list'

import { FormInput, FormSelectWithOptions, FormTextArea } from '../../src/components/FormField'
import { FormProvider, useForm } from 'react-hook-form'
import z from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'

describe('FormInput', () => {
    afterEach(cleanup)

    it('for text input', async () => {
        const zTextForm = z.object({a: z.object({b: z.string().regex(/a/, 'bb')})})
        type TextForm = z.infer<typeof zTextForm>
        const onSubmit = jest.fn()
        const FormHolder = () => {
            const methods = useForm<TextForm>({resolver: zodResolver(zTextForm)})
            return <form onSubmit={methods.handleSubmit(onSubmit)}>
                <FormProvider {...methods}>
                    <FormInput type='text' placeholder='a' name='a.b' minLength={1} maxLength={2} />
                    <button type='submit'>sub</button>
                </FormProvider>
            </form>
        }
        const {getByPlaceholderText, getByText, findByText, rerender} = render(<FormHolder/>)
        const inputElem = getByPlaceholderText('a') as HTMLInputElement
        const subBtn = getByText('sub') as HTMLButtonElement

        expect(inputElem.type).toBe('text')
        expect(inputElem.minLength).toBe(1)
        expect(inputElem.maxLength).toBe(2)
        inputElem.value = 'q'
        subBtn.click()
        rerender(<FormHolder/>)
        const e = await findByText('bb')
        expect(e).not.toBeNull()
        expect(onSubmit.mock.calls.length).toBe(0)
    })

    it('for number input', async () => {
        const zTextForm = z.object({a: z.object({b: z.number().min(5, 'bb')})})
        type TextForm = z.infer<typeof zTextForm>
        const onSubmit = jest.fn()
        const FormHolder = () => {
            const methods = useForm<TextForm>({
                resolver: zodResolver(zTextForm),
                defaultValues: {a: {b: 0}}
            })
            return <form onSubmit={methods.handleSubmit(onSubmit)}>
                <FormProvider {...methods}>
                    <FormInput type='number' placeholder='a' name='a.b' min={1} max={3} step={2} />
                    <button type='submit'>sub</button>
                </FormProvider>
            </form>
        }
        const {getByPlaceholderText, getByText, findByText, rerender} = render(<FormHolder/>)
        const inputElem = getByPlaceholderText('a') as HTMLInputElement
        const subBtn = getByText('sub') as HTMLButtonElement
        expect(inputElem.type).toBe('number')
        expect(inputElem.min).toBe('1')
        expect(inputElem.max).toBe('3')
        expect(inputElem.step).toBe('2')
        inputElem.value = '3'
        subBtn.click()
        rerender(<FormHolder/>)
        const e = await findByText('bb')
        expect(e).not.toBeNull()
        expect(onSubmit.mock.calls.length).toBe(0)
    })

    it('for readonly text input', async () => {
        const FormHolder = () => {
            const methods = useForm()
            return <form>
                <FormProvider {...methods}>
                    {/* FIXME: in FormField.tsx name should be nullable field; clean this up when that occurs */}
                    <FormInput type='text' name='' roValue='bb' />
                </FormProvider>
            </form>
        }
        expect(() => render(<FormHolder/>)).not.toThrow()
    })

    it('for readonly text input (name&&ro)', async () => {
        const FormHolder = () => {
            const methods = useForm()
            return <form>
                <FormProvider {...methods}>
                    <FormInput type='text' name='a' roValue='bb' />
                </FormProvider>
            </form>
        }
        expect(() => render(<FormHolder/>)).toThrow()
    })

    it('for password input', async () => {
        const FormHolder = () => {
            const methods = useForm()
            return <form>
                <FormProvider {...methods}>
                    <FormInput type='password' placeholder='a' name='a.b' minLength={1} maxLength={2} />
                    <button type='submit'>sub</button>
                </FormProvider>
            </form>
        }
        const {getByPlaceholderText} = render(<FormHolder/>)
        const inputElem = getByPlaceholderText('a') as HTMLInputElement
        expect(inputElem.type).toBe('password')
        expect(inputElem.minLength).toBe(1)
        expect(inputElem.maxLength).toBe(2)
    })

    it('for select from array', async () => {
        const opts = ['a', 'b']
        const FormHolder = () => {
            const methods = useForm()
            return <form>
                <FormProvider {...methods}>
                    <FormSelectWithOptions name='a' optionValues={opts} />
                    <button type='submit'>sub</button>
                </FormProvider>
            </form>
        }
        const {getAllByRole} = render(<FormHolder/>)
        const opts$ = Object.fromEntries(
            (getAllByRole('option') as HTMLOptionElement[])
                .map(e => [e.value, e.text])
        )
        expect(Object.keys(opts$)).toEqual(opts)
        expect(Object.values(opts$)).toEqual(opts)
    })

    it('for select from map', async () => {
        const opts = {'a': 'b', 'c': 'd'}
        const FormHolder = () => {
            const methods = useForm()
            return <form>
                <FormProvider {...methods}>
                    <FormSelectWithOptions name='a' optionValues={opts} />
                    <button type='submit'>sub</button>
                </FormProvider>
            </form>
        }
        const {getAllByRole} = render(<FormHolder/>)
        const opts$ = Object.fromEntries(
            (getAllByRole('option') as HTMLOptionElement[])
                .map(e => [e.text, e.value])
        )
        expect(opts$).toEqual(opts)
    })

    it('for select error display', async () => {
        const zTextForm = z.object({a: z.string().regex(/a/, 'bb')})
        type TextForm = z.infer<typeof zTextForm>
        const onSubmit = jest.fn()
        const FormHolder = () => {
            const methods = useForm<TextForm>({resolver: zodResolver(zTextForm)})
            const opts = ['b']
            return <form onSubmit={methods.handleSubmit(onSubmit)}>

                <FormProvider {...methods}>
                    <FormSelectWithOptions name='a' optionValues={opts} defaultValue='a' />
                    <button type='submit'>sub</button>
                </FormProvider>
            </form>
        }
        const {getByText, findByText, rerender} = render(<FormHolder/>)
        const subBtn = getByText('sub') as HTMLButtonElement
        subBtn.click()
        rerender(<FormHolder/>)
        const e = await findByText('bb')
        expect(e).not.toBeNull()
        expect(onSubmit.mock.calls.length).toBe(0)
    })

    it('for select with empty array optionValues renders nothing', () => {
        const FormHolder = () => {
            const methods = useForm()
            return <form>
                <FormProvider {...methods}>
                    <FormSelectWithOptions name='a' optionValues={[]} />
                    <button type='submit'>sub</button>
                </FormProvider>
            </form>
        }
        const {queryByRole} = render(<FormHolder/>)
        expect(queryByRole('combobox')).toBeNull()
    })

    it('for select with empty object optionValues renders nothing', () => {
        const FormHolder = () => {
            const methods = useForm()
            return <form>
                <FormProvider {...methods}>
                    <FormSelectWithOptions name='a' optionValues={{}} />
                    <button type='submit'>sub</button>
                </FormProvider>
            </form>
        }
        const {queryByRole} = render(<FormHolder/>)
        expect(queryByRole('combobox')).toBeNull()
    })

    it('for textarea', async () => {
        const zTextForm = z.object({a: z.string()})
        type TextForm = z.infer<typeof zTextForm>
        const FormHolder = () => {
            const methods = useForm<TextForm>({resolver: zodResolver(zTextForm)})
            return <form>
                <FormProvider {...methods}>
                    <FormTextArea placeholder='a' name='a' />
                    <button type='submit'>sub</button>
                </FormProvider>
            </form>
        }
        const {getByPlaceholderText} = render(<FormHolder/>)
        const textarea = getByPlaceholderText('a') as HTMLTextAreaElement
        expect(textarea).not.toBeNull()
        textarea.value = 'hello'
        expect(textarea.value).toBe('hello')
    })


})
/*

export function FormTextArea<T extends FieldValues>({
    blockClasses,
    inpClasses,
    placeholder,
    label,
    name,
    onChange,
}: FieldProps<T, HTMLTextAreaElement> & Partial<FieldHasPlaceholder>): ReactElement {
    const {
        register,
        formState: { errors },
    } = useFormContext<T>()
    const inputClasses = [...(inpClasses ?? [])]
    const errorValue = getChain<FieldError>(errors, name)
    return (
        <Flex dir="col" auxClasses={blockClasses}>
            <OptionalLabel label={label} />
            <textarea
                className={classNames(
                    'bg-gray-400 text-gray-900',
                    'focus:ring',
                    'p-2 resize-x',
                    ...inputClasses
                )}
                placeholder={placeholder}
                {...register(name, { onChange })}
            />
            <ErrorMessage text={errorValue?.message} />
        </Flex>
    )
}
*/