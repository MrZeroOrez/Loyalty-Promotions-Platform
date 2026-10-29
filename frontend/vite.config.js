import { defineConfig } from "vite";
export default defineConfig({
  server: {
    host: "127.0.0.1",
    port: 5181,
    strictPort: true,
    proxy: {
      "/api": "http://127.0.0.1:8091",
      "/actuator": "http://127.0.0.1:8091",
    },
  },
});
