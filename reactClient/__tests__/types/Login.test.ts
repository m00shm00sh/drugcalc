import { describe, it } from '@jest/globals'
import { expectParseFail, expectParseOk } from './__parse_okfail'
import { LoginRequestSchema, LoginResponseSchema } from '../../src/types/Login'

describe('LoginRequest', () => {
    it('should require nonempty username',
        expectParseFail(LoginRequestSchema, { name: '', pass: '1' })
    )
    it('should require nonempty password',
        expectParseFail(LoginRequestSchema, { name: '1', pass: '' })
    )
})
describe('LoginResponse', () => {
    it('should have a jwt in token', () => {
        expectParseOk(LoginResponseSchema,
            // this is a synctactically valid JWT but not an actual one because there's no exp
            { token: 'eyJhbGciOiJIUzI1NiJ9.eyJwYXlsb2FkIjoiMSJ9.pjn2adl1sEOVoy21vNYhOBD8fFFW7faEKA_TJteJQSU' }
        )()
    })
    it('should reject a non-jwt token',
        expectParseFail(LoginResponseSchema, { token: 'a' })
    )
})
