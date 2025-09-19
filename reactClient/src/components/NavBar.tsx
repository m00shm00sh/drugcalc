import { Link } from 'react-router-dom'

interface LoginProps {
    isLoggedIn: boolean;
}
export const NavBar = ({ isLoggedIn }: LoginProps) => {
    return (
        <nav>
            <div className="nav grid row">
                <div>
                    <Link className="btn" to="/edit/compounds">
                        Compounds editor
                    </Link>
                </div>
                <div>
                    <Link className="btn" to="/edit/blends">
                        Blends editor
                    </Link>
                </div>
                <div>
                    <Link className="btn" to="/edit/frequencies">
                        Frequencies editor
                    </Link>
                </div>
            </div>
        </nav>
    )
}
