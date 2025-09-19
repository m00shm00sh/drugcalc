import { type ReactNode } from 'react'

interface RCWidget {
    children: ReactNode;
    grid?: boolean;
}
const RC =
    (what: string) =>
        ({ children, grid }: RCWidget) => {
            let className = what
            if (grid) className += ' grid'
            return <div className={className}>{children}</div>
        }

export const Row = RC('row')
export const Column = RC('column')
