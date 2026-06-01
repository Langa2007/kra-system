import { defineConfig, devices } from "@playwright/test";

const port = process.env.PLAYWRIGHT_PORT ?? "3000";
const baseURL = `http://127.0.0.1:${port}`;

export default defineConfig({
  expect: {
    timeout: 10_000,
  },
  fullyParallel: false,
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"], channel: "chrome" },
    },
    {
      name: "mobile-chrome",
      use: { ...devices["Pixel 5"], channel: "chrome" },
    },
  ],
  reporter: "list",
  testDir: "./tests",
  timeout: 30_000,
  use: {
    baseURL,
    trace: "on-first-retry",
  },
  workers: 1,
  webServer: {
    command: `npm run dev -- --hostname 127.0.0.1 --port ${port}`,
    reuseExistingServer: true,
    timeout: 120_000,
    url: baseURL,
  },
});
