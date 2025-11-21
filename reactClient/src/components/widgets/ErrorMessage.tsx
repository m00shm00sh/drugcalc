import { Centered } from "./Centered"

export const ErrorMessage = ({ text }: { text?: string }) => (
    <Centered className='text-red-600 text-sm'>{text}</Centered>
)
export const BigErrorMessage = ({ text }: { text?: string }) => (
    <Centered className='text-red-600 text-xl'>{text}</Centered>
)
