export const ErrorMessage = ({ text }: { text?: string }) => (
    <div className={'text-red-600 text-sm align-middle text-center'}>{text}</div>
)
export const BigErrorMessage = ({ text }: { text?: string }) => (
    <div className={'text-red-600 text-xl align-middle text-center'}>{text}</div>
)
