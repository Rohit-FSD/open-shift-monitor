import { useEffect, useState } from "react"
import { RefreshCw } from "lucide-react"

interface Props {
  onRefresh?: () => void
}

const ControlBar = ({ onRefresh }: Props) => {

  const [autoRefresh, setAutoRefresh] = useState(true)
  const [lastUpdated, setLastUpdated] = useState("Just now")

  const handleRefresh = () => {

    if (onRefresh) onRefresh()

    setLastUpdated("Just now")

  }

  useEffect(() => {

    if (!autoRefresh) return

    const interval = setInterval(() => {

      if (onRefresh) onRefresh()

      setLastUpdated("Just now")

    }, 30000)

    return () => clearInterval(interval)

  }, [autoRefresh])

  return (

    <div className="flex items-center justify-between mb-6">

      {/* LEFT SIDE */}

      <div className="flex items-center gap-4">

        <select className="bg-slate-800 px-3 py-2 rounded text-sm">
          <option>PROD</option>
        </select>

        <select className="bg-slate-800 px-3 py-2 rounded text-sm">
          <option>GA</option>
        </select>

        <select className="bg-slate-800 px-3 py-2 rounded text-sm">
          <option>Namespace: 22429</option>
        </select>

      </div>

      {/* RIGHT SIDE */}

      <div className="flex items-center gap-4 text-sm">

        <button
          onClick={handleRefresh}
          className="flex items-center gap-2 bg-slate-800 px-3 py-2 rounded hover:bg-slate-700"
        >
          <RefreshCw size={14} />
          Refresh
        </button>

        <div className="flex items-center gap-2">

          <span>Auto Refresh</span>

          <button
            onClick={() => setAutoRefresh(!autoRefresh)}
            className={`w-10 h-5 rounded-full relative ${
              autoRefresh ? "bg-green-500" : "bg-gray-500"
            }`}
          >
            <div
              className={`w-4 h-4 bg-white rounded-full absolute top-0.5 transition ${
                autoRefresh ? "right-0.5" : "left-0.5"
              }`}
            />
          </button>

        </div>

        <div className="text-slate-400">
          Last updated: {lastUpdated}
        </div>

      </div>

    </div>

  )

}

export default ControlBar