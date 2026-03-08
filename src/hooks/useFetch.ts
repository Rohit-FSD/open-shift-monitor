import { useEffect, useState } from "react"

const useFetch = (url: string, refreshInterval?: number) => {

  const [data, setData] = useState<any>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<any>(null)

  const fetchData = async () => {

    try {

      const response = await fetch(url)

      if (!response.ok) {
        throw new Error("API request failed")
      }

      const json = await response.json()

      setData(json)

    } catch (err) {

      setError(err)

    } finally {

      setLoading(false)

    }

  }

  useEffect(() => {

    fetchData()

    if (!refreshInterval) return

    const interval = setInterval(fetchData, refreshInterval)

    return () => clearInterval(interval)

  }, [url, refreshInterval])

  return { data, loading, error }
}

export default useFetch