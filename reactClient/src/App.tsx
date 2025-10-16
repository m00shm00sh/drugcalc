import { Tab, Tabs, TabList, TabPanel } from 'react-tabs'
import './App.css'
import 'react-tabs/style/react-tabs.css'
import { Login, Logout } from './components/Auth'
import { Calc } from './components/Calc'
import { BlendsEditor } from './components/item-editor/BlendsEditor'
import { CompoundsEditor } from './components/item-editor/CompoundsEditor'
import { ConfigEditor } from './components/item-editor/ConfigEditor'
import { FrequenciesEditor } from './components/item-editor/FrequenciesEditor'
import { useLocalToken } from './hooks/useLocalData'
import { Flex } from './widgets/RowCol'
import { Centered } from './widgets/Centered'

const App = () => {
    const loginToken = useLocalToken()[0]
    const isLoggedIn = !!loginToken
    const loginProps = { loginToken, isLoggedIn, }
    return (
        <>
            <title>Drugcalc</title>
            <Flex dir="col">
                <Tabs>
                    <Centered>
                        <TabList>
                            <Tab>
                                Calc
                            </Tab>
                            <Tab>
                                Editor
                            </Tab>
                            <Tab>
                                Auth
                            </Tab>
                        </TabList>
                    </Centered>
                    <TabPanel>
                        <Calc />
                    </TabPanel>
                    <TabPanel>
                        <Tabs>
                            <Centered>
                                <TabList>
                                    <Tab>Compounds</Tab>
                                    <Tab>Blends</Tab>
                                    <Tab>Frequencies</Tab>
                                    <Tab>Config</Tab>
                                </TabList>
                            </Centered>
                            <TabPanel>
                                <CompoundsEditor {...loginProps} />
                            </TabPanel>
                            <TabPanel>
                                <BlendsEditor {...loginProps} />
                            </TabPanel>
                            <TabPanel>
                                <FrequenciesEditor {...loginProps} />
                            </TabPanel>
                            <TabPanel>
                                <ConfigEditor />
                            </TabPanel>
                        </Tabs>
                    </TabPanel>
                    <TabPanel>
                        {isLoggedIn
                            ? <Logout />
                            : <Login />
                        }
                    </TabPanel>
                </Tabs>
            </Flex>
        </>
    )
}

export default App
