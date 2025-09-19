export const Button = (
    props: React.DetailedHTMLProps<
        React.ButtonHTMLAttributes<HTMLButtonElement>,
        HTMLButtonElement
    >,
) => {
    return (
        <div className="button-container">
            <button {...props} />
        </div>
    )
}
