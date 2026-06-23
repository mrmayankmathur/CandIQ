// Dev-only: proxy API + SSE calls to the Spring Boot backend during `browserDevelopmentRun`.
// In production the SPA is served by Spring Boot from the same origin, so this is unused.
config.devServer = config.devServer || {};
config.devServer.proxy = [
    {
        context: ["/api"],
        target: "http://localhost:8080",
        // keep SSE connections open
        onProxyReq: function () {},
    },
];
