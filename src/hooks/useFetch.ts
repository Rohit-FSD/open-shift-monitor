import { useEffect, useState } from "react"

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const useFetch = <T = any>(url?: string | null) => {

  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState<boolean>(false)
  const [error, setError] = useState<unknown>(null)

  const fetchData = async () => {

    // 🚫 if no url → do nothing
    if (!url) return

    try {

      setLoading(true)

      const res = await fetch(url)

      const json = await res.json()

      setData(json)

    } catch (err) {

      setError(err)

    } finally {

      setLoading(false)

    }
  }

  useEffect(() => {

    fetchData()

  }, [url])

  // 🔥 expose refetch
  const refetch = () => fetchData()

  return { data, loading, error, refetch }

}

export default useFetch