import classNames from "classnames"

export const Centered = ({
    className,
    ...props
}: React.DetailedHTMLProps<React.HTMLAttributes<HTMLDivElement>, HTMLDivElement>) =>
    <div className={classNames(
        `align-middle text-center`,
        className
    )} {...props} />
