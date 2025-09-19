export const ErrorMessage = ({ text }: { text?: string }) => (
    <div className={'text-red-600 text-sm align-middle text-center'}>
        {text}
    </div>
)
