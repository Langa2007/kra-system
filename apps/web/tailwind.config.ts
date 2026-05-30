import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./src/**/*.{js,ts,jsx,tsx,mdx}"],
  theme: {
    extend: {
      colors: {
        ink: "#1f2933",
        paper: "#f7f8f5",
        line: "#d9ded7",
        authority: "#245c4f",
        revenue: "#9a5b13",
        exposure: "#a33b2f",
        assurance: "#3f6f3c",
      },
      boxShadow: {
        panel: "0 1px 2px rgba(31, 41, 51, 0.08)",
      },
    },
  },
  plugins: [],
};

export default config;
