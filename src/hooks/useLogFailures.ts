import useFetch from "./useFetch"

const useLogFailures = (namespace?: string, service?: string) => {

  const url =
    namespace && service
      ? `http://localhost:8080/api/log-failure-detection/${namespace}/${service}`
      : null

  return useFetch(url)
}

export default useLogFailures