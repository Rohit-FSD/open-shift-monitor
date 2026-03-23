import { useState } from "react"

import Sidebar from "../components/layout/Sidebar"
import Navbar from "../components/layout/Navbar"
import ControlBar from "../components/layout/ControlBar"

import ClusterOverview from "../features/dashboard/ClusterOverview"
import ServicesTable from "../features/dashboard/ServicesTable"
import QAImpact from "../features/dashboard/QAImpact"
import Recommendations from "../features/dashboard/Recommendations"
import SLAMetrics from "../features/dashboard/SLAMetrics"

import useFetch from "../hooks/useFetch"

const DashboardPage = () => {

  const [env, setEnv] = useState<string>("")
  const [namespace, setNamespace] = useState<string>("")

  // 🔥 dynamic APIs based on ENV

  const {
    data: slaData,
    refetch: refreshSla
  } = useFetch(
    env
      ? `http://localhost:8080/api/sla/report/${env}`
      : null
  )

  const {
    data: servicesData,
    refetch: refreshServices
  } = useFetch(
    env
      ? `http://localhost:8080/api/deployments/status/env/${env}`
      : null
  )

  // 🔁 refresh all

  const refreshAll = () => {

    refreshSla && refreshSla()
    refreshServices && refreshServices()

  }

  // 🎯 handle env change from control bar

  const handleEnvChange = (selectedEnv: string, ns: string) => {

    setEnv(selectedEnv)
    setNamespace(ns)

  }

  return (

    <div className="flex min-h-screen bg-slate-950 text-white">

      <Sidebar />

      <div className="flex-1 flex flex-col">

        <Navbar />

        <div className="p-6 max-w-7xl mx-auto w-full min-h-screen">

          <ControlBar
            onRefresh={refreshAll}
            onEnvChange={handleEnvChange}
          />

          <ClusterOverview slaData={slaData} />

          <div className="mt-6">
            <ServicesTable
              services={servicesData || []}
              env={env}
            />
          </div>

          <div className="grid grid-cols-3 gap-6 mt-6">

            <SLAMetrics data={slaData} />

            <QAImpact data={slaData} />

            <Recommendations data={slaData} />

          </div>

        </div>

      </div>

    </div>

  )
}

export default DashboardPage