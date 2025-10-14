import { Link } from 'react-router-dom'
import { Flex } from '../widgets/RowCol'

interface LoginProps {
    isLoggedIn: boolean
}
export const NavBar = ({ isLoggedIn }: LoginProps) => {
    return (
        <nav>
            <Flex dir="col">
                <Flex dir="row">
                    <div className="px-2">
                        <Link className="bg-gray-400 rounded-xl px-2" to="/">
                            Calc
                        </Link>
                    </div>
                </Flex>
                <Flex dir="row">
                    <div className="px-2">
                        <Link className="bg-gray-400 rounded-xl px-2" to="/edit/compounds">
                            Compounds editor
                        </Link>
                    </div>
                    <div className="px-2">
                        <Link className="bg-gray-400 rounded-xl px-2" to="/edit/blends">
                            Blends editor
                        </Link>
                    </div>
                    <div className="px-2">
                        <Link className="bg-gray-400 rounded-xl px-2" to="/edit/frequencies">
                            Frequencies editor
                        </Link>
                    </div>
                    <div className="px-2">
                        <Link className="bg-gray-400 rounded-xl px-2" to="/edit/config">
                            Config Editor
                        </Link>
                    </div>
                </Flex>
                <Flex dir="row" auxClasses="px-2">
                    {isLoggedIn ? (
                        <Link className="bg-gray-400 rounded-xl px-2" to="/auth/logout">
                            Logout
                        </Link>
                    ) : (
                        <Link className="bg-gray-400 rounded-xl px-2" to="/auth/login">
                            Login
                        </Link>
                    )}
                </Flex>
            </Flex>
        </nav>
    )
}
