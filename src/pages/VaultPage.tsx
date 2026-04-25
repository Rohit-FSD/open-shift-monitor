import Sidebar from "../components/layout/Sidebar"
import Navbar from "../components/layout/Navbar"
import VaultSecretsPanel from "../features/vault/VaultSecretsPanel"
import { KeyRound } from "lucide-react"

const VaultPage = () => {
  return (
    <div className="flex min-h-screen bg-slate-950 text-white">
      <Sidebar />
      <div className="flex-1 flex flex-col">
        <Navbar />
        <div className="p-6 max-w-5xl mx-auto w-full">
          <div className="flex items-center gap-2 mb-6">
            <KeyRound size={20} className="text-blue-400" />
            <h1 className="text-xl font-semibold">CSM Vault</h1>
          </div>
          <VaultSecretsPanel />
        </div>
      </div>
    </div>
  )
}

export default VaultPage
