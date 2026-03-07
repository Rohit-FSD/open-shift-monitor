import { RefreshCw } from "lucide-react"
import { useState } from "react"

const ControlBar = () => {

  const [autoRefresh, setAutoRefresh] = useState(true)

  return (
    <div className="flex items-center justify-between mb-6">

      {/* Left Controls */}
      <div className="flex items-center gap-4">

        <select className="bg-slate-800 border border-slate-700 px-3 py-2 rounded-md text-sm">
          <option>PROD</option>
          <option>QA</option>
          <option>DEV</option>
        </select>

        <select className="bg-slate-800 border border-slate-700 px-3 py-2 rounded-md text-sm">
          <option>GA</option>
          <option>Release</option>
        </select>

        <select className="bg-slate-800 border border-slate-700 px-3 py-2 rounded-md text-sm">
          <option>Namespace: 22429</option>
          <option>Namespace: 22430</option>
        </select>

      </div>

      {/* Right Controls */}
      <div className="flex items-center gap-4">

        <button className="flex items-center gap-2 bg-slate-800 border border-slate-700 px-3 py-2 rounded-md hover:bg-slate-700 transition">
          <RefreshCw size={16} />
          Refresh
        </button>

        <div className="flex items-center gap-2 text-sm">
          <span>Auto Refresh</span>

          <button
            onClick={() => setAutoRefresh(!autoRefresh)}
            className={`w-10 h-5 rounded-full transition ${
              autoRefresh ? "bg-green-500" : "bg-slate-600"
            }`}
          >
            <div
              className={`h-5 w-5 bg-white rounded-full transform transition ${
                autoRefresh ? "translate-x-5" : "translate-x-0"
              }`}
            />
          </button>

        </div>

      </div>

    </div>
  )
}

export default ControlBar