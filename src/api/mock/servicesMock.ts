export const servicesMock = [
  {
    service: "css",
    version: "9.57.2",
    pods: "1/1",
    uptime: "5.47%",
    sla: "BREACH",

    namespace: "22429",
    replicas: "1 Ready: 1",

    containers: [
      { name: "css-ui", version: "9.39.18" },
      { name: "css", version: "9.57.2" }
    ],

    podDetails: [
      {
        name: "css-68f9d8d6fb-wcrkn",
        node: "compute-14.np1.apaas4.barclays.intranet",
        restarts: 0
      }
    ]
  },

  {
    service: "bcp-idv-service",
    version: "1.2.3",
    pods: "1/1",
    uptime: "5.47%",
    sla: "BREACH",

    namespace: "22429",
    replicas: "1 Ready: 1",

    containers: [
      { name: "idv-ui", version: "9.25.69" },
      { name: "bcp-idv-service", version: "1.2.3" }
    ],

    podDetails: [
      {
        name: "bcp-idv-service-84c99fc",
        node: "compute-4.npt.apaas4.barclays.intranet",
        restarts: 0
      }
    ]
  },

  {
    service: "css-odstest",
    version: "10.2.68",
    pods: "0/0",
    uptime: "0%",
    sla: "BREACH",

    namespace: "22429",
    replicas: "0 Ready: 0",

    containers: [
      { name: "css-ui", version: "10.3.49" },
      { name: "css", version: "10.2.68-SNAPSHOT" }
    ],

    podDetails: []
  }
]