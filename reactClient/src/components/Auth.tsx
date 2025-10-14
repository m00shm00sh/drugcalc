import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useState } from 'react'
import { FormProvider, useForm } from 'react-hook-form'
import { useLocalToken } from '../hooks/useLocalData'
import type { LoginRequest } from '../types/Login'
import { LoginRequestSchema, LoginResponseSchema } from '../types/Login'
import { postJson } from '../util/fetcher'
import { Button } from '../widgets/Button'
import { Centered } from '../widgets/Centered'
import { Flex } from '../widgets/RowCol'
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
        <>
            <Centered>
                <h1 className="text-2xl">Login</h1>
            </Centered>
            <FormProvider {...methods}>
                <form onSubmit={methods.handleSubmit((l) => doLogin(l))}>
                    <Flex dir='col' auxClasses={['gap-2']}>
                        <FormInput name="name" type="text"
                            label="user" placeholder="user"
                        />
                        <FormInput name="pass" type="password"
                            label="password" placeholder="password"
                        />
                        <Button type="submit">Login</Button>
                    </Flex>
                </form>
            </FormProvider>
        </>
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
