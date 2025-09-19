export const Centered = ({
    className,
    ...props
}: React.DetailedHTMLProps<
    React.HTMLAttributes<HTMLDivElement>,
    HTMLDivElement
>) => {
    className ??= ''
    if (className) className = ' ' + className
    return <div className={'align-middle text-center' + className} {...props} />
}
