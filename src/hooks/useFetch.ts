import { useEffect, useState } from "react"

const useFetch = (url: string) => {

  const [data, setData] = useState<any>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<any>(null)

  useEffect(() => {

    const fetchData = async () => {

      try {

        setLoading(true)

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

    fetchData()

  }, [url])

  return { data, loading, error }
}

export default useFetch