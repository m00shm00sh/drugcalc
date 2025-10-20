import { Tab, TabList, TabPanel, Tabs } from 'react-tabs'
import 'react-tabs/style/react-tabs.css'
import './App.css'
import { Login, Logout } from './components/Auth'
import { Calc } from './components/Calc'
import { BlendsDeleter } from './components/item-editor/BlendsDeleter'
import { BlendsEditor } from './components/item-editor/BlendsEditor'
import { CompoundsDeleter } from './components/item-editor/CompoundsDeleter'
import { CompoundsEditor } from './components/item-editor/CompoundsEditor'
import { ConfigEditor } from './components/item-editor/ConfigEditor'
import { FrequenciesDeleter } from './components/item-editor/FrequenciesDeleter'
import { FrequenciesEditor } from './components/item-editor/FrequenciesEditor'
import { useLocalToken } from './hooks/useLocalData'
import { Centered } from './widgets/Centered'
import { Flex } from './widgets/RowCol'

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
                            { isLoggedIn &&
                                <Tab>
                                    Deleter
                                </Tab>
                            }
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
                    { isLoggedIn &&
                        <TabPanel>
                            <Tabs>
                                <Centered>
                                    <TabList>
                                        <Tab>Compounds</Tab>
                                        <Tab>Blends</Tab>
                                        <Tab>Frequencies</Tab>
                                    </TabList>
                                </Centered>
                                <TabPanel>
                                    <CompoundsDeleter {...loginProps} />
                                </TabPanel>
                                <TabPanel>
                                    <BlendsDeleter {...loginProps} />
                                </TabPanel>
                                <TabPanel>
                                    <FrequenciesDeleter {...loginProps} />
                                </TabPanel>
                            </Tabs>
                        </TabPanel>
                    }
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
