import type { ReactNode } from "react"
import { BigErrorMessage } from "./ErrorMessage"

type RequireContentProps = {
    predicate: boolean
    errorText: string
    children: ReactNode
}
export const RequireContent = ({
    predicate,
    errorText,
    children,
}: RequireContentProps) =>
    predicate ? children : <BigErrorMessage text={errorText} />
