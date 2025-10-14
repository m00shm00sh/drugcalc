import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useState } from 'react'
import { FormProvider, useForm } from 'react-hook-form'
import { useLocalToken } from '../hooks/useLocalData'
import type { LoginRequest } from '../types/Login'
import { LoginRequestSchema, LoginResponseSchema } from '../types/Login'
import { postJson } from '../util/fetcher'
import { Button } from '../widgets/Button'
import { Centered } from '../widgets/Centered'
import { Grid } from '../widgets/RowCol'
import { FormInput } from './FormField'

export const Login = () => {
    const [, setToken] = useLocalToken()
    const [didLogin, setDidLogin] = useState(false)
    const doLogin = async (req: LoginRequest) => {
        const response = await postJson('/api/login', LoginResponseSchema, req)
        setToken(response.token)
        setDidLogin(true)
    }

    const methods = useForm({
        defaultValues: {},
        resolver: zodResolver(LoginRequestSchema),
    })
    return didLogin ? (
        <div className="flex justify-center">
            <Centered>
                <h1 className="text-2xl">Login complete</h1>
            </Centered>
        </div>
    ) : (
        <FormProvider {...methods}>
            <form onSubmit={methods.handleSubmit((l) => doLogin(l))}>
                <Grid colSpec={2}>
                    <div>user</div>
                    <FormInput name="name" placeholder="user" type="text" />
                    <div>password</div>
                    <FormInput name="pass" placeholder="password" type="password" />
                    <Button type="submit">Login</Button>
                </Grid>
            </form>
        </FormProvider>
    )
}

export const Logout = () => {
    const [token, setToken] = useLocalToken()
    // have to move the clear out of the render path
    useEffect(() => {
        if (token) {
            setToken('')
        }
    }, [token, setToken])
    return (
        <div className="flex justify-center">
            <Centered>
                <h1 className="text-2xl">Logout complete</h1>
            </Centered>
        </div>
    )
}
