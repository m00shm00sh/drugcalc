import { z } from 'zod'

export const LoginRequestSchema = z.object({
    name: z.string().nonempty(),
    pass: z.string().nonempty(),
})
export type LoginRequest = z.infer<typeof LoginRequestSchema>
export const LoginResponseSchema = z.object({
    token: z.jwt(),
})
