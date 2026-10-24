import { act, cleanup, fireEvent, render } from '@testing-library/react'
import '@testing-library/jest-dom'
import './__class_list'

import { Login, Logout } from '../../src/components/Auth'
import { mockNextFetch, resetMock } from '../__fetch_utils'

describe('Login', () => {
    afterEach(() => {
        cleanup()
        resetMock()
    })

    it('renders the login heading', () => {
        const {getByRole} = render(<Login />)
        expect(getByRole('heading', {name: 'Login'})).not.toBeNull()
    })

    it('renders the login form fields', () => {
        const {getByPlaceholderText, getByRole} = render(<Login />)
        expect(getByPlaceholderText('user')).not.toBeNull()
        expect(getByPlaceholderText('password')).not.toBeNull()
        expect(getByRole('button', {name: 'Login'})).not.toBeNull()
    })

    it('logs in successfully and shows completion', async () => {
        const token = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c'
        mockNextFetch({token})
        const {getByPlaceholderText, getByRole, findByText} = render(<Login />)

        fireEvent.change(getByPlaceholderText('user'), {target: {value: 'testuser'}})
        fireEvent.change(getByPlaceholderText('password'), {target: {value: 'testpass'}})

        const btn = getByRole('button', {name: 'Login'}) as HTMLButtonElement
        await act(async () => {
            fireEvent.click(btn)
        })

        await findByText('Login complete')
    })

    it('shows a validation error on empty submit', async () => {
        const {getByRole, findAllByText} = render(<Login />)
        const btn = getByRole('button', {name: 'Login'}) as HTMLButtonElement
        fireEvent.click(btn)
        const errors = await findAllByText(/Too small/)
        expect(errors.length).toBe(2)
    })
})

describe('Logout', () => {
    afterEach(() => {
        cleanup()
        resetMock()
    })

    it('renders the logout complete heading', () => {
        const {getByRole} = render(<Logout />)
        expect(getByRole('heading', {name: 'Logout complete'})).not.toBeNull()
    })

    it('clears the token when one is stored', () => {
        const getStorage = window.localStorage.getItem as jest.Mock
        const setItemMock = window.localStorage.setItem as jest.Mock
        // useLocalStorage from usehooks-ts parses localStorage values as JSON
        getStorage.mockImplementation((k: string) => k === 'login' ? JSON.stringify('stored-token') : null)
        act(() => {
            render(<Logout />)
        })
        // useEffect runs after render; token is truthy so setToken('') is called at line 59
        expect(setItemMock).toHaveBeenCalledWith('login', '""')
    })
})
