import { useState } from "react"
import Sidebar from "../components/layout/Sidebar"
import Navbar from "../components/layout/Navbar"
import ControlBar from "../components/layout/ControlBar"
import JourneyLogSearch from "../features/dashboard/JourneyLogSearch"

const JourneyLogsPage = () => {
  const [env, setEnv] = useState<string>("")

  const handleEnvChange = (selectedEnv: string) => setEnv(selectedEnv)

  return (
    <div className="flex min-h-screen bg-slate-950 text-white">
      <Sidebar />
      <div className="flex-1 flex flex-col">
        <Navbar />
        <div className="p-6 max-w-7xl mx-auto w-full">
          <ControlBar onRefresh={() => {}} onEnvChange={handleEnvChange} />
          <div className="mb-6">
            <h1 className="text-2xl font-semibold text-white">Journey Logs</h1>
            <p className="text-slate-400 text-sm mt-1">
              Search and download pod logs by Correlation ID or Transaction ID
            </p>
          </div>
          <JourneyLogSearch env={env} />
        </div>
      </div>
    </div>
  )
}

export default JourneyLogsPage
