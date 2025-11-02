import { z } from 'zod'

export const zodOptBool = z.literal([true, false]).optional()
export const SelectableSchema = z.object({'selected': zodOptBool})
export type Selectable = z.infer<typeof SelectableSchema>
