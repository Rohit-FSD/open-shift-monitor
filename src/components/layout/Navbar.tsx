import { Search } from "lucide-react"

const Navbar = () => {

  return (

    <div className="h-14 flex items-center justify-between px-6 border-b border-slate-800 bg-slate-900">

      {/* SEARCH */}

      <div className="relative">

        <Search
          size={16}
          className="absolute left-3 top-2.5 text-slate-400"
        />

        <input
          placeholder="Search service..."
          className="bg-slate-800 text-sm pl-9 pr-4 py-2 rounded w-64 outline-none"
        />

      </div>

      {/* RIGHT SIDE */}

      <div className="text-sm text-slate-400">
        Last updated: Just now
      </div>

    </div>

  )

}

export default Navbar