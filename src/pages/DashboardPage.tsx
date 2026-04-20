import { useState } from "react"

import Sidebar from "../components/layout/Sidebar"
import Navbar from "../components/layout/Navbar"
import ControlBar from "../components/layout/ControlBar"

import ServicesTable from "../features/dashboard/ServicesTable"
import SuccessRateCard from "../features/dashboard/SuccessRateCard"
import ApplicationFailures from "../features/dashboard/ApplicationFailures"

import useFetch from "../hooks/useFetch"

const DashboardPage = () => {
  const [env, setEnv] = useState<string>("")

  const {
    data: servicesData,
    refetch: refreshServices,
  } = useFetch(
    env ? `http://localhost:8080/api/deployments/status/env/${env}` : null
  )

  const refreshAll = () => {
    refreshServices && refreshServices()
  }

  const handleEnvChange = (selectedEnv: string) => {
    setEnv(selectedEnv)
  }

  return (
    <div className="flex min-h-screen bg-slate-950 text-white">
      <Sidebar />
      <div className="flex-1 flex flex-col">
        <Navbar />
        <div className="p-6 max-w-7xl mx-auto w-full min-h-screen">

          <ControlBar onRefresh={refreshAll} onEnvChange={handleEnvChange} />

          <ServicesTable services={servicesData || []} env={env} />

          <div className="mt-6">
            <SuccessRateCard env={env} />
          </div>

          <ApplicationFailures env={env} />

        </div>
      </div>
    </div>
  )
}

export default DashboardPage
