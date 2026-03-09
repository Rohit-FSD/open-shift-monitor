import { useEffect, useState } from "react"

const useLogFailures = () => {

  const [data, setData] = useState<any>(null)
  const [loading, setLoading] = useState(true)

  const fetchFailures = async () => {

    try {

      const res = await fetch(
        "http://localhost:8080/api/log-failure-detection"
      )

      const json = await res.json()

      setData(json)

    } catch (e) {

      console.error("Failed to load log failures")

    } finally {

      setLoading(false)

    }

  }

  useEffect(() => {

    fetchFailures()

  }, [])

  return { data, loading }

}

export default useLogFailures