import { useEffect, useState } from "react"
import useFetch from "../../hooks/useFetch"

interface Props {
  onRefresh: () => void
}

const ControlBar = ({ onRefresh }: Props) => {

  const { data, loading, error } = useFetch(
    "http://localhost:8080/api/openshift/environments"
  )

  const [selectedEnv, setSelectedEnv] = useState<string>("")
  const [namespace, setNamespace] = useState<string>("")

  useEffect(() => {

    if (data && Object.keys(data).length > 0) {

      const firstEnv = Object.keys(data)[0]

      setSelectedEnv(firstEnv)

      setNamespace(data[firstEnv]?.namespace || "")

    }

  }, [data])

  // 🔁 When env changes
  const handleEnvChange = (env: string) => {

    setSelectedEnv(env)

    setNamespace(data?.[env]?.namespace || "")

  }

  return (

    <div className="flex items-center justify-between mb-6">

      {/* LEFT SIDE */}

      <div className="flex gap-3 items-center">

        {/* ENV DROPDOWN */}

        <select
          value={selectedEnv}
          onChange={(e) => handleEnvChange(e.target.value)}
          className="bg-slate-800 border border-slate-700 px-3 py-2 rounded-md text-sm"
        >

          {loading && <option>Loading...</option>}

          {!loading && data &&
            Object.keys(data).map((env) => (

              <option key={env} value={env}>
                {env}
              </option>

            ))}

        </select>

        {/* REALM (OPTIONAL if needed later) */}

        <div className="bg-slate-800 border border-slate-700 px-3 py-2 rounded-md text-sm text-slate-300">

          {data?.[selectedEnv]?.realm || "-"}

        </div>

        {/* NAMESPACE → READONLY INPUT ✅ */}

        <input
          value={namespace}
          readOnly
          className="bg-slate-800 border border-slate-700 px-3 py-2 rounded-md text-sm w-[140px] text-slate-300"
        />

      </div>

      {/* RIGHT SIDE */}

      <div className="flex items-center gap-3">

        <button
          onClick={onRefresh}
          className="bg-slate-800 border border-slate-700 px-4 py-2 rounded-md text-sm hover:bg-slate-700"
        >
          🔄 Refresh
        </button>

        {/* AUTO REFRESH TOGGLE (UI only for now) */}

        <div className="flex items-center gap-2 text-sm">

          <span className="text-slate-400">Auto Refresh</span>

          <div className="w-10 h-5 bg-green-500 rounded-full relative cursor-pointer">

            <div className="absolute right-1 top-1 w-3 h-3 bg-white rounded-full"></div>

          </div>

        </div>

      </div>

    </div>

  )
}

export default ControlBar