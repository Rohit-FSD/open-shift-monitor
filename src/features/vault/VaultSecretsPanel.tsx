import { useEffect, useState } from "react"
import {
  KeyRound,
  RefreshCw,
  Eye,
  EyeOff,
  Copy,
  Check,
  ShieldOff,
  Database,
  AlertCircle,
} from "lucide-react"

interface ConfiguredResponse {
  enabled: boolean
  baseUrl: string
  role: string
  names: string[]
  paths: Record<string, string>
}

interface DbCredential {
  username: string
  password: string
  fetchedAt: string
  leaseSeconds: number
}

interface LoadedRow {
  username: string
  passwordLoaded: boolean
  fetchedAt: string
  leaseSeconds: number
}

const API = "http://localhost:8080/api/admin/vault"

const VaultSecretsPanel = () => {
  const [config, setConfig] = useState<ConfiguredResponse | null>(null)
  const [selected, setSelected] = useState<string>("")
  const [credential, setCredential] = useState<DbCredential | null>(null)
  const [loaded, setLoaded] = useState<Record<string, LoadedRow>>({})
  const [showPassword, setShowPassword] = useState(false)
  const [copied, setCopied] = useState<"username" | "password" | null>(null)
  const [loading, setLoading] = useState(false)
  const [invalidating, setInvalidating] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const loadConfig = async () => {
    try {
      const res = await fetch(`${API}/configured`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data: ConfiguredResponse = await res.json()
      setConfig(data)
      if (!selected && data.names.length > 0) setSelected(data.names[0])
    } catch (e: any) {
      setError(e.message || "Failed to load vault configuration")
    }
  }

  const loadLoaded = async () => {
    try {
      const res = await fetch(`${API}/secrets`)
      if (!res.ok) return
      setLoaded(await res.json())
    } catch {
      /* ignore */
    }
  }

  useEffect(() => {
    loadConfig()
    loadLoaded()
  }, [])

  const fetchSecret = async () => {
    if (!selected) return
    setLoading(true)
    setError(null)
    setCredential(null)
    setShowPassword(false)
    try {
      const res = await fetch(`${API}/secrets/${encodeURIComponent(selected)}/refresh`, {
        method: "POST",
      })
      if (!res.ok) {
        const text = await res.text()
        throw new Error(text || `HTTP ${res.status}`)
      }
      setCredential(await res.json())
      loadLoaded()
    } catch (e: any) {
      setError(e.message || "Failed to fetch secret")
    } finally {
      setLoading(false)
    }
  }

  const invalidateToken = async () => {
    setInvalidating(true)
    setError(null)
    try {
      const res = await fetch(`${API}/token/invalidate`, { method: "POST" })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
    } catch (e: any) {
      setError(e.message || "Failed to invalidate token")
    } finally {
      setInvalidating(false)
    }
  }

  const copy = async (value: string, which: "username" | "password") => {
    try {
      await navigator.clipboard.writeText(value)
      setCopied(which)
      setTimeout(() => setCopied(null), 1200)
    } catch {
      /* ignore */
    }
  }

  if (config && !config.enabled) {
    return (
      <div className="bg-slate-900 border border-slate-700 rounded-lg p-6 text-sm text-slate-400">
        <div className="flex items-center gap-2 mb-2">
          <ShieldOff size={16} className="text-amber-400" />
          <span className="text-white font-medium">CSM Vault is disabled</span>
        </div>
        Set <code className="text-amber-300">csm-vault.enabled=true</code> in application.yml.
      </div>
    )
  }

  return (
    <div className="space-y-6">

      {/* Picker + fetch */}
      <div className="bg-slate-900 border border-slate-700 rounded-lg p-5">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2">
            <KeyRound size={16} className="text-blue-400" />
            <h3 className="text-white font-medium text-sm">Fetch DB Credential</h3>
          </div>
          <button
            onClick={invalidateToken}
            disabled={invalidating}
            className="text-xs text-slate-400 hover:text-white flex items-center gap-1.5
              border border-slate-700 hover:border-slate-500 rounded px-2 py-1 disabled:opacity-50"
            title="Drop cached vault token; next fetch will re-authenticate"
          >
            <RefreshCw size={11} className={invalidating ? "animate-spin" : ""} />
            Invalidate token
          </button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-[1fr_auto] gap-3 mb-3">
          <div>
            <label className="block text-[10px] uppercase tracking-wider text-slate-500 mb-1">
              Database
            </label>
            <select
              value={selected}
              onChange={e => setSelected(e.target.value)}
              className="w-full bg-slate-800 border border-slate-700 rounded px-3 py-2 text-sm
                text-white focus:outline-none focus:border-blue-500"
            >
              {!config && <option value="">Loading…</option>}
              {config?.names.length === 0 && <option value="">No secrets configured</option>}
              {config?.names.map(n => (
                <option key={n} value={n}>{n}</option>
              ))}
            </select>
            {selected && config?.paths[selected] && (
              <div className="mt-1 text-[11px] text-slate-500 truncate">
                {config.paths[selected]}
              </div>
            )}
          </div>
          <div className="flex items-end">
            <button
              onClick={fetchSecret}
              disabled={!selected || loading}
              className="bg-blue-600 hover:bg-blue-500 disabled:opacity-50 disabled:cursor-not-allowed
                text-white text-sm font-medium rounded px-4 py-2 flex items-center gap-2 h-[38px]"
            >
              <RefreshCw size={14} className={loading ? "animate-spin" : ""} />
              {loading ? "Fetching…" : "Fetch password"}
            </button>
          </div>
        </div>

        {error && (
          <div className="mt-3 text-xs text-red-300 bg-red-900/30 border border-red-800/50
            rounded px-3 py-2 flex items-start gap-2">
            <AlertCircle size={12} className="mt-0.5 shrink-0" />
            <span className="break-all">{error}</span>
          </div>
        )}

        {credential && (
          <div className="mt-4 bg-slate-950 border border-slate-800 rounded p-4 space-y-3">
            <Field
              label="Username"
              value={credential.username}
              onCopy={() => copy(credential.username, "username")}
              copied={copied === "username"}
            />
            <Field
              label="Password"
              value={showPassword ? credential.password : "•".repeat(Math.min(credential.password.length, 16))}
              onCopy={() => copy(credential.password, "password")}
              copied={copied === "password"}
              right={
                <button
                  onClick={() => setShowPassword(s => !s)}
                  className="text-slate-400 hover:text-white p-1"
                  title={showPassword ? "Hide" : "Show"}
                >
                  {showPassword ? <EyeOff size={14} /> : <Eye size={14} />}
                </button>
              }
            />
            <div className="grid grid-cols-2 gap-3 pt-1 text-[11px] text-slate-500">
              <div>
                <span className="text-slate-600">Fetched: </span>
                {new Date(credential.fetchedAt).toLocaleString()}
              </div>
              <div>
                <span className="text-slate-600">Lease: </span>
                {credential.leaseSeconds}s
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Loaded secrets table */}
      <div className="bg-slate-900 border border-slate-700 rounded-lg p-5">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2">
            <Database size={16} className="text-purple-400" />
            <h3 className="text-white font-medium text-sm">Loaded Secrets</h3>
          </div>
          <button
            onClick={loadLoaded}
            className="text-xs text-slate-400 hover:text-white flex items-center gap-1.5"
          >
            <RefreshCw size={11} />
            Refresh
          </button>
        </div>

        {Object.keys(loaded).length === 0 ? (
          <div className="text-xs text-slate-500 py-4 text-center">
            No secrets loaded yet. Fetch one above to populate the cache.
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-[10px] uppercase tracking-wider text-slate-500 border-b border-slate-800">
                <th className="text-left py-2">Name</th>
                <th className="text-left py-2">Username</th>
                <th className="text-left py-2">Password</th>
                <th className="text-left py-2">Fetched At</th>
                <th className="text-left py-2">Lease</th>
              </tr>
            </thead>
            <tbody>
              {Object.entries(loaded).map(([name, row]) => (
                <tr key={name} className="border-b border-slate-800/60 hover:bg-slate-800/30">
                  <td className="py-2 text-blue-300 font-mono text-xs">{name}</td>
                  <td className="py-2 text-slate-300 font-mono text-xs">{row.username}</td>
                  <td className="py-2">
                    {row.passwordLoaded ? (
                      <span className="text-green-400 text-xs flex items-center gap-1">
                        <Check size={11} /> loaded
                      </span>
                    ) : (
                      <span className="text-slate-500 text-xs">—</span>
                    )}
                  </td>
                  <td className="py-2 text-slate-400 text-xs">
                    {new Date(row.fetchedAt).toLocaleString()}
                  </td>
                  <td className="py-2 text-slate-400 text-xs">{row.leaseSeconds}s</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {config && (
        <div className="text-[11px] text-slate-600">
          Vault: <span className="text-slate-500">{config.baseUrl}</span> ·
          role: <span className="text-slate-500">{config.role}</span>
        </div>
      )}
    </div>
  )
}

interface FieldProps {
  label: string
  value: string
  onCopy: () => void
  copied: boolean
  right?: React.ReactNode
}

const Field = ({ label, value, onCopy, copied, right }: FieldProps) => (
  <div>
    <div className="text-[10px] uppercase tracking-wider text-slate-500 mb-1">{label}</div>
    <div className="flex items-center gap-2 bg-slate-900 border border-slate-800 rounded px-3 py-2">
      <code className="flex-1 text-sm text-white font-mono break-all">{value}</code>
      <button
        onClick={onCopy}
        className="text-slate-400 hover:text-white p-1"
        title="Copy"
      >
        {copied ? <Check size={14} className="text-green-400" /> : <Copy size={14} />}
      </button>
      {right}
    </div>
  </div>
)

export default VaultSecretsPanel
