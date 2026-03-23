import useFetch from "./useFetch"

const useLogFailures = (env?: string, service?: string) => {

  const url =
    env && service
      ? `http://localhost:8080/api/log-failure-detection/${env}/${service}`
      : null

  return useFetch(url)
}

export default useLogFailures