import { RefreshCw } from "lucide-react"

const ControlBar = () => {

  return (

    <div className="flex items-center justify-between mb-6">

      {/* LEFT */}

      <div className="flex items-center gap-4">

        <input
          placeholder="Search service..."
          className="bg-slate-800 text-sm px-4 py-2 rounded w-64 outline-none"
        />

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

      {/* RIGHT */}

      <div className="flex items-center gap-4 text-sm">

        <button onClick={() => window.location.reload()} className="flex items-center gap-2 bg-slate-800 px-3 py-2 rounded hover:bg-slate-700">

          <RefreshCw size={14} />

          Refresh

        </button>

        <div className="flex items-center gap-2">

          <span>Auto Refresh</span>

          <div  className="w-10 h-5 bg-green-500 rounded-full relative">

            <div className="w-4 h-4 bg-white rounded-full absolute top-0.5 right-0.5" />

          </div>

        </div>

        <div className="text-slate-400">

          Last updated: Just now

        </div>

      </div>

    </div>

  )
}

export default ControlBar